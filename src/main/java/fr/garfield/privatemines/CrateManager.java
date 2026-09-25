package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Système de CLÉS + CRATES (coffres à récompenses).
 *
 * <p>3 rangs : Commune / Rare / Légendaire. Les clés sont stockées PAR JOUEUR (compteur, pas
 * d'item physique) et se gagnent en minant via l'enchant « Fortune des Abysses » (voir
 * {@link EnchantManager}). La commande {@code /keys} ouvre un menu compteur.</p>
 *
 * <p>Les crates sont des GROSSES TÊTES posées dans le monde (ArmorStand invisible + tête HeadDB
 * agrandie via l'attribut d'échelle 1.21). On les place avec {@code /crate sethere <rang>}.
 * <b>Clic droit</b> sur une crate = l'ouvrir (consomme 1 clé du rang), <b>clic gauche</b> =
 * prévisualiser les récompenses possibles. Les hologrammes au-dessus se posent à part avec
 * {@code /holo} (voir {@link HologramManager}).</p>
 *
 * <p>Persistance : {@code crates.yml} (positions des crates + clés de chaque joueur).</p>
 */
public class CrateManager implements Listener {

    private final PrivateMines plugin;

    public CrateManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ===== Définition des rangs de clé/crate =====

    /** Un rang de clé/crate (Commune, Rare, Légendaire). */
    static class CrateRank {
        final String key;        // identifiant technique (commune/rare/legendaire)
        final String display;    // nom coloré affiché
        final String color;      // code couleur (§...)
        final int headId;        // id HeadDB de la grosse tête (coffre thématique)
        final Material fallback; // matière de secours si HeadDB absent
        CrateRank(String key, String display, String color, int headId, Material fallback) {
            this.key = key; this.display = display; this.color = color;
            this.headId = headId; this.fallback = fallback;
        }
    }

    // IDs HeadDB des têtes de coffre par rang (fournis par le serveur).
    static final CrateRank COMMUNE = new CrateRank("commune",    "§fCoffre Commun",      "§f", 35473, Material.CHEST);
    static final CrateRank RARE    = new CrateRank("rare",       "§9Coffre Rare",       "§9", 35474, Material.ENDER_CHEST);
    static final CrateRank LEGEND  = new CrateRank("legendaire", "§6Coffre Légendaire", "§6", 35472, Material.TRAPPED_CHEST);
    static final CrateRank[] RANKS = { COMMUNE, RARE, LEGEND };

    static CrateRank rankOf(String key) {
        if (key == null) return null;
        for (CrateRank r : RANKS) if (r.key.equalsIgnoreCase(key)) return r;
        return null;
    }

    // ===== État : clés des joueurs + crates placées =====

    // clés joueur : UUID -> (rangKey -> nombre)
    private final java.util.Map<UUID, java.util.Map<String, Integer>> keys = new java.util.HashMap<>();

    /** Une crate placée dans le monde. */
    static class PlacedCrate {
        String rankKey;
        Location loc;
        float yaw;                  // orientation de la tête (0=Sud, 90=Ouest, 180=Nord, -90/270=Est)
        transient ArmorStand stand; // entité (recréée au besoin)
    }
    // crate : petit helper item multi-lignes (namedItem du core ne prend qu'une ligne de lore).
    private ItemStack menuItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && lore.length > 0) m.setLore(java.util.Arrays.asList(lore));
        it.setItemMeta(m);
        return it;
    }
    // crates : posKey "x,y,z" -> crate
    private final java.util.Map<String, PlacedCrate> crates = new java.util.LinkedHashMap<>();

    private java.io.File file;
    private org.bukkit.configuration.file.FileConfiguration config;

    private static String posKey(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ===== Accès aux clés (utilisé par l'enchant Fortune) =====

    public int getKeys(UUID id, String rankKey) {
        java.util.Map<String, Integer> m = keys.get(id);
        return m == null ? 0 : m.getOrDefault(rankKey, 0);
    }

    public void giveKey(UUID id, String rankKey, int amount) {
        if (amount == 0) return;
        java.util.Map<String, Integer> m = keys.computeIfAbsent(id, k -> new java.util.HashMap<>());
        m.merge(rankKey, amount, Integer::sum);
        if (m.get(rankKey) <= 0) m.remove(rankKey);
    }

    /** Retire une clé si disponible. Renvoie true si la clé a bien été consommée. */
    private boolean consumeKey(UUID id, String rankKey) {
        if (getKeys(id, rankKey) <= 0) return false;
        giveKey(id, rankKey, -1);
        return true;
    }

    // ===== Persistance =====

    public void load() {
        file = new java.io.File(plugin.getDataFolder(), "crates.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        // Clés des joueurs
        keys.clear();
        org.bukkit.configuration.ConfigurationSection ksec = config.getConfigurationSection("keys");
        if (ksec != null) {
            for (String uuidStr : ksec.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuidStr);
                    java.util.Map<String, Integer> m = new java.util.HashMap<>();
                    for (CrateRank r : RANKS) {
                        int n = config.getInt("keys." + uuidStr + "." + r.key, 0);
                        if (n > 0) m.put(r.key, n);
                    }
                    if (!m.isEmpty()) keys.put(id, m);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Crates placées
        crates.clear();
        org.bukkit.configuration.ConfigurationSection csec = config.getConfigurationSection("crates");
        if (csec != null) {
            for (String pk : csec.getKeys(false)) {
                String path = "crates." + pk;
                String wn = config.getString(path + ".world", "world");
                World w = Bukkit.getWorld(wn);
                if (w == null) continue;
                PlacedCrate c = new PlacedCrate();
                c.rankKey = config.getString(path + ".rank", "commune");
                c.yaw = (float) config.getDouble(path + ".yaw", 0.0);
                c.loc = new Location(w,
                        config.getDouble(path + ".x"),
                        config.getDouble(path + ".y"),
                        config.getDouble(path + ".z"));
                crates.put(posKey(c.loc), c);
            }
        }
    }

    public void save() {
        if (config == null) return;
        config.set("keys", null);
        for (java.util.Map.Entry<UUID, java.util.Map<String, Integer>> e : keys.entrySet()) {
            for (java.util.Map.Entry<String, Integer> k : e.getValue().entrySet()) {
                if (k.getValue() != null && k.getValue() > 0)
                    config.set("keys." + e.getKey() + "." + k.getKey(), k.getValue());
            }
        }
        config.set("crates", null);
        for (java.util.Map.Entry<String, PlacedCrate> e : crates.entrySet()) {
            PlacedCrate c = e.getValue();
            if (c.loc == null || c.loc.getWorld() == null) continue;
            String path = "crates." + e.getKey().replace(",", "_");
            config.set(path + ".rank", c.rankKey);
            config.set(path + ".yaw", c.yaw);
            config.set(path + ".world", c.loc.getWorld().getName());
            config.set(path + ".x", c.loc.getX());
            config.set(path + ".y", c.loc.getY());
            config.set(path + ".z", c.loc.getZ());
        }
        try { config.save(file); } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder crates.yml");
        }
    }

    // ===== Entités des crates (grosse tête via ArmorStand) =====

    private static final double CRATE_SCALE = 1.6; // échelle de la grosse tête

    private void spawnCrateEntity(PlacedCrate c) {
        if (c.loc == null || c.loc.getWorld() == null) return;
        CrateRank r = rankOf(c.rankKey);
        // Un ArmorStand porte sa tête ~1,8 bloc AU-DESSUS de sa position (× l'échelle).
        // On descend donc le stand pour que la tête repose au niveau du sol du bloc posé,
        // et on le centre sur le bloc (x+0.5, z+0.5).
        double headOffset = 1.8 * CRATE_SCALE; // hauteur du casque au-dessus des pieds
        Location at = c.loc.clone().add(0.5, -headOffset + 0.2, 0.5);
        at.setYaw(c.yaw); // orientation de la tête (Sud/Ouest/Nord/Est)
        ArmorStand stand = c.loc.getWorld().spawn(at, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setMarker(false);      // garde une hitbox cliquable
            s.setInvulnerable(true);
            s.setPersistent(false);  // on les recrée depuis crates.yml
            s.setBasePlate(false);
            s.setArms(false);
            s.setCustomNameVisible(false);
            s.setMetadata("island_crate", new org.bukkit.metadata.FixedMetadataValue(plugin, c.rankKey));
            // Grosse tête : attribut d'échelle 1.21 (≈ taille d'un bloc).
            try {
                org.bukkit.attribute.AttributeInstance scale = s.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (scale != null) scale.setBaseValue(CRATE_SCALE);
            } catch (Throwable ignored) {}
            if (s.getEquipment() != null) s.getEquipment().setHelmet(crateHead(r));
        });
        c.stand = stand;
    }

    // Renvoie la tête à afficher : tête HeadDB si dispo ET résolue, sinon un bloc-repli VISIBLE
    // (jamais un cube bleu cassé). On considère un PLAYER_HEAD nu (sans skin) comme "non résolu".
    private ItemStack crateHead(CrateRank r) {
        if (plugin.getPetHeads() != null && plugin.getPetHeads().isAvailable()) {
            ItemStack head = plugin.getPetHeads().getHead(r.headId);
            // getHead() renvoie un PLAYER_HEAD nu (cube bleu) si l'id n'est pas en cache : on refuse ce cas.
            if (head != null && head.getType() == Material.PLAYER_HEAD && head.hasItemMeta()
                    && head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm
                    && (sm.getOwnerProfile() != null || sm.getOwningPlayer() != null)) {
                return head;
            }
        }
        return new ItemStack(r.fallback); // coffre vanilla coloré par rang (toujours visible)
    }

    private void removeCrateEntity(PlacedCrate c) {
        if (c.stand != null && c.stand.isValid()) c.stand.remove();
        c.stand = null;
    }

    // ===== Hologramme auto au-dessus de la crate (via le HologramManager existant) =====

    // Nom unique et stable de l'hologramme d'une crate (retrouvable dans /holo).
    private String hologramName(PlacedCrate c) {
        return "crate_" + c.loc.getBlockX() + "_" + c.loc.getBlockY() + "_" + c.loc.getBlockZ();
    }

    // Crée (ou met à jour) l'hologramme au-dessus de la crate : nom + aide clic droit/gauche.
    private void createHologramFor(PlacedCrate c) {
        HologramManager holo = plugin.getHoloManager();
        if (holo == null) return;
        CrateRank r = rankOf(c.rankKey);
        // On place les lignes au-dessus de la grosse tête (≈ 2,3 blocs au-dessus du sol du bloc).
        Location above = c.loc.clone().add(0.5, 2.3, 0.5);
        java.util.List<String> lines = java.util.Arrays.asList(
                (r != null ? r.display.replace("§", "&") : "&fCoffre"),
                "&eClic droit &7: ouvrir",
                "&eClic gauche &7: aperçu");
        holo.setHologramLines(hologramName(c), above, lines);
        holo.save();
    }

    // Supprime l'hologramme associé à la crate.
    private void removeHologramFor(PlacedCrate c) {
        HologramManager holo = plugin.getHoloManager();
        if (holo != null) holo.removeHologram(hologramName(c));
    }

    public void spawnAllCrates() {
        for (PlacedCrate c : crates.values()) {
            if (c.loc != null && (c.stand == null || !c.stand.isValid())) spawnCrateEntity(c);
        }
    }

    /** À l'arrêt : retire les entités puis sauvegarde. */
    public void shutdown() {
        for (PlacedCrate c : crates.values()) removeCrateEntity(c);
        save();
    }

    // Recrée les crates quand leur chunk se recharge (entités non persistantes).
    @EventHandler
    public void onChunkLoadCrate(org.bukkit.event.world.ChunkLoadEvent event) {
        for (PlacedCrate c : crates.values()) {
            if (c.loc == null || c.loc.getWorld() == null) continue;
            if (!c.loc.getWorld().equals(event.getWorld())) continue;
            if ((c.loc.getBlockX() >> 4) == event.getChunk().getX()
                    && (c.loc.getBlockZ() >> 4) == event.getChunk().getZ()
                    && (c.stand == null || !c.stand.isValid())) {
                spawnCrateEntity(c);
            }
        }
    }

    // ===== Interaction : clic sur une crate =====

    @EventHandler
    public void onCrateClick(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        // Clic DROIT sur l'ArmorStand -> OUVRIR la crate.
        if (!event.getRightClicked().hasMetadata("island_crate")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String rankKey = event.getRightClicked().getMetadata("island_crate").get(0).asString();
        openCrate(player, rankOf(rankKey));
    }

    @EventHandler
    public void onCrateAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Filet de sécurité : si un clic gauche parvient quand même à cibler l'ArmorStand
        // (rare, car il est invulnérable), on ouvre l'aperçu au lieu de le laisser filer.
        if (!event.getEntity().hasMetadata("island_crate")) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        String rankKey = event.getEntity().getMetadata("island_crate").get(0).asString();
        openPreview(player, rankOf(rankKey));
    }

    // L'ArmorStand des crates est INVULNÉRABLE (incassable), donc Paper ne génère PAS
    // d'EntityDamageByEntityEvent au clic gauche. On détecte donc le clic gauche via
    // PlayerInteractEvent + un ray-trace vers l'ArmorStand de crate visé -> APERÇU.
    private static final double CRATE_REACH = 5.0;
    @EventHandler
    public void onCrateLeftClick(org.bukkit.event.player.PlayerInteractEvent event) {
        org.bukkit.event.block.Action a = event.getAction();
        if (a != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && a != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        org.bukkit.util.RayTraceResult res = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                CRATE_REACH, 0.5,
                e -> e.hasMetadata("island_crate"));
        if (res == null || res.getHitEntity() == null) return;
        if (!res.getHitEntity().hasMetadata("island_crate")) return;
        event.setCancelled(true);
        String rankKey = res.getHitEntity().getMetadata("island_crate").get(0).asString();
        openPreview(player, rankOf(rankKey));
    }

    // ===== Ouverture d'une crate (tirage récompense) =====

    private void openCrate(Player player, CrateRank r) {
        if (r == null) return;
        if (!consumeKey(player.getUniqueId(), r.key)) {
            player.sendMessage("§cIl te faut une §f" + r.display + " §cpour ouvrir ce coffre !");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        grantReward(player, r);
    }

    // ===== Table de loot par rang =====

    /** Une récompense possible d'une crate. */
    private static class Reward {
        final String type;   // money / enchant / fragments / pet / armor / key / block / tool / jackpot
        final int weight;    // poids de tirage
        final double amount; // montant (argent/fragments) ou niveaux (enchant) ou nb de clés
        final String label;  // texte affiché
        final String extra;  // paramètre libre selon le type : rang de clé, plage de rareté "min-max"…
        Reward(String type, int weight, double amount, String label) {
            this(type, weight, amount, label, null);
        }
        Reward(String type, int weight, double amount, String label, String extra) {
            this.type = type; this.weight = weight; this.amount = amount; this.label = label; this.extra = extra;
        }
    }

    // Loot de plus en plus généreux selon le rang. Valeurs = « Serv prison/RECOMPENSES_CRATES.md ».
    // Progression simple : mêmes types, montants croissants. Le total des poids n'a pas besoin
    // de faire 100 (tirage pondéré). extra : pour "key" = rang de clé ; pour "pet"/"armor" = "minRank-maxRank".
    private java.util.List<Reward> lootTable(CrateRank r) {
        java.util.List<Reward> t = new java.util.ArrayList<>();
        if (r == COMMUNE) {
            t.add(new Reward("money",     35, 4_000,  "§a4 000$"));
            t.add(new Reward("money",     20, 10_000, "§a10 000$"));
            t.add(new Reward("enchant",   15, 3,      "§d+3 niveaux d'enchant"));
            t.add(new Reward("fragments", 15, 30,     "§d30 Fragments de Souvenir ✦"));
            t.add(new Reward("key",       10, 2,      "§f2 clés Communes",   "commune"));
            t.add(new Reward("key",       5,  1,      "§91 clé Rare",        "rare"));
            t.add(new Reward("boost",     2,  0,      "§6Vente ×1,5 (5 min)", "1.5"));
        } else if (r == RARE) {
            t.add(new Reward("money",     25, 20_000, "§a20 000$"));
            t.add(new Reward("money",     15, 75_000, "§a75 000$"));
            t.add(new Reward("enchant",   20, 5,      "§d+5 niveaux d'enchant"));
            t.add(new Reward("fragments", 15, 100,    "§d100 Fragments de Souvenir ✦"));
            t.add(new Reward("pet",       8,  0,      "§5Un familier §7(commun→rare)", "0-2"));
            t.add(new Reward("armor",     5,  0,      "§5Une armure d'Oublié",         "0-2"));
            t.add(new Reward("key",       5,  2,      "§92 clés Rares",       "rare"));
            t.add(new Reward("key",       5,  1,      "§61 clé Légendaire",   "legendaire"));
            t.add(new Reward("boost",     5,  0,      "§6Vente ×2,5 (5 min)", "2.5"));
            t.add(new Reward("tool",      2,  0,      "§5⛏ Pic du Démonteur"));
        } else { // LEGEND
            t.add(new Reward("money",     20, 150_000, "§a150 000$"));
            t.add(new Reward("money",     12, 500_000, "§a500 000$"));
            t.add(new Reward("enchant",   20, 10,      "§d+10 niveaux d'enchant"));
            t.add(new Reward("fragments", 12, 250,     "§d250 Fragments de Souvenir ✦"));
            t.add(new Reward("pet",       13, 0,       "§6Un familier §7(épique→légendaire)", "3-4"));
            t.add(new Reward("armor",     10, 0,       "§6Une armure d'Oublié §7(épique→lég.)", "3-4"));
            t.add(new Reward("key",       5,  2,       "§62 clés Légendaires", "legendaire"));
            t.add(new Reward("boost",     3,  0,       "§6Vente ×5 (5 min)",   "5.0"));
            t.add(new Reward("block",     5,  0,       "§eUn bloc mystère"));
            t.add(new Reward("tool",      2,  0,       "§5⛏ Pic du Démonteur"));
            t.add(new Reward("jackpot",   1,  0,       "§6§l🌟 Jackpot ?"));
        }
        return t;
    }

    private void grantReward(Player player, CrateRank r) {
        java.util.List<Reward> table = lootTable(r);
        int total = 0;
        for (Reward rw : table) total += rw.weight;
        int roll = new java.util.Random().nextInt(Math.max(1, total));
        Reward won = table.get(table.size() - 1);
        int acc = 0;
        for (Reward rw : table) { acc += rw.weight; if (roll < acc) { won = rw; break; } }

        // L'en-tête s'affiche AVANT la récompense pour que le détail (ex. le nom de chaque
        // enchant amélioré) apparaisse dessous, dans l'encadré.
        player.sendMessage("§8§m                                        ");
        player.sendMessage("  §f✦ Tu ouvres un §f" + r.display + " §f!");
        player.sendMessage("  §7Récompense : " + won.label);

        switch (won.type) {
            case "money":
                if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, won.amount);
                break;
            case "enchant":
                grantRandomEnchantLevels(player, (int) won.amount);
                break;
            case "fragments":
                if (plugin.getFragments() != null) plugin.getFragments().addFragments(player, (long) won.amount);
                break;
            case "pet": {
                // Vrai pet ajouté au coffre Familiers (plage de rareté selon la crate).
                int[] plage = parseRange(won.extra, 0, 2);
                if (plugin.getPetMenu() != null) {
                    plugin.getPetMenu().givePetInRarityRange(player, plage[0], plage[1]);
                }
                break;
            }
            case "armor": {
                // Vraie armure d'Oublié d'une rareté tirée dans la plage.
                int[] plage = parseRange(won.extra, 0, 2);
                giveCrateArmor(player, plage[0], plage[1]);
                break;
            }
            case "key": {
                // Clé(s) d'une autre crate (rang dans extra).
                CrateRank kr = rankOf(won.extra);
                if (kr != null) giveKey(player.getUniqueId(), kr.key, (int) won.amount);
                break;
            }
            case "block":
                giveRandomBlock(player);
                break;
            case "boost": {
                // Boost VENTE ×N temporaire (5 min). Le multiplicateur est dans extra ("1.5"/"2.5"/"5.0").
                double mult = 1.5;
                try { if (won.extra != null) mult = Double.parseDouble(won.extra); } catch (NumberFormatException ignored) {}
                long dureeMs = 5L * 60L * 1000L; // 5 minutes
                plugin.applySellBoost(player, mult, dureeMs);
                String multTxt = (mult == Math.floor(mult))
                        ? String.valueOf((long) mult)
                        : String.format(java.util.Locale.US, "%.1f", mult).replace('.', ',');
                player.sendMessage("  §6⚡ Vente §e×" + multTxt + " §6activée pour §e5 minutes §6! §7(mine pour en profiter)");
                // ActionBar + son de confirmation.
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize("§6⚡ Vente ×" + multTxt + " §7— §e5:00"));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);
                break;
            }
            case "tool": {
                // Pic du Démonteur : seul outil capable de récupérer un spawner d'île posé.
                org.bukkit.inventory.ItemStack pic = plugin.getSpawnerManager().makeTool();
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste =
                        player.getInventory().addItem(pic);
                for (org.bukkit.inventory.ItemStack rem : reste.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
                break;
            }
            case "jackpot":
                grantJackpot(player);
                break;
            default:
                break;
        }
        player.sendMessage("§8§m                                        ");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    // Parse une plage "min-max" (ex "3-4") en tableau [min,max] ; défaut si invalide.
    private int[] parseRange(String s, int defMin, int defMax) {
        if (s != null && s.contains("-")) {
            try {
                String[] p = s.split("-");
                return new int[]{ Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()) };
            } catch (Exception ignored) { /* défaut ci-dessous */ }
        }
        return new int[]{ defMin, defMax };
    }

    // Donne une armure d'Oublié de rareté aléatoire dans la plage [minRank, maxRank]
    // (0=Commune … 5=Mythique). Ajoutée à l'inventaire, ou posée au sol s'il est plein.
    private void giveCrateArmor(Player player, int minRank, int maxRank) {
        if (plugin.getArmor() == null) return;
        ArmorManager.Rarete[] all = ArmorManager.Rarete.values();
        int lo = Math.max(0, Math.min(minRank, all.length - 1));
        int hi = Math.max(lo, Math.min(maxRank, all.length - 1));
        java.util.Random rng = new java.util.Random();
        ArmorManager.Rarete rarete = all[lo + rng.nextInt(hi - lo + 1)];
        org.bukkit.inventory.ItemStack armure = plugin.getArmor().genererArmurePourRarete(rarete, rng);
        if (armure == null) return;
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste = player.getInventory().addItem(armure);
        for (org.bukkit.inventory.ItemStack rem : reste.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rem);
        }
        player.sendMessage("  §8» " + rarete.display + " §8armure d'Oublié");
    }

    // Le JACKPOT (Légendaire, très rare) : les 3 gros lots d'un coup.
    // Raretés « Légendaire » = index 4 (Rarete.LEGENDAIRE ; pet arc = Baleine/Léviathan).
    private void grantJackpot(Player player) {
        if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, 1_000_000);
        giveCrateArmor(player, 4, 4);                 // armure Légendaire garantie
        if (plugin.getPetMenu() != null) plugin.getPetMenu().givePetInRarityRange(player, 4, 4); // pet Légendaire
        player.sendMessage("  §6§l🌟 JACKPOT : §a1 000 000$ §7+ §6armure §7+ §5familier §6légendaires §6!");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // Un enchant candidat au tirage : sa clé, son nom affiché, le niveau de pioche requis
    // et son plafond RÉEL (chaque enchant a le sien : Dîme 25, Vol 3, Célérité 3, etc.).
    private static final class EnchantCible {
        final String cle, nom; final int req, max;
        EnchantCible(String cle, String nom, int req, int max) {
            this.cle = cle; this.nom = nom; this.req = req; this.max = max;
        }
    }

    // TOUS les enchants de la pioche, avec leur prérequis et leur plafond.
    // Le Vol est volontairement exclu : il demande aussi d'avoir trouvé la Plume,
    // le gagner par crate contournerait cette quête.
    private static final EnchantCible[] TOUS_ENCHANTS = {
            new EnchantCible("efficiency",  "§fEfficacité",            EnchantManager.REQ_EFFICIENCY,  EnchantManager.EFFICIENCY_MAX),
            new EnchantCible("vein",        "§bVein Miner",            EnchantManager.REQ_VEIN,        EnchantManager.VEIN_MAX),
            new EnchantCible("forage",      "§dForage",                EnchantManager.REQ_FORAGE,      EnchantManager.FORAGE_MAX),
            new EnchantCible("colonne",     "§3Colonne d'Écume",       EnchantManager.REQ_COLONNE,     EnchantManager.COLONNE_MAX),
            new EnchantCible("harpon",      "§9Pluie de Harpons",      EnchantManager.REQ_HARPON,      EnchantManager.HARPON_MAX),
            new EnchantCible("explosion",   "§cExplosion",             EnchantManager.REQ_EXPLOSION,   EnchantManager.EXPLOSION_MAX),
            new EnchantCible("fracture",    "§6Fracture",              EnchantManager.REQ_FRACTURE,    EnchantManager.FRACTURE_MAX),
            new EnchantCible("fortune",     "§eFortune des Abysses",   EnchantManager.REQ_FORTUNE,     EnchantManager.FORTUNE_MAX),
            new EnchantCible("dime",        "§6Dîme du Passeur",       EnchantManager.REQ_DIME,        EnchantManager.DIME_MAX),
            new EnchantCible("memoire",     "§aMémoire de la Roche",   EnchantManager.REQ_MEMOIRE,     EnchantManager.MEMOIRE_MAX),
            new EnchantCible("cyclone",     "§bCyclone",               EnchantManager.REQ_CYCLONE,     EnchantManager.CYCLONE_MAX),
            new EnchantCible("reflux",      "§3Reflux",                EnchantManager.REQ_REFLUX,      EnchantManager.REFLUX_MAX),
            new EnchantCible("gouffre",     "§8Gouffre",               EnchantManager.REQ_GOUFFRE,     EnchantManager.GOUFFRE_MAX),
            new EnchantCible("contrebande", "§eSel de Contrebande",    EnchantManager.REQ_CONTREBANDE, EnchantManager.CONTREBANDE_MAX),
            new EnchantCible("haste",       "§eCélérité",              EnchantManager.REQ_HASTE,       EnchantManager.HASTE_MAX),
            new EnchantCible("fleche",      "§9Pluie de Flèches",      EnchantManager.REQ_FLECHE,      EnchantManager.FLECHE_MAX),
            new EnchantCible("tnt",         "§cPluie de TNT",          EnchantManager.REQ_TNT,         EnchantManager.TNT_MAX)
    };

    // Niveau actuel d'un enchant pour ce joueur.
    private int niveauActuel(EnchantManager em, Player p, String cle) {
        switch (cle) {
            case "efficiency":  return em.getEfficiencyLevel(p);
            case "vein":        return em.getVeinLevel(p);
            case "forage":      return em.getForageLevel(p);
            case "colonne":     return em.getColonneLevel(p);
            case "harpon":      return em.getHarponLevel(p);
            case "explosion":   return em.getExplosionLevel(p);
            case "fracture":    return em.getFractureLevel(p);
            case "fortune":     return em.getFortuneLevel(p);
            case "dime":        return em.getDimeLevel(p);
            case "memoire":     return em.getMemoireLevel(p);
            case "cyclone":     return em.getCycloneLevel(p);
            case "reflux":      return em.getRefluxLevel(p);
            case "gouffre":     return em.getGouffreLevel(p);
            case "contrebande": return em.getContrebandeLevel(p);
            case "haste":       return em.getHasteLevel(p);
            case "fleche":      return em.getFlecheLevel(p);
            default:            return em.getTntLevel(p);
        }
    }

    // Écrit le nouveau niveau d'un enchant.
    private void ecrireNiveau(EnchantManager em, UUID id, String cle, int lvl) {
        switch (cle) {
            case "efficiency":  em.setEfficiencyLevel(id, lvl); break;
            case "vein":        em.setVeinLevel(id, lvl); break;
            case "forage":      em.setForageLevel(id, lvl); break;
            case "colonne":     em.setColonneLevel(id, lvl); break;
            case "harpon":      em.setHarponLevel(id, lvl); break;
            case "explosion":   em.setExplosionLevel(id, lvl); break;
            case "fracture":    em.setFractureLevel(id, lvl); break;
            case "fortune":     em.setFortuneLevel(id, lvl); break;
            case "dime":        em.setDimeLevel(id, lvl); break;
            case "memoire":     em.setMemoireLevel(id, lvl); break;
            case "cyclone":     em.setCycloneLevel(id, lvl); break;
            case "reflux":      em.setRefluxLevel(id, lvl); break;
            case "gouffre":     em.setGouffreLevel(id, lvl); break;
            case "contrebande": em.setContrebandeLevel(id, lvl); break;
            case "haste":       em.setHasteLevel(id, lvl); break;
            case "fleche":      em.setFlecheLevel(id, lvl); break;
            default:            em.setTntLevel(id, lvl); break;
        }
    }

    // Donne des niveaux répartis sur les enchants DÉBLOQUÉS et NON AU MAX de la pioche.
    // Deux vérifications à chaque tirage :
    //   1) niveau de pioche suffisant (sinon on offrirait un niveau invisible et inutilisable) ;
    //   2) enchant pas déjà à son plafond PROPRE (Dîme 25, Célérité 3, Efficacité 100, …).
    // Chaque niveau attribué est annoncé dans le chat, ligne par ligne.
    public void grantRandomEnchantLevels(Player player, int levels) {
        EnchantManager em = plugin.getEnchants();
        if (em == null) return;
        int pioche = plugin.getPickaxeLevel(player);
        UUID id = player.getUniqueId();
        java.util.Random rng = new java.util.Random();

        int donnes = 0;
        for (int i = 0; i < levels; i++) {
            // La liste est recalculée à chaque tour : un enchant qui vient d'atteindre son
            // plafond sort automatiquement du tirage suivant.
            java.util.List<EnchantCible> dispo = new java.util.ArrayList<>();
            for (EnchantCible e : TOUS_ENCHANTS) {
                if (pioche < e.req) continue;                          // pas encore débloqué
                if (niveauActuel(em, player, e.cle) >= e.max) continue; // déjà au maximum
                dispo.add(e);
            }
            if (dispo.isEmpty()) break; // plus rien à améliorer

            EnchantCible choisi = dispo.get(rng.nextInt(dispo.size()));
            int nouveau = Math.min(choisi.max, niveauActuel(em, player, choisi.cle) + 1);
            ecrireNiveau(em, id, choisi.cle, nouveau);
            player.sendMessage("§7  §8• §a+1 niveau §7pour " + choisi.nom + " §8(niv. " + nouveau + ")");
            donnes++;
        }

        // Rien n'a pu être donné (aucun enchant débloqué, ou tous au max) :
        // on convertit en argent pour que la récompense ne soit pas perdue.
        if (donnes < levels) {
            int perdus = levels - donnes;
            double lot = 5_000.0 * perdus;
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, lot);
            player.sendMessage("§7  §8• §7" + perdus + " niveau(x) sans cible → §a"
                    + PrivateMines.formatNumber(lot) + "$");
        }
    }

    // ===== Bloc mystère : 1 bloc au hasard parmi TOUS les blocs du jeu =====
    // Liste noire = les blocs cochés dans « Serv prison/RECOMPENSES_CRATES.md » :
    // items fantômes (air/fluides), techniques (portails, spawner, structure…), infestés,
    // barrier/light. RESTENT dans le tirage : COMMAND_BLOCK et BEDROCK (collector, rendus
    // posables + récupérables par le système « incassable de crate »), reinforced_deepslate,
    // beacon, dragon egg, blocs précieux… → un bloc collector a naturellement ~1 chance sur 1000+.
    private static final java.util.Set<Material> BLOCS_EXCLUS = new java.util.HashSet<>(java.util.Arrays.asList(
            // Groupe A cochés (command block SIMPLE reste autorisé ; chain/repeating/minecart exclus)
            Material.REPEATING_COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.JIGSAW,
            Material.BARRIER, Material.LIGHT,
            Material.SPAWNER,
            Material.END_GATEWAY, Material.NETHER_PORTAL,
            Material.END_PORTAL, Material.END_PORTAL_FRAME, // pas d'item obtenable
            Material.BUDDING_AMETHYST,
            // Groupe B (blocs fantômes) — tout coché
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN,
            Material.FIRE, Material.SOUL_FIRE,
            Material.PISTON_HEAD, Material.MOVING_PISTON,
            Material.POWDER_SNOW, Material.FROSTED_ICE,
            Material.INFESTED_STONE, Material.INFESTED_COBBLESTONE, Material.INFESTED_STONE_BRICKS,
            Material.INFESTED_MOSSY_STONE_BRICKS, Material.INFESTED_CRACKED_STONE_BRICKS,
            Material.INFESTED_CHISELED_STONE_BRICKS, Material.INFESTED_DEEPSLATE,
            // Groupe D coché
            Material.PLAYER_HEAD
            // NB : BEDROCK volontairement PAS exclu (collector cassable via crate).
    ));

    // Cache de la liste des blocs valides (calculée une fois).
    private static java.util.List<Material> BLOCS_VALIDES = null;

    private static java.util.List<Material> blocsValides() {
        if (BLOCS_VALIDES == null) {
            java.util.List<Material> l = new java.util.ArrayList<>();
            for (Material m : Material.values()) {
                if (m.isLegacy()) continue;          // ignore les anciens matériaux legacy
                if (!m.isBlock()) continue;          // uniquement de vrais blocs posables
                if (!m.isItem()) continue;           // qui existent aussi en item (sinon rien à donner)
                if (BLOCS_EXCLUS.contains(m)) continue;
                l.add(m);
            }
            BLOCS_VALIDES = l;
        }
        return BLOCS_VALIDES;
    }

    private void giveRandomBlock(Player player) {
        java.util.List<Material> pool = blocsValides();
        if (pool.isEmpty()) return;
        Material mat = pool.get(new java.util.Random().nextInt(pool.size()));
        ItemStack bloc = new ItemStack(mat, 1);
        // Si c'est un incassable géré (command block / bedrock), on le tamponne pour qu'il soit
        // posable ET récupérable (système CrateBlockListener).
        if (CrateBlockListener.estConcerne(mat) && plugin.getCrateBlocks() != null) {
            bloc = plugin.getCrateBlocks().tag(bloc);
        }
        java.util.Map<Integer, ItemStack> reste = player.getInventory().addItem(bloc);
        for (ItemStack rem : reste.values()) player.getWorld().dropItemNaturally(player.getLocation(), rem);
        // Nom lisible du bloc (ex STONE_BRICKS → "Stone Bricks").
        String nom = mat.name().toLowerCase().replace('_', ' ');
        player.sendMessage("  §8» §f" + nom);
    }

    // ===== Menu /keys (compteur) =====

    static final String KEYS_TITLE = "§6§lMes Clés";

    public void openKeys(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, KEYS_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, bg);
        int[] slots = {11, 13, 15};
        for (int i = 0; i < RANKS.length; i++) {
            CrateRank r = RANKS[i];
            int n = getKeys(player.getUniqueId(), r.key);
            ItemStack head = plugin.getPetHeads() != null && plugin.getPetHeads().isAvailable()
                    ? plugin.getPetHeads().getHead(r.headId) : new ItemStack(Material.TRIPWIRE_HOOK);
            ItemMeta m = head.getItemMeta();
            m.setDisplayName(r.color + "§lClé " + r.display.replaceAll("§.", "").replace("Coffre ", ""));
            m.setLore(java.util.Arrays.asList(
                    "§7Tu en possèdes : " + r.color + "§l" + n,
                    "",
                    "§7Ouvre le §f" + r.display + " §7correspondant",
                    "§7en cliquant dessus au spawn.",
                    "§8Gagne des clés en minant (Fortune des Abysses).",
                    "",
                    "§e/crates"));
            head.setItemMeta(m);
            menu.setItem(slots[i], head);
        }
        player.openInventory(menu);
    }

    @EventHandler
    public void onKeysMenuClick(InventoryClickEvent event) {
        if (KEYS_TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    // ===== Menu aperçu des récompenses (clic droit) =====

    private void openPreview(Player player, CrateRank r) {
        if (r == null) return;
        // Inventaire de 45 slots (5 rangées) : de quoi afficher TOUTES les récompenses,
        // même le Légendaire qui en a 11. Les récompenses vont sur les rangées 2 à 4.
        Inventory menu = Bukkit.createInventory(null, 45, previewTitle(r));
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);
        java.util.List<Reward> table = lootTable(r);
        // Slots centraux des rangées 2, 3 et 4 (7 par rangée = 21 emplacements possibles).
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = 0; i < table.size() && i < slots.length; i++) {
            Reward rw = table.get(i);
            // On affiche la valeur du tableau telle quelle en % (35 -> "35%"), sans la recalculer.
            Material icon = iconFor(rw.type);
            ItemStack it = new ItemStack(icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(rw.label);
            m.setLore(java.util.Arrays.asList("§7Chance : §e" + rw.weight + "%"));
            it.setItemMeta(m);
            menu.setItem(slots[i], it);
        }
        int n = getKeys(player.getUniqueId(), r.key);
        menu.setItem(40, plugin.namedItem(Material.TRIPWIRE_HOOK,
                r.color + "Tes clés : §l" + n,
                "§7Clic droit sur le coffre pour l'ouvrir."));
        player.openInventory(menu);
    }

    // Icône d'aperçu selon le type de récompense.
    private Material iconFor(String type) {
        switch (type) {
            case "money":     return Material.GOLD_NUGGET;
            case "enchant":   return Material.ENCHANTED_BOOK;
            case "fragments": return Material.AMETHYST_SHARD;
            case "pet":       return Material.PLAYER_HEAD;
            case "armor":     return Material.DIAMOND_CHESTPLATE;
            case "key":       return Material.TRIPWIRE_HOOK;
            case "block":     return Material.GRASS_BLOCK;
            case "boost":     return Material.CLOCK;
            case "tool":      return Material.NETHERITE_PICKAXE;
            case "jackpot":   return Material.NETHER_STAR;
            default:          return Material.CHEST;
        }
    }

    private String previewTitle(CrateRank r) {
        return "§8Aperçu » " + r.display;
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title != null && title.startsWith("§8Aperçu » ")) event.setCancelled(true);
    }

    // ===== Menu /crate settings (voir / déplacer les crates) =====

    static final String SETTINGS_TITLE = "§5§lCrates §7» Gestion";
    // Crate sélectionnée par joueur (posKey) pour le bouton « Déplacer ici ».
    private final java.util.Map<UUID, String> settingsSelected = new java.util.HashMap<>();
    // Association slot -> posKey de la crate affichée (par joueur), pour retrouver la crate cliquée.
    private final java.util.Map<UUID, java.util.Map<Integer, String>> settingsSlotMap = new java.util.HashMap<>();

    public void openSettings(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, SETTINGS_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        java.util.Map<Integer, String> slotMap = new java.util.HashMap<>();
        int slot = 0;
        for (java.util.Map.Entry<String, PlacedCrate> e : crates.entrySet()) {
            if (slot >= 45) break; // 45 emplacements max (5 rangées) ; la 6e est réservée aux boutons
            PlacedCrate c = e.getValue();
            CrateRank r = rankOf(c.rankKey);
            ItemStack icon = crateHead(r);
            ItemMeta m = icon.getItemMeta();
            m.setDisplayName((r != null ? r.display : c.rankKey));
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Position : §f" + c.loc.getBlockX() + ", " + c.loc.getBlockY() + ", " + c.loc.getBlockZ());
            lore.add("§7Monde : §f" + (c.loc.getWorld() != null ? c.loc.getWorld().getName() : "?"));
            lore.add("");
            boolean selected = e.getKey().equals(settingsSelected.get(player.getUniqueId()));
            lore.add(selected ? "§a✔ Sélectionnée" : "§eClic §7» s'y téléporter et la sélectionner");
            m.setLore(lore);
            if (selected) {
                m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            icon.setItemMeta(m);
            menu.setItem(slot, icon);
            slotMap.put(slot, e.getKey());
            slot++;
        }
        settingsSlotMap.put(player.getUniqueId(), slotMap);

        if (crates.isEmpty()) {
            menu.setItem(22, menuItem(Material.BARRIER, "§cAucune crate placée",
                    "§7Pose-en une avec §e/crate sethere <rang>"));
        }

        // Barre de boutons (dernière rangée).
        String sel = settingsSelected.get(player.getUniqueId());
        boolean hasSel = sel != null && crates.containsKey(sel);
        menu.setItem(49, menuItem(Material.ARROW, "§7Fermer"));
        if (hasSel) {
            PlacedCrate c = crates.get(sel);
            CrateRank r = rankOf(c.rankKey);
            menu.setItem(47, menuItem(Material.ENDER_PEARL, "§b⤓ Déplacer ici",
                    "§7Ramène la crate sélectionnée (" + (r != null ? r.display : c.rankKey) + "§7)",
                    "§7à ta position actuelle (hologramme inclus)."));
            menu.setItem(48, menuItem(Material.COMPASS, "§e⟳ Orienter : §f" + facingName(c.yaw),
                    "§7Clic §7» tourner d'un quart (Sud → Ouest → Nord → Est).",
                    "§7Fais face à la crate pour voir le coffre s'orienter."));
            menu.setItem(51, menuItem(Material.LAVA_BUCKET, "§c🗑 Supprimer la sélectionnée"));
        } else {
            menu.setItem(47, menuItem(Material.GRAY_DYE, "§8Déplacer ici",
                    "§7Sélectionne d'abord une crate (clic dessus)."));
            menu.setItem(48, menuItem(Material.GRAY_DYE, "§8Orienter",
                    "§7Sélectionne d'abord une crate (clic dessus)."));
        }
        player.openInventory(menu);
    }

    @EventHandler
    public void onSettingsClick(InventoryClickEvent event) {
        if (!SETTINGS_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        String sel = settingsSelected.get(player.getUniqueId());

        // Bouton « Déplacer ici ».
        if (slot == 47) {
            if (sel == null || !crates.containsKey(sel)) { player.sendMessage("§eSélectionne d'abord une crate."); return; }
            PlacedCrate c = crates.get(sel);
            moveCrate(c, player.getLocation());
            settingsSelected.put(player.getUniqueId(), posKey(c.loc)); // la clé de position a changé
            player.sendMessage("§aCrate déplacée à ta position.");
            openSettings(player);
            return;
        }
        // Bouton « Orienter » : tourne la crate d'un quart de tour (Sud→Ouest→Nord→Est).
        if (slot == 48) {
            if (sel == null || !crates.containsKey(sel)) { player.sendMessage("§eSélectionne d'abord une crate."); return; }
            PlacedCrate c = crates.get(sel);
            c.yaw = nextFacingYaw(c.yaw);
            removeCrateEntity(c);
            spawnCrateEntity(c);
            save();
            player.sendMessage("§aCoffre orienté vers §f" + facingName(c.yaw) + "§a.");
            openSettings(player);
            return;
        }
        // Bouton « Supprimer la sélectionnée ».
        if (slot == 51) {
            if (sel == null || !crates.containsKey(sel)) return;
            PlacedCrate c = crates.get(sel);
            removeCrateEntity(c);
            removeHologramFor(c);
            crates.remove(sel);
            settingsSelected.remove(player.getUniqueId());
            save();
            player.sendMessage("§aCrate supprimée.");
            openSettings(player);
            return;
        }

        // Clic sur une crate de la liste -> TP + sélection.
        java.util.Map<Integer, String> slotMap = settingsSlotMap.get(player.getUniqueId());
        if (slotMap != null && slotMap.containsKey(slot)) {
            String posKey = slotMap.get(slot);
            PlacedCrate c = crates.get(posKey);
            if (c == null || c.loc == null) return;
            settingsSelected.put(player.getUniqueId(), posKey);
            // TP à côté de la crate (2 blocs de recul, à hauteur du sol).
            Location tp = c.loc.clone().add(0.5, 0.0, 2.5);
            tp.setDirection(c.loc.clone().add(0.5, 1, 0.5).toVector().subtract(tp.toVector()));
            player.teleport(tp);
            player.sendMessage("§7Téléporté à la crate. Utilise §b⤓ Déplacer ici §7pour la ramener où tu veux.");
            openSettings(player);
        }
    }

    // Yaw Minecraft : 0=Sud, 90=Ouest, 180=Nord, 270=Est. On cycle par quart de tour.
    private float nextFacingYaw(float yaw) {
        int q = Math.round(yaw / 90f) & 3; // quart courant (0..3)
        return ((q + 1) & 3) * 90f;        // quart suivant
    }
    private String facingName(float yaw) {
        int q = Math.round(yaw / 90f) & 3;
        switch (q) {
            case 0:  return "Sud";
            case 1:  return "Ouest";
            case 2:  return "Nord";
            default: return "Est";
        }
    }

    // Déplace une crate (entité + hologramme + clé de position) vers une nouvelle localisation.
    private void moveCrate(PlacedCrate c, Location dest) {
        removeCrateEntity(c);
        removeHologramFor(c);
        crates.remove(posKey(c.loc));
        c.loc = dest.getBlock().getLocation();
        crates.put(posKey(c.loc), c);
        spawnCrateEntity(c);
        createHologramFor(c);
        save();
    }

    // ===== Commande /crate (OP) =====

    public boolean handleCrate(Player player, String[] args) {
        if (!player.isOp()) { player.sendMessage("§cCommande réservée aux opérateurs."); return true; }
        if (args.length == 0) {
            player.sendMessage("§6Crates : §e/crate sethere <commune|rare|legendaire>");
            player.sendMessage("§7Autres : §e/crate settings §7(menu) §7• §e/crate remove §7• §e/crate list");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "settings":
            case "setting": {
                openSettings(player);
                return true;
            }
            case "sethere": {
                if (args.length < 2) { player.sendMessage("§cUsage : /crate sethere <commune|rare|legendaire>"); return true; }
                CrateRank r = rankOf(args[1]);
                if (r == null) { player.sendMessage("§cRang inconnu. §7commune / rare / legendaire"); return true; }
                Location loc = player.getLocation().getBlock().getLocation();
                PlacedCrate c = new PlacedCrate();
                c.rankKey = r.key;
                c.loc = loc;
                crates.put(posKey(loc), c);
                spawnCrateEntity(c);
                createHologramFor(c);
                save();
                player.sendMessage("§a" + r.display + " §aposée ici, avec son hologramme. §7(éditable via /holo)");
                return true;
            }
            case "remove": {
                // On supprime la crate la PLUS PROCHE du joueur (dans 8 blocs) : bien plus fiable que viser.
                PlacedCrate near = getNearestCrate(player, 8.0);
                if (near == null) { player.sendMessage("§cAucune crate à moins de 8 blocs. Approche-toi et réessaie."); return true; }
                CrateRank r = rankOf(near.rankKey);
                removeCrateEntity(near);
                removeHologramFor(near);
                crates.remove(posKey(near.loc));
                save();
                player.sendMessage("§a" + (r != null ? r.display : near.rankKey) + " §asupprimée §8("
                        + near.loc.getBlockX() + ", " + near.loc.getBlockY() + ", " + near.loc.getBlockZ() + ").");
                return true;
            }
            case "list": {
                if (crates.isEmpty()) { player.sendMessage("§7Aucune crate placée."); return true; }
                player.sendMessage("§6Crates (" + crates.size() + ") :");
                for (PlacedCrate c : crates.values()) {
                    CrateRank r = rankOf(c.rankKey);
                    player.sendMessage("§7• " + (r != null ? r.display : c.rankKey) + " §8("
                            + c.loc.getBlockX() + ", " + c.loc.getBlockY() + ", " + c.loc.getBlockZ() + ")");
                }
                return true;
            }
            case "clearall": {
                // Supprime TOUTES les crates (dépannage). Retire aussi les entités ArmorStand orphelines proches.
                int count = crates.size();
                for (PlacedCrate c : crates.values()) { removeCrateEntity(c); removeHologramFor(c); }
                crates.clear();
                save();
                // Nettoyage de sécurité : retire les ArmorStands "island_crate" dans 16 blocs autour.
                for (org.bukkit.entity.Entity e : player.getNearbyEntities(16, 16, 16)) {
                    if (e.hasMetadata("island_crate")) e.remove();
                }
                player.sendMessage("§a" + count + " crate(s) supprimée(s) + entités proches nettoyées.");
                return true;
            }
            case "givekey":
            case "givekeys": {
                // /crate givekey <joueur> <rang> [nombre] — pour tester / récompenser.
                if (args.length < 3) { player.sendMessage("§cUsage : /crate givekey <joueur> <rang> [nombre]"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage("§cJoueur introuvable."); return true; }
                CrateRank r = rankOf(args[2]);
                if (r == null) { player.sendMessage("§cRang inconnu."); return true; }
                int amount = 1;
                if (args.length >= 4) { try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {} }
                giveKey(target.getUniqueId(), r.key, amount);
                save();
                player.sendMessage("§aDonné §e" + amount + " " + r.display + " §aà §f" + target.getName() + "§a.");
                target.sendMessage("§aTu as reçu §e" + amount + " " + r.display + " §a!");
                return true;
            }
            case "boost": {
                // /crate boost <mult> [minutes] [joueur] — active un boost de vente pour tester.
                if (args.length < 2) { player.sendMessage("§cUsage : /crate boost <mult> [minutes] [joueur]"); return true; }
                double mult;
                try { mult = Double.parseDouble(args[1].replace(',', '.')); }
                catch (NumberFormatException e) { player.sendMessage("§cMultiplicateur invalide (ex : 2.5)."); return true; }
                if (mult < 1.0) { player.sendMessage("§cLe multiplicateur doit être ≥ 1."); return true; }
                int minutes = 5;
                if (args.length >= 3) { try { minutes = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {} }
                Player cible = player;
                if (args.length >= 4) {
                    Player t = Bukkit.getPlayerExact(args[3]);
                    if (t == null) { player.sendMessage("§cJoueur introuvable."); return true; }
                    cible = t;
                }
                plugin.applySellBoost(cible, mult, (long) minutes * 60L * 1000L);
                String multTxt = (mult == Math.floor(mult)) ? String.valueOf((long) mult)
                        : String.format(java.util.Locale.US, "%.1f", mult).replace('.', ',');
                cible.sendMessage("§6⚡ Boost de vente §e×" + multTxt + " §6activé pour §e" + minutes + " min §6!");
                cible.playSound(cible.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);
                if (cible != player) player.sendMessage("§aBoost ×" + multTxt + " (" + minutes + " min) donné à §f" + cible.getName() + "§a.");
                return true;
            }
            case "testblock":
            case "testblocks": {
                // /crate testblock — se donner les 2 blocs incassables MARQUÉS (comme via crate).
                if (plugin.getCrateBlocks() == null) { player.sendMessage("§cSystème indisponible."); return true; }
                org.bukkit.inventory.ItemStack cmd = plugin.getCrateBlocks().tag(new org.bukkit.inventory.ItemStack(Material.COMMAND_BLOCK, 1));
                org.bukkit.inventory.ItemStack bed = plugin.getCrateBlocks().tag(new org.bukkit.inventory.ItemStack(Material.BEDROCK, 1));
                for (org.bukkit.inventory.ItemStack it : new org.bukkit.inventory.ItemStack[]{cmd, bed}) {
                    java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste = player.getInventory().addItem(it);
                    for (org.bukkit.inventory.ItemStack rem : reste.values())
                        player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
                player.sendMessage("§aReçu §fCommand Block §aet §fBedrock §amarqués (posables + récupérables).");
                return true;
            }
            default:
                player.sendMessage("§cSous-commande inconnue. §e/crate sethere|settings|remove|clearall|list|givekey|boost|testblock");
                return true;
        }
    }

    // Renvoie la crate visée (cône ~10°, portée 6 blocs), ou null.
    private PlacedCrate getLookedCrate(Player player) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection();
        PlacedCrate best = null; double bestDist = Double.MAX_VALUE;
        for (PlacedCrate c : crates.values()) {
            if (c.loc == null || c.loc.getWorld() == null || !c.loc.getWorld().equals(eye.getWorld())) continue;
            org.bukkit.util.Vector to = c.loc.clone().add(0.5, 1.0, 0.5).toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 0.1 || dist > 6.0) continue;
            if (to.normalize().dot(dir) < 0.985) continue;
            if (dist < bestDist) { bestDist = dist; best = c; }
        }
        return best;
    }

    // Renvoie la crate la plus proche du joueur dans un rayon donné, ou null.
    private PlacedCrate getNearestCrate(Player player, double maxDist) {
        Location p = player.getLocation();
        PlacedCrate best = null; double bestDist = Double.MAX_VALUE;
        for (PlacedCrate c : crates.values()) {
            if (c.loc == null || c.loc.getWorld() == null || !c.loc.getWorld().equals(p.getWorld())) continue;
            double dist = c.loc.clone().add(0.5, 0.5, 0.5).distance(p);
            if (dist <= maxDist && dist < bestDist) { bestDist = dist; best = c; }
        }
        return best;
    }
}
