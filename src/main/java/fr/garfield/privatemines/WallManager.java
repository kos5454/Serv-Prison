package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Système de MURS invisibles (barrières) — évite de builder des murs physiques pour délimiter une zone.
 *
 * <p>Un mur est un SEGMENT vertical défini par deux points (A, B) : un plan vertical qui va du Y le plus
 * bas au Y le plus haut des deux points. Une nappe de particules le rend visible. Tout joueur NON-OP qui
 * tente de le franchir est repoussé en arrière (téléporté juste avant la limite).</p>
 *
 * <p>Sélection via un bâton « Sélecteur de Mur » : clic GAUCHE = point 1, clic DROIT = point 2 (comme la
 * hache WorldEdit). Les murs sont nommés et persistés dans {@code walls.yml} (survivent au redémarrage).</p>
 */
public class WallManager implements Listener {

    private final PrivateMines plugin;
    private final NamespacedKey wandKey;   // marque le bâton sélecteur (anti-confusion)

    // Sélection en cours par joueur (points A et B en attente de /mur create).
    private final Map<UUID, Location> pointA = new java.util.HashMap<>();
    private final Map<UUID, Location> pointB = new java.util.HashMap<>();

    // Murs définis (nom -> Wall). LinkedHashMap pour garder l'ordre de création.
    private final Map<String, Wall> walls = new LinkedHashMap<>();

    private File file;
    private org.bukkit.configuration.file.FileConfiguration config;

    // Titres des menus /mur settings.
    private static final String LIST_TITLE   = "§8Murs » Liste";
    private static final String EDIT_PREFIX  = "§8Mur » ";        // suivi du nom
    private static final String CONFIRM_PREFIX = "§8Supprimer » "; // suivi du nom

    // Joueurs en train de REDÉFINIR un mur : UUID -> nom du mur à remplacer une fois les 2 points reposés.
    private final Map<UUID, String> redefining = new java.util.HashMap<>();

