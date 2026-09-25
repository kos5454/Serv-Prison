package fr.garfield.privatemines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Système « bloc incassable de CRATE ». Certains blocs normalement incassables en survie
 * (COMMAND_BLOCK, BEDROCK) peuvent tomber en récompense de crate ; on les tamponne d'un
 * marqueur invisible (PDC). Une fois marqués, le joueur peut :
 *  - les POSER (on mémorise la position dans crate_blocks.yml, persistant) ;
 *  - les RÉCUPÉRER en tapant dessus (clic gauche) — comme ces blocs sont incassables,
 *    on simule la casse (setType AIR) et on redonne l'item marqué.
 *
 * Un bloc du même type présent naturellement dans le monde (NON marqué) reste incassable :
 * seul un bloc posé depuis un item de crate figure dans la liste des positions.
 */
public class CrateBlockListener implements Listener {

    private final PrivateMines plugin;
    private final org.bukkit.NamespacedKey markKey;

    // Positions des blocs incassables issus de crate (clé "world:x:y:z"), persistées.
    private final Set<String> positions = new HashSet<>();
    private final File file;
    private org.bukkit.configuration.file.FileConfiguration config;

    // Types concernés par le système (les seuls incassables autorisés en crate).
    private static final Set<Material> TYPES = new HashSet<>(java.util.Arrays.asList(
            Material.COMMAND_BLOCK, Material.BEDROCK
    ));

    public CrateBlockListener(PrivateMines plugin) {
        this.plugin = plugin;
        this.markKey = new org.bukkit.NamespacedKey(plugin, "crate_breakable");
        this.file = new File(plugin.getDataFolder(), "crate_blocks.yml");
        load();
    }

    // ---- Persistance ----
    private void load() {
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        positions.clear();
        positions.addAll(config.getStringList("positions"));
    }
    private void save() {
        config.set("positions", new java.util.ArrayList<>(positions));
        try { config.save(file); }
        catch (Exception e) { plugin.getLogger().warning("Sauvegarde crate_blocks.yml impossible : " + e.getMessage()); }
    }
    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    // ---- Marquage de l'item (appelé par CrateManager quand une crate donne un tel bloc) ----
    /** Vrai si ce Material relève du système incassable-de-crate. */
    public static boolean estConcerne(Material m) { return TYPES.contains(m); }

    /** Tamponne un item bloc pour qu'une fois posé il devienne récupérable. Renvoie l'item marqué. */
    public ItemStack tag(ItemStack it) {
        if (it == null || !estConcerne(it.getType())) return it;
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        // Nom coloré violet + gras (item collector).
        m.setDisplayName("§5§l" + nomAffiche(it.getType()));
        // Petit lore pour que le joueur sache que ce bloc est spécial.
        java.util.List<String> lore = m.hasLore() ? m.getLore() : new java.util.ArrayList<>();
        lore.add("§8Bloc de coffre — posable et récupérable");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Nom lisible du bloc pour l'affichage.
    private static String nomAffiche(Material mat) {
        switch (mat) {
            case COMMAND_BLOCK: return "Command Block";
            case BEDROCK:       return "Bedrock";
            default:            return mat.name().toLowerCase().replace('_', ' ');
        }
    }

    private boolean estMarque(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(markKey, PersistentDataType.BYTE);
    }

    // ---- POSE normale (bedrock) : on mémorise la position d'un bloc marqué ----
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!estMarque(inHand)) return;
        if (!estConcerne(event.getBlock().getType())) return;
        positions.add(key(event.getBlock().getLocation()));
        save();
        event.getPlayer().sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("§8Bloc de coffre posé — tape dessus pour le récupérer"));
    }

    // ---- POSE FORCÉE du command block : vanilla refuse la pose en survie, donc on la
    //      simule à la main quand le joueur clique-droit sur une face de bloc avec un
    //      command block MARQUÉ (issu de crate) en main. Priorité HIGH pour agir avant
    //      d'éventuelles protections, et on n'intervient QUE pour l'item marqué.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlaceCommandBlock(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return; // évite le double-traitement main/off-main
        ItemStack inHand = event.getItem();
        if (inHand == null || inHand.getType() != Material.COMMAND_BLOCK) return;
        if (!estMarque(inHand)) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        // Empêche le comportement vanilla (qui refuserait) et on place nous-mêmes.
        event.setCancelled(true);
        Player p = event.getPlayer();
        Block target = clicked.getRelative(event.getBlockFace());
        // On ne pose que dans de l'air / un bloc remplaçable, et pas dans le joueur.
        if (!target.getType().isAir() && !target.isReplaceable()) return;
        if (target.getLocation().distanceSquared(p.getLocation()) < 0.6) return;
        target.setType(Material.COMMAND_BLOCK, false);
        positions.add(key(target.getLocation()));
        save();
        // Consomme un exemplaire de l'item en main (comme une pose normale, hors créatif).
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            int amt = inHand.getAmount();
            if (amt <= 1) p.getInventory().setItemInMainHand(null);
            else inHand.setAmount(amt - 1);
        }
        p.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_STONE_PLACE, 1f, 1f);
        p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("§8Command Block posé — tape dessus pour le récupérer"));
    }

    // ---- CASSE par clic gauche : ces blocs sont incassables, on simule la casse ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null || !estConcerne(b.getType())) return;
        String k = key(b.getLocation());
        if (!positions.contains(k)) return; // bloc du monde non issu de crate : on ne touche à rien
        Player p = event.getPlayer();
        // On récupère : casse simulée + rendu de l'item marqué.
        Material type = b.getType();
        b.setType(Material.AIR);
        positions.remove(k);
        save();
        ItemStack drop = tag(new ItemStack(type, 1));
        java.util.Map<Integer, ItemStack> reste = p.getInventory().addItem(drop);
        for (ItemStack rem : reste.values()) p.getWorld().dropItemNaturally(p.getLocation(), rem);
        p.getWorld().playSound(b.getLocation(), org.bukkit.Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
        event.setCancelled(true);
    }

    // ---- Sécurité : si un tel bloc est cassé par un autre moyen (explosion mine, etc.), on nettoie la position ----
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String k = key(event.getBlock().getLocation());
        if (positions.remove(k)) save();
    }
}
