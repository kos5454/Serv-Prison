package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Spawners d'île : interaction (menu d'amélioration), mort de mob (loot fer/or
 * fusionné + respawn du stack), et contrôle du spawn naturel des mobs par zone.
 *
 * Extrait de la god-class PrivateMines — dépend du plugin pour accéder au
 * {@link SpawnerManager}, au {@link ParcelleManager} et au {@link ZoneManager}.
 */
public class SpawnerListener implements Listener {

    private static final double LOOT_MERGE_RADIUS = 4.0; // rayon de fusion des lingots au sol

    private final PrivateMines plugin;

    public SpawnerListener(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Clic droit sur un spawner d'île posé : ouvre le menu d'amélioration (proprio uniquement).
    @EventHandler
    public void onSpawnerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.SPAWNER) return;
        Block b = event.getClickedBlock();
        SpawnerManager.PlacedSpawner ps = plugin.getSpawnerManager().getPlacedAt(b.getX(), b.getY(), b.getZ());
        if (ps == null) return; // spawner vanilla quelconque : on ignore
        event.setCancelled(true); // pas d'interaction vanilla
        Player p = event.getPlayer();
        Parcelle parc = plugin.getParcelleManager().getParcelleAt(b.getX(), b.getZ());
        if (parc != null && !parc.getOwner().equals(p.getUniqueId())) {
            p.sendMessage("§cSeul le propriétaire peut améliorer ce spawner.");
            return;
        }
        plugin.getSpawnerManager().openUpgradeMenu(p, ps);
    }

    // Mort d'un mob de spawner d'île (vrai combat) : loot fer/or au sol + respawn si le stack > 0.
    @EventHandler
    public void onSpawnerMobDeath(EntityDeathEvent event) {
        LivingEntity ent = event.getEntity();
        if (!ent.hasMetadata("island_spawner")) return;
        SpawnerManager.PlacedSpawner s = plugin.getSpawnerManager().spawnerOfEntity(ent.getUniqueId());
        // On remplace le loot vanilla par NOTRE loot (fer/or selon le type, ×niveau de loot).
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (s == null) return;
        int loot = plugin.getSpawnerManager().onMobDeath(s); // décrémente le stack + respawn si besoin
        Material ingot = "gold".equals(s.type) ? Material.GOLD_INGOT : Material.IRON_INGOT;
        // On gère le drop nous-mêmes (au lieu de event.getDrops()) pour le fusionner avec
        // les lingots du même type déjà au sol dans un rayon de ~4 blocs : un seul tas visible.
        event.getDrops().clear();
        if (loot > 0) dropAndMergeLoot(ent.getLocation(), ingot, loot);
        // Supprimer le cadavre immédiatement (au tick suivant) : sinon l'anim de mort (~1s)
        // laisse le corps rouge bloquer le mob qui a respawn au même endroit.
        Bukkit.getScheduler().runTask(plugin, ent::remove);
    }

    // Fait tomber le loot fer/or et le fusionne avec un tas existant du même type à proximité.
    private void dropAndMergeLoot(Location loc, Material mat, int amount) {
        // Cherche un item-drop du même type déjà au sol dans le rayon → on incrémente sa pile.
        for (Entity e : loc.getWorld().getNearbyEntities(loc, LOOT_MERGE_RADIUS, LOOT_MERGE_RADIUS, LOOT_MERGE_RADIUS)) {
            if (!(e instanceof Item item)) continue;
            ItemStack stack = item.getItemStack();
            if (stack.getType() != mat) continue;
            item.setItemStack(new ItemStack(mat, stack.getAmount() + amount));
            item.setPickupDelay(0);
            return; // fusionné : aucun nouvel item créé
        }
        // Aucun tas proche : on crée le premier item.
        Item dropped = loc.getWorld().dropItem(loc, new ItemStack(mat, amount));
        dropped.setPickupDelay(0);
    }

    // Contrôle du spawn des mobs par zone (flags "mobs" = hostiles, "animals" = neutres/animaux).
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity ent = event.getEntity();
        // Ne JAMAIS toucher aux entités posées par le plugin (PNJ, spawners d'île, OneBlock…).
        if (estAuPlugin(ent)) return;
        // Ni aux spawns VOULUS par le plugin (OneBlock : animaux du biome + mobs hostiles de brique 9,
        // créés via spawnEntity -> raison CUSTOM). Le contrôle de zone ne vise que les spawns NATURELS.
        if (event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        String flag = flagDe(ent);
        if (flag == null) return;   // pas une créature (ex : ArmorStand des crates et des holos)

        if (plugin.getZones().isSpawnBlocked(ent.getLocation(), flag)) {
            event.setCancelled(true);
        }
    }

    /**
     * Sous quel flag de zone tombe cette entité : {@code "mobs"} (hostiles) ou {@code "animals"}
     * (tout le reste). Renvoie {@code null} si l'entité n'est pas une créature.
     *
     * <p>⚠ Depuis le 2026-08-23, TOUT ce qui n'est pas hostile tombe sous « Animaux/neutres ».
     * Avant, la liste était fermée (Animals, WaterMob, Ambient, Golem) et les villageois, les
     * marchands ambulants et compagnie ne tombaient sous AUCUN flag : ils spawnaient quoi que
     * l'admin coche dans /zone.</p>
     *
     * <p>⚠ Le filtre {@code instanceof Mob} est essentiel : sans lui, les ArmorStand (têtes des
     * crates physiques, hologrammes) seraient traités comme des animaux et purgés.</p>
     *
     * <p>Partagée avec la purge périodique de {@link ZoneManager} pour que le blocage du spawn
     * et le nettoyage classent exactement pareil.</p>
     */
    public static String flagDe(LivingEntity ent) {
        if (!(ent instanceof org.bukkit.entity.Mob)) return null;
        boolean hostile = ent instanceof Monster
                || ent instanceof Slime
                || ent instanceof Ghast
                || ent instanceof Phantom;
        return hostile ? "mobs" : "animals";
    }

    /** Entités posées par le plugin : jamais bloquées ni purgées, où qu'elles soient. */
    public static boolean estAuPlugin(org.bukkit.entity.Entity ent) {
        return ent.hasMetadata("NPC")              // PNJ Citizens
            || ent.hasMetadata("island_spawner")   // mobs de nos spawners d'île
            || ent.hasMetadata("island_crate")     // têtes des crates physiques
            || ent.hasMetadata("ob_merchant")      // la Caravane du OneBlock
            || ent.hasMetadata("ob_animal")        // animaux de biome du OneBlock
            || ent.hasMetadata("ob_mob");          // mobs hostiles du OneBlock
    }
}
