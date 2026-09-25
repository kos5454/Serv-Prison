package fr.garfield.privatemines;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Gère les zones protégées : création, sélection des coins, stockage, et vérification des règles.
public class ZoneManager {

    private final PrivateMines plugin;

    // Sélection en cours par joueur (coin 1 et coin 2 avant de créer la zone).
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public ZoneManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    /**
     * PURGE PÉRIODIQUE des mobs présents dans une zone protégée.
     *
     * <p>Le listener de spawn ne bloque que la NAISSANCE d'un mob. Un creeper né hors zone qui
     * marche jusqu'au spawn n'était jamais inquiété — c'était la cause des « il y a encore des
     * mobs dans la zone du spawn ». Cette tâche fait le ménage toutes les 5 MINUTES (choix user :
     * un balayage toutes les 5 s relisait la config des milliers de fois par minute). Contrepartie
     * assumée : un mob peut vivre jusqu'à 5 min dans la zone avant d'être retiré.</p>
     *
     * <p>Ne touche jamais : les joueurs, les entités posées par le plugin (PNJ, crates, spawners
     * d'île, Caravane, faune du OneBlock — voir {@link SpawnerListener#estAuPlugin}), ni quoi que
     * ce soit dans l'End, dont l'arène et le dragon sont gérés par {@link EndManager}.</p>
     *
     * <p>Coût : on ne balaie QUE les mondes qui portent au moins une zone, et on sort
     * immédiatement s'il n'y en a aucune. Premier passage 10 s après le démarrage.</p>
     */
    public void startPurgeTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ConfigurationSection zones = plugin.getConfig().getConfigurationSection("zones");
            if (zones == null) return;
            Set<String> noms = zones.getKeys(false);
            if (noms.isEmpty()) return;

