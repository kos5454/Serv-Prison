package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Protection des zones (spawn / PvP) et respawn.
 *
 * Regroupe les règles de zone protégée pilotées par les flags de {@link ZoneManager}
 * (« break », « pvp ») ainsi que la logique de respawn au spawn du serveur.
 *
 * Extrait de la god-class PrivateMines pour l'alléger — dépend du plugin pour
 * accéder aux zones ({@code plugin.getZones()}) et au spawn ({@code plugin.getSpawnLocation()}).
 */
public class ProtectionListener implements Listener {

    private final PrivateMines plugin;

    public ProtectionListener(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Zones protégées : empêche de casser des blocs si le flag "break" est actif (sauf OP).
    @EventHandler
    public void onSpawnBreak(BlockBreakEvent event) {
        if (!event.getPlayer().isOp()
                && plugin.getZones().getProtectingZone(event.getBlock().getLocation(), "break") != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cTu ne peux pas casser ici (zone protegee).");
        }
    }

    // Zones protégées : pas de PvP/dégâts si le flag "pvp" est actif.
    @EventHandler
    public void onSpawnPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (plugin.getZones().getProtectingZone(event.getEntity().getLocation(), "pvp") != null) {
            event.setCancelled(true);
        }
    }

    // À la mort : on respawn au spawn (/setspawn), ou à 0,0 par défaut.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = plugin.getSpawnLocation();
        if (spawn == null) {
            // Pas de spawn défini : on envoie à 0,0 (au sol du monde principal).
            World world = Bukkit.getWorld("world");
            spawn = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        }
        event.setRespawnLocation(spawn);
    }
}
