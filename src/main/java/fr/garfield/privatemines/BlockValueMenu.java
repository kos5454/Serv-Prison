package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu /bv (alias /blockvalue) : la fiche de chaque bloc que tu peux miner.
 *
 * Deux lectures dans un seul écran :
 *   - COMBIEN ÇA VAUT  : le prix final, tous bonus compris, avec le détail du calcul ;
 *   - COMBIEN ÇA T'A RAPPORTÉ : l'argent réellement encaissé grâce à ce bloc, sa part dans
 *     tes gains, le gain moyen par bloc et ton record sur une minute.
 *
 * Menu de consultation : rien n'est cliquable à part le bouton fermer.
 */
public class BlockValueMenu implements Listener {

    private static final String TITLE = "§6§l✦ Valeur des Blocs ✦";
    private static final int SIZE = 54;
    private static final int SLOT_HEAD = 4;      // en-tête (mine de référence + bonus)
    private static final int SLOT_TOTAL = 49;    // récapitulatif des gains
    private static final int SLOT_CLOSE = 53;    // fermer

    // Les 5 emplacements de blocs, toujours aux mêmes cases : le joueur retrouve ses repères
    // d'une mine à l'autre. Les blocs pas encore débloqués restent visibles, en silhouette.
    private static final int[] TILES = {20, 21, 22, 23, 24};

    private final PrivateMines plugin;

    public BlockValueMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    /** Une ligne du tableau : le bloc, son prix de base, et la clé de ses statistiques. */
    private static final class Ligne {
        final Material icone; final String nom; final double base; final String cle;
        final String debloque; // texte « comment l'obtenir » si base == 0
        Ligne(Material icone, String nom, double base, String cle, String debloque) {
            this.icone = icone; this.nom = nom; this.base = base; this.cle = cle; this.debloque = debloque;
        }
    }

    public void openBlockValue(Player player) {
        Inventory menu = Bukkit.createInventory(null, SIZE, TITLE);

        // ── Habillage : fond neutre, cadre orange, séparateur sous l'en-tête ──────────
        ItemStack fond = plugin.makeFiller();
        for (int i = 0; i < SIZE; i++) menu.setItem(i, fond);
        ItemStack cadre = plugin.pane(Material.ORANGE_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) menu.setItem(i, cadre);                 // rangée du haut
        for (int i = 45; i < SIZE; i++) menu.setItem(i, cadre);             // rangée du bas
        for (int i = 9; i <= 36; i += 9) { menu.setItem(i, cadre); menu.setItem(i + 8, cadre); }
        // Ligne de séparation juste au-dessus des blocs.
        for (int i = 10; i <= 16; i++) menu.setItem(i, plugin.pane(Material.BLACK_STAINED_GLASS_PANE));

        // ⚠️ 2026-09-02 : /bv décrit la mine OÙ LE JOUEUR SE TROUVE, et n'affiche QUE les blocs
        // qu'elle contient réellement. Afficher la pierre dans une mine 100 % grès n'a aucun sens.
        // Les PRIX, eux, restent le max sur toutes les mines débloquées (getXxxSellPrice).
        String mineCourante = plugin.getPlayerMine(player);
        String mineName     = plugin.getMineDisplayNameShort(mineCourante);
        double bonus        = plugin.getTotalMoneyBonusPercent(player);

        menu.setItem(SLOT_HEAD, enTete(mineName, bonus));

        // ── Le tableau des blocs ─────────────────────────────────────────────────────
        List<Ligne> lignes = construireLignes(player, mineCourante);
        double totalGains = plugin.getBlocksEarnedTotal(player);
        for (int i = 0; i < TILES.length; i++) {
            menu.setItem(TILES[i], i < lignes.size()
                    ? tuile(player, lignes.get(i), totalGains)
                    : fond);
        }

        menu.setItem(SLOT_TOTAL, recap(player, totalGains));
        menu.setItem(SLOT_CLOSE, plugin.namedItem(Material.BARRIER, "§c§lFermer", null));
        player.openInventory(menu);
    }

    /**
     * Construit les lignes du tableau pour la mine OÙ LE JOUEUR SE TROUVE.
     *
     * ⚠️ 2026-09-02 : on n'affiche QUE les blocs que cette mine contient vraiment (pourcentage de
     * composition > 0). Dans la mine 1 on voit la pierre seule ; à partir de la mine 2 la pierre et
     * le charbon ; en Arc II le grès seul, puisqu'il n'y a rien d'autre à y miner. Lister un bloc
     * absent de la mine où l'on creuse n'apprend rien au joueur.
     *
     * Les PRIX affichés sont ceux réellement payés (getXxxSellPrice = max sur les mines
     * débloquées), pas les prix de cette mine-ci : casser du charbon en mine 3 rapporte bien le
     * prix de ta meilleure mine à charbon.
     */
    private List<Ligne> construireLignes(Player player, String mine) {
        List<Ligne> l = new ArrayList<>();
        // Le bloc de base est toujours là : c'est ce qui remplit la mine.
        l.add(new Ligne(plugin.getMineBaseBlock(mine), plugin.getMineBaseBlockName(mine),
                plugin.getStoneSellPrice(player), PrivateMines.BK_STONE, null));
        if (plugin.getMineCoalPct(mine) > 0)
            l.add(new Ligne(Material.COAL_ORE, "§8§lCharbon",
                    plugin.getCoalSellPrice(player), PrivateMines.BK_COAL, null));
        if (plugin.getMineCoalBlockPct(mine) > 0)
            l.add(new Ligne(Material.COAL_BLOCK, "§0§lBloc de charbon",
                    plugin.getCoalBlockSellPrice(player), PrivateMines.BK_COALBLOCK, null));
        if (plugin.getMineIronPct(mine) > 0)
            l.add(new Ligne(Material.IRON_ORE, "§f§lFer",
                    plugin.getIronSellPrice(player), PrivateMines.BK_IRON, null));
        if (plugin.getMineAbyssalPct(mine) > 0)
            l.add(new Ligne(Material.DEEPSLATE_IRON_ORE, "§3§lFer des Abîmes",
                    plugin.getAbyssalSellPrice(player), PrivateMines.BK_ABYSSAL, null));
        return l;
    }

