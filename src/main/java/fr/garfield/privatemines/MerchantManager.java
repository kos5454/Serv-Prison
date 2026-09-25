package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.UUID;

/**
 * Brique 10 — le Marchand ambulant du OneBlock (« la Caravane »).
 *
 * <p>Périodiquement (~30 min), pour chaque joueur présent sur SA propre Île, une caravane
 * (un Marchand ambulant / Wandering Trader vanilla : il marche, il peut mourir) apparaît près de son bloc,
 * annonce son arrivée, et repart ~5 min plus tard. <b>Clic droit</b> dessus ouvre l'interface
 * marchande <b>vanilla</b> — mais garnie de NOS articles à NOS prix (en émeraudes).</p>
 *
 * <p>Monnaie = <b>émeraude</b> (rare en phase 10). On utilise les vrais trades du villageois
 * ({@link MerchantRecipe}) : « X émeraudes → notre objet ». Le vanilla gère le paiement.
 * Stock actuel = décos non-solides + blocs d'autres biomes. À terme : tous les blocs obtenables.</p>
 */
public class MerchantManager implements Listener {

    // Rythme de la caravane (en ticks). 20 ticks = 1 seconde.
    private static final long INTERVALLE_TICKS = 20L * 60L * 30L; // ~30 min entre deux passages
    private static final long DUREE_TICKS      = 20L * 60L * 5L;  // ~5 min de présence

    private final PrivateMines plugin;

    // Marchand actuellement présent par propriétaire d'Île (UUID -> le Marchand ambulant).
    private final java.util.Map<UUID, WanderingTrader> presents = new java.util.HashMap<>();

    public MerchantManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ===== Cycle de vie de la caravane =====

