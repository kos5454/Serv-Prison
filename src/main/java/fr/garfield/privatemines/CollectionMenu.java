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

/**
 * Menu /collections : les trouvailles rares des mines (items-clés de fin d'arc + la Plume).
 *
 * Principe : UNE case par arc — l'item-clé qui garde le passage vers l'arc suivant. Les arcs
 * dont l'item n'est pas encore codé s'affichent en « ??? » avec l'indication de la mine où
 * chercher : le joueur voit sa collection à remplir sans qu'on lui spoile la récompense.
 *
 * Pour AJOUTER une trouvaille : une ligne dans TROUVAILLES. Rien d'autre à toucher.
 */
public class CollectionMenu implements Listener {

    private static final String TITLE = "§6§l✦ Collections ✦";

    /** Une trouvaille collectionnable. */
    private static final class Trouvaille {
        final String nom;          // nom affiché une fois trouvée
        final Material icone;      // icône une fois trouvée (PLAYER_HEAD = tête HeadDB via headId)
        final int headId;          // id HeadDB, ou 0 si icône normale
        final String ou;           // où la chercher (indice affiché même non trouvée)
        final String lore;         // une ligne d'ambiance, affichée une fois trouvée
        final java.util.function.Predicate<Player> obtenue; // test « le joueur l'a ? »
        final boolean codee;       // false = pas encore implémentée en jeu (« à venir »)

        Trouvaille(String nom, Material icone, int headId, String ou, String lore,
                   java.util.function.Predicate<Player> obtenue, boolean codee) {
            this.nom = nom; this.icone = icone; this.headId = headId;
            this.ou = ou; this.lore = lore; this.obtenue = obtenue; this.codee = codee;
        }
    }

    private final PrivateMines plugin;
    private final List<Trouvaille> trouvailles = new ArrayList<>();

    public CollectionMenu(PrivateMines plugin) {
        this.plugin = plugin;
        construireTable();
    }

    /**
     * La table des trouvailles. Une entrée par item-clé d'arc, dans l'ordre des arcs.
     * Les arcs sans item codé sont déclarés avec codee=false : ils occupent leur case
     * en « à venir » pour montrer la longueur du chemin.
     */
    private void construireTable() {
        // Arc I — East Blue : le Chapeau de paille (mine 21, drop en minant). CODÉ.
        trouvailles.add(new Trouvaille(
                "§e§l🎩 Chapeau de paille",
                Material.PLAYER_HEAD, PrivateMines.STRAW_HAT_HEAD_ID,
                "§7Mine 21 — §5Passe des Adieux",
                "§7La coiffe d'un rêveur parti trop loin.",
                p -> plugin.hasFoundStrawHat(p), true));

        // La Plume — trouvaille rare hors arc (niveau de pioche 45, 1/1000 par bloc). CODÉE.
        trouvailles.add(new Trouvaille(
                "§f§l🪶 La Plume",
                Material.FEATHER, 0,
                "§7En minant, dès le §eniveau de pioche 45",
                "§7Blanche comme un souffle, chaude comme un souvenir.",
                p -> plugin.getActe() != null && plugin.getActe().hasFoundPlume(p), true));

        // Arcs suivants : l'item-clé n'existe pas encore en jeu. On réserve leur case.
        // Quand tu codes l'item d'un arc : remplace l'entrée par une vraie (codee=true + test).
        for (PrivateMines.ArcDef arc : PrivateMines.ARCS) {
            if (arc.number == 1) continue; // Arc I déjà couvert par le Chapeau de paille
            trouvailles.add(new Trouvaille(
                    arc.color + "Clé de l'" + romain(arc.number) + "ᵉ arc",
                    Material.GRAY_DYE, 0,
                    "§7Mine " + arc.lastRank + " — fin de « " + arc.color + arc.name + "§7 »",
                    "§8Cet objet n'a pas encore été forgé.",
                    p -> false, false));
        }
    }

    private static String romain(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return (n >= 0 && n < r.length) ? r[n] : String.valueOf(n);
    }

    public void open(Player player) {
        Inventory menu = Bukkit.createInventory(null, 45, TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);

        // Les trouvailles : rangées 1 et 2 (7 cases par rangée, colonnes 1-7).
        int[] cases = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int trouvees = 0;
        for (int i = 0; i < trouvailles.size() && i < cases.length; i++) {
            Trouvaille t = trouvailles.get(i);
            boolean a = t.codee && t.obtenue.test(player);
            if (a) trouvees++;
            menu.setItem(cases[i], tuile(t, a));
        }

        // Progression globale, en bas.
        int total = trouvailles.size();
        menu.setItem(40, progression(trouvees, total));

        // Emplacement réservé à la chasse aux 100 têtes (pas encore codée).
        menu.setItem(43, plugin.namedItem(Material.SKELETON_SKULL,
                "§8§lLes Têtes Oubliées",
                "§7Cent visages dorment dans les mines.",
                "",
                "§8Chasse à venir."));

        player.openInventory(menu);
    }

    /** Une case : l'objet révélé s'il est trouvé, une silhouette mystère sinon. */
    private ItemStack tuile(Trouvaille t, boolean obtenue) {
        if (obtenue) {
            ItemStack it = (t.headId > 0 && plugin.getPetHeads() != null)
                    ? plugin.getPetHeads().getHead(t.headId)
                    : new ItemStack(t.icone);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(t.nom);
            m.setLore(java.util.Arrays.asList(
                    t.lore,
                    "",
                    "§a✔ Trouvé",
                    t.ou));
            it.setItemMeta(m);
            return it;
        }

        // Non trouvée : silhouette. On garde l'indice du lieu — c'est ce qui donne un objectif.
        ItemStack it = new ItemStack(t.codee ? Material.GRAY_DYE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§8§l??? ");
        List<String> lore = new ArrayList<>();
        if (t.codee) {
            lore.add("§8Quelque chose dort là-bas.");
            lore.add("");
            lore.add("§7Où chercher :");
            lore.add(t.ou);
        } else {
            lore.add("§8Cet objet n'a pas encore été forgé.");
            lore.add("");
            lore.add("§7Un jour, ici :");
            lore.add(t.ou);
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** Barre de progression globale de la collection. */
    private ItemStack progression(int trouvees, int total) {
        int pct = total == 0 ? 0 : (trouvees * 100) / total;
        int plein = total == 0 ? 0 : (trouvees * 20) / total;
        StringBuilder barre = new StringBuilder("§a");
        for (int i = 0; i < 20; i++) {
            if (i == plein) barre.append("§8");
            barre.append("|");
        }
        return plugin.namedItem(Material.BOOK,
                "§6§lTa collection",
                "§7Trouvailles : §e" + trouvees + "§7/§e" + total,
                barre.toString(),
                "§7Complétion : §e" + pct + "%",
                "",
                "§8Les mines gardent leurs secrets pour",
                "§8ceux qui creusent assez longtemps.");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true); // menu de consultation : rien n'est cliquable
    }
}