            Set<String> mondes = new java.util.HashSet<>();
            for (String name : noms) {
                String w = plugin.getConfig().getString("zones." + name + ".world");
                if (w != null && !EndManager.END_WORLD.equals(w)) mondes.add(w);
            }
            for (String nomMonde : mondes) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(nomMonde);
                if (w == null) continue;
                for (org.bukkit.entity.LivingEntity ent : w.getLivingEntities()) {
                    if (ent instanceof Player) continue;
                    if (SpawnerListener.estAuPlugin(ent)) continue;
                    String flag = SpawnerListener.flagDe(ent);
                    if (flag == null) continue;
                    if (isSpawnBlocked(ent.getLocation(), flag)) ent.remove();
                }
            }
        }, 200L, 6000L);
    }

    // ----- Sélection des coins -----
    public void setPos1(Player p, Location loc) { pos1.put(p.getUniqueId(), loc); }
    public void setPos2(Player p, Location loc) { pos2.put(p.getUniqueId(), loc); }
    public Location getPos1(Player p) { return pos1.get(p.getUniqueId()); }
    public Location getPos2(Player p) { return pos2.get(p.getUniqueId()); }

    // ----- Création d'une zone à partir de la sélection -----
    // Retourne un message d'erreur, ou null si succès.
    public String createZone(Player p, String name) {
        Location a = getPos1(p);
        Location b = getPos2(p);
        if (a == null || b == null) {
            return "Definis d'abord les 2 coins avec /zone pos1 et /zone pos2.";
        }
        if (zoneExists(name)) {
            return "Une zone nommee '" + name + "' existe deja.";
        }
        String path = "zones." + name;
        plugin.getConfig().set(path + ".world", a.getWorld().getName());
        plugin.getConfig().set(path + ".x1", a.getBlockX());
        plugin.getConfig().set(path + ".y1", a.getBlockY());
        plugin.getConfig().set(path + ".z1", a.getBlockZ());
        plugin.getConfig().set(path + ".x2", b.getBlockX());
        plugin.getConfig().set(path + ".y2", b.getBlockY());
        plugin.getConfig().set(path + ".z2", b.getBlockZ());
        // Par défaut : casse/pose/pvp bloqués (flag true = règle protégée).
        plugin.getConfig().set(path + ".flags.break", true);
        plugin.getConfig().set(path + ".flags.place", true);
        plugin.getConfig().set(path + ".flags.pvp", true);
        // Par défaut : spawn de mobs AUTORISÉ (flag false) — l'admin l'active via le menu si besoin.
        plugin.getConfig().set(path + ".flags.mobs", false);
        plugin.getConfig().set(path + ".flags.animals", false);
        plugin.saveConfig();
        return null;
    }

    public boolean zoneExists(String name) {
        return plugin.getConfig().contains("zones." + name);
    }

    public void deleteZone(String name) {
        plugin.getConfig().set("zones." + name, null);
        plugin.saveConfig();
    }

    public Set<String> getZoneNames() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("zones");
        if (sec == null) return java.util.Collections.emptySet();
        return sec.getKeys(false);
    }

    // ----- Vérification des règles -----
    // Retourne le nom de la zone qui PROTÈGE cette action à cette position, ou null si aucune.
    // flag = "break", "place" ou "pvp".
    public String getProtectingZone(Location loc, String flag) {
        ConfigurationSection zones = plugin.getConfig().getConfigurationSection("zones");
        if (zones == null) return null;

        for (String name : zones.getKeys(false)) {
            String path = "zones." + name;
            String world = plugin.getConfig().getString(path + ".world");
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) continue;

            int x1 = plugin.getConfig().getInt(path + ".x1");
            int z1 = plugin.getConfig().getInt(path + ".z1");
            int x2 = plugin.getConfig().getInt(path + ".x2");
            int z2 = plugin.getConfig().getInt(path + ".z2");

            // La zone protège TOUTE la hauteur (du bas au ciel) entre les coins X/Z.
            // On ignore le Y des coins (sinon une zone "plate" à un seul Y ne protège rien).
            boolean inside = loc.getBlockX() >= Math.min(x1, x2) && loc.getBlockX() <= Math.max(x1, x2)
                          && loc.getBlockZ() >= Math.min(z1, z2) && loc.getBlockZ() <= Math.max(z1, z2);

            if (inside && plugin.getConfig().getBoolean(path + ".flags." + flag, true)) {
                return name; // cette zone protège cette action ici
            }
        }
        return null;
    }

    // Vrai si une zone à cette position BLOQUE le spawn du flag donné ("mobs" ou "animals").
    // Défaut = false (spawn autorisé) pour ne pas bloquer les zones qui n'ont pas ce réglage.
    public boolean isSpawnBlocked(Location loc, String flag) {
        ConfigurationSection zones = plugin.getConfig().getConfigurationSection("zones");
        if (zones == null || loc.getWorld() == null) return false;
        for (String name : zones.getKeys(false)) {
            String path = "zones." + name;
            if (!loc.getWorld().getName().equals(plugin.getConfig().getString(path + ".world"))) continue;
            int x1 = plugin.getConfig().getInt(path + ".x1");
            int z1 = plugin.getConfig().getInt(path + ".z1");
            int x2 = plugin.getConfig().getInt(path + ".x2");
            int z2 = plugin.getConfig().getInt(path + ".z2");
            boolean inside = loc.getBlockX() >= Math.min(x1, x2) && loc.getBlockX() <= Math.max(x1, x2)
                          && loc.getBlockZ() >= Math.min(z1, z2) && loc.getBlockZ() <= Math.max(z1, z2);
            if (inside && plugin.getConfig().getBoolean(path + ".flags." + flag, false)) {
                return true;
            }
        }
        return false;
    }

    // Active/désactive une règle d'une zone (true = protégé).
    public void setFlag(String zone, String flag, boolean prot) {
        plugin.getConfig().set("zones." + zone + ".flags." + flag, prot);
        plugin.saveConfig();
    }

    public boolean getFlag(String zone, String flag) {
        return plugin.getConfig().getBoolean("zones." + zone + ".flags." + flag, true);
    }

    // Récupère les bornes d'une zone : [worldName, minX, minY, minZ, maxX, maxY, maxZ] ou null.
    public int[] getBounds(String name) {
        if (!zoneExists(name)) return null;
        String path = "zones." + name;
        int x1 = plugin.getConfig().getInt(path + ".x1");
        int y1 = plugin.getConfig().getInt(path + ".y1");
        int z1 = plugin.getConfig().getInt(path + ".z1");
        int x2 = plugin.getConfig().getInt(path + ".x2");
        int y2 = plugin.getConfig().getInt(path + ".y2");
        int z2 = plugin.getConfig().getInt(path + ".z2");
        return new int[] {
            Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        };
    }

    public String getWorld(String name) {
        return plugin.getConfig().getString("zones." + name + ".world");
    }

    // Renomme une zone (copie la section sous le nouveau nom, supprime l'ancienne).
    public boolean renameZone(String oldName, String newName) {
        if (!zoneExists(oldName) || zoneExists(newName)) return false;
        ConfigurationSection old = plugin.getConfig().getConfigurationSection("zones." + oldName);
        plugin.getConfig().set("zones." + newName, old);
        plugin.getConfig().set("zones." + oldName, null);
        plugin.saveConfig();
        return true;
    }
}
