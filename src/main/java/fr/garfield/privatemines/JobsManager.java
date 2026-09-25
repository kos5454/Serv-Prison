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
import java.util.UUID;

/**
 * Système de MÉTIERS (/jobs). Pour l'instant UN seul métier : le Mineur (icône bloc de pierre).
 *
 * <p>Le Mineur récompense le total de blocs minés ({@link PrivateMines#getTotalMined}) par des
 * PALIERS : atteindre un seuil de blocs débloque une récompense en argent à réclamer une fois.
 * Les paliers réclamés sont persistés par joueur (bitmask dans playerdata sous « <uuid>.jobsMineur »).</p>
 */
public class JobsManager implements Listener {

    private final PrivateMines plugin;

    public JobsManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ── Paliers du métier Mineur (blocs requis → récompense $). Récompense ≈ 0,8 × blocs. ──
    private static final long[] MINEUR_SEUILS = {
            1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 75_000, 100_000, 150_000, 250_000, 350_000,
            500_000, 750_000, 1_000_000, 1_500_000, 2_500_000, 5_000_000, 7_500_000, 10_000_000,
            25_000_000, 50_000_000, 100_000_000
    };
    private static final long[] MINEUR_RECOMPENSES = {
            1_000, 2_000, 4_000, 8_000, 20_000, 40_000, 60_000, 80_000, 120_000, 200_000, 280_000,
            400_000, 600_000, 800_000, 1_200_000, 2_000_000, 4_000_000, 6_000_000, 8_000_000,
            20_000_000, 40_000_000, 80_000_000
    };

    static final String MENU_TITLE  = "§8§lMétiers";
    static final String MINEUR_TITLE = "§8Métier » §7Mineur";

    // ── Persistance : bitmask des paliers du Mineur déjà réclamés, par joueur. ──
    private long claimedMask(UUID id) {
        return plugin.getDataConfig().getLong(id + ".jobsMineur", 0L);
    }
    private void setClaimed(UUID id, int index) {
        long mask = claimedMask(id) | (1L << index);
        plugin.getDataConfig().set(id + ".jobsMineur", mask);
    }
    private boolean isClaimed(UUID id, int index) {
        return (claimedMask(id) & (1L << index)) != 0;
    }

    // ── Menu principal /jobs : liste des métiers (1 pour l'instant). ──
    public void openMenu(Player p) {
        Inventory menu = Bukkit.createInventory(null, 27, MENU_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, bg);

        long mined = plugin.getTotalMined(p);
        int claimable = countClaimable(p);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Blocs minés : §b" + PrivateMines.formatNumber(mined));
        lore.add("§7Paliers à réclamer : " + (claimable > 0 ? "§a" + claimable : "§80"));
        lore.add("");
        lore.add("§e▶ Clique pour voir les paliers");
        menu.setItem(13, named(Material.STONE, "§7§lMineur", lore));

        menu.setItem(22, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    // ── Menu du métier Mineur : progression + tous les paliers. ──
    public void openMineur(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, MINEUR_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        long mined = plugin.getTotalMined(p);

        // En-tête : progression globale.
        List<String> hlore = new ArrayList<>();
        hlore.add("");
        hlore.add("§7Blocs minés au total : §b" + PrivateMines.formatNumber(mined));
        int claimable = countClaimable(p);
        if (claimable > 0) hlore.add("§a" + claimable + " palier(s) à réclamer !");
        menu.setItem(4, named(Material.STONE, "§7§lMétier Mineur", hlore));

        // Un item par palier (à partir du slot 9).
        int slot = 9;
        for (int i = 0; i < MINEUR_SEUILS.length && slot < 45; i++, slot++) {
            long seuil = MINEUR_SEUILS[i];
            long reward = MINEUR_RECOMPENSES[i];
            boolean reached = mined >= seuil;
            boolean claimed = isClaimed(p.getUniqueId(), i);

            Material icon;
            String name;
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Objectif : §b" + PrivateMines.formatNumber(seuil) + " §7blocs");
            lore.add("§7Récompense : §6" + PrivateMines.formatNumber(reward) + "$");
            lore.add("");
            if (claimed) {
                icon = Material.LIME_STAINED_GLASS_PANE;
                name = "§aPalier " + (i + 1) + " §7» §aréclamé ✔";
                lore.add("§8Déjà réclamé.");
            } else if (reached) {
                icon = Material.CHEST;
                name = "§6§lPalier " + (i + 1) + " §7» §aà réclamer !";
                lore.add("§e▶ Clique pour réclamer");
            } else {
                icon = Material.GRAY_STAINED_GLASS_PANE;
                name = "§7Palier " + (i + 1) + " §8» §cverrouillé";
                long reste = seuil - mined;
                lore.add("§7Encore §c" + PrivateMines.formatNumber(reste) + " §7blocs.");
            }
            ItemStack it = named(icon, name, lore);
            menu.setItem(slot, it);
        }

        menu.setItem(49, plugin.namedItem(Material.ARROW, "§e◀ Retour", "§7Revenir aux métiers"));
        p.openInventory(menu);
    }

    // Nombre de paliers atteints mais pas encore réclamés.
    private int countClaimable(Player p) {
        long mined = plugin.getTotalMined(p);
        int c = 0;
        for (int i = 0; i < MINEUR_SEUILS.length; i++) {
            if (mined >= MINEUR_SEUILS[i] && !isClaimed(p.getUniqueId(), i)) c++;
        }
        return c;
    }

    // ── Clics ──
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String title = event.getView().getTitle();

        if (MENU_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 22) { p.closeInventory(); return; }
            if (slot == 13) { openMineur(p); return; }
            return;
        }

        if (MINEUR_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 49) { openMenu(p); return; }
            // Les paliers commencent au slot 9.
            int index = slot - 9;
            if (index < 0 || index >= MINEUR_SEUILS.length) return;
            claimMineur(p, index);
        }
    }

    // Réclame la récompense d'un palier du Mineur (si atteint et pas déjà réclamé).
    private void claimMineur(Player p, int index) {
        if (isClaimed(p.getUniqueId(), index)) return;
        long mined = plugin.getTotalMined(p);
        if (mined < MINEUR_SEUILS[index]) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        long reward = MINEUR_RECOMPENSES[index];
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy != null) economy.depositPlayer(p, reward);
        setClaimed(p.getUniqueId(), index);
        plugin.saveDataConfig("le métier Mineur de " + p.getName());
        p.sendMessage("§a✔ Palier §e" + (index + 1) + " §adu métier Mineur réclamé : §6+"
                + PrivateMines.formatNumber(reward) + "$ §a!");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        openMineur(p); // rafraîchit le menu
    }

    // ── Helper : item avec nom + lore. ──
    private ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }
}
