package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gère des "spots" de particules décoratifs, posés par les admins via /particules.
 *
 * Chaque spot a une position et un type de particule (choisi dans une petite palette).
 * Les spots sont sauvegardés dans particles.yml et réaffichés en continu par une tâche répétée.
 *
 * Deux menus :
 *  - Menu principal : bouton "Poser ici" + liste des spots existants (clic = supprimer).
 *  - Palette : choix de l'effet à poser à la position actuelle du joueur.
 */
public class ParticleManager implements Listener {

    private final PrivateMines plugin;
    private File file;
    private FileConfiguration config;

    // Un spot de particule décoratif.
    static class Spot {
        String id;         // identifiant unique (clé de config)
        Location loc;      // position
        String particle;   // nom de la palette (ex. "AMES_BLEUES")
        Spot(String id, Location loc, String particle) {
            this.id = id; this.loc = loc; this.particle = particle;
        }
    }

    // Tous les spots posés, en mémoire (id -> spot).
    private final Map<String, Spot> spots = new LinkedHashMap<>();

    // Palette d'effets : clé interne -> (nom affiché, icône du menu, particule Bukkit).
    static class Palette {
        final String label; final Material icon; final Particle particle;
        Palette(String label, Material icon, Particle particle) {
            this.label = label; this.icon = icon; this.particle = particle;
        }
    }
    private static final Map<String, Palette> PALETTE = new LinkedHashMap<>();
    static {
        PALETTE.put("AMES_BLEUES", new Palette("§bÂmes bleues", Material.SOUL_LANTERN, Particle.SOUL_FIRE_FLAME));
        PALETTE.put("ENCHANT",     new Palette("§dRunes d'enchant", Material.ENCHANTING_TABLE, Particle.ENCHANT));
        PALETTE.put("FLAMME",      new Palette("§6Flammes", Material.CAMPFIRE, Particle.FLAME));
        PALETTE.put("COEUR",       new Palette("§cCœurs", Material.RED_DYE, Particle.HEART));
        PALETTE.put("NOTE",        new Palette("§aNotes de musique", Material.NOTE_BLOCK, Particle.NOTE));
        PALETTE.put("PORTAIL",     new Palette("§5Portail", Material.OBSIDIAN, Particle.PORTAL));
        PALETTE.put("VILLAGEOIS",  new Palette("§eJoie", Material.EMERALD, Particle.HAPPY_VILLAGER));
        PALETTE.put("FEU_AME",     new Palette("§3Feu d'âme", Material.SOUL_SAND, Particle.SOUL));
    }

    static final String MENU_TITLE    = "§b§lParticules §7» §fGestion";
    static final String PALETTE_TITLE = "§b§lParticules §7» §fChoisir l'effet";

    public ParticleManager(PrivateMines plugin) {
        this.plugin = plugin;
        load();
    }

    // ===== Sauvegarde / chargement =====

