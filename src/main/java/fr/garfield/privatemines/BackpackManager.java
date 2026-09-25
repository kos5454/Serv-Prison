package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Backpack (/bp) : stockage de 54 slots par joueur pour les blocs achetés hors parcelle,
 * vidable uniquement sur sa parcelle. Persisté dans playerdata.yml sous la clé ".bp.<i>".
 * Extrait de PrivateMines pour alléger le fichier principal.
 */
public class BackpackManager implements Listener {

    public static final String TITLE = "§8Backpack";

    private final PrivateMines plugin;

    public BackpackManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Contenu du backpack par joueur (54 slots).
    private final java.util.Map<java.util.UUID, ItemStack[]> backpacks = new java.util.HashMap<>();

    public ItemStack[] getBpContents(Player p) {
        return backpacks.computeIfAbsent(p.getUniqueId(), k -> new ItemStack[54]);
    }

    // Charge le backpack d'un joueur depuis la config (appelé par loadPlayer).
    public void loadBackpack(java.util.UUID id, org.bukkit.configuration.file.FileConfiguration dataConfig) {
        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < 54; i++) {
            contents[i] = dataConfig.getItemStack(id + ".bp." + i);
        }
        backpacks.put(id, contents);
    }

    // Dépose qty exemplaires de mat dans le backpack. Renvoie ce qui n'a pas pu rentrer.
    public int giveToBackpack(Player player, Material mat, int qty) {
        ItemStack[] bp = getBpContents(player);
        int remaining = qty;
        for (int i = 0; i < 54 && remaining > 0; i++) {
            if (bp[i] == null || bp[i].getType() == Material.AIR) {
                int stack = Math.min(mat.getMaxStackSize(), remaining);
                bp[i] = new ItemStack(mat, stack);
                remaining -= stack;
            } else if (bp[i].getType() == mat && bp[i].getAmount() < bp[i].getMaxStackSize()) {
                int add = Math.min(bp[i].getMaxStackSize() - bp[i].getAmount(), remaining);
                bp[i].setAmount(bp[i].getAmount() + add);
                remaining -= add;
            }
        }
        saveBp(player);
        return remaining;
    }

    // Dépose qty exemplaires de mat dans l'inventaire du joueur. Renvoie ce qui n'a pas pu rentrer.
    public int giveToInventory(Player player, Material mat, int qty) {
        int max = mat.getMaxStackSize();
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        int remaining = qty;
        while (remaining > 0) {
            int s = Math.min(max, remaining);
            stacks.add(new ItemStack(mat, s));
            remaining -= s;
        }
        java.util.Map<Integer, ItemStack> overflow =
                player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        int notAdded = 0;
        for (ItemStack left : overflow.values()) notAdded += left.getAmount();
        return notAdded;
    }

    public void openBackpack(Player player) {
        Inventory bp = Bukkit.createInventory(null, 54, TITLE);
        ItemStack[] contents = getBpContents(player);
        for (int i = 0; i < 54; i++) {
            if (contents[i] != null) bp.setItem(i, contents[i]);
        }
        player.openInventory(bp);
    }

    // Sauvegarde le contenu du backpack quand le joueur ferme le GUI.
    @EventHandler
    public void onBpClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        Player player = (Player) event.getPlayer();
        backpacks.put(player.getUniqueId(), event.getInventory().getContents().clone());
        saveBp(player);
    }

    // Bloque le retrait d'items du BP hors parcelle. EXCEPTION : le Chapeau de paille (déco / item-clé)
    // peut être déposé et retiré du /bp partout — c'est un objet perso qu'on range où l'on veut.
    @EventHandler
    public void onBpClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        Player player = (Player) event.getWhoClicked();
        // Le clic concerne le Chapeau de paille (dans le slot OU sur le curseur) → toujours autorisé.
        if (plugin.isStrawHat(event.getCurrentItem()) || plugin.isStrawHat(event.getCursor())) return;
        if (!plugin.isInParcelleZone(player)) {
            event.setCancelled(true);
            player.sendMessage("§cTu ne peux sortir tes blocs que sur ta parcelle !");
        }
    }

    // Sauvegarde le backpack dans playerdata.yml.
    public void saveBp(Player p) {
        org.bukkit.configuration.file.FileConfiguration dataConfig = plugin.getDataConfig();
        String id = p.getUniqueId().toString();
        ItemStack[] contents = getBpContents(p);
        for (int i = 0; i < 54; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                dataConfig.set(id + ".bp." + i, contents[i]);
            } else {
                dataConfig.set(id + ".bp." + i, null);
            }
        }
        plugin.saveDataConfig("le bp de " + p.getName());
    }
}