    /** Lance la tâche répétée qui fait passer la caravane (~30 min). Appelé au onEnable. */
    public void startCaravaneTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (estSurSonIle(p)) faireVenir(p);
            }
        }, 20L * 60L, INTERVALLE_TICKS); // 1er passage après 1 min, puis toutes les 30 min
    }

    // Fait apparaître la caravane sur l'Île du joueur (si pas déjà présente), et programme son départ.
    // Public : permet aussi de la forcer via une commande de test (/ob forcemerchant).
    public boolean faireVenir(Player p) {
        UUID owner = p.getUniqueId();
        if (presents.containsKey(owner)) return false; // déjà là

        Parcelle parc = plugin.getParcelleManager().getParcelle(owner);
        if (parc == null) return false;
        Location centre = plugin.getOneBlock().blocLocation(parc);
        if (centre == null || centre.getWorld() == null) return false;
        World w = centre.getWorld();

        // Apparition à côté du bloc, sur le sol. On utilise un VRAI Marchand ambulant
        // (Wandering Trader) : c'est le mob « caravane » de Minecraft, ses trades marchent
        // parfaitement avec l'IA active (il marche) — contrairement au villageois.
        Location spot = centre.clone().add(2.5, 1.0, 2.5);
        WanderingTrader v = (WanderingTrader) w.spawnEntity(spot, org.bukkit.entity.EntityType.WANDERING_TRADER);
        // 100 % vanilla : IA active (il marche) et il PEUT mourir (choix user).
        // S'il meurt avant la fin, pas grave : il repasse au prochain cycle (~30 min).
        v.setCustomName("§2§lLa Caravane §7(clic droit)");
        v.setCustomNameVisible(true);
        v.setMetadata("ob_merchant", new org.bukkit.metadata.FixedMetadataValue(plugin, owner.toString()));
        v.setRemoveWhenFarAway(false);  // on gère nous-mêmes son retrait (pas de despawn distance)
        v.setDespawnDelay(Integer.MAX_VALUE); // pas de despawn auto du marchand ambulant (on gère les 5 min)
        v.setCanPickupItems(false);

        // On REMPLACE ses trades vanilla (émeraudes contre trucs random) par NOS articles à NOS prix.
        v.setRecipes(construitTrades());

        presents.put(owner, v);

        p.sendMessage("§2§l🛒 La Caravane §aarrive sur ton Île ! §7(clic droit sur le marchand · repart dans 5 min)");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WANDERING_TRADER_YES, 1f, 1f);

        // Départ programmé.
        Bukkit.getScheduler().runTaskLater(plugin, () -> faireRepartir(owner), DUREE_TICKS);
        return true;
    }

    // Retire la caravane de l'Île d'un joueur (départ programmé ou nettoyage).
    private void faireRepartir(UUID owner) {
        WanderingTrader v = presents.remove(owner);
        if (v != null && !v.isDead()) {
            v.remove();
            Player p = Bukkit.getPlayer(owner);
            if (p != null && p.isOnline()) {
                p.sendMessage("§7La Caravane repart… §8(elle repassera plus tard)");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WANDERING_TRADER_NO, 1f, 1f);
            }
        }
    }

    /** Nettoyage à l'arrêt du plugin : retire tous les marchands encore présents. */
    public void removeAll() {
        for (WanderingTrader v : presents.values()) if (v != null && !v.isDead()) v.remove();
        presents.clear();
    }

    // Le joueur est-il physiquement sur SA propre Île ?
    private boolean estSurSonIle(Player p) {
        Parcelle sienne = plugin.getParcelleManager().getParcelle(p.getUniqueId());
        if (sienne == null) return false;
        return sienne.contains(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
    }

    // ===== Mort du marchand (100 % vanilla : il peut mourir) =====

    // À sa mort, on le retire de la liste des présents (il repassera au prochain cycle ~30 min).
    // On efface son drop/xp (pas de loot d'émeraudes gratuit).
    @EventHandler
    public void onMerchantDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!event.getEntity().hasMetadata("ob_merchant")) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        UUID owner = ownerDe(event.getEntity());
        if (owner != null) {
            presents.remove(owner);
            Player p = Bukkit.getPlayer(owner);
            if (p != null && p.isOnline()) {
                p.sendMessage("§7La Caravane a été détruite… §8(elle repassera au prochain tour)");
            }
        }
    }

    // Lit l'UUID du propriétaire stocké dans la metadata « ob_merchant » d'une entité.
    private UUID ownerDe(org.bukkit.entity.Entity e) {
        if (!e.hasMetadata("ob_merchant")) return null;
        try { return UUID.fromString(e.getMetadata("ob_merchant").get(0).asString()); }
        catch (Exception ex) { return null; }
    }

    // ===== Catalogue : 5 familles de blocs BRUTS (non-craftables) =====
    // On classe automatiquement tous les Material « bruts naturels » du serveur en 5 familles.
    // On EXCLUT tout ce qui est craftable/transformé (dalles, escaliers, murs, portes, clôtures,
    // briques, planches, béton, verre teinté, laine, blocs polis/taillés/gravés…) : ça ne se vend
    // pas, le joueur le fabrique. À chaque passage, on tire 2 items au hasard par famille = 10 trades.

    private static final int FAMILLES = 5;
    private static final int ITEMS_PAR_FAMILLE = 2;

    // Blocs BANNIS du stock (trop cheat / normalement inaccessibles en survie), choisis par le user.
    // - BUDDING_AMETHYST : source infinie d'améthyste, impossible à obtenir en survie.
    // - RAW_*_BLOCK : 1 bloc = 9 minerais bruts (source de minerai massive à bas prix).
    private static final java.util.Set<Material> BANNIS = java.util.EnumSet.of(
            Material.BUDDING_AMETHYST,
            Material.RAW_IRON_BLOCK,
            Material.RAW_GOLD_BLOCK,
            Material.RAW_COPPER_BLOCK
    );

    // Familles remplies une fois au chargement (index 0..4). Chacune = liste de Material bruts.
    private final java.util.List<java.util.List<Material>> catalogue = new java.util.ArrayList<>();

    // Construit le catalogue en scannant tous les Material. Appelé une fois au onEnable (startCaravaneTask).
    private void construitCatalogue() {
        for (int i = 0; i < FAMILLES; i++) catalogue.add(new java.util.ArrayList<>());
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock() || m.isAir()) continue;
            if (BANNIS.contains(m)) continue;             // blocs cheat exclus (choix user)
            if (estCraftableOuTransforme(m)) continue;    // on ne vend que le brut naturel
            int fam = familleDe(m);
            if (fam >= 0) catalogue.get(fam).add(m);
        }
    }

    // Familles : 0 Sols/roches · (1 = plus utilisée) · 2 Bois & feuilles · 3 Plantes & décos · 4 Coraux & exotiques.
    // La famille Minerais est VOLONTAIREMENT retirée du stock (choix user) : les minerais (charbon,
    // fer, or, diamant, émeraude, lapis, redstone…) se MINENT, ils ne s'achètent pas. Vendre de
    // l'émeraude/du minerai d'émeraude contre des émeraudes n'avait aucun sens.
    // Renvoie -1 si le bloc ne rentre dans aucune famille vendable (il est alors ignoré).
    private int familleDe(Material m) {
        String n = m.name();
        // Minerais (et blocs de minerai brut) : IGNORÉS, jamais vendus.
        // (Ancient Debris n'est PAS un minerai classique : c'est un bloc exotique du Nether,
        //  gardé en vente à prix fort dans la famille 4 — voir plus bas.)
        if (n.endsWith("_ORE")
                || n.equals("RAW_IRON_BLOCK") || n.equals("RAW_COPPER_BLOCK") || n.equals("RAW_GOLD_BLOCK")) return -1;
        // 2 — Bois & feuilles bruts (bûches, bois, feuilles, racines, champignons géants, tiges).
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_LEAVES")
                || n.endsWith("_ROOTS") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")
                || n.endsWith("MUSHROOM_BLOCK") || n.equals("MUSHROOM_STEM")
                || n.equals("MANGROVE_ROOTS") || n.equals("MUDDY_MANGROVE_ROOTS")
                || n.equals("NETHER_WART_BLOCK") || n.equals("WARPED_WART_BLOCK")) return 2;
        // 3 — Plantes & décos (fleurs, pétales, herbes, pousses, champis, cactus, bambou, kelp non-bloc…).
        if (n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE") || n.endsWith("_TULIP")
                || n.endsWith("_MUSHROOM") || n.endsWith("_ROSE") || n.endsWith("_ORCHID")
                || n.equals("DANDELION") || n.equals("POPPY") || n.equals("CORNFLOWER")
                || n.equals("ALLIUM") || n.equals("OXEYE_DAISY") || n.equals("LILY_OF_THE_VALLEY")
                || n.equals("SUNFLOWER") || n.equals("LILAC") || n.equals("PEONY") || n.equals("ROSE_BUSH")
                || n.equals("PINK_PETALS") || n.equals("WITHER_ROSE") || n.equals("TORCHFLOWER")
                || n.equals("CACTUS") || n.equals("BAMBOO") || n.equals("SUGAR_CANE")
                || n.equals("LILY_PAD") || n.equals("VINE") || n.equals("GLOW_LICHEN")
                || n.equals("GRASS") || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS")
                || n.equals("FERN") || n.equals("LARGE_FERN") || n.equals("DEAD_BUSH")
                || n.equals("SWEET_BERRY_BUSH") || n.equals("SEA_PICKLE") || n.equals("SPORE_BLOSSOM")
                || n.contains("AZALEA") || n.contains("MOSS") || n.equals("HANGING_ROOTS")
                || n.equals("BIG_DRIPLEAF") || n.equals("SMALL_DRIPLEAF") || n.equals("PITCHER_PLANT")) return 3;
        // 4 — Coraux & exotiques (coraux, prismarine, blocs Nether/End bruts, éléments rares).
        if (n.contains("CORAL") || n.contains("PRISMARINE") || n.equals("SPONGE") || n.equals("WET_SPONGE")
                || n.equals("SEA_LANTERN") || n.equals("KELP") || n.equals("SEAGRASS") || n.equals("TALL_SEAGRASS")
                || n.equals("ANCIENT_DEBRIS") || n.equals("NETHERRACK") || n.contains("BASALT") || n.equals("BLACKSTONE")
                || n.equals("SOUL_SAND") || n.equals("SOUL_SOIL") || n.equals("MAGMA_BLOCK")
                || n.equals("GLOWSTONE") || n.equals("SHROOMLIGHT") || n.contains("FROGLIGHT")
                || n.equals("END_STONE") || n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN")
                || n.equals("SCULK") || n.equals("SCULK_CATALYST") || n.equals("SCULK_SENSOR")
                || n.equals("GLOW_BERRIES") || n.contains("AMETHYST") || n.equals("BUDDING_AMETHYST")) return 4;
        // 0 — Sols & roches (le reste des blocs « terrain » naturels).
        if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT") || n.equals("PODZOL")
                || n.equals("GRASS_BLOCK") || n.equals("DIRT_PATH") || n.equals("FARMLAND") || n.equals("MYCELIUM")
                || n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL") || n.equals("CLAY")
                || n.equals("MUD") || n.equals("SNOW_BLOCK") || n.equals("POWDER_SNOW") || n.equals("ICE")
                || n.equals("PACKED_ICE") || n.equals("BLUE_ICE") || n.equals("STONE") || n.equals("GRANITE")
                || n.equals("DIORITE") || n.equals("ANDESITE") || n.equals("DEEPSLATE") || n.equals("TUFF")
                || n.equals("CALCITE") || n.equals("DRIPSTONE_BLOCK") || n.equals("POINTED_DRIPSTONE")
                || n.equals("COBBLESTONE") || n.equals("MOSSY_COBBLESTONE") || n.equals("COBBLED_DEEPSLATE")
                || n.equals("TERRACOTTA") || n.endsWith("_TERRACOTTA") && !n.contains("GLAZED")
                || n.equals("SANDSTONE") || n.equals("RED_SANDSTONE") || n.equals("SNOW")) return 0;
        return -1; // ignoré
    }

    // Filtre anti-craftable : tout ce qui est transformé/fabriqué par le joueur → on ne le vend pas.
    // On en profite pour retirer le « bruit » inutile (choix user) : plantes en pot, coraux morts,
    // tiges techniques de melon/citrouille.
    private boolean estCraftableOuTransforme(Material m) {
        String n = m.name();
        // Bruit exclu explicitement :
        if (n.startsWith("POTTED_")) return true;                 // plantes en pot
        if (n.startsWith("DEAD_") && n.contains("CORAL")) return true; // coraux morts (blocs, éventails, muraux)
        if (n.endsWith("_STEM") && (n.contains("MELON") || n.contains("PUMPKIN"))) return true; // tiges melon/citrouille
        return n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.endsWith("_WALL") || n.endsWith("_FENCE")
            || n.endsWith("_FENCE_GATE") || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")
            || n.endsWith("_PLANKS") || n.endsWith("_SIGN") || n.endsWith("_PRESSURE_PLATE")
            || n.endsWith("_BUTTON") || n.endsWith("_BRICKS") || n.endsWith("_BRICK")
            || n.endsWith("_CARPET") || n.endsWith("_BANNER") || n.endsWith("_BED")
            || n.endsWith("_CANDLE") || n.endsWith("_SHULKER_BOX") || n.endsWith("_GLAZED_TERRACOTTA")
            || n.endsWith("_CONCRETE") || n.endsWith("_CONCRETE_POWDER") || n.endsWith("_WOOL")
            || n.endsWith("_STAINED_GLASS") || n.endsWith("_STAINED_GLASS_PANE")
            || n.contains("POLISHED") || n.contains("CHISELED") || n.contains("CUT_")
            || n.contains("SMOOTH_") || n.contains("CRACKED_") || n.contains("BRICK")
            || n.contains("TILE") || n.contains("PILLAR") || n.equals("GLASS") || n.equals("GLASS_PANE")
            || (n.contains("_COPPER") && !n.equals("RAW_COPPER_BLOCK") && !n.endsWith("_ORE")) // cuivre oxydé/ciré = transformé
            || (n.contains("QUARTZ") && !n.equals("NETHER_QUARTZ_ORE")) // quartz = crafté depuis le minerai
            || (n.contains("LANTERN") && !n.equals("SEA_LANTERN"))
            || n.contains("MOSAIC") || n.contains("STRIPPED_") // bois écorcé = transformé (hache)
            // Blocs fonctionnels / craftés courants (liste explicite, plus fiable que isInteractable()).
            || n.equals("BOOKSHELF") || n.equals("CHISELED_BOOKSHELF") || n.equals("CRAFTING_TABLE")
            || n.equals("FURNACE") || n.equals("BLAST_FURNACE") || n.equals("SMOKER")
            || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL") || n.equals("ENDER_CHEST")
            || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("HOPPER") || n.equals("OBSERVER")
            || n.equals("PISTON") || n.equals("STICKY_PISTON") || n.equals("NOTE_BLOCK") || n.equals("JUKEBOX")
            || n.equals("LOOM") || n.equals("CARTOGRAPHY_TABLE") || n.equals("FLETCHING_TABLE")
            || n.equals("SMITHING_TABLE") || n.equals("GRINDSTONE") || n.equals("STONECUTTER")
            || n.equals("LECTERN") || n.equals("COMPOSTER") || n.equals("CAULDRON") || n.equals("BEACON")
            || n.equals("BREWING_STAND") || n.equals("ENCHANTING_TABLE") || n.equals("ANVIL")
            || n.equals("CHIPPED_ANVIL") || n.equals("DAMAGED_ANVIL") || n.equals("BELL")
            || n.equals("DAYLIGHT_DETECTOR") || n.equals("REDSTONE_LAMP") || n.equals("TARGET")
            || n.equals("LODESTONE") || n.equals("RESPAWN_ANCHOR") || n.equals("CONDUIT")
            || n.equals("DECORATED_POT") || n.equals("FLOWER_POT") || n.equals("TNT")
            || n.equals("SCAFFOLDING") || n.equals("LADDER") || n.equals("TORCH")
            || n.equals("REDSTONE_BLOCK") || n.equals("SLIME_BLOCK") || n.equals("HONEY_BLOCK")
            || n.equals("HAY_BLOCK") || n.equals("BONE_BLOCK") || n.equals("DRIED_KELP_BLOCK")
            || n.equals("COAL_BLOCK") || n.equals("IRON_BLOCK") || n.equals("GOLD_BLOCK")
            || n.equals("DIAMOND_BLOCK") || n.equals("EMERALD_BLOCK") || n.equals("LAPIS_BLOCK")
            || n.equals("REDSTONE_BLOCK") || n.equals("NETHERITE_BLOCK") || n.equals("COPPER_BLOCK");
    }

    // Construit 10 recettes marchandes : 2 items au hasard par famille, prix selon rareté.
    private java.util.List<MerchantRecipe> construitTrades() {
        if (catalogue.isEmpty()) construitCatalogue();
        java.util.List<MerchantRecipe> recettes = new java.util.ArrayList<>();
        java.util.Random rng = new java.util.Random();

        for (int fam = 0; fam < FAMILLES; fam++) {
            java.util.List<Material> pool = catalogue.get(fam);
            if (pool.isEmpty()) continue;
            java.util.Set<Material> deja = new java.util.HashSet<>();
            int voulus = Math.min(ITEMS_PAR_FAMILLE, pool.size());
            int garde = 0;
            while (deja.size() < voulus && garde++ < 40) {
                Material m = pool.get(rng.nextInt(pool.size()));
                if (!deja.add(m)) continue;
                int prix = prixDe(fam, m);
                int qte  = qteDe(prix);
                ItemStack resultat = new ItemStack(m, qte);
                MerchantRecipe r = new MerchantRecipe(resultat, Integer.MAX_VALUE);
                r.addIngredient(new ItemStack(Material.EMERALD, prix));
                r.setExperienceReward(false);
                r.setVillagerExperience(0);
                recettes.add(r);
            }
        }
        return recettes;
    }

    // Prix en émeraudes selon la rareté (prix SPÉCIAUX d'abord, puis auto par famille).
    private int prixDe(int famille, Material m) {
        // ── Prix spéciaux (blocs sensibles gardés en vente, choix user) ──
        switch (m) {
            case ANCIENT_DEBRIS:                       return 32; // mène au netherite : cher
            case SPONGE: case WET_SPONGE:              return 8;  // rare (Elder Guardian)
            case OBSIDIAN: case CRYING_OBSIDIAN:       return 5;  // portails / résistant
            case GLOWSTONE: case SEA_LANTERN: case SHROOMLIGHT: return 3; // lumières déco
            default: break;
        }
        // (Aucun minerai n'est vendu : pas de prix "diamant/émeraude" ici.)
        switch (famille) {
            case 4: return m.name().contains("CORAL") ? 2 : 3; // Coraux 2, exotiques (Nether/End) 3
            case 2: return 1;                       // Bois & feuilles : courant
            case 3: return 1;                       // Plantes & décos : courant
            default: return 1;                      // Sols & roches : courant
        }
    }

    // Quantité vendue selon le prix : plus c'est cher/rare, moins on en donne.
    private int qteDe(int prix) {
        if (prix >= 5) return 1;
        if (prix >= 3) return 2;
        return 8; // courant : un petit lot
    }
}
