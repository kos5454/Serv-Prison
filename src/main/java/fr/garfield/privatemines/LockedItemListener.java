package fr.garfield.privatemines;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Protection des items « fixes » du joueur : le Sac de Minage, la Pioche et
 * l'Ordinateur Quantique peuvent être RANGÉS librement dans l'inventaire (n'importe
 * quel slot), mais ne peuvent JAMAIS être jetés au sol (touche Q / drop / hotbar-swap
 * vers l'extérieur). Empêche aussi de jeter quoi que ce soit au spawn ou dans la mine
 * (anti-perte d'items-quête).
 *
 * Extrait de la god-class PrivateMines — dépend du plugin pour les tests d'item
 * ({@code isBag}/{@code isPickaxe}/{@code isOrdi}) et la zone du joueur
 * ({@code getPlayerZoneMap()}).
 *
 * NB : le MENU du sac (achats capacité/vente) reste dans PrivateMines.
 */
public class LockedItemListener implements Listener {

    private final PrivateMines plugin;

    public LockedItemListener(PrivateMines plugin) {
        this.plugin = plugin;
    }

    private boolean estProtege(ItemStack it) {
        return plugin.isBag(it) || plugin.isPickaxe(it) || plugin.isOrdi(it) || plugin.isPetsHead(it);
    }

    // Empêche de jeter le sac, la pioche, et TOUT item quand on est au spawn OU dans la mine.
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        // Dans l'End : le drop est TOTALEMENT libre (zone de PvP/loot). L'inventaire de l'End est
        // l'inventaire hors-mine : ni pioche ni sac ne s'y trouvent, aucune protection nécessaire.
        if (EndManager.END_WORLD.equals(p.getWorld().getName())) {
            return;
        }
        String zone = plugin.getPlayerZoneMap().get(p.getUniqueId());
        // Au spawn ET dans la mine : on ne peut rien lâcher (évite de perdre des items-quête, la Tête de Pioche, etc.).
        if ("spawn".equals(zone) || "mine".equals(zone)) {
            event.setCancelled(true);
            p.sendActionBar(LegacyComponentSerializer
                    .legacySection().deserialize("§cTu ne peux pas jeter d'objets ici."));
            return;
        }
        if (estProtege(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // Autorise le déplacement dans l'inventaire, mais bloque uniquement le fait de JETER
    // (touche Q / Ctrl+Q) le sac, la pioche ou l'ordinateur.
    @EventHandler
    public void onThrowLockedItem(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (estProtege(event.getCurrentItem())) event.setCancelled(true);
        }
    }
}
