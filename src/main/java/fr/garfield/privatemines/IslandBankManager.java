package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Banque d'île (Brique A du système « Niveau de parcelle »).
 * Le joueur dépose du FER et de l'OR (lingots ou blocs) dans sa parcelle. Le menu affiche
 * un COMPTEUR (pas les blocs) + le score en points (fer×1 + or×3). On peut aussi retirer.
 * Le score alimente le /classements des îles.
 */
public class IslandBankManager implements Listener {

    private final PrivateMines plugin;
    public IslandBankManager(PrivateMines plugin) { this.plugin = plugin; }

    static final String BANK_TITLE = "§6§lBanque d'Île";
    // 1 bloc = 9 lingots.
    private static final int BLOCK = 9;

    public void openBank(Player player) {
        Parcelle parc = plugin.getParcelleManager().getParcelle(player.getUniqueId());
        if (parc == null) {
            player.sendMessage("§cTu n'as pas encore d'Île. Fais §e/ob §cd'abord.");
            return;
        }
        Inventory menu = Bukkit.createInventory(null, 27, BANK_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        // Compteur FER (slot 11) + OR (slot 15).
        menu.setItem(11, counterItem(Material.IRON_INGOT, "§f§lFer déposé",
                parc.getIronDeposited(), 1));
        menu.setItem(15, counterItem(Material.GOLD_INGOT, "§e§lOr déposé",
                parc.getGoldDeposited(), 3));

        // Score de l'île (slot 13).
        ItemStack score = new ItemStack(Material.NETHER_STAR);
        ItemMeta sm = score.getItemMeta();
        sm.setDisplayName("§b§l✦ Niveau de l'Île");
        sm.setLore(Arrays.asList(
                "§7Score : §b" + PrivateMines.formatNumber(parc.islandPoints()) + " §7points",
                "",
                "§7Fer §f×1 §7+ Or §f×3",
                "§7Visible dans §e/classements§7."));
        score.setItemMeta(sm);
        menu.setItem(13, score);

        // Boutons DÉPÔT (ligne du bas).
        menu.setItem(20, actionItem(Material.IRON_BLOCK, "§a§lDéposer tout mon FER",
                "§7Dépose tous les lingots et blocs", "§7de fer de ton inventaire."));
        menu.setItem(24, actionItem(Material.GOLD_BLOCK, "§a§lDéposer tout mon OR",
                "§7Dépose tous les lingots et blocs", "§7d'or de ton inventaire."));
        // Boutons RETRAIT.
        menu.setItem(19, actionItem(Material.HOPPER, "§c§lRetirer du FER",
                "§7Clic : §f1 lingot §7· Shift : §f1 bloc§7 (9)."));
        menu.setItem(25, actionItem(Material.HOPPER, "§c§lRetirer de l'OR",
                "§7Clic : §f1 lingot §7· Shift : §f1 bloc§7 (9)."));

        player.openInventory(menu);
    }

    private ItemStack counterItem(Material mat, String name, long ingots, int ptPerIngot) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(
                "§7Quantité : §f" + PrivateMines.formatNumber(ingots) + " §7lingots",
                "§7= §f" + PrivateMines.formatNumber(ingots / BLOCK) + " §7blocs §8+ " + (ingots % BLOCK) + " lingots",
                "§7Vaut §b" + PrivateMines.formatNumber(ingots * (long) ptPerIngot) + " §7points"));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack actionItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onBankClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!BANK_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        Parcelle parc = plugin.getParcelleManager().getParcelle(player.getUniqueId());
        if (parc == null) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        boolean shift = event.isShiftClick();

        switch (clicked.getType()) {
            case IRON_BLOCK:  depositAll(player, parc, true);  break;   // déposer tout le fer
            case GOLD_BLOCK:  depositAll(player, parc, false); break;   // déposer tout l'or
            case HOPPER:
                // Distingue fer/or par le nom du bouton.
                String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";
                boolean iron = name.contains("FER");
                withdraw(player, parc, iron, shift ? BLOCK : 1);
                break;
            default: return;
        }
        plugin.getParcelleManager().save();
        openBank(player); // rafraîchit l'affichage
    }

    // Dépose tous les lingots ET blocs (fer ou or) de l'inventaire du joueur dans la banque.
    private void depositAll(Player player, Parcelle parc, boolean iron) {
        Material ingot = iron ? Material.IRON_INGOT : Material.GOLD_INGOT;
        Material block = iron ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
        long added = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            if (it.getType() == ingot) { added += it.getAmount(); player.getInventory().setItem(i, null); }
            else if (it.getType() == block) { added += (long) it.getAmount() * BLOCK; player.getInventory().setItem(i, null); }
        }
        if (added <= 0) {
            player.sendMessage("§7Tu n'as pas de " + (iron ? "§ffer" : "§eor") + " §7à déposer.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (iron) parc.addIron(added); else parc.addGold(added);
        player.sendMessage("§aDéposé §f" + PrivateMines.formatNumber(added) + " " + (iron ? "§ffer" : "§eor")
                + " §a! §7(+" + PrivateMines.formatNumber(added * (iron ? 1 : 3)) + " points)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    // Retire <ingots> lingots (fer ou or) de la banque et les rend au joueur.
    private void withdraw(Player player, Parcelle parc, boolean iron, int ingots) {
        long have = iron ? parc.getIronDeposited() : parc.getGoldDeposited();
        if (have < ingots) {
            player.sendMessage("§cPas assez de " + (iron ? "§ffer" : "§eor") + " §cdans la banque.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        Material giveMat;
        int giveAmount;
        if (ingots >= BLOCK) { giveMat = iron ? Material.IRON_BLOCK : Material.GOLD_BLOCK; giveAmount = ingots / BLOCK; }
        else { giveMat = iron ? Material.IRON_INGOT : Material.GOLD_INGOT; giveAmount = ingots; }
        // On retire de la banque puis on donne (l'excédent qui ne rentre pas est reperdu → on remet).
        if (iron) parc.addIron(-ingots); else parc.addGold(-ingots);
        java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(new ItemStack(giveMat, giveAmount));
        int notGiven = 0;
        for (ItemStack rem : left.values()) notGiven += rem.getAmount();
        if (notGiven > 0) {
            // Inventaire plein : on recrédite la banque de ce qui n'a pas pu être donné.
            long refund = (long) notGiven * (giveMat.name().endsWith("_BLOCK") ? BLOCK : 1);
            if (iron) parc.addIron(refund); else parc.addGold(refund);
            player.sendMessage("§eInventaire plein : une partie n'a pas pu être retirée.");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }
}