    /** En-tête : d'où viennent les prix affichés. */
    private ItemStack enTete(String mineName, double bonus) {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§e§lTes prix de vente");
        m.setLore(java.util.Arrays.asList(
                "",
                "§7Référence : " + mineName,
                "§7Bonus d'argent : §a+" + pct(bonus) + "%",
                "",
                "§8Tes prix suivent ta §7meilleure mine§8 et",
                "§8s'appliquent partout, même plus bas.",
                "§8Les montants affichés sont §7définitifs§8 :",
                "§8sac, familiers et armures compris."));
        it.setItemMeta(m);
        return it;
    }

    /** La fiche d'un bloc : sa valeur, puis ce qu'il t'a rapporté. */
    private ItemStack tuile(Player player, Ligne ligne, double totalGains) {
        // Bloc pas encore accessible : silhouette + comment y arriver.
        if (ligne.base <= 0) {
            ItemStack it = new ItemStack(Material.GRAY_DYE);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§8§l" + org.bukkit.ChatColor.stripColor(ligne.nom));
            m.setLore(java.util.Arrays.asList(
                    "",
                    "§8Pas encore accessible.",
                    "",
                    ligne.debloque == null ? "§7Continue de descendre." : ligne.debloque));
            it.setItemMeta(m);
            return it;
        }

        double prix   = plugin.getFinalBlockPrice(player, ligne.base);
        long   mines  = plugin.getBlocksMined(player, ligne.cle);
        double gagne  = plugin.getBlocksEarned(player, ligne.cle);
        double record = plugin.getBlocksBestMin(player, ligne.cle);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Prix par bloc  §a§l" + PrivateMines.formatMoney(prix) + "$");
        lore.add("§8Base " + PrivateMines.formatMoney(ligne.base) + "$ §8· bonus +"
                + pct(plugin.getTotalMoneyBonusPercent(player)) + "%");
        lore.add("");

        if (mines <= 0) {
            // Jamais miné : on le dit clairement plutôt que d'aligner des zéros.
            lore.add("§8Tu n'as pas encore miné ce bloc.");
            lore.add("§8Ses gains s'afficheront ici.");
        } else {
            lore.add("§6§lCe bloc t'a rapporté");
            lore.add("§7Total  §e" + PrivateMines.formatMoney(gagne) + "$");
            if (totalGains > 0) {
                double part = (gagne / totalGains) * 100.0;
                lore.add("§7Part de tes gains  §e" + pct(part) + "%");
                lore.add("§8" + barre(part));
            }
            lore.add("§7Blocs minés  §f" + PrivateMines.formatNumber(mines));
            lore.add("§7Moyenne  §f" + PrivateMines.formatMoney(gagne / mines) + "$ §8/bloc");
            if (record > 0) {
                lore.add("");
                lore.add("§d✦ Record sur 1 min  §f" + PrivateMines.formatMoney(record) + "$");
            }
        }

        ItemStack it = new ItemStack(ligne.icone);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ligne.nom);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** Récapitulatif : le total encaissé et le bloc qui te fait vivre. */
    private ItemStack recap(Player player, double total) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (total <= 0) {
            lore.add("§7Va miner : tes gains par bloc");
            lore.add("§7apparaîtront ici.");
        } else {
            lore.add("§7Encaissé en tout  §a§l" + PrivateMines.formatMoney(total) + "$");
            // Le bloc le plus rentable jusqu'ici.
            String meilleur = null; double best = 0;
            for (String cle : PrivateMines.BLOCK_KEYS) {
                double g = plugin.getBlocksEarned(player, cle);
                if (g > best) { best = g; meilleur = cle; }
            }
            if (meilleur != null) {
                lore.add("");
                lore.add("§7Ta meilleure source");
                lore.add("§f" + nomLisible(meilleur) + " §8(" + pct((best / total) * 100.0) + "% de tes gains)");
            }
        }
        lore.add("");
        lore.add("§8Comptabilisé depuis la mise à jour :");
        lore.add("§8ce que tu as miné avant n'y figure pas.");
        return plugin.namedItem(Material.GOLD_INGOT, "§6§lTes gains par bloc",
                lore.toArray(new String[0]));
    }

    /** Petite barre de proportion (10 crans) pour visualiser une part en pourcentage. */
    private static String barre(double pourcent) {
        int plein = (int) Math.round(Math.max(0, Math.min(100, pourcent)) / 10.0);
        StringBuilder b = new StringBuilder("§e");
        for (int i = 0; i < 10; i++) {
            if (i == plein) b.append("§8");
            b.append("▪");
        }
        return b.toString();
    }

    private static String nomLisible(String cle) {
        switch (cle) {
            case PrivateMines.BK_COAL:      return "Charbon";
            case PrivateMines.BK_COALBLOCK: return "Bloc de charbon";
            case PrivateMines.BK_IRON:      return "Fer";
            case PrivateMines.BK_ABYSSAL:   return "Fer des Abîmes";
            default:                        return "Bloc de base";
        }
    }

    private static String pct(double v) {
        return String.format(Locale.FRANCE, "%.2f", v);
    }

    @EventHandler
    public void onBlockValueClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true); // menu de consultation
        if (event.getRawSlot() == SLOT_CLOSE) ((Player) event.getWhoClicked()).closeInventory();
    }
}
