package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Menu ADMIN « /is admin » : liste TOUTES les Îles du serveur (têtes des propriétaires),
 * paginé, avec coordonnées / statut de connexion / score de banque / nom d'Île.
 * Un clic téléporte l'admin sur l'Île choisie (aucun contrôle de permission d'entrée :
 * c'est le but d'un menu OP).
 */
public class IslandAdminMenu implements Listener {

    private static final String TITLE = "§4§l⚙ Îles du serveur";
    private static final String TITLE_CONFIRM = "§4§l⚠ Supprimer cette Île ?";

    // 45 cases : 5 rangées (28 slots d'Îles) + rangée du bas pour la navigation.
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    // Écran de confirmation (27 cases).
    private static final int CONFIRM_YES = 11;
    private static final int CONFIRM_NO  = 15;

    private final PrivateMines plugin;

    // Page courante de chaque admin (pour savoir quoi afficher aux flèches).
    private final java.util.Map<UUID, Integer> page = new java.util.HashMap<>();
    // Ce que chaque slot affiché représente, par admin : slot -> UUID du propriétaire.
    private final java.util.Map<UUID, java.util.Map<Integer, UUID>> slotOwner = new java.util.HashMap<>();
    // Île en attente de confirmation de suppression, par admin.
    private final java.util.Map<UUID, UUID> pendingDelete = new java.util.HashMap<>();

    public IslandAdminMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    /** Ouvre le menu à la page demandée (0 = première). */
    public void open(Player admin, int pageIndex) {
        List<Parcelle> all = new ArrayList<>(plugin.getParcelleManager().getAllParcelles());
        // Les Îles les plus riches d'abord : c'est l'ordre le plus utile pour surveiller.
        all.sort(Comparator.comparingLong(Parcelle::islandPoints).reversed());

        int maxPage = Math.max(0, (all.size() - 1) / PER_PAGE);
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex > maxPage) pageIndex = maxPage;
        page.put(admin.getUniqueId(), pageIndex);

        Inventory menu = Bukkit.createInventory(null, SIZE, TITLE);
        java.util.Map<Integer, UUID> map = new java.util.HashMap<>();

        int start = pageIndex * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Parcelle parc = all.get(start + i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(parc.getOwner());
            menu.setItem(i, headFor(parc, owner));
            map.put(i, parc.getOwner());
        }
        slotOwner.put(admin.getUniqueId(), map);

