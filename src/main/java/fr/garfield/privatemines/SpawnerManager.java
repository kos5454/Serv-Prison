package fr.garfield.privatemines;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawners d'île (système « Niveau de parcelle »).
 * Brique B : définition des types de spawner + fabrication de l'item « taggé » vendu au shop.
 * (La pose sur la parcelle, le stack de mobs, le kill et les améliorations = briques C/D/E.)
 */
public class SpawnerManager implements org.bukkit.event.Listener {

    private final PrivateMines plugin;

    // Clés NBT posées sur l'item spawner (persistantes).
    private final NamespacedKey typeKey;      // "iron" | "gold"
    private final NamespacedKey lvlSpeedKey;  // niveau vitesse de génération
    private final NamespacedKey lvlAmountKey; // niveau nb de mobs par cycle
    private final NamespacedKey lvlCapKey;    // niveau capacité max du stack
    private final NamespacedKey lvlLootKey;   // niveau loot par mob
    private final NamespacedKey toolKey;      // marque l'outil « Pic du Démonteur »
    private final NamespacedKey toolUsesKey;  // utilisations restantes de l'outil

    public SpawnerManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.typeKey     = new NamespacedKey(plugin, "spawner_type");
        this.lvlSpeedKey = new NamespacedKey(plugin, "spawner_lvl_speed");
        this.lvlAmountKey= new NamespacedKey(plugin, "spawner_lvl_amount");
        this.lvlCapKey   = new NamespacedKey(plugin, "spawner_lvl_cap");
        this.lvlLootKey  = new NamespacedKey(plugin, "spawner_lvl_loot");
        this.toolKey     = new NamespacedKey(plugin, "spawner_tool");
        this.toolUsesKey = new NamespacedKey(plugin, "spawner_tool_uses");
    }

    public NamespacedKey getTypeKey()   { return typeKey; }
    public NamespacedKey getSpeedKey()  { return lvlSpeedKey; }
    public NamespacedKey getAmountKey() { return lvlAmountKey; }
    public NamespacedKey getCapKey()    { return lvlCapKey; }
    public NamespacedKey getLootKey()   { return lvlLootKey; }

    // ===== Définition d'un type de spawner =====
    public static final class SpawnerDef {
        final String type; final String name; final EntityType mob; final Material lootIngot;
        final double price;
        SpawnerDef(String type, String name, EntityType mob, Material lootIngot, double price) {
            this.type = type; this.name = name; this.mob = mob; this.lootIngot = lootIngot; this.price = price;
        }
        public String getType() { return type; }
        public String getName() { return name; }
        public EntityType getMob() { return mob; }
        public Material getLootIngot() { return lootIngot; }
        public double getPrice() { return price; }
    }

    // Les 2 spawners disponibles.
    public static final SpawnerDef IRON = new SpawnerDef("iron", "§f§lSpawner de Golem de Fer",
            EntityType.IRON_GOLEM, Material.IRON_INGOT, 1_000_000.0);
    public static final SpawnerDef GOLD = new SpawnerDef("gold", "§e§lSpawner de Piglin Zombifié",
            EntityType.ZOMBIFIED_PIGLIN, Material.GOLD_INGOT, 1_500_000.0);

    public SpawnerDef defByType(String type) {
        if ("gold".equals(type)) return GOLD;
        return IRON;
    }

    // Fabrique l'item spawner « taggé » (niveaux d'amélioration à 1 au départ) pour la vente/livraison.
    public ItemStack makeSpawnerItem(SpawnerDef def, int amount) {
        ItemStack it = new ItemStack(Material.SPAWNER, amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(def.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Pose-le sur ta §eparcelle §7pour");
        lore.add("§7générer du " + (def.type.equals("iron") ? "§ffer" : "§eor") + "§7.");
        lore.add("");
        lore.add("§8» Clic droit posé = améliorer");
        lore.add("§8» Frappe le mob = récupérer le loot");
        m.setLore(lore);
        // NBT : type + niveaux d'amélioration initiaux (1).
        m.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, def.type);
        m.getPersistentDataContainer().set(lvlSpeedKey, PersistentDataType.INTEGER, 1);
        m.getPersistentDataContainer().set(lvlAmountKey, PersistentDataType.INTEGER, 1);
        m.getPersistentDataContainer().set(lvlCapKey, PersistentDataType.INTEGER, 1);
        m.getPersistentDataContainer().set(lvlLootKey, PersistentDataType.INTEGER, 1);
        it.setItemMeta(m);
        return it;
    }

    // Vrai si l'item est un de nos spawners taggés ; renvoie son type ("iron"/"gold") ou null sinon.
    public String spawnerTypeOf(ItemStack it) {
        if (it == null || it.getType() != Material.SPAWNER || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    // Donne un spawner au joueur (inventaire, ou par terre si plein).
    public void giveSpawner(Player player, SpawnerDef def, int amount) {
        ItemStack item = makeSpawnerItem(def, amount);
        java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack rem : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rem);
    }

    // ==========================================================================================
    //  BRIQUE C : spawners POSÉS sur les parcelles (mob stacké + compteur + génération).
    // ==========================================================================================

    // Un spawner posé sur une parcelle.
    public static final class PlacedSpawner {
        String type;                 // "iron" | "gold"
        int x, y, z;                 // position du bloc spawner
        int lvlSpeed = 1, lvlAmount = 1, lvlCap = 1, lvlLoot = 1;
        int stack = 0;               // nombre de mobs actuellement dans le stack
        int tickCounter = 0;         // compteur interne pour la cadence de génération
        transient java.util.UUID mobUuid; // l'entité affichée (1 seule)
    }

    // Tous les spawners posés (clé = "x,y,z").
    private final java.util.Map<String, PlacedSpawner> placed = new java.util.HashMap<>();
    private java.io.File spawnersFile;
    private org.bukkit.configuration.file.YamlConfiguration spawnersConfig;

    private static String posKey(int x, int y, int z) { return x + "," + y + "," + z; }

    // ---- Paramètres de progression (par niveau) ----
    // Cadence : cycles de génération = toutes (max(2, 11 - lvlSpeed)) secondes → niv1 = 10s, niv9 = 2s.
    private int cycleSeconds(PlacedSpawner s) { return Math.max(2, 11 - s.lvlSpeed); }
    // Mobs ajoutés par cycle = lvlAmount.
    private int mobsPerCycle(PlacedSpawner s) { return s.lvlAmount; }
    // Capacité max du stack = 50 * lvlCap.
    private int stackCap(PlacedSpawner s) { return 50 * s.lvlCap; }
    // Loot (lingots) par mob tué = lvlLoot.
    public int lootPerMob(PlacedSpawner s) { return s.lvlLoot; }

    // ---- Chargement / sauvegarde ----
    public void loadSpawners() {
        spawnersFile = new java.io.File(plugin.getDataFolder(), "spawners.yml");
        if (!spawnersFile.exists()) {
            try { plugin.getDataFolder().mkdirs(); spawnersFile.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        spawnersConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnersFile);
        for (String key : spawnersConfig.getKeys(false)) {
            try {
                PlacedSpawner s = new PlacedSpawner();
                s.type = spawnersConfig.getString(key + ".type", "iron");
                s.x = spawnersConfig.getInt(key + ".x");
                s.y = spawnersConfig.getInt(key + ".y");
                s.z = spawnersConfig.getInt(key + ".z");
                s.lvlSpeed  = spawnersConfig.getInt(key + ".lvlSpeed", 1);
                s.lvlAmount = spawnersConfig.getInt(key + ".lvlAmount", 1);
                s.lvlCap    = spawnersConfig.getInt(key + ".lvlCap", 1);
                s.lvlLoot   = spawnersConfig.getInt(key + ".lvlLoot", 1);
                s.stack     = spawnersConfig.getInt(key + ".stack", 0);
                placed.put(key, s);
            } catch (Exception ignored) {}
        }
    }

    public void saveSpawners() {
        if (spawnersConfig == null) return;
        // On repart d'une config vierge pour ne pas garder d'entrées supprimées.
        spawnersConfig = new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.Map.Entry<String, PlacedSpawner> e : placed.entrySet()) {
            String k = e.getKey(); PlacedSpawner s = e.getValue();
            spawnersConfig.set(k + ".type", s.type);
            spawnersConfig.set(k + ".x", s.x);
            spawnersConfig.set(k + ".y", s.y);
            spawnersConfig.set(k + ".z", s.z);
            spawnersConfig.set(k + ".lvlSpeed", s.lvlSpeed);
            spawnersConfig.set(k + ".lvlAmount", s.lvlAmount);
            spawnersConfig.set(k + ".lvlCap", s.lvlCap);
            spawnersConfig.set(k + ".lvlLoot", s.lvlLoot);
            spawnersConfig.set(k + ".stack", s.stack);
        }
        try { spawnersConfig.save(spawnersFile); } catch (java.io.IOException ignored) {}
    }

    public PlacedSpawner getPlacedAt(int x, int y, int z) { return placed.get(posKey(x, y, z)); }

    // ---- Pose d'un spawner ----
    // Appelé quand un joueur pose un item spawner taggé. Enregistre le spawner et fait spawn le mob.
    public void onSpawnerPlaced(org.bukkit.block.Block block, String type, ItemStack sourceItem) {
        PlacedSpawner s = new PlacedSpawner();
        s.type = type;
        s.x = block.getX(); s.y = block.getY(); s.z = block.getZ();
        // Récupère les niveaux depuis l'item posé (permet de garder les upgrades après reprise).
        if (sourceItem != null && sourceItem.hasItemMeta()) {
            var pdc = sourceItem.getItemMeta().getPersistentDataContainer();
            s.lvlSpeed  = pdc.getOrDefault(lvlSpeedKey,  PersistentDataType.INTEGER, 1);
            s.lvlAmount = pdc.getOrDefault(lvlAmountKey, PersistentDataType.INTEGER, 1);
            s.lvlCap    = pdc.getOrDefault(lvlCapKey,    PersistentDataType.INTEGER, 1);
            s.lvlLoot   = pdc.getOrDefault(lvlLootKey,   PersistentDataType.INTEGER, 1);
        }
        s.stack = 1; // démarre avec 1 mob
        placed.put(posKey(s.x, s.y, s.z), s);
        spawnMob(s);
        saveSpawners();
    }

    // ==========================================================================================
    //  LIMITE : 1 spawner de chaque type par parcelle.
    // ==========================================================================================

    // Compte les spawners d'un type donné déjà posés DANS la parcelle indiquée.
    public int countOnParcelle(Parcelle parc, String type) {
        if (parc == null) return 0;
        int n = 0;
        for (PlacedSpawner s : placed.values()) {
            if (!s.type.equals(type)) continue;
            Parcelle chez = plugin.getParcelleManager().getParcelleAt(s.x, s.z);
            if (chez != null && chez.getOwner().equals(parc.getOwner())) n++;
        }
        return n;
    }

    // ==========================================================================================
    //  OUTIL SPÉCIAL « Pic du Démonteur » — seul moyen de récupérer un spawner posé.
    //  Pioche en netherite, 5 utilisations, ne casse QUE les spawners d'île.
    // ==========================================================================================

    public static final int TOOL_USES = 5;
    public static final String TOOL_NAME = "§5§l⛏ Pic du Démonteur";

    // Fabrique un Pic du Démonteur neuf (5 utilisations).
    public ItemStack makeTool() {
        ItemStack it = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(TOOL_NAME);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Le seul outil capable de démonter");
        lore.add("§7un §espawner d'île §7sans le briser.");
        lore.add("");
        lore.add("§7Utilisations : §a" + TOOL_USES + "§7/" + TOOL_USES);
        lore.add("");
        lore.add("§8Ne casse rien d'autre qu'un spawner.");
        lore.add("§8Le spawner récupéré garde ses améliorations.");
        m.setLore(lore);
        m.getPersistentDataContainer().set(toolKey, PersistentDataType.INTEGER, 1);
        m.getPersistentDataContainer().set(toolUsesKey, PersistentDataType.INTEGER, TOOL_USES);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        try { m.setMaxStackSize(1); } catch (Throwable ignored) {}
        it.setItemMeta(m);
        return it;
    }

    // Vrai si l'item est un Pic du Démonteur.
    public boolean isTool(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.INTEGER);
    }

    // Utilisations restantes (0 si ce n'est pas l'outil).
    public int toolUsesLeft(ItemStack it) {
        if (!isTool(it)) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(toolUsesKey, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    // Consomme une utilisation sur l'outil tenu en main. L'outil DISPARAÎT à 0.
    public void consumeToolUse(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isTool(it)) return;
        int left = toolUsesLeft(it) - 1;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage("§c✖ Ton §5Pic du Démonteur §cs'est brisé.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(toolUsesKey, PersistentDataType.INTEGER, left);
        // Le lore et la barre de durabilité suivent les utilisations restantes.
        List<String> lore = m.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§7Utilisations :")) {
                    lore.set(i, "§7Utilisations : §a" + left + "§7/" + TOOL_USES);
                    break;
                }
            }
            m.setLore(lore);
        }
        if (m instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable d = (org.bukkit.inventory.meta.Damageable) m;
            int max = Material.NETHERITE_PICKAXE.getMaxDurability();
            d.setDamage((int) Math.round(max * (1.0 - (double) left / TOOL_USES)));
        }
        it.setItemMeta(m);
        p.getInventory().setItemInMainHand(it);
        p.sendMessage("§5⛏ §7Pic du Démonteur : §e" + left + " §7utilisation(s) restante(s).");
    }

    // Le Pic du Démonteur ne casse RIEN d'autre qu'un spawner d'île : toute autre casse est annulée.
    // Priorité HIGHEST pour passer avant les autres protections et couper net.
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolBreakOther(org.bukkit.event.block.BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!isTool(p.getInventory().getItemInMainHand())) return;
        org.bukkit.block.Block b = event.getBlock();
        boolean estSpawnerDIle = b.getType() == Material.SPAWNER
                && getPlacedAt(b.getX(), b.getY(), b.getZ()) != null;
        if (estSpawnerDIle) return; // seul cas autorisé (géré par ParcelleListener)
        event.setCancelled(true);
        p.sendMessage("§5⛏ §7Le Pic du Démonteur ne peut casser que des §espawners d'île§7.");
    }

    // ---- Retrait d'un spawner (cassé) : rend l'item taggé et supprime le mob ----
    public ItemStack onSpawnerBroken(int x, int y, int z) {
        PlacedSpawner s = placed.remove(posKey(x, y, z));
        if (s == null) return null;
        removeMob(s);
        saveSpawners();
        // Rend un item taggé avec les niveaux conservés.
        SpawnerDef def = defByType(s.type);
        ItemStack it = makeSpawnerItem(def, 1);
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(lvlSpeedKey, PersistentDataType.INTEGER, s.lvlSpeed);
        m.getPersistentDataContainer().set(lvlAmountKey, PersistentDataType.INTEGER, s.lvlAmount);
        m.getPersistentDataContainer().set(lvlCapKey, PersistentDataType.INTEGER, s.lvlCap);
        m.getPersistentDataContainer().set(lvlLootKey, PersistentDataType.INTEGER, s.lvlLoot);
        it.setItemMeta(m);
        return it;
    }

    /**
     * Supprime TOUS les spawners posés dans une zone X/Z (suppression d'une Île).
     * Indispensable : un spawner n'est pas un bloc vanilla mais une entrée de `placed`
     * persistée dans spawners.yml ; raser le terrain ne l'enlève pas, et la tâche de
     * génération continuerait à faire apparaître des mobs à cet endroit.
     * Renvoie le nombre de spawners retirés.
     */
    public int removeSpawnersInArea(int minX, int maxX, int minZ, int maxZ) {
        java.util.List<String> aSupprimer = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, PlacedSpawner> e : placed.entrySet()) {
            PlacedSpawner s = e.getValue();
            if (s.x >= minX && s.x <= maxX && s.z >= minZ && s.z <= maxZ) aSupprimer.add(e.getKey());
        }
        for (String k : aSupprimer) {
            PlacedSpawner s = placed.remove(k);
            if (s != null) removeMob(s); // retire aussi le mob stacké encore vivant
        }
        if (!aSupprimer.isEmpty()) saveSpawners();
        return aSupprimer.size();
    }

    /**
     * Purge les spawners ORPHELINS : ceux dont la position n'appartient plus à aucune parcelle
     * (Île supprimée avant l'ajout du nettoyage automatique). Renvoie le nombre retiré.
     */
    public int cleanupOrphanSpawners() {
        java.util.List<String> orphelins = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, PlacedSpawner> e : placed.entrySet()) {
            PlacedSpawner s = e.getValue();
            if (plugin.getParcelleManager().getParcelleAt(s.x, s.z) == null) orphelins.add(e.getKey());
        }
        for (String k : orphelins) {
            PlacedSpawner s = placed.remove(k);
            if (s != null) removeMob(s);
        }
        if (!orphelins.isEmpty()) saveSpawners();
        return orphelins.size();
    }

    // ---- Le mob stacké (1 seule entité par spawner) ----
    private org.bukkit.Location mobLocation(PlacedSpawner s) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld("world");
        return new org.bukkit.Location(w, s.x + 0.5, s.y + 1, s.z + 0.5);
    }

    private void spawnMob(PlacedSpawner s) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld("world");
        if (w == null) return;
        removeMob(s); // évite les doublons
        SpawnerDef def = defByType(s.type);
        org.bukkit.entity.Entity ent = w.spawnEntity(mobLocation(s), def.mob);
        // Métadonnée : marque nos mobs pour que les autres handlers (spawn/despawn) les ignorent.
        ent.setMetadata("island_spawner", new org.bukkit.metadata.FixedMetadataValue(plugin, s.type));
        if (ent instanceof org.bukkit.entity.LivingEntity le) {
            le.setRemoveWhenFarAway(false);   // ne despawn jamais
            le.setPersistent(true);
            le.setAI(false);                  // reste sur place (pas de balade = moins de lag)
            le.setSilent(true);
            le.setCollidable(false);
            if (le instanceof org.bukkit.entity.Mob mob) mob.setTarget(null);
            le.setCustomNameVisible(true);
        }
        // Toujours ADULTE : les piglins zombifiés peuvent spawner en bébé (indésirable ici).
        if (ent instanceof org.bukkit.entity.Ageable age) age.setAdult();
        if (ent instanceof org.bukkit.entity.Zombie z) z.setBaby(false);
        s.mobUuid = ent.getUniqueId();
        updateMobName(s);
    }

    private void removeMob(PlacedSpawner s) {
        // 1) L'entité qu'on connaît par son UUID.
        if (s.mobUuid != null) {
            org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(s.mobUuid);
            if (ent != null) ent.remove();
            s.mobUuid = null;
        }
        // 2) Filet de sécurité : mobUuid est transient (perdu au redémarrage du serveur),
        //    donc après un reload on ne saurait plus quelle entité retirer et le mob
        //    resterait orphelin sur la parcelle. On balaie donc aussi la zone du spawner
        //    et on retire tout mob marqué « island_spawner » qui traîne autour.
        org.bukkit.Location loc = mobLocation(s);
        if (loc.getWorld() == null) return;
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2.0, 3.0, 2.0)) {
            if (e.hasMetadata("island_spawner")) e.remove();
        }
    }

    // Met à jour le nom flottant = compteur du stack.
    private void updateMobName(PlacedSpawner s) {
        if (s.mobUuid == null) return;
        org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(s.mobUuid);
        if (ent == null) return;
        SpawnerDef def = defByType(s.type);
        String base = s.type.equals("iron") ? "§fGolem de Fer" : "§ePiglin";
        ent.setCustomName(base + " §7×§a" + s.stack + " §8/ " + stackCap(s));
    }

    // Retrouve le PlacedSpawner correspondant à une entité (par UUID).
    public PlacedSpawner spawnerOfEntity(java.util.UUID entityId) {
        for (PlacedSpawner s : placed.values()) if (entityId.equals(s.mobUuid)) return s;
        return null;
    }

    // ---- Task périodique : génération de mobs (toutes les secondes) ----
    public void startSpawnerTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean dirty = false;
            for (PlacedSpawner s : placed.values()) {
                // (Ré)assure la présence du mob si le chunk est chargé et qu'il a disparu.
                ensureMob(s);
                s.tickCounter++;
                if (s.tickCounter < cycleSeconds(s)) continue;
                s.tickCounter = 0;
                int cap = stackCap(s);
                if (s.stack >= cap) continue;
                s.stack = Math.min(cap, s.stack + mobsPerCycle(s));
                updateMobName(s);
                dirty = true;
            }
            if (dirty) saveSpawners();
        }, 20L, 20L); // chaque seconde
    }

    // S'assure qu'un mob vivant existe SI le stack > 0 (respawn s'il a disparu au rechargement de chunk).
    private void ensureMob(PlacedSpawner s) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld("world");
        if (w == null) return;
        if (!w.isChunkLoaded(s.x >> 4, s.z >> 4)) return; // parcelle non chargée : rien à faire
        if (s.stack <= 0) return; // stack vide : pas de mob tant qu'il ne se régénère pas
        org.bukkit.entity.Entity ent = (s.mobUuid != null) ? org.bukkit.Bukkit.getEntity(s.mobUuid) : null;
        if (ent == null || ent.isDead()) spawnMob(s);
        else updateMobName(s);
    }

    // Appelé quand le mob d'un spawner MEURT (vrai combat). Décrémente le stack ; si > 0, un
    // nouveau mob respawn immédiatement au même endroit. Renvoie le nb de lingots à dropper.
    public int onMobDeath(PlacedSpawner s) {
        if (s.stack > 0) s.stack--;
        s.mobUuid = null;
        if (s.stack > 0) spawnMob(s); // remplaçant immédiat, même place
        saveSpawners();
        return lootPerMob(s);
    }

    // ---- Améliorations (coûts + caps) ----
    public static final int MAX_SPEED = 9, MAX_AMOUNT = 10, MAX_CAP = 20, MAX_LOOT = 10;
    // Coût de la 1re amélioration, PAR TYPE de spawner (le Piglin est plus cher).
    private static final double UP_BASE_IRON = 1_000_000.0;
    private static final double UP_BASE_GOLD = 1_500_000.0;
    // Multiplicateur progressif : ×1,4 au 2e palier puis +0,1 à chaque palier suivant.
    private static final double UP_MULT_START = 1.4, UP_MULT_STEP = 0.1;

    // Niveau actuel d'une amélioration.
    public int levelOf(PlacedSpawner s, String what) {
        switch (what) {
            case "speed":  return s.lvlSpeed;
            case "amount": return s.lvlAmount;
            case "cap":    return s.lvlCap;
            default:       return s.lvlLoot;
        }
    }
    // Niveau max d'une amélioration.
    public int maxOf(String what) {
        switch (what) {
            case "speed":  return MAX_SPEED;
            case "amount": return MAX_AMOUNT;
            case "cap":    return MAX_CAP;
            default:       return MAX_LOOT;
        }
    }
    // Coût pour passer du niveau actuel au suivant.
    // Le multiplicateur GRANDIT à chaque palier : niv1→2 = base, puis ×1,4 pour le suivant,
    // ×1,5 pour celui d'après, ×1,6, etc. (+0,1 par palier). Ainsi le début reste accessible
    // et les derniers niveaux deviennent un vrai objectif de fin de jeu.
    public double upgradeCost(PlacedSpawner s, String what) {
        int lvl = levelOf(s, what);
        double cout = "gold".equals(s.type) ? UP_BASE_GOLD : UP_BASE_IRON;
        for (int n = 2; n <= lvl; n++) {
            cout *= UP_MULT_START + (n - 2) * UP_MULT_STEP; // n=2 → ×1,4 ; n=3 → ×1,5 ; …
        }
        return Math.floor(cout);
    }

    // Applique une amélioration (retourne le nouveau niveau, ou -1 si déjà au max).
    public int upgrade(PlacedSpawner s, String what) {
        if (levelOf(s, what) >= maxOf(what)) return -1;
        switch (what) {
            case "speed":  s.lvlSpeed++;  break;
            case "amount": s.lvlAmount++; break;
            case "cap":    s.lvlCap++;    break;
            case "loot":   s.lvlLoot++;   break;
            default: return -1;
        }
        updateMobName(s);
        saveSpawners();
        return levelOf(s, what);
    }

    // Valeur "concrète" d'une amélioration à un niveau donné (pour l'affichage du menu).
    public String effectText(PlacedSpawner s, String what) {
        switch (what) {
            case "speed":  return "1 cycle / " + cycleSeconds(s) + "s";
            case "amount": return "+" + mobsPerCycle(s) + " mob(s) / cycle";
            case "cap":    return "max " + stackCap(s) + " mobs";
            default:       return lootPerMob(s) + " lingot(s) / mob";
        }
    }

    // Nettoie tous les mobs à l'arrêt du serveur (évite les doublons au reload).
    public void removeAllMobs() {
        for (PlacedSpawner s : placed.values()) removeMob(s);
    }

    // ==========================================================================================
    //  BRIQUE E : menu d'amélioration (clic droit sur le spawner posé).
    // ==========================================================================================
    static final String UPGRADE_TITLE = "§5§lAmélioration du Spawner";
    // Quel spawner (posKey) chaque joueur est en train d'éditer.
    private final java.util.Map<java.util.UUID, String> editing = new java.util.HashMap<>();

    // Ordre des 4 améliorations dans le menu + leurs icônes/libellés.
    private static final String[] UP_KEYS  = {"speed", "amount", "cap", "loot"};
    private static final org.bukkit.Material[] UP_ICONS = {
        org.bukkit.Material.CLOCK, org.bukkit.Material.WHEAT_SEEDS, org.bukkit.Material.CHEST, org.bukkit.Material.GOLD_INGOT };
    private static final String[] UP_NAMES = {
        "§b⏱ Vitesse de génération", "§a✚ Mobs par cycle", "§6▣ Capacité du stack", "§e✦ Loot par mob" };
    private static final int[] UP_SLOTS = {10, 12, 14, 16};

    public void openUpgradeMenu(org.bukkit.entity.Player player, PlacedSpawner s) {
        editing.put(player.getUniqueId(), posKey(s.x, s.y, s.z));
        org.bukkit.inventory.Inventory menu = org.bukkit.Bukkit.createInventory(null, 27, UPGRADE_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        // En-tête (slot 4) : type + stack actuel.
        SpawnerDef def = defByType(s.type);
        ItemStack head = new ItemStack(org.bukkit.Material.SPAWNER);
        ItemMeta hm = head.getItemMeta();
        hm.setDisplayName(def.getName());
        hm.setLore(java.util.Arrays.asList(
                "§7Stock actuel : §a" + s.stack + " §8/ " + stackCap(s) + " mobs",
                "§7Génère du " + (s.type.equals("iron") ? "§ffer" : "§eor") + "§7."));
        head.setItemMeta(hm);
        menu.setItem(4, head);

        for (int i = 0; i < UP_KEYS.length; i++) menu.setItem(UP_SLOTS[i], upgradeTile(s, UP_KEYS[i], UP_ICONS[i], UP_NAMES[i]));

        menu.setItem(22, plugin.namedItem(org.bukkit.Material.BARRIER, "§cFermer", null));
        player.openInventory(menu);
    }

    private ItemStack upgradeTile(PlacedSpawner s, String what, org.bukkit.Material icon, String name) {
        int lvl = levelOf(s, what), max = maxOf(what);
        ItemStack it = new ItemStack(icon);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name + " §7[§a" + lvl + "§7/§a" + max + "§7]");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Actuel : §f" + effectText(s, what));
        if (lvl >= max) {
            lore.add("");
            lore.add("§a✔ Niveau maximum atteint");
        } else {
            lore.add("");
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(upgradeCost(s, what)) + "$");
            lore.add("§e➜ Clic pour améliorer !");
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    @org.bukkit.event.EventHandler
    public void onUpgradeClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (!UPGRADE_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        String key = editing.get(player.getUniqueId());
        if (key == null) return;
        PlacedSpawner s = placed.get(key);
        if (s == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot == 22) { player.closeInventory(); return; }

        for (int i = 0; i < UP_SLOTS.length; i++) {
            if (slot != UP_SLOTS[i]) continue;
            String what = UP_KEYS[i];
            if (levelOf(s, what) >= maxOf(what)) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            double cost = upgradeCost(s, what);
            net.milkbowl.vault.economy.Economy eco = plugin.getEconomy();
            if (eco == null) return;
            if (eco.getBalance(player) < cost) {
                player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            eco.withdrawPlayer(player, cost);
            int newLvl = upgrade(s, what);
            // Si on vient d'augmenter le cap ou la vitesse, on s'assure qu'un mob existe si le stack > 0.
            ensureMob(s);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            player.sendMessage("§aAmélioration §f" + UP_NAMES[i] + " §a→ niveau §e" + newLvl + " §7(-" + PrivateMines.formatNumber(cost) + "$)");
            openUpgradeMenu(player, s); // rafraîchit
            return;
        }
    }
}
