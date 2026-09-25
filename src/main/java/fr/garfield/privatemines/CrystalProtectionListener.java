package fr.garfield.privatemines;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Protection des cristaux de l'End (déco du Puits des Souvenirs).
 *
 * Un End Crystal est une ENTITÉ : la règle /zone (qui protège des blocs) ne le
 * couvre pas. On empêche donc les joueurs de le frapper et on rend ses
 * explosions totalement inoffensives (aucun bloc cassé, aucun joueur blessé).
 *
 * ⚠️ EXCEPTION : dans le VRAI monde de l'End ({@code world_the_end}), les cristaux
 * sont ceux du dragon (event quotidien) — ils doivent rester DESTRUCTIBLES et vanilla.
 * La protection ne s'applique donc qu'HORS de l'End (Puits des Souvenirs, etc.).
 *
 * Handlers autonomes (ne dépendent d'aucun état de PrivateMines) — extraits de
 * la god-class pour l'alléger.
 */
public class CrystalProtectionListener implements Listener {

    // Vrai si l'entité est un cristal situé DANS le monde de l'End (→ pas de protection).
    private boolean dansEnd(org.bukkit.entity.Entity e) {
        return e != null && e.getWorld() != null
                && e.getWorld().getName().equals(EndManager.END_WORLD);
    }

    // 1) Un joueur (ou toute entité) ne peut pas endommager/détruire un cristal de l'End
    //    — SAUF dans le monde de l'End (cristaux du dragon, destructibles).
    @EventHandler
    public void onCrystalDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.END_CRYSTAL && !dansEnd(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // 2) Si un cristal explose malgré tout (autre cause), l'explosion ne casse AUCUN bloc
    //    — hors de l'End (dans l'End, l'explosion vanilla est conservée).
    @EventHandler
    public void onCrystalExplode(EntityExplodeEvent event) {
        if (event.getEntityType() == EntityType.END_CRYSTAL && !dansEnd(event.getEntity())) {
            event.blockList().clear();      // aucun bloc détruit
            event.setCancelled(true);       // et on annule carrément l'explosion
        }
    }

    // 3) Et l'explosion d'un cristal ne blesse aucun joueur — hors de l'End.
    @EventHandler
    public void onCrystalDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() == EntityType.END_CRYSTAL && !dansEnd(event.getDamager())) {
            event.setCancelled(true);
        }
    }
}
