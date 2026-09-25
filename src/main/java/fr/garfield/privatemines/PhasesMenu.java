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

/**
 * Menu /phases (brique 8) — les 12 phases du « Bloc du Grand Appel ».
 *
 * <p>Deux vues, toutes deux en lecture seule :</p>
 * <ul>
 *   <li><b>Grille</b> : 12 icônes (une par phase). La phase actuelle du joueur est surlignée
 *       (enchantée + « ◄ toi »). Clic sur une phase → vue détail.</li>
 *   <li><b>Détail</b> : la LISTE des blocs obtenables dans la phase (SANS pourcentage,
 *       règle « on ne révèle pas la compo », cf. menu /mine) + le palier. Bouton retour.</li>
 * </ul>
 *
 * Les vues sont identifiées par leur titre ; la vue détail encode le n° de phase dans le titre
 * pour router le bouton retour et rester cohérente avec le pattern maison ({@link BlockValueMenu}).
 */
public class PhasesMenu implements Listener {

    private static final String TITRE_GRILLE = "§6§l⛏ Les 12 Phases";
    // Préfixe de la vue détail ; le nom de la phase suit (ex. "§8Phase §7» §f🌾 Plaines").
    private static final String PREFIXE_DETAIL = "§8Phase §7» ";

    private final PrivateMines plugin;

    public PhasesMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ===== Vue grille =====

    public void openGrille(Player player) {
        OneBlockManager ob = plugin.getOneBlock();
        Parcelle parc = ob.parcelleOf(player);
        int cur = (parc == null) ? 1 : parc.getObPhase();
        int cycle = (parc == null) ? 0 : parc.getObCycle();

        Inventory menu = Bukkit.createInventory(null, 54, TITRE_GRILLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        ItemStack border = plugin.pane(Material.ORANGE_STAINED_GLASS_PANE);
        int[] frame = {0,1,2,3,4,5,6,7,8, 45,46,47,48,50,51,52,53};
        for (int s : frame) menu.setItem(s, border);

        // En-tête : rappel de où en est le joueur.
        OneBlockManager.Phase curPh = ob.phase(cur);
        menu.setItem(4, plugin.namedItem(Material.NETHER_STAR, "§e§lTa progression",
                "§7Cycle actuel : §6" + (cycle + 1),
                "§7Phase actuelle : §f" + curPh.nom,
                "",
                "§8Clique une phase pour voir",
                "§8les blocs qu'on peut y obtenir."));

        // Les 12 phases sur 2 lignes centrées : slots 19..24 puis 28..33 (6 + 6).
        int[] slots = {19,20,21,22,23,24, 28,29,30,31,32,33};
        for (int i = 1; i <= OneBlockManager.PHASES; i++) {
            OneBlockManager.Phase ph = ob.phase(i);
            boolean actuelle = (i == cur);
            ItemStack it = new ItemStack(ph.bloc);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName((actuelle ? "§a§l" : "§f§l") + i + ". " + ph.nom
                    + (actuelle ? " §a◄ toi" : ""));
            m.setLore(java.util.Arrays.asList(
                    "§7Palier : §f" + ph.palier + " §7blocs",
                    "",
                    actuelle ? "§aTu es dans cette phase." : "§8Clique pour voir ses blocs."));
            if (actuelle) {
                // Petit halo « enchanté » pour repérer la phase en cours.
                m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(m);
            menu.setItem(slots[i - 1], it);
        }

        menu.setItem(49, plugin.namedItem(Material.BARRIER, "§cFermer", null));
        player.openInventory(menu);
    }

    // ===== Vue détail d'une phase =====

    private void openDetail(Player player, int num) {
        OneBlockManager ob = plugin.getOneBlock();
        OneBlockManager.Phase ph = ob.phase(num);
        java.util.List<Material> blocs = ob.blocsDePhase(num);

        Inventory menu = Bukkit.createInventory(null, 54, PREFIXE_DETAIL + ph.nom);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        ItemStack border = plugin.pane(Material.ORANGE_STAINED_GLASS_PANE);
        int[] frame = {0,1,2,3,4,5,6,7,8, 45,46,47,48,50,51,52,53};
        for (int s : frame) menu.setItem(s, border);

        menu.setItem(4, plugin.namedItem(ph.bloc, "§e§l" + ph.nom,
                "§7Palier : §f" + ph.palier + " §7blocs à casser",
                "",
                "§8Blocs qu'on peut obtenir ici :"));

        // Un item par bloc obtenable, à partir du slot 19 (zone centrale 4×7).
        int slot = 19;
        for (Material mat : blocs) {
            if (slot > 43) break; // sécurité : la zone centrale suffit largement (max 10-12 blocs)
            if (slot == 26 || slot == 27) slot = 28; // saute les bords des lignes
            if (slot == 35 || slot == 36) slot = 37;
            ItemStack it = new ItemStack(mat);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§f" + ob.niceName(mat));
            it.setItemMeta(m);
            menu.setItem(slot, it);
            slot++;
        }

        menu.setItem(49, plugin.namedItem(Material.ARROW, "§eRetour", "§7Revenir aux 12 phases."));
        player.openInventory(menu);
    }

    // ===== Clics =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        // --- Vue grille ---
        if (TITRE_GRILLE.equals(title)) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw == 49) { player.closeInventory(); return; }
            int num = phaseFromGrilleSlot(raw);
            if (num > 0) openDetail(player, num);
            return;
        }

        // --- Vue détail ---
        if (title.startsWith(PREFIXE_DETAIL)) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            if (raw == 49) openGrille(player);      // Retour
            return;
        }
    }

    // Mappe un slot cliqué dans la grille vers le n° de phase (1..12), ou 0 si hors zone.
    private int phaseFromGrilleSlot(int slot) {
        int[] slots = {19,20,21,22,23,24, 28,29,30,31,32,33};
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i + 1;
        return 0;
    }
}
