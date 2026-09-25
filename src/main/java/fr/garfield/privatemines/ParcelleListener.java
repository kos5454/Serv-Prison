package fr.garfield.privatemines;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Protections sur les parcelles : casser / poser / coffres / portes / PvP / mort.
 *
 * Applique les permissions de la {@link Parcelle} (propriétaire, amis avec leurs
 * flags, visiteurs) et intègre la pose/récupération des spawners d'île.
 *
 * NB : les MENUS de parcelle (paramètres, amis) et leur logique d'achat restent
 * dans PrivateMines — ce listener ne couvre QUE les protections de terrain.
 *
 * Extrait de la god-class PrivateMines — dépend du plugin pour accéder au
 * {@link ParcelleManager}, au {@link SpawnerManager} et au helper {@code isBag}.
 */
public class ParcelleListener implements Listener {

    private final PrivateMines plugin;

    public ParcelleListener(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Protection parcelle : empêche casser si pas autorisé.
    @EventHandler
    public void onParcelleBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                event.getBlock().getX(), event.getBlock().getZ());
        if (parc == null) return;
        // Cassage d'un spawner d'île posé : on rend l'item taggé (niveaux conservés), pas de spawner vanilla.
        Block b = event.getBlock();
        if (b.getType() == Material.SPAWNER) {
            SpawnerManager.PlacedSpawner ps = plugin.getSpawnerManager().getPlacedAt(b.getX(), b.getY(), b.getZ());
            if (ps != null) {
                if (!parc.getOwner().equals(p.getUniqueId())) { event.setCancelled(true); p.sendMessage("§cTu ne peux pas casser ce spawner."); return; }
                // Un spawner posé ne se casse QU'AVEC le Pic du Démonteur (crate / vote).
                ItemStack main = p.getInventory().getItemInMainHand();
                if (!plugin.getSpawnerManager().isTool(main)) {
                    event.setCancelled(true);
                    p.sendMessage("§c✖ Ce spawner est scellé. Il te faut un §5Pic du Démonteur §c(dans les §fcrates§c) pour le récupérer.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                ItemStack given = plugin.getSpawnerManager().onSpawnerBroken(b.getX(), b.getY(), b.getZ());
                event.setDropItems(false); // pas de drop vanilla
                b.setType(Material.AIR);
                if (given != null) {
                    Map<Integer, ItemStack> left = p.getInventory().addItem(given);
                    for (ItemStack rem : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
                plugin.getSpawnerManager().consumeToolUse(p);
                p.sendMessage("§aSpawner récupéré §7(tes améliorations sont conservées).");
                return;
            }
        }
        // ── OneBlock « Le Bloc du Grand Appel » : le bloc central se casse en boucle.
        // Seul le PROPRIÉTAIRE (ou un ami autorisé à casser) peut miner son bloc.
        OneBlockManager ob = plugin.getOneBlock();
        if (ob != null && ob.isBlocCentral(parc, b.getX(), b.getY(), b.getZ())) {
            boolean allowed = parc.getOwner().equals(p.getUniqueId())
                    || (parc.isFriend(p.getUniqueId()) && parc.isFriendsCanBreak());
            if (!allowed) {
                event.setCancelled(true);
                p.sendMessage("§cTu ne peux pas casser ce bloc ici !");
                return;
            }
            event.setDropItems(false);   // pas de drop au sol : on donne dans l'inventaire
            ob.onBlocCasse(p, parc, b);  // +1, replace le bloc, donne le drop vanilla réel
            return;
        }

        if (parc.getOwner().equals(p.getUniqueId())) return; // proprio = libre
        // Ami avec la permission "casser" autorisé.
        if (parc.isFriend(p.getUniqueId()) && parc.isFriendsCanBreak()) return;
        event.setCancelled(true);
        p.sendMessage("§cTu ne peux pas casser ici !");
    }

    // Protection parcelle : empêche poser si pas autorisé.
    @EventHandler
    public void onParcellePlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (plugin.isBag(event.getItemInHand())) return; // déjà géré ailleurs
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                event.getBlock().getX(), event.getBlock().getZ());
        if (parc == null) return;
        // Pose d'un spawner d'île taggé (uniquement par le propriétaire, sur sa parcelle).
        String spType = plugin.getSpawnerManager().spawnerTypeOf(event.getItemInHand());
        if (spType != null) {
            if (!parc.getOwner().equals(p.getUniqueId())) {
                event.setCancelled(true);
                p.sendMessage("§cTu ne peux poser un spawner que sur TA parcelle.");
                return;
            }
            // Un seul spawner de chaque type par parcelle.
            if (plugin.getSpawnerManager().countOnParcelle(parc, spType) >= 1) {
                event.setCancelled(true);
                String nom = plugin.getSpawnerManager().defByType(spType).getName();
                p.sendMessage("§c✖ Tu as déjà un " + nom + " §csur ta parcelle §7(1 seul par type).");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            plugin.getSpawnerManager().onSpawnerPlaced(event.getBlock(), spType, event.getItemInHand());
            p.sendMessage("§aSpawner posé ! Il génère déjà des mobs. §7(clic droit dessus = améliorer)");
            return;
        }
        if (parc.getOwner().equals(p.getUniqueId())) return;
        // Ami avec la permission "poser" autorisé.
        if (parc.isFriend(p.getUniqueId()) && parc.isFriendsCanPlace()) return;
        event.setCancelled(true);
        p.sendMessage("§cTu ne peux pas poser ici !");
    }

    // Protection coffres sur parcelle.
    @EventHandler
    public void onParcelleChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST
                && type != Material.BARREL && type != Material.SHULKER_BOX) return;
        Player p = event.getPlayer();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                event.getClickedBlock().getX(), event.getClickedBlock().getZ());
        if (parc == null) return;
        // Le coffre du OneBlock (bloc central) ne s'OUVRE pas : il faut le CASSER pour récupérer le loot.
        org.bukkit.block.Block cb = event.getClickedBlock();
        OneBlockManager ob = plugin.getOneBlock();
        if (ob != null && ob.isBlocCentral(parc, cb.getX(), cb.getY(), cb.getZ())) {
            event.setCancelled(true);
            p.sendMessage("§7Casse ce coffre pour récupérer son butin !");
            return;
        }
        if (parc.getOwner().equals(p.getUniqueId())) return;
        // Ami avec la permission "coffres" autorisé.
        if (parc.isFriend(p.getUniqueId()) && parc.isFriendsCanChests()) return;
        if (!parc.canEnter(p.getUniqueId()) || !parc.isVisitorsCanChests()) {
            event.setCancelled(true);
            p.sendMessage("§cTu ne peux pas ouvrir les coffres ici !");
        }
    }

    // Protection portes/portails sur parcelle (ami avec perm "portes" autorisé).
    @EventHandler
    public void onParcelleDoor(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        String n = type.name();
        boolean isDoorLike = n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")
                || n.endsWith("_FENCE_GATE") || n.endsWith("_BUTTON")
                || type == Material.LEVER;
        if (!isDoorLike) return;
        Player p = event.getPlayer();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                event.getClickedBlock().getX(), event.getClickedBlock().getZ());
        if (parc == null) return;
        if (parc.getOwner().equals(p.getUniqueId())) return;
        // Ami avec perm "portes" autorisé.
        if (parc.isFriend(p.getUniqueId()) && parc.isFriendsCanDoors()) return;
        // Sinon : visiteurs autorisés à interagir ?
        if (parc.canEnter(p.getUniqueId()) && parc.isVisitorsCanInteract()) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        p.sendMessage("§cTu ne peux pas utiliser ça ici !");
    }

    // Protection PvP sur parcelle : on bloque UNIQUEMENT le vrai joueur→joueur.
    // Les mobs hostiles (OneBlock, brique 9) DOIVENT pouvoir blesser le joueur → on les laisse passer.
    @EventHandler
    public void onParcellePvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                victim.getLocation().getBlockX(), victim.getLocation().getBlockZ());
        if (parc == null) return;

        // Détermine l'attaquant réel : direct, ou le tireur si c'est un projectile (flèche…).
        org.bukkit.entity.Entity attaquant = event.getDamager();
        if (attaquant instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof org.bukkit.entity.Entity src) {
            attaquant = src;
        }
        // On n'annule QUE si l'attaquant est un autre joueur (vrai PvP). Un mob → dégâts autorisés.
        if (attaquant instanceof Player) {
            event.setCancelled(true);
        }
    }

    // Pas de perte d'items à la mort sur la parcelle.
    @EventHandler
    public void onParcelleDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(
                p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        if (parc == null) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
    }
}
