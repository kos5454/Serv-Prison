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

/**
 * Menu /warp : téléportation rapide vers les lieux du serveur (icônes cliquables).
 * Remplace le /warp d'un autre plugin (déclaré dans plugin.yml → PrivateMines gère la commande).
 *
 * Destinations : Spawn (réutilise /spawn), Classements (-65/86/627 plein Nord), Ma mine
 * (réutilise /mine), et Zone PvP « à venir » (bouton verrouillé tant que la zone n'existe pas).
 */
public class WarpMenu implements Listener {

    private static final String WARP_TITLE = "§b§l✦ Warps ✦";

    // Slots des icônes dans un inventaire 27 cases.
    private static final int SLOT_SPAWN = 10;
    private static final int SLOT_CLASSEMENTS = 12;
    private static final int SLOT_MINE = 13;
    private static final int SLOT_CRATES = 14;
    private static final int SLOT_END = 16;
    private static final int SLOT_CLOSE = 22;

    private final PrivateMines plugin;

    public WarpMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    public void openWarp(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, WARP_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, bg);
        ItemStack border = plugin.pane(Material.CYAN_STAINED_GLASS_PANE);
        int[] frame = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int s : frame) menu.setItem(s, border);

        // Spawn
        menu.setItem(SLOT_SPAWN, plugin.namedItem(Material.RESPAWN_ANCHOR,
                "§a§lSpawn",
                "§7Le port d'arrivée du serveur.",
                "",
                "§e▶ Clique pour t'y téléporter"));

        // Classements
        menu.setItem(SLOT_CLASSEMENTS, plugin.namedItem(Material.GOLD_BLOCK,
                "§6§lClassements",
                "§7La zone des tops du serveur.",
                "",
                "§e▶ Clique pour t'y téléporter"));

        // Ma mine
        menu.setItem(SLOT_MINE, plugin.namedItem(Material.DIAMOND_PICKAXE,
                "§b§lMa mine",
                "§7Retourne à ta mine privée.",
                "",
                "§e▶ Clique pour t'y téléporter"));

        // Crates — la zone des coffres à clés.
        menu.setItem(SLOT_CRATES, plugin.namedItem(Material.TRIPWIRE_HOOK,
                "§d§lCrates",
                "§7La zone des coffres à récompenses.",
                "",
                "§e▶ Clique pour t'y téléporter"));

        // End — l'Antre du Vide (event quotidien du Dragon).
        menu.setItem(SLOT_END, plugin.namedItem(Material.DRAGON_HEAD,
                "§5§lL'End §7— l'Antre du Vide",
                "§7Le §5Dragon du Vide §7apparaît §fchaque jour à 20h§7.",
                "§7Le §fTop 3 §7des dégâts se partage le butin.",
                "",
                "§c⚠ PvP activé · perte de stuff à la mort",
                "",
                "§e▶ Clique pour t'y téléporter"));

        menu.setItem(SLOT_CLOSE, plugin.namedItem(Material.BARRIER, "§cFermer", (String[]) null));
        player.openInventory(menu);
    }

    @EventHandler
    public void onWarpClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!WARP_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_SPAWN:
                player.closeInventory();
                player.performCommand("spawn");
                break;
            case SLOT_CLASSEMENTS: {
                player.closeInventory();
                // Même point que /classements : -65, 86, 627, plein Nord (yaw 180).
                Location loc = new Location(player.getWorld(), -65 + 0.5, 86, 627 + 0.5, 180f, 0f);
                player.teleport(loc);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                break;
            }
            case SLOT_MINE:
                player.closeInventory();
                player.performCommand("mine");
                break;
            case SLOT_CRATES: {
                player.closeInventory();
                // Yaw 90 = plein Ouest.
                Location loc = new Location(player.getWorld(), -27 + 0.5, 19, -149 + 0.5, 90f, 0f);
                player.teleport(loc);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                break;
            }
            case SLOT_END:
                player.closeInventory();
                plugin.getEnd().teleporterSalle(player);
                break;
            case SLOT_CLOSE:
                player.closeInventory();
                break;
            default:
                break;
        }
    }
}