    private void load() {
        file = new File(plugin.getDataFolder(), "particles.yml");
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Impossible de créer particles.yml"); }
        }
        config = YamlConfiguration.loadConfiguration(file);
        spots.clear();
        if (config.contains("spots")) {
            for (String id : config.getConfigurationSection("spots").getKeys(false)) {
                String path = "spots." + id;
                String worldName = config.getString(path + ".world");
                World w = Bukkit.getWorld(worldName);
                if (w == null) continue;
                Location loc = new Location(w,
                        config.getDouble(path + ".x"),
                        config.getDouble(path + ".y"),
                        config.getDouble(path + ".z"));
                String particle = config.getString(path + ".particle", "AMES_BLEUES");
                spots.put(id, new Spot(id, loc, particle));
            }
        }
    }

    private void save() {
        config.set("spots", null);
        for (Spot s : spots.values()) {
            String path = "spots." + s.id;
            config.set(path + ".world", s.loc.getWorld().getName());
            config.set(path + ".x", s.loc.getX());
            config.set(path + ".y", s.loc.getY());
            config.set(path + ".z", s.loc.getZ());
            config.set(path + ".particle", s.particle);
        }
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Impossible de sauvegarder particles.yml"); }
    }

    // ===== Ajout / suppression =====

    // Pose un spot à la position du joueur (arrondie au centre du bloc, un peu au-dessus du sol).
    private void addSpot(Player p, String paletteKey) {
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5);
        // Id unique basé sur le compteur en config (pas de Date/random, indisponibles ici).
        int next = config.getInt("counter", 0) + 1;
        config.set("counter", next);
        String id = "spot" + next;
        spots.put(id, new Spot(id, loc, paletteKey));
        save();
        Palette pal = PALETTE.getOrDefault(paletteKey, PALETTE.get("AMES_BLEUES"));
        p.sendMessage("§a✔ Particule §f" + pal.label + " §aposée ici §7(" + loc.getBlockX()
                + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ").");
    }

    private void removeSpot(String id) {
        spots.remove(id);
        save();
    }

    // ===== Affichage en continu =====

    public void startParticleTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Spot s : spots.values()) {
                if (s.loc.getWorld() == null) continue;
                Palette pal = PALETTE.get(s.particle);
                if (pal == null) continue;
                for (Player p : s.loc.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(s.loc) > 60 * 60) continue;
                    p.spawnParticle(pal.particle, s.loc, 8, 0.25, 0.4, 0.25, 0.02);
                }
            }
        }, 20L, 12L); // ~0,6s
    }

    // ===== Menus =====

    public void openMenu(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, MENU_TITLE);
        // Bouton "Poser ici".
        ItemStack add = new ItemStack(Material.BLAZE_ROD);
        ItemMeta am = add.getItemMeta();
        am.setDisplayName("§a§l+ Poser une particule ici");
        List<String> aLore = new ArrayList<>();
        aLore.add("§7Pose un effet à §fta position actuelle§7.");
        aLore.add("§eClic pour choisir l'effet.");
        am.setLore(aLore);
        add.setItemMeta(am);
        menu.setItem(4, add);

        // Liste des spots posés (à partir du slot 9).
        int slot = 9;
        for (Spot s : spots.values()) {
            if (slot >= 54) break;
            Palette pal = PALETTE.getOrDefault(s.particle, PALETTE.get("AMES_BLEUES"));
            ItemStack it = new ItemStack(pal.icon);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName(pal.label + " §7#" + s.id.replace("spot", ""));
            List<String> lore = new ArrayList<>();
            lore.add("§7Position : §f" + s.loc.getBlockX() + ", " + s.loc.getBlockY()
                    + ", " + s.loc.getBlockZ());
            lore.add("§7Monde : §f" + s.loc.getWorld().getName());
            lore.add("");
            lore.add("§c§lClic pour supprimer.");
            im.setLore(lore);
            it.setItemMeta(im);
            // On mémorise l'id dans le PDC de l'item pour le retrouver au clic.
            menu.setItem(slot, tagId(it, s.id));
            slot++;
        }
        if (spots.isEmpty()) {
            ItemStack none = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta nm = none.getItemMeta();
            nm.setDisplayName("§7Aucune particule posée pour l'instant.");
            none.setItemMeta(nm);
            menu.setItem(22, none);
        }
        p.openInventory(menu);
    }

    private void openPalette(Player p) {
        Inventory menu = Bukkit.createInventory(null, 27, PALETTE_TITLE);
        int slot = 10;
        for (Map.Entry<String, Palette> e : PALETTE.entrySet()) {
            ItemStack it = new ItemStack(e.getValue().icon);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName(e.getValue().label);
            List<String> lore = new ArrayList<>();
            lore.add("§eClic pour poser cet effet ici.");
            im.setLore(lore);
            it.setItemMeta(im);
            menu.setItem(slot, tagId(it, "PAL:" + e.getKey()));
            slot++;
            if (slot == 17) slot = 19; // saute le bord
        }
        p.openInventory(menu);
    }

    // Écrit un identifiant caché dans le lore de l'item (dernière ligne invisible) pour le retrouver au clic.
    private ItemStack tagId(ItemStack it, String id) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "particle_ref"),
                org.bukkit.persistence.PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private String readId(ItemStack it) {
        if (it == null || it.getItemMeta() == null) return null;
        return it.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "particle_ref"),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    // ===== Clics dans les menus =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isMenu = MENU_TITLE.equals(title);
        boolean isPalette = PALETTE_TITLE.equals(title);
        if (!isMenu && !isPalette) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (isMenu) {
            // Bouton "Poser ici".
            if (clicked.getType() == Material.BLAZE_ROD) {
                openPalette(p);
                return;
            }
            // Sinon : clic sur un spot -> suppression.
            String id = readId(clicked);
            if (id != null && spots.containsKey(id)) {
                removeSpot(id);
                p.sendMessage("§c✖ Particule supprimée.");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1f);
                openMenu(p); // rafraîchit
            }
            return;
        }

        // Palette : pose l'effet choisi.
        String ref = readId(clicked);
        if (ref != null && ref.startsWith("PAL:")) {
            String key = ref.substring(4);
            addSpot(p, key);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            openMenu(p); // retour au menu principal
        }
    }
}
