package fr.garfield.privatemines;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Préfixe de chat : ajoute « (N) » devant le pseudo, où N est le numéro de la
 * meilleure mine débloquée par le joueur (son « rang de mine »).
 *
 * Extrait de la god-class PrivateMines — dépend du plugin pour {@code getMineRank()}.
 *
 * NB : le renommage/suppression de zone par le chat ({@code onRenameChat}) reste
 * dans PrivateMines car il partage l'état d'édition de zone avec le menu zone.
 */
public class ChatTagListener implements Listener {

    private final PrivateMines plugin;

    public ChatTagListener(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Préfixe (N) = numéro de mine, avant le pseudo dans le chat.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMineChatTag(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        event.setFormat("§7(§e" + plugin.getMineRank(event.getPlayer()) + "§7) " + event.getFormat());
    }
}
