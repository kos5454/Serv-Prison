package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ParcelleManager {

    private static final int INITIAL_SIZE  = 10;
    private static final int GRID_SPACING  = 750; // distance entre centres de parcelles
    private static final int GRID_ORIGIN_X = 3000; // point de départ de la grille (loin du spawn/mine)
    private static final int GRID_ORIGIN_Z = 3000;
    private static final int GRID_WORLD_Y  = 64;   // hauteur du sol des parcelles

    private final PrivateMines plugin;
    private final File dataFile;
    private FileConfiguration config;

    // UUID owner -> Parcelle
    private final Map<UUID, Parcelle> parcelles = new HashMap<>();
    // Index du prochain slot de grille à attribuer
    private int nextGridIndex = 0;
    // Slots de grille libérés par une suppression d'Île : réutilisés en priorité
    // (sinon la grille se décalerait à l'infini en laissant des trous).
    private final List<Integer> freeGridIndexes = new ArrayList<>();

    public ParcelleManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "parcelles.yml");
        load();
    }

    // Retourne la parcelle d'un joueur, ou null si il n'en a pas.
    public Parcelle getParcelle(UUID owner) {
        return parcelles.get(owner);
    }

    // Toutes les parcelles connues (lecture seule) — utilisé par le classement /ob top.
    public java.util.Collection<Parcelle> getAllParcelles() {
        return parcelles.values();
    }

    // Trouve la parcelle qui contient ce point (X/Z), ou null.
    public Parcelle getParcelleAt(int x, int z) {
        for (Parcelle p : parcelles.values()) {
            if (p.contains(x, z)) return p;
        }
        return null;
    }

    // Crée et attribue une parcelle au joueur (appelé à la 1ère connexion).
    public Parcelle createParcelle(UUID owner) {
        // On réutilise d'abord un emplacement libéré par une suppression, sinon on avance dans la grille.
        int index;
        if (!freeGridIndexes.isEmpty()) index = freeGridIndexes.remove(0);
        else index = nextGridIndex++;
        int[] pos = gridPosition(index);
        Parcelle p = new Parcelle(owner, pos[0], pos[1], INITIAL_SIZE);
        parcelles.put(owner, p);
        save();
        return p;
    }

    /**
     * Supprime définitivement l'Île d'un joueur : efface la donnée (map + parcelles.yml),
     * libère son emplacement de grille pour la prochaine Île créée, et rase les blocs de la zone.
     * Le nettoyage du terrain est étalé sur plusieurs ticks pour ne pas figer le serveur.
     * Renvoie false si le joueur n'avait pas d'Île.
     */
    public boolean deleteParcelle(UUID owner) {
        Parcelle p = parcelles.remove(owner);
        if (p == null) return false;

        // Libère l'emplacement de grille (déduit de la position, pas stocké dans la parcelle).
        int index = gridIndexOf(p.getCenterX(), p.getCenterZ());
        if (index >= 0 && !freeGridIndexes.contains(index)) freeGridIndexes.add(index);

        // Efface la clé du fichier : save() ne fait qu'écrire, il n'enlève jamais rien.
        config.set(owner.toString(), null);
        save();

        // Les spawners sont des entrées de spawners.yml, pas de vrais blocs : sans ça, la tâche
        // de génération continuerait à faire apparaître des mobs sur l'emplacement rasé.
        int half = p.getSize() / 2 + 2;
        int nbSpawners = plugin.getSpawnerManager().removeSpawnersInArea(
                p.getCenterX() - half, p.getCenterX() + half,
                p.getCenterZ() - half, p.getCenterZ() + half);
        if (nbSpawners > 0) {
            plugin.getLogger().info("[Île supprimée] " + nbSpawners + " spawner(s) retiré(s).");
        }

        clearEntities(p);
        clearTerrain(p);
        return true;
    }

    /** Supprime les entités restées sur la parcelle (mobs, items au sol, cadres...) sauf les joueurs. */
    private void clearEntities(Parcelle p) {
        org.bukkit.World world = Bukkit.getWorld("world");
        if (world == null) return;
        int half = p.getSize() / 2 + 2;
        org.bukkit.Location centre = new org.bukkit.Location(world, p.getCenterX(), GRID_WORLD_Y, p.getCenterZ());
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(centre, half, 256, half)) {
            if (e instanceof org.bukkit.entity.Player) continue;
            e.remove();
        }
    }

    /** Index de grille correspondant à un centre de parcelle, ou -1 si hors grille. */
    private int gridIndexOf(int centerX, int centerZ) {
        int col = (centerX - GRID_ORIGIN_X) / GRID_SPACING;
        int row = (centerZ - GRID_ORIGIN_Z) / GRID_SPACING;
        if (col < 0 || row < 0) return -1;
        // Cohérent avec gridPosition() : 100 colonnes par ligne.
        return row * 100 + col;
    }

    /**
     * Rase la zone d'une parcelle. La taille varie de 10×10 à 100×100 (agrandissements) :
     * une grande Île représente plusieurs centaines de milliers de blocs, donc on efface
     * par petits paquets étalés sur plusieurs ticks plutôt qu'en une passe synchrone.
     *
     * Plage verticale bornée : les Îles sont construites au-dessus du sol de grille
     * (GRID_WORLD_Y), inutile de creuser tout le terrain vanilla en dessous.
     */
    private void clearTerrain(Parcelle p) {
        org.bukkit.World world = Bukkit.getWorld("world");
        if (world == null) return;

        // Marge : on nettoie un peu plus large que la taille pour emporter les bords.
        int half = p.getSize() / 2 + 2;
        final int minX = p.getCenterX() - half, maxX = p.getCenterX() + half;
        final int minZ = p.getCenterZ() - half, maxZ = p.getCenterZ() + half;
        // Quelques blocs sous le sol (fondations/grottes creusées) jusqu'au plafond de build.
        final int minY = Math.max(world.getMinHeight(), GRID_WORLD_Y - 8);
        final int maxY = world.getMaxHeight() - 1;

        // Budget constant par tick : le temps de nettoyage s'allonge avec la taille de l'Île,
        // mais le coût par tick reste le même — une Île 100×100 ne fige pas plus qu'une 10×10.
        final int BLOCS_PAR_TICK = 20_000;
        final int largeur = (maxX - minX + 1) * (maxZ - minZ + 1);

        new org.bukkit.scheduler.BukkitRunnable() {
            int y = minY;
            @Override public void run() {
                int budget = BLOCS_PAR_TICK;
                while (y <= maxY && budget > 0) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            org.bukkit.block.Block b = world.getBlockAt(x, y, z);
                            if (b.getType() != org.bukkit.Material.AIR) b.setType(org.bukkit.Material.AIR, false);
                        }
                    }
                    y++;
                    budget -= largeur;
                }
                if (y > maxY) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Position X/Z du centre de la parcelle à l'index i dans la grille.
    // On fait une spirale simple en lignes : 0,0 / 1,0 / 2,0 / ... / 0,1 / ...
    private int[] gridPosition(int index) {
        // Grille carrée : on avance ligne par ligne.
        int cols = 100; // largeur max de la grille (100 parcelles par ligne)
        int col = index % cols;
        int row = index / cols;
        return new int[]{
            GRID_ORIGIN_X + col * GRID_SPACING,
            GRID_ORIGIN_Z + row * GRID_SPACING
        };
    }

    public int getFloorY() { return GRID_WORLD_Y; }

    // Sauvegarde toutes les parcelles dans parcelles.yml.
    public void save() {
        config.set("nextGridIndex", nextGridIndex);
        config.set("freeGridIndexes", new ArrayList<>(freeGridIndexes));
        for (Map.Entry<UUID, Parcelle> entry : parcelles.entrySet()) {
            String key = entry.getKey().toString();
            Parcelle p = entry.getValue();
            config.set(key + ".centerX", p.getCenterX());
            config.set(key + ".centerZ", p.getCenterZ());
            config.set(key + ".size", p.getSize());
            config.set(key + ".maxInvited", p.getMaxInvited());
            config.set(key + ".islandName", p.getIslandName()); // null = nom par défaut
            config.set(key + ".friendsCanDoors",  p.isFriendsCanDoors());
            config.set(key + ".friendsCanBreak",  p.isFriendsCanBreak());
            config.set(key + ".friendsCanPlace",  p.isFriendsCanPlace());
            config.set(key + ".friendsCanChests", p.isFriendsCanChests());
            config.set(key + ".visitorsAllowed",    p.isVisitorsAllowed());
            config.set(key + ".visitorsCanBreak",   p.isVisitorsCanBreak());
            config.set(key + ".visitorsCanPlace",   p.isVisitorsCanPlace());
            config.set(key + ".visitorsCanChests",  p.isVisitorsCanChests());
            config.set(key + ".visitorsCanInteract",p.isVisitorsCanInteract());
            config.set(key + ".visitorsCanPvp",     p.isVisitorsCanPvp());
            List<String> invited = new ArrayList<>();
            for (UUID u : p.getInvited()) invited.add(u.toString());
            config.set(key + ".invited", invited);
            // Banque d'île.
            config.set(key + ".ironDeposited", p.getIronDeposited());
            config.set(key + ".goldDeposited", p.getGoldDeposited());
            // OneBlock (phase / blocs cassés / cycle).
            config.set(key + ".ob.phase", p.getObPhase());
            config.set(key + ".ob.blocs", p.getObBlocs());
            config.set(key + ".ob.cycle", p.getObCycle());
            config.set(key + ".ob.chest1", p.getObChest1());
            config.set(key + ".ob.chest2", p.getObChest2());
        }
        try { config.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder parcelles.yml");
        }
    }

    // Charge les parcelles depuis parcelles.yml.
    private void load() {
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(dataFile);
        nextGridIndex = config.getInt("nextGridIndex", 0);
        freeGridIndexes.clear();
        freeGridIndexes.addAll(config.getIntegerList("freeGridIndexes"));

        for (String key : config.getKeys(false)) {
            if (key.equals("nextGridIndex") || key.equals("freeGridIndexes")) continue;
            try {
                UUID owner = UUID.fromString(key);
                int cx   = config.getInt(key + ".centerX");
                int cz   = config.getInt(key + ".centerZ");
                int size = config.getInt(key + ".size", INITIAL_SIZE);
                Parcelle p = new Parcelle(owner, cx, cz, size);
                p.setMaxInvited(config.getInt(key + ".maxInvited", 1));
                p.setIslandName(config.getString(key + ".islandName", null));
                p.setFriendsCanDoors( config.getBoolean(key + ".friendsCanDoors", false));
                p.setFriendsCanBreak( config.getBoolean(key + ".friendsCanBreak", false));
                p.setFriendsCanPlace( config.getBoolean(key + ".friendsCanPlace", false));
                p.setFriendsCanChests(config.getBoolean(key + ".friendsCanChests", false));
                p.setVisitorsAllowed(    config.getBoolean(key + ".visitorsAllowed", false));
                p.setVisitorsCanBreak(   config.getBoolean(key + ".visitorsCanBreak", false));
                p.setVisitorsCanPlace(   config.getBoolean(key + ".visitorsCanPlace", false));
                p.setVisitorsCanChests(  config.getBoolean(key + ".visitorsCanChests", false));
                p.setVisitorsCanInteract(config.getBoolean(key + ".visitorsCanInteract", false));
                p.setVisitorsCanPvp(     config.getBoolean(key + ".visitorsCanPvp", false));
                List<String> invited = config.getStringList(key + ".invited");
                for (String u : invited) {
                    try { p.getInvited().add(UUID.fromString(u)); } catch (Exception ignored) {}
                }
                p.setIronDeposited(config.getLong(key + ".ironDeposited", 0));
                p.setGoldDeposited(config.getLong(key + ".goldDeposited", 0));
                // OneBlock.
                p.setObPhase(config.getInt(key + ".ob.phase", 1));
                p.setObBlocs(config.getInt(key + ".ob.blocs", 0));
                p.setObCycle(config.getInt(key + ".ob.cycle", 0));
                p.setObChest1(config.getInt(key + ".ob.chest1", 0));
                p.setObChest2(config.getInt(key + ".ob.chest2", 0));
                parcelles.put(owner, p);
            } catch (Exception ignored) {}
        }
    }
}