    public WallManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wall_wand");
        load();
    }

    /** Un mur = segment vertical entre deux points dans un monde donné. */
    static final class Wall {
        final String name;
        final String world;
        final double x1, z1, x2, z2;
        final int yMin, yMax;
        boolean enabled;
        // Affichage des particules : outil d'admin, NON persisté (repart caché au redémarrage).
        // false = mur totalement invisible (mais toujours bloquant) ; true = ligne dessinée en entier.
        boolean shown = false;

        Wall(String name, String world, double x1, double z1, double x2, double z2,
             int yMin, int yMax, boolean enabled) {
            this.name = name; this.world = world;
            this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
            this.yMin = yMin; this.yMax = yMax; this.enabled = enabled;
        }

    }

    // ===== Persistance =====

    private void load() {
        file = new File(plugin.getDataFolder(), "walls.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); } catch (Exception ignored) {}
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        walls.clear();
        if (config.isConfigurationSection("walls")) {
            for (String name : config.getConfigurationSection("walls").getKeys(false)) {
                String path = "walls." + name + ".";
                Wall w = new Wall(
                        name,
                        config.getString(path + "world", "world"),
                        config.getDouble(path + "x1"), config.getDouble(path + "z1"),
                        config.getDouble(path + "x2"), config.getDouble(path + "z2"),
                        config.getInt(path + "yMin"), config.getInt(path + "yMax"),
                        config.getBoolean(path + "enabled", true));
                walls.put(name.toLowerCase(), w);
            }
        }
    }

    public void save() {
        config.set("walls", null);
        for (Wall w : walls.values()) {
            String path = "walls." + w.name + ".";
            config.set(path + "world", w.world);
            config.set(path + "x1", w.x1);
            config.set(path + "z1", w.z1);
            config.set(path + "x2", w.x2);
            config.set(path + "z2", w.z2);
            config.set(path + "yMin", w.yMin);
            config.set(path + "yMax", w.yMax);
            config.set(path + "enabled", w.enabled);
        }
        try { config.save(file); } catch (Exception e) {
            plugin.getLogger().warning("Impossible de sauvegarder walls.yml : " + e.getMessage());
        }
    }

    // ===== Bâton sélecteur =====

    /** Donne au joueur le bâton « Sélecteur de Mur ». */
    public void giveWand(Player p) {
        ItemStack it = new ItemStack(Material.BREEZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§b§lSélecteur de Mur");
        m.setLore(java.util.Arrays.asList(
                "§7Clic §aGAUCHE §7sur un bloc → §aPoint 1",
                "§7Clic §eDROIT §7sur un bloc → §ePoint 2",
                "",
                "§8Puis §f/mur create <nom>"));
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.getInventory().addItem(it);
        p.sendMessage("§b✦ §7Tu as reçu le §fSélecteur de Mur§7. Clique 2 blocs (gauche puis droit).");
    }

    private boolean isWand(ItemStack it) {
        if (it == null || it.getType() != Material.BREEZE_ROD || !it.hasItemMeta()) return false;
        Byte b = it.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    // Clic gauche/droit avec le bâton → enregistre le point. Annule l'action pour ne rien casser/poser.
    @EventHandler(priority = EventPriority.HIGH)
    public void onWandInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!isWand(event.getItem())) return;
        Block b = event.getClickedBlock();
        if (b == null) return;
        event.setCancelled(true);
        Location loc = b.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pointA.put(p.getUniqueId(), loc);
            p.sendMessage("§a✔ Point 1 §7: §f" + fmt(loc));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pointB.put(p.getUniqueId(), loc);
            p.sendMessage("§e✔ Point 2 §7: §f" + fmt(loc));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.1f);
        }

        // Deux points prêts.
        if (pointA.containsKey(p.getUniqueId()) && pointB.containsKey(p.getUniqueId())) {
            previewSelection(p);
            // Cas REDÉFINITION : on remplace directement les coords du mur ciblé (le nom reste).
            String redefName = redefining.get(p.getUniqueId());
            if (redefName != null) {
                applyRedefine(p, redefName);
            } else {
                p.sendMessage("§7Deux points prêts. §f/mur create <nom> §7pour créer le mur.");
            }
        }
    }

    /** Applique la nouvelle sélection (2 points) au mur en cours de redéfinition. */
    private void applyRedefine(Player p, String name) {
        Wall old = walls.get(name.toLowerCase());
        Location a = pointA.get(p.getUniqueId());
        Location b = pointB.get(p.getUniqueId());
        if (old == null || a == null || b == null) { redefining.remove(p.getUniqueId()); return; }
        if (!a.getWorld().equals(b.getWorld())) {
            p.sendMessage("§cLes deux points doivent être dans le même monde.");
            return;
        }
        int yMin = Math.min(a.getBlockY(), b.getBlockY());
        int yMax = Math.max(a.getBlockY(), b.getBlockY());
        if (yMax - yMin < 2) yMax = yMin + 3;
        Wall w = new Wall(old.name, a.getWorld().getName(),
                a.getX(), a.getZ(), b.getX(), b.getZ(), yMin, yMax, old.enabled);
        walls.put(name.toLowerCase(), w);
        save();
        redefining.remove(p.getUniqueId());
        pointA.remove(p.getUniqueId());
        pointB.remove(p.getUniqueId());
        p.sendMessage("§a✔ Mur §f" + name + " §aredéfini ! §7(nouveau Y " + yMin + " → " + yMax + ")");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private String fmt(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    // ===== Création / gestion =====

    /** Crée un mur nommé à partir de la sélection en cours du joueur. */
    public void createWall(Player p, String name) {
        Location a = pointA.get(p.getUniqueId());
        Location b = pointB.get(p.getUniqueId());
        if (a == null || b == null) {
            p.sendMessage("§cSélectionne d'abord 2 points avec le §fSélecteur de Mur §c(clic gauche + clic droit).");
            return;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            p.sendMessage("§cLes deux points doivent être dans le même monde.");
            return;
        }
        String key = name.toLowerCase();
        if (walls.containsKey(key)) {
            p.sendMessage("§cUn mur nommé §f" + name + " §cexiste déjà. Choisis un autre nom (ou §f/mur remove " + name + "§c).");
            return;
        }
        // Hauteur du mur = du Y le plus bas au Y le plus haut des 2 points (tu contrôles en cliquant haut/bas).
        int yMin = Math.min(a.getBlockY(), b.getBlockY());
        int yMax = Math.max(a.getBlockY(), b.getBlockY());
        if (yMax - yMin < 2) yMax = yMin + 3; // au moins 3 blocs de haut
        Wall w = new Wall(name, a.getWorld().getName(),
                a.getX(), a.getZ(), b.getX(), b.getZ(), yMin, yMax, true);
        walls.put(key, w);
        save();
        p.sendMessage("§a✔ Mur §f" + name + " §acréé ! §7(ligne A→B, du Y " + yMin + " au Y " + yMax + ")");
        p.sendMessage("§7Les non-OP seront repoussés. §8/mur settings §7pour le gérer / voir la ligne.");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        pointA.remove(p.getUniqueId());
        pointB.remove(p.getUniqueId());
    }

    public void removeWall(Player p, String name) {
        if (walls.remove(name.toLowerCase()) != null) {
            save();
            p.sendMessage("§a✔ Mur §f" + name + " §asupprimé.");
        } else {
            p.sendMessage("§cAucun mur nommé §f" + name + "§c.");
        }
    }

    public void toggleWall(Player p, String name) {
        Wall w = walls.get(name.toLowerCase());
        if (w == null) { p.sendMessage("§cAucun mur nommé §f" + name + "§c."); return; }
        w.enabled = !w.enabled;
        save();
        p.sendMessage("§7Mur §f" + name + " §7: " + (w.enabled ? "§aACTIVÉ" : "§cDÉSACTIVÉ") + "§7.");
    }

    public void listWalls(Player p) {
        if (walls.isEmpty()) { p.sendMessage("§7Aucun mur défini. §8/mur wand §7pour commencer."); return; }
        p.sendMessage("§b§lMurs définis §7(" + walls.size() + ") :");
        for (Wall w : walls.values()) {
            p.sendMessage("§8• §f" + w.name + " §7" + (w.enabled ? "§a[actif]" : "§c[inactif]")
                    + " §8world=" + w.world + " Y " + w.yMin + "→" + w.yMax);
        }
    }

    /** Téléporte le joueur au milieu d'un mur (pour aller le voir). */
    public void tpWall(Player p, String name) {
        Wall w = walls.get(name.toLowerCase());
        if (w == null) { p.sendMessage("§cAucun mur nommé §f" + name + "§c."); return; }
        World world = Bukkit.getWorld(w.world);
        if (world == null) { p.sendMessage("§cMonde introuvable."); return; }
        double mx = (w.x1 + w.x2) / 2.0, mz = (w.z1 + w.z2) / 2.0;
        p.teleport(new Location(world, mx + 0.5, w.yMax + 1, mz + 0.5));
        p.sendMessage("§a✔ Téléporté au mur §f" + name + "§a.");
    }

    // ===== Aperçu de la sélection =====

    /** Aperçu temporaire (~6 s) de la ligne sélectionnée (A→B), en particules autour du joueur. */
    private void previewSelection(Player p) {
        Location a = pointA.get(p.getUniqueId());
        Location b = pointB.get(p.getUniqueId());
        if (a == null || b == null) return;
        int yMin = Math.min(a.getBlockY(), b.getBlockY());
        int yMax = Math.max(a.getBlockY(), b.getBlockY());
        if (yMax - yMin < 2) yMax = yMin + 3;
        final Wall preview = new Wall("§preview", a.getWorld().getName(),
                a.getX(), a.getZ(), b.getX(), b.getZ(), yMin, yMax, true);
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || ticks > 120) { cancel(); return; }
                drawLineAround(p, preview);
                ticks += 8;
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    /**
     * Démarre la tâche de rendu : dessine, pour chaque mur AFFICHÉ ({@code shown}), une LIGNE continue de
     * particules du point A au point B, du Y bas au Y haut. Rien n'est dessiné tant que l'affichage n'est
     * pas activé (mur invisible mais bloquant). On ne dessine que pour les joueurs proches (perf).
     */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Wall w : walls.values()) {
                if (!w.shown) continue;
                World world = Bukkit.getWorld(w.world);
                if (world == null) continue;
                for (Player p : world.getPlayers()) {
                    drawLineAround(p, w);
                }
            }
        }, 10L, 8L); // ~2,5 fois/seconde → ligne bien continue
    }

    // Portée (blocs) autour du joueur, le long de la ligne, où l'on dessine les particules. Les particules
    // ne se voient de toute façon qu'à courte distance côté client : inutile (et coûteux) de dessiner les
    // centaines de blocs qu'on ne verra pas. On dessine une FENÊTRE glissante centrée sur le joueur → où
    // qu'il aille le long du mur, il voit toujours sa portion (effet « ligne sans limite »).
    private static final double DRAW_RANGE = 40.0;

    /**
     * Dessine la portion de la ligne A→B située dans un rayon {@link #DRAW_RANGE} autour du joueur, du Y bas
     * au Y haut. Ligne pleine (pas tous les 0,5 bloc), sans trous. Rien dessiné si le joueur est trop loin
     * de la ligne. Cette fenêtre glissante fait paraître la ligne « infinie » sans lag sur les longs murs.
     */
    private void drawLineAround(Player p, Wall w) {
        double ax = w.x1 + 0.5, az = w.z1 + 0.5;
        double bx = w.x2 + 0.5, bz = w.z2 + 0.5;
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;

        // Projette le joueur sur la ligne (paramètre t clampé 0..1) → point le plus proche de lui.
        double px = p.getLocation().getX(), pz = p.getLocation().getZ();
        double tCenter = ((px - ax) * dx + (pz - az) * dz) / (len * len);
        tCenter = Math.max(0, Math.min(1, tCenter));

        // Distance du joueur à la ligne : si trop loin, on ne dessine rien (les particules seraient invisibles).
        double cx = ax + tCenter * dx, cz = az + tCenter * dz;
        double distToLine = Math.hypot(px - cx, pz - cz);
        if (distToLine > DRAW_RANGE + 8) return;

        // Fenêtre [tStart, tEnd] = ±DRAW_RANGE blocs le long de la ligne autour de la projection du joueur.
        double half = DRAW_RANGE / len;
        double tStart = Math.max(0, tCenter - half);
        double tEnd = Math.min(1, tCenter + half);

        double stepLen = 0.5, stepY = 0.5;
        double tStep = stepLen / len;
        for (double t = tStart; t <= tEnd; t += tStep) {
            double lx = ax + dx * t;
            double lz = az + dz * t;
            for (double y = w.yMin; y <= w.yMax + 1; y += stepY) {
                p.spawnParticle(org.bukkit.Particle.END_ROD, lx, y, lz, 1, 0, 0, 0, 0);
            }
        }
    }

    // ===== Anti-franchissement (repousse les non-OP qui traversent la ligne) =====

    // Distance (blocs) sous laquelle on considère que le joueur "touche" la ligne A→B et doit être repoussé.
    private static final double PUSH_RADIUS = 0.8;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;                 // les OP passent librement
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        // Optimisation : rien à faire si le joueur n'a pas changé de bloc.
        if (to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()
                && to.getBlockY() == from.getBlockY()) return;

        for (Wall w : walls.values()) {
            if (!w.enabled) continue;
            if (!w.world.equals(to.getWorld().getName())) continue;
            // Le mur bloque sur TOUTE la hauteur (peu importe le Y) : impossible de passer par-dessous
            // ni par-dessus, comme sur les vrais serveurs. La hauteur des points ne sert qu'à l'AFFICHAGE.
            double dist = distanceToSegment(to.getX(), to.getZ(),
                    w.x1 + 0.5, w.z1 + 0.5, w.x2 + 0.5, w.z2 + 0.5);
            if (dist < PUSH_RADIUS) {
                // Repousse : on renvoie le joueur à sa position précédente + léger recul opposé à la ligne.
                Location back = from.clone();
                back.setPitch(to.getPitch());
                back.setYaw(to.getYaw());
                event.setTo(back);
                Vector away = new Vector(from.getX() - to.getX(), 0, from.getZ() - to.getZ());
                if (away.lengthSquared() > 0.0001) {
                    p.setVelocity(away.normalize().multiply(0.4));
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                return;
            }
        }
    }

    /** Distance 2D (plan XZ) d'un point (px,pz) au segment (ax,az)-(bx,bz). */
    private double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1e-9) {
            double ddx = px - ax, ddz = pz - az;
            return Math.sqrt(ddx * ddx + ddz * ddz);
        }
        double t = ((px - ax) * dx + (pz - az) * dz) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx, cz = az + t * dz;
        double ddx = px - cx, ddz = pz - cz;
        return Math.sqrt(ddx * ddx + ddz * ddz);
    }

    // ===== Menu /mur settings =====

    /** Ouvre la liste cliquable des murs. */
    public void openSettings(Player player) {
        int rows = Math.max(1, (int) Math.ceil(walls.size() / 9.0));
        Inventory menu = Bukkit.createInventory(null, Math.min(54, (rows + 1) * 9), LIST_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        int slot = 0;
        for (Wall w : walls.values()) {
            if (slot >= menu.getSize() - 9) break;
            ItemStack it = new ItemStack(w.enabled ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§b§l🧱 " + w.name);
            m.setLore(java.util.Arrays.asList(
                    "§7État : " + (w.enabled ? "§a[actif]" : "§c[inactif]"),
                    "§8Monde : " + w.world,
                    "§8Point 1 : " + (int) w.x1 + ", " + w.yMax + ", " + (int) w.z1,
                    "§8Point 2 : " + (int) w.x2 + ", " + w.yMin + ", " + (int) w.z2,
                    "§8Hauteur : Y " + w.yMin + " → " + w.yMax,
                    "",
                    "§e▶ Clique pour gérer ce mur"));
            it.setItemMeta(m);
            menu.setItem(slot++, it);
        }
        // Bouton « nouveau mur » (bâton) en bas.
        ItemStack wand = new ItemStack(Material.BREEZE_ROD);
        ItemMeta wm = wand.getItemMeta();
        wm.setDisplayName("§a§l+ Nouveau mur");
        wm.setLore(java.util.Arrays.asList("§7Reçois le Sélecteur de Mur,", "§7clique 2 points puis §f/mur create <nom>"));
        wand.setItemMeta(wm);
        menu.setItem(menu.getSize() - 5, wand);

        player.openInventory(menu);
    }

    /** Ouvre le sous-menu d'édition d'un mur. */
    private void openEdit(Player player, String name) {
        Wall w = walls.get(name.toLowerCase());
        if (w == null) { player.sendMessage("§cCe mur n'existe plus."); openSettings(player); return; }
        Inventory menu = Bukkit.createInventory(null, 27, EDIT_PREFIX + w.name);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        // Info (slot 4).
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§b§l🧱 " + w.name);
        im.setLore(java.util.Arrays.asList(
                "§7État : " + (w.enabled ? "§a[actif]" : "§c[inactif]"),
                "§8Y " + w.yMin + " → " + w.yMax));
        info.setItemMeta(im);
        menu.setItem(4, info);

        // Toggle (slot 10).
        ItemStack toggle = new ItemStack(w.enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta tm = toggle.getItemMeta();
        tm.setDisplayName(w.enabled ? "§c⏻ Désactiver" : "§a⏻ Activer");
        tm.setLore(java.util.Arrays.asList("§7Allume/éteint le mur", "§7(particules + blocage)."));
        toggle.setItemMeta(tm);
        menu.setItem(10, toggle);

        // Afficher / Cacher la ligne (slot 12) — particules du point A au point B, du sol au sommet.
        ItemStack see = new ItemStack(w.shown ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta sm = see.getItemMeta();
        sm.setDisplayName(w.shown ? "§d👁 Cacher la ligne" : "§d👁 Afficher la ligne");
        sm.setLore(java.util.Arrays.asList(
                "§7Affiche/cache la ligne de particules",
                "§7du point A au point B (sol → sommet).",
                "",
                "§7Actuellement : " + (w.shown ? "§aAFFICHÉE" : "§8cachée")));
        see.setItemMeta(sm);
        menu.setItem(12, see);

        // TP Point 1 (slot 14).
        ItemStack tp1 = new ItemStack(Material.LODESTONE);
        ItemMeta t1 = tp1.getItemMeta();
        t1.setDisplayName("§b⤤ TP au Point 1");
        t1.setLore(java.util.Arrays.asList("§8" + (int) w.x1 + ", " + w.yMax + ", " + (int) w.z1));
        tp1.setItemMeta(t1);
        menu.setItem(14, tp1);

        // TP Point 2 (slot 15).
        ItemStack tp2 = new ItemStack(Material.LODESTONE);
        ItemMeta t2 = tp2.getItemMeta();
        t2.setDisplayName("§b⤥ TP au Point 2");
        t2.setLore(java.util.Arrays.asList("§8" + (int) w.x2 + ", " + w.yMin + ", " + (int) w.z2));
        tp2.setItemMeta(t2);
        menu.setItem(15, tp2);

        // Redéfinir (slot 16).
        ItemStack redef = new ItemStack(Material.BREEZE_ROD);
        ItemMeta rm = redef.getItemMeta();
        rm.setDisplayName("§e↻ Redéfinir les 2 points");
        rm.setLore(java.util.Arrays.asList(
                "§7Reçois le Sélecteur de Mur.",
                "§7Clique 2 nouveaux points → ils",
                "§7remplaceront la position du mur",
                "§7(le nom reste §f" + w.name + "§7)."));
        redef.setItemMeta(rm);
        menu.setItem(16, redef);

        // Supprimer (slot 22).
        ItemStack del = new ItemStack(Material.BARRIER);
        ItemMeta dm = del.getItemMeta();
        dm.setDisplayName("§c🗑 Supprimer");
        dm.setLore(java.util.Arrays.asList("§7Supprime ce mur définitivement.", "§8(demande confirmation)"));
        del.setItemMeta(dm);
        menu.setItem(22, del);

        // Retour (slot 18).
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7← Retour à la liste");
        back.setItemMeta(bm);
        menu.setItem(18, back);

        player.openInventory(menu);
    }

    /** Ouvre la confirmation de suppression d'un mur. */
    private void openConfirmDelete(Player player, String name) {
        Wall w = walls.get(name.toLowerCase());
        if (w == null) { player.sendMessage("§cCe mur n'existe plus."); openSettings(player); return; }
        Inventory menu = Bukkit.createInventory(null, 27, CONFIRM_PREFIX + w.name);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        ItemStack yes = new ItemStack(Material.RED_CONCRETE);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName("§c✔ Oui, supprimer §f" + w.name);
        yes.setItemMeta(ym);
        menu.setItem(11, yes);

        ItemStack no = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName("§a✘ Non, annuler");
        no.setItemMeta(nm);
        menu.setItem(15, no);

        player.openInventory(menu);
    }

    // Gestion des clics dans les menus /mur settings.
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isList = LIST_TITLE.equals(title);
        boolean isEdit = title.startsWith(EDIT_PREFIX);
        boolean isConfirm = title.startsWith(CONFIRM_PREFIX);
        if (!isList && !isEdit && !isConfirm) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (isList) {
            if (clicked.getType() == Material.BREEZE_ROD) { // nouveau mur
                p.closeInventory();
                giveWand(p);
                return;
            }
            if (clicked.getType() == Material.LIME_STAINED_GLASS || clicked.getType() == Material.RED_STAINED_GLASS) {
                String name = org.bukkit.ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).replace("🧱 ", "").trim();
                openEdit(p, name);
            }
            return;
        }

        if (isEdit) {
            String name = title.substring(EDIT_PREFIX.length());
            Wall w = walls.get(name.toLowerCase());
            if (w == null) { openSettings(p); return; }
            switch (event.getSlot()) {
                case 10: // toggle
                    w.enabled = !w.enabled; save();
                    openEdit(p, name);
                    break;
                case 12: // afficher / cacher la ligne (toggle)
                    w.shown = !w.shown;
                    p.sendMessage("§d👁 §7Ligne du mur §f" + name + " §7: "
                            + (w.shown ? "§aAFFICHÉE" : "§8cachée") + "§7.");
                    openEdit(p, name);
                    break;
                case 14: { // tp point 1
                    World world = Bukkit.getWorld(w.world);
                    if (world != null) { p.teleport(new Location(world, w.x1 + 0.5, w.yMax + 1, w.z1 + 0.5)); p.closeInventory(); }
                    break;
                }
                case 15: { // tp point 2
                    World world = Bukkit.getWorld(w.world);
                    if (world != null) { p.teleport(new Location(world, w.x2 + 0.5, w.yMin + 1, w.z2 + 0.5)); p.closeInventory(); }
                    break;
                }
                case 16: // redéfinir
                    p.closeInventory();
                    redefining.put(p.getUniqueId(), name);
                    pointA.remove(p.getUniqueId());
                    pointB.remove(p.getUniqueId());
                    giveWand(p);
                    p.sendMessage("§e↻ §7Redéfinition de §f" + name + " §7: clique 2 nouveaux points (gauche + droit).");
                    break;
                case 22: // supprimer
                    openConfirmDelete(p, name);
                    break;
                case 18: // retour
                    openSettings(p);
                    break;
                default: break;
            }
            return;
        }

        if (isConfirm) {
            String name = title.substring(CONFIRM_PREFIX.length());
            if (clicked.getType() == Material.RED_CONCRETE) {
                walls.remove(name.toLowerCase());
                save();
                p.sendMessage("§a✔ Mur §f" + name + " §asupprimé.");
                openSettings(p);
            } else if (clicked.getType() == Material.LIME_CONCRETE) {
                openEdit(p, name);
            }
        }
    }

    // ===== Dispatch de la commande /mur =====

    public boolean handleMur(Player p, String[] args) {
        if (!p.isOp()) { p.sendMessage("§cRéservé aux OP."); return true; }
        if (args.length == 0) {
            p.sendMessage("§b§lMurs invisibles §7— commandes :");
            p.sendMessage("§7/mur settings §8» menu graphique de gestion des murs");
            p.sendMessage("§7/mur wand §8» reçois le bâton sélecteur (clic gauche/droit)");
            p.sendMessage("§7/mur create <nom> §8» crée un mur depuis tes 2 points");
            p.sendMessage("§7/mur list §8» liste les murs");
            p.sendMessage("§7/mur remove <nom> §8» supprime un mur");
            p.sendMessage("§7/mur toggle <nom> §8» active/désactive un mur");
            p.sendMessage("§7/mur tp <nom> §8» te téléporte à un mur");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "settings":
            case "setting":
            case "menu":
                openSettings(p);
                return true;
            case "wand":
            case "baton":
                giveWand(p);
                return true;
            case "create":
            case "creer":
                if (args.length < 2) { p.sendMessage("§cUsage : §f/mur create <nom>"); return true; }
                createWall(p, args[1]);
                return true;
            case "remove":
            case "delete":
            case "supprimer":
                if (args.length < 2) { p.sendMessage("§cUsage : §f/mur remove <nom>"); return true; }
                removeWall(p, args[1]);
                return true;
            case "toggle":
                if (args.length < 2) { p.sendMessage("§cUsage : §f/mur toggle <nom>"); return true; }
                toggleWall(p, args[1]);
                return true;
            case "list":
            case "liste":
                listWalls(p);
                return true;
            case "tp":
                if (args.length < 2) { p.sendMessage("§cUsage : §f/mur tp <nom>"); return true; }
                tpWall(p, args[1]);
                return true;
            default:
                p.sendMessage("§cSous-commande inconnue. §f/mur §7pour l'aide.");
                return true;
        }
    }

    /** Noms des murs (pour l'auto-complétion). */
    public List<String> wallNames() {
        return new ArrayList<>(walls.keySet());
    }
}
