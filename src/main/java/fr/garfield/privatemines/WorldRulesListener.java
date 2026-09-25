package fr.garfield.privatemines;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Règles globales du monde (valables partout sur le serveur SAUF l'End) :
 *  - aucun dégât de chute pour les joueurs — sauf dans l'End où ils restent actifs ;
 *  - les blocs à gravité (sable, gravier, concrete powder, enclume...) ne tombent
 *    jamais : dès qu'un bloc tente de se transformer en FallingBlock, on annule.
 *
 * Handlers autonomes (ne dépendent d'aucun état de PrivateMines) — extraits de
 * la god-class pour l'alléger.
 */
public class WorldRulesListener implements Listener {

    // Pas de dégâts de chute — partout SAUF dans l'End (zone de danger : chute létale).
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (EndManager.END_WORLD.equals(event.getEntity().getWorld().getName())) return; // End : chute active
            event.setCancelled(true);
        }
    }

    // Empêche tout bloc à gravité (sable, gravier, concrete powder, enclume...)
    // de tomber : quand il essaie de se transformer en FallingBlock, on annule.
    @EventHandler
    public void onGravityBlockFall(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.FALLING_BLOCK) {
            event.setCancelled(true);
        }
    }
}
