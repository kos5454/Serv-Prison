package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

/**
 * Classements (Argent / Gains-s / Progression / Blocs minés / Enchants) : calcul périodique,
 * menu /classements et hologrammes de top auto-mis à jour au spawn.
 * Extrait de PrivateMines pour alléger le fichier principal.
 */
public class LeaderboardManager implements Listener {

    private final PrivateMines plugin;

    public LeaderboardManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    static class LeaderEntry { java.util.UUID uuid; String name; long value; }

    private final java.util.Map<String, java.util.List<LeaderEntry>> leaderboards = new java.util.HashMap<>();
    private static final String[] CLASS_METRICS = {"money", "income", "rank", "mined", "ench", "island"};
    private static final String[] CLASS_TITLES  = {"TOP ARGENT", "TOP GAINS/s MAX", "TOP PROGRESSION", "TOP BLOCS MINÉS", "TOP ENCHANTS", "TOP ÎLES"};

    public void startLeaderboardTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            recomputeLeaderboards();
            updateLeaderboardHolograms();
        }, 200L, 1200L); // démarre à 10s, recalcul toutes les 60s
    }

    private void recomputeLeaderboards() {
        org.bukkit.configuration.file.FileConfiguration dataConfig = plugin.getDataConfig();
        if (dataConfig == null) return;
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        java.util.Map<java.util.UUID, String> names = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> money = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> income = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> rank = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> mined = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> ench = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> island = new java.util.HashMap<>();

        for (String key : dataConfig.getKeys(false)) {
            java.util.UUID uuid;
            try { uuid = java.util.UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String nm = op.getName();
            if (nm == null) continue;
            Player online = Bukkit.getPlayer(uuid);
            names.put(uuid, nm);

            money.put(uuid, economy != null ? Math.round(economy.getBalance(op) * 100.0) : 0L); // en centimes
            // Gains/s = RECORD du meilleur $/s jamais atteint (persisté), lu même hors-ligne.
            double maxInc = online != null ? plugin.getMaxIncomePerSec(online) : dataConfig.getDouble(key + ".maxIncomePerSec", 0.0);
            income.put(uuid, Math.round(maxInc * 100.0)); // $/s en centimes

            int r;
            if (online != null) r = plugin.getMineRank(online);
            else {
                // Joueur hors-ligne : on lit les flags ".unlockedMine<code>" de la table MINES.
                r = 1;
                for (PrivateMines.MineDef d : PrivateMines.MINES) {
                    if (PrivateMines.mineRankOf(d.code) <= 1) continue;
                    if (dataConfig.getBoolean(key + ".unlockedMine" + d.code)) r = PrivateMines.mineRankOf(d.code);
                }
            }
            rank.put(uuid, (long) r);

            mined.put(uuid, online != null ? plugin.getTotalMined(online) : dataConfig.getLong(key + ".totalMined", 0L));

            long e;
            EnchantManager em = plugin.getEnchants();
            if (online != null) e = em.getEfficiencyLevel(online) + em.getExplosionLevel(online) + em.getForageLevel(online) + em.getFractureLevel(online) + em.getVeinLevel(online) + em.getColonneLevel(online) + em.getDimeLevel(online) + em.getHarponLevel(online) + em.getCycloneLevel(online) + em.getMemoireLevel(online);
            else e = dataConfig.getInt(key + ".efficiencyLevel") + dataConfig.getInt(key + ".explosionLevel")
                    + dataConfig.getInt(key + ".forageLevel") + dataConfig.getInt(key + ".fractureLevel") + dataConfig.getInt(key + ".veinLevel") + dataConfig.getInt(key + ".colonneLevel") + dataConfig.getInt(key + ".dimeLevel") + dataConfig.getInt(key + ".harponLevel") + dataConfig.getInt(key + ".cycloneLevel") + dataConfig.getInt(key + ".memoireLevel");
            ench.put(uuid, e);

            // Niveau d'île = points de la banque (fer×1 + or×3), lu depuis la parcelle du joueur.
            Parcelle parc = plugin.getParcelleManager().getParcelle(uuid);
            island.put(uuid, parc != null ? parc.islandPoints() : 0L);
        }
        leaderboards.put("money", topList(money, names));
        leaderboards.put("income", topList(income, names));
        leaderboards.put("rank", topList(rank, names));
        leaderboards.put("mined", topList(mined, names));
        leaderboards.put("ench", topList(ench, names));
        leaderboards.put("island", topList(island, names));
    }

    private java.util.List<LeaderEntry> topList(java.util.Map<java.util.UUID, Long> vals, java.util.Map<java.util.UUID, String> names) {
        java.util.List<LeaderEntry> list = new ArrayList<>();
        for (java.util.Map.Entry<java.util.UUID, Long> e : vals.entrySet()) {
            LeaderEntry le = new LeaderEntry();
            le.uuid = e.getKey(); le.name = names.get(e.getKey()); le.value = e.getValue();
            list.add(le);
        }
        list.sort((a, b) -> Long.compare(b.value, a.value));
        return list.size() > 10 ? new ArrayList<>(list.subList(0, 10)) : list;
    }

    private String leaderValue(String metric, long v, boolean amp) {
        String c = amp ? "&" : "§";
        switch (metric) {
            case "money": return c + "6" + PrivateMines.formatNumber(v / 100.0) + "$"; // v est en centimes
            case "income":return c + "e" + PrivateMines.formatNumber(v / 100.0) + "$/s"; // v est en centimes
            case "rank":  return c + "bMine " + v;
            case "mined": return c + "a" + PrivateMines.formatNumber(v) + " blocs";
            case "ench":  return c + "d" + v + " niv.";
            case "island":return c + "b" + PrivateMines.formatNumber(v) + " pts";
            default:      return String.valueOf(v);
        }
    }

    // ----- Menu /classements -----
    private static final String CLASS_TITLE = "§6§l⭐ Classements";
    private final java.util.Map<java.util.UUID, String> classementsTab = new java.util.HashMap<>();
    private static final int[] LEADER_HEAD_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33}; // grille 5x2 centrée (3e ligne)
    private static final int[] CLASS_BTN_SLOTS = {1, 2, 3, 4, 5, 6}; // 6 onglets centrés
    private static final Material[] CLASS_BTN_MATS = {Material.GOLD_INGOT, Material.CLOCK, Material.FILLED_MAP, Material.IRON_PICKAXE, Material.ENCHANTED_BOOK, Material.NETHER_STAR};
    private static final String[] CLASS_BTN_NAMES = {"§6💰 Argent", "§e⏱ Gains/s max", "§b🏝 Progression", "§a⛏ Blocs minés", "§d✦ Enchants", "§b✦ Niveau d'Île"};

    public void openClassements(Player p) {
        classementsTab.putIfAbsent(p.getUniqueId(), "money");
        // Recalcul frais à chaque ouverture : le menu reflète l'état de l'instant (blocs, argent, enchants...).
        recomputeLeaderboards();
        Inventory menu = Bukkit.createInventory(null, 54, CLASS_TITLE);
        refreshClassements(p, menu);
        p.openInventory(menu);
    }

    private void refreshClassements(Player p, Inventory menu) {
        String tab = classementsTab.getOrDefault(p.getUniqueId(), "money");
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        ItemStack border = plugin.pane(Material.ORANGE_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) menu.setItem(i, border);
        for (int i = 45; i < 54; i++) menu.setItem(i, border);

        for (int i = 0; i < CLASS_METRICS.length; i++) {
            ItemStack it = new ItemStack(CLASS_BTN_MATS[i]);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(CLASS_BTN_NAMES[i]);
            boolean cur = CLASS_METRICS[i].equals(tab);
            m.setLore(java.util.Arrays.asList(cur ? "§aAffiché ✔" : "§7Clic pour voir ce classement"));
            if (cur) {
                m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(m);
            menu.setItem(CLASS_BTN_SLOTS[i], it);
        }

        java.util.List<LeaderEntry> list = leaderboards.getOrDefault(tab, java.util.Collections.emptyList());
        for (int i = 0; i < LEADER_HEAD_SLOTS.length; i++) {
            if (i >= list.size()) { menu.setItem(LEADER_HEAD_SLOTS[i], null); continue; }
            LeaderEntry le = list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(le.uuid));
            String medal = i == 0 ? "§6§l#1" : i == 1 ? "§7§l#2" : i == 2 ? "§c§l#3" : "§e#" + (i + 1);
            sm.setDisplayName(medal + " §f" + le.name);
            sm.setLore(java.util.Arrays.asList(leaderValue(tab, le.value, false)));
            head.setItemMeta(sm);
            menu.setItem(LEADER_HEAD_SLOTS[i], head);
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7Fermer", null));
    }

    @EventHandler
    public void onClassementsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!CLASS_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == 49) { p.closeInventory(); return; }
        for (int i = 0; i < CLASS_BTN_SLOTS.length; i++) {
            if (slot == CLASS_BTN_SLOTS[i]) {
                classementsTab.put(p.getUniqueId(), CLASS_METRICS[i]);
                refreshClassements(p, event.getInventory());
                return;
            }
        }
    }

    // ----- Hologrammes de classement au spawn (auto-mis à jour) -----
    private void updateLeaderboardHolograms() {
        HologramManager holoManager = plugin.getHoloManager();
        if (holoManager == null) return;
        String[] holoNames = {"top-argent", "top-gains", "top-progression", "top-blocs", "top-enchants", "top-iles"};
        String[] colors = {"&6", "&e", "&b", "&a", "&d", "&b"};
        for (int mi = 0; mi < CLASS_METRICS.length; mi++) {
            String hn = holoNames[mi];
            // Classement masqué (supprimé définitivement via le menu/commande) : on ne le recrée pas.
            if (holoManager.estMasque(hn)) continue;
            Location spawnIfNew = null;
            if (!holoManager.exists(hn)) {
                Location spawn = plugin.getSpawnLocation();
                if (spawn == null) continue; // pas de spawn défini : on réessaiera
                spawnIfNew = spawn.clone().add(0, 3.0 + mi * 0.0, 0); // au spawn, à déplacer ensuite avec /holo movehere
            }
            java.util.List<String> lines = new ArrayList<>();
            lines.add(colors[mi] + "&l⭐ " + CLASS_TITLES[mi]);
            java.util.List<LeaderEntry> list = leaderboards.getOrDefault(CLASS_METRICS[mi], java.util.Collections.emptyList());
            if (list.isEmpty()) lines.add("&7Aucune donnée pour l'instant");
            for (int i = 0; i < list.size(); i++) {
                LeaderEntry le = list.get(i);
                String medal = i == 0 ? "&6#1" : i == 1 ? "&7#2" : i == 2 ? "&c#3" : "&e#" + (i + 1);
                lines.add(medal + " &f" + le.name + " &8- " + leaderValue(CLASS_METRICS[mi], le.value, true));
            }
            holoManager.setHologramLines(hn, spawnIfNew, lines);
        }
        holoManager.save();
    }
}