        // Barre de navigation.
        ItemStack bg = plugin.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 45; s < SIZE; s++) menu.setItem(s, bg);

        if (pageIndex > 0) {
            menu.setItem(SLOT_PREV, plugin.namedItem(Material.ARROW, "§e◀ Page précédente",
                    "§7Page " + pageIndex + " / " + (maxPage + 1)));
        }
        if (pageIndex < maxPage) {
            menu.setItem(SLOT_NEXT, plugin.namedItem(Material.ARROW, "§ePage suivante ▶",
                    "§7Page " + (pageIndex + 2) + " / " + (maxPage + 1)));
        }
        menu.setItem(SLOT_INFO, plugin.namedItem(Material.BOOK,
                "§6§lÎles du serveur",
                "§7Total : §e" + all.size() + " §7Île" + (all.size() > 1 ? "s" : ""),
                "§7Page §e" + (pageIndex + 1) + "§7/§e" + (maxPage + 1),
                "",
                "§7Triées par §escore de banque §7décroissant.",
                "",
                "§e▶ Clique sur une tête pour t'y téléporter",
                "§c▶ Shift + clic droit pour supprimer une Île"));

        admin.openInventory(menu);
    }

    /** Tête du propriétaire + fiche complète de son Île. */
    private ItemStack headFor(Parcelle parc, OfflinePlayer owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) ((SkullMeta) meta).setOwningPlayer(owner);

        String pseudo = owner.getName() != null ? owner.getName() : "§8?";
        meta.setDisplayName("§e" + pseudo);

        List<String> lore = new ArrayList<>();
        lore.add("§7Île : " + plugin.islandDisplayName(parc, pseudo));
        lore.add("");
        lore.add(owner.isOnline() ? "§a● En ligne" : "§8● Hors ligne" + dernierePresence(owner));
        lore.add("§7Banque : §e" + parc.islandPoints() + " §7pts §8(fer " + parc.getIronDeposited()
                + " · or " + parc.getGoldDeposited() + ")");
        lore.add("§7OneBlock : §fphase " + parc.getObPhase() + " §8(" + parc.getObBlocs() + " blocs)");
        lore.add("§7Position : §f" + parc.getCenterX() + " §8/ §f" + plugin.getParcelleManager().getFloorY()
                + " §8/ §f" + parc.getCenterZ());
        lore.add("§7Taille : §f" + parc.getSize() + "×" + parc.getSize());
        lore.add("");
        lore.add("§e▶ Clique pour t'y téléporter");
        lore.add("§c▶ Shift + clic droit §7pour supprimer l'Île");
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /** « (il y a 3j) » quand la dernière connexion est connue, sinon rien. */
    private String dernierePresence(OfflinePlayer owner) {
        long last = owner.getLastSeen();
        if (last <= 0) return "";
        long jours = (System.currentTimeMillis() - last) / (1000L * 60 * 60 * 24);
        if (jours <= 0) return " §8(aujourd'hui)";
        return " §8(il y a " + jours + "j)";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player admin = (Player) event.getWhoClicked();
        if (!admin.isOp()) { admin.closeInventory(); return; }

        int slot = event.getRawSlot();
        int cur = page.getOrDefault(admin.getUniqueId(), 0);

        if (slot == SLOT_PREV) { open(admin, cur - 1); return; }
        if (slot == SLOT_NEXT) { open(admin, cur + 1); return; }
        if (slot >= 45 || slot < 0) return;

        java.util.Map<Integer, UUID> map = slotOwner.get(admin.getUniqueId());
        if (map == null) return;
        UUID ownerUuid = map.get(slot);
        if (ownerUuid == null) return;

        Parcelle parc = plugin.getParcelleManager().getParcelle(ownerUuid);
        if (parc == null) { admin.sendMessage("§cCette Île n'existe plus."); return; }

        // Shift + clic droit = suppression (passe par un écran de confirmation).
        if (event.isRightClick() && event.isShiftClick()) {
            openConfirm(admin, ownerUuid);
            return;
        }

        admin.closeInventory();
        teleporter(admin, parc, ownerUuid);
    }

    /** Écran de confirmation avant une suppression définitive. */
    private void openConfirm(Player admin, UUID ownerUuid) {
        Parcelle parc = plugin.getParcelleManager().getParcelle(ownerUuid);
        if (parc == null) { admin.sendMessage("§cCette Île n'existe plus."); return; }
        pendingDelete.put(admin.getUniqueId(), ownerUuid);

        String pseudo = Bukkit.getOfflinePlayer(ownerUuid).getName();
        if (pseudo == null) pseudo = "§8?";

        Inventory menu = Bukkit.createInventory(null, 27, TITLE_CONFIRM);
        ItemStack bg = plugin.pane(Material.RED_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) menu.setItem(i, bg);

        menu.setItem(13, plugin.namedItem(Material.PLAYER_HEAD,
                "§e" + pseudo,
                "§7Île : " + plugin.islandDisplayName(parc, pseudo),
                "§7Position : §f" + parc.getCenterX() + " §8/ §f" + parc.getCenterZ(),
                "§7Banque : §e" + parc.islandPoints() + " §7pts"));

        menu.setItem(CONFIRM_YES, plugin.namedItem(Material.LIME_CONCRETE,
                "§a§l✔ Supprimer définitivement",
                "§7Les blocs de l'Île seront §crasés§7,",
                "§7ses données §ceffacées§7, et sa place",
                "§7rendue à la prochaine Île créée.",
                "",
                "§c⚠ Action irréversible."));

        menu.setItem(CONFIRM_NO, plugin.namedItem(Material.RED_CONCRETE,
                "§c§l✘ Annuler",
                "§7Retour à la liste des Îles."));

        admin.openInventory(menu);
    }

    /** Clics dans l'écran de confirmation. */
    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE_CONFIRM.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player admin = (Player) event.getWhoClicked();
        if (!admin.isOp()) { admin.closeInventory(); return; }

        int slot = event.getRawSlot();
        if (slot == CONFIRM_NO) {
            pendingDelete.remove(admin.getUniqueId());
            open(admin, page.getOrDefault(admin.getUniqueId(), 0));
            return;
        }
        if (slot != CONFIRM_YES) return;

        UUID ownerUuid = pendingDelete.remove(admin.getUniqueId());
        if (ownerUuid == null) { admin.closeInventory(); return; }

        Parcelle parc = plugin.getParcelleManager().getParcelle(ownerUuid);
        if (parc == null) { admin.sendMessage("§cCette Île n'existe plus."); admin.closeInventory(); return; }

        String pseudo = Bukkit.getOfflinePlayer(ownerUuid).getName();
        if (pseudo == null) pseudo = ownerUuid.toString();

        // Sort tout le monde de l'Île avant de la raser (sinon on les enterre dans le vide).
        evacuer(parc, admin);

        boolean ok = plugin.getParcelleManager().deleteParcelle(ownerUuid);
        if (!ok) { admin.sendMessage("§cSuppression impossible."); admin.closeInventory(); return; }

        admin.sendMessage("§4[ADMIN] §7Île de §e" + pseudo + " §7supprimée. §8(terrain en cours de nettoyage)");
        plugin.getLogger().info("[ADMIN] " + admin.getName() + " a supprimé l'Île de " + pseudo);
        admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);

        // Retour à la liste, rafraîchie.
        open(admin, page.getOrDefault(admin.getUniqueId(), 0));
    }

    /** Téléporte au spawn tous les joueurs présents sur la parcelle (sauf l'admin, prévenu). */
    private void evacuer(Parcelle parc, Player admin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!parc.contains(p.getLocation().getBlockX(), p.getLocation().getBlockZ())) continue;
            p.setWorldBorder(null);
            p.performCommand("spawn");
            if (!p.getUniqueId().equals(admin.getUniqueId())) {
                p.sendMessage("§cCette Île vient d'être supprimée par un administrateur.");
            }
        }
    }

    /** TP admin sur une Île, avec la WorldBorder de la parcelle (comme /ob visit). */
    private void teleporter(Player admin, Parcelle parc, UUID ownerUuid) {
        plugin.leaveMineIfNeeded(admin);
        plugin.getPlayerZoneMap().put(admin.getUniqueId(), "parcelle");
        org.bukkit.World w = Bukkit.getWorld("world");
        admin.teleport(new org.bukkit.Location(w,
                parc.getCenterX() + 0.5, plugin.getParcelleManager().getFloorY() + 1, parc.getCenterZ() + 0.5));

        org.bukkit.WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(parc.getCenterX() + 0.5, parc.getCenterZ() + 0.5);
        wb.setSize(parc.getSize() + 1);
        wb.setWarningDistance(0); wb.setWarningTime(0);
        admin.setWorldBorder(wb);

        String pseudo = Bukkit.getOfflinePlayer(ownerUuid).getName();
        admin.sendMessage("§4[ADMIN] §7Téléporté sur " + plugin.islandDisplayName(parc, pseudo) + "§7.");
        admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
    }
}
