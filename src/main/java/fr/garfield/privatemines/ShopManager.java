package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop : menus (Bois/pousses, Décoration, Utilitaires) et clavier d'achat par quantité.
 * Extrait de PrivateMines pour alléger le fichier principal.
 */
public class ShopManager implements Listener {

    private final PrivateMines plugin;

    public ShopManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    private static final String SHOP_MAIN_TITLE  = "§6§lShop";
    private static final String SHOP_BOIS_TITLE  = "§6§lShop §7» §aBois";
    private static final String SHOP_UTIL_TITLE  = "§6§lShop §7» §bUtilitaires";
    private static final String SHOP_DECO_TITLE  = "§6§lShop §7» §dDécoration";
    private static final String SHOP_SPAWNER_TITLE = "§6§lShop §7» §5Spawners";
    private static final String SHOP_KB_PREFIX   = "§6§lAcheter : ";
    private static final double WOOD_PRICE       = 500.0;
    private static final int    WOOD_MAX_QTY     = 500;
    private static final int    UTIL_MAX_QTY     = 500;

    // Utilitaires vendus au shop : material, nom, prix unitaire.
    private static final Material[] UTIL_BLOCKS = {
        Material.ANVIL, Material.ENCHANTING_TABLE, Material.BOOKSHELF,
        Material.WATER_BUCKET, Material.LAVA_BUCKET,
    };
    private static final String[] UTIL_NAMES = {
        "§b⚒ Enclume", "§b📚 Table d'enchantement", "§b📖 Bibliothèque",
        "§b🪣 Seau d'eau", "§b🪣 Seau de lave",
    };
    private static final double[] UTIL_PRICES = {
        5000.0, 25000.0, 500.0, 250.0, 1000.0,
    };

    // Renvoie le prix unitaire d'un material utilitaire, ou -1 si non trouvé.
    private double utilPrice(Material mat) {
        for (int i = 0; i < UTIL_BLOCKS.length; i++) if (UTIL_BLOCKS[i] == mat) return UTIL_PRICES[i];
        return -1;
    }

    // Blocs de décoration vendus au shop : de vrais blocs vanilla posables sur la parcelle.
    private static final int DECO_MAX_QTY = 500;
    private static final Material[] DECO_BLOCKS = {
        Material.LANTERN,      Material.SOUL_LANTERN, Material.SEA_LANTERN,
        Material.END_ROD,      Material.CANDLE,       Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,Material.CHAIN,        Material.BARREL,
        Material.BELL,         Material.SCAFFOLDING,  Material.COBWEB,
        Material.ITEM_FRAME,   Material.GLOW_ITEM_FRAME, Material.PAINTING,
        Material.FLOWER_POT,   Material.AMETHYST_CLUSTER, Material.GLOW_LICHEN,
        Material.LILY_PAD,     Material.BIG_DRIPLEAF,
    };
    private static final String[] DECO_NAMES = {
        "§d🏮 Lanterne",          "§d🏮 Lanterne des Âmes",  "§d🏮 Lanterne Aquatique",
        "§d✨ Tige du Bout du Monde","§d🕯 Bougie",           "§d🔥 Feu de Camp",
        "§d🔥 Feu de Camp des Âmes","§d⛓ Chaîne",            "§d🛢 Tonneau",
        "§d🔔 Cloche",            "§d🪜 Échafaudage",        "§d🕸 Toile d'Araignée",
        "§d🖼 Cadre",             "§d🖼 Cadre Lumineux",     "§d🎨 Tableau",
        "§d🪴 Pot de Fleur",      "§d💎 Amas d'Améthyste",   "§d🟢 Lichen Luisant",
        "§d🪷 Nénuphar",          "§d🌿 Grande Feuille",
    };
    private static final double[] DECO_PRICES = {
        100.0, 150.0, 250.0,
        200.0, 50.0,  150.0,
        200.0, 80.0,  200.0,
        500.0, 60.0,  100.0,
        100.0, 150.0, 100.0,
        50.0,  200.0, 120.0,
        50.0,  80.0,
    };

    // Renvoie le prix unitaire d'un bloc déco, ou -1 si non trouvé.
    private double decoPrice(Material mat) {
        for (int i = 0; i < DECO_BLOCKS.length; i++) if (DECO_BLOCKS[i] == mat) return DECO_PRICES[i];
        return -1;
    }

    private static final Material[] WOOD_BLOCKS = {
        Material.OAK_SAPLING,        Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
        Material.JUNGLE_SAPLING,     Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
        Material.MANGROVE_PROPAGULE, Material.CHERRY_SAPLING, Material.PALE_OAK_SAPLING,
    };

    private static final String[] WOOD_NAMES = {
        "§aPousse de Chêne",      "§aPousse d'Épicéa",    "§aPousse de Bouleau",
        "§aPousse de Jungle",     "§aPousse d'Acacia",    "§aPousse de Chêne Noir",
        "§aPropagule de Mangrove","§aPousse de Cerisier", "§aPousse de Chêne Pâle",
    };

    // ===== Catégories paginées de blocs (Construction, Couleurs, Nature, Précieux) ==========
    // Une entrée vendable : un bloc, son nom affiché, son prix unitaire.
    static final class ShopEntry {
        final Material mat; final String name; final double price;
        // Prix de RACHAT à l'unité (0 = non rachetable par le shop).
        final double sellPrice;
        ShopEntry(Material mat, String name, double price) { this(mat, name, price, 0); }
        ShopEntry(Material mat, String name, double price, double sellPrice) {
            this.mat = mat; this.name = name; this.price = price; this.sellPrice = sellPrice;
        }
    }
    // Une catégorie : titre du menu, icône dans le menu principal, description, et ses entrées.
    static final class ShopCategory {
        final String key; final String title; final Material icon; final String iconName; final String iconDesc;
        final java.util.List<ShopEntry> entries;
        ShopCategory(String key, String title, Material icon, String iconName, String iconDesc, java.util.List<ShopEntry> entries) {
            this.key = key; this.title = title; this.icon = icon; this.iconName = iconName; this.iconDesc = iconDesc; this.entries = entries;
        }
    }
    private static final int CAT_MAX_QTY  = 500;   // quantité max par achat
    private static final int CAT_PER_PAGE = 45;    // 45 blocs par page (6e ligne = navigation)

    // Petit helper pour construire une entrée sans répéter le prix « auto ».
    private static ShopEntry e(Material mat, String name, double price) { return new ShopEntry(mat, name, price); }
    // Variante avec prix de rachat (le shop reprend l'item au joueur).
    private static ShopEntry e(Material mat, String name, double price, double sell) {
        return new ShopEntry(mat, name, price, sell);
    }

    // Les catégories de blocs (l'ordre = ordre d'affichage dans le menu principal).
    private static final java.util.List<ShopCategory> CATEGORIES = buildCategories();

    private static java.util.List<ShopCategory> buildCategories() {
        java.util.List<ShopCategory> cats = new java.util.ArrayList<>();

        // --- CONSTRUCTION : blocs pleins pour bâtir (prix auto par rareté) ---
        java.util.List<ShopEntry> constr = new java.util.ArrayList<>(java.util.Arrays.asList(
            e(Material.STONE, "§7Pierre", 20), e(Material.COBBLESTONE, "§7Pavé", 15),
            e(Material.STONE_BRICKS, "§7Briques de pierre", 40), e(Material.MOSSY_STONE_BRICKS, "§7Briques moussues", 50),
            e(Material.CHISELED_STONE_BRICKS, "§7Briques sculptées", 60), e(Material.SMOOTH_STONE, "§7Pierre lisse", 40),
            e(Material.GRANITE, "§7Granite", 25), e(Material.POLISHED_GRANITE, "§7Granite poli", 40),
            e(Material.DIORITE, "§7Diorite", 25), e(Material.POLISHED_DIORITE, "§7Diorite polie", 40),
            e(Material.ANDESITE, "§7Andésite", 25), e(Material.POLISHED_ANDESITE, "§7Andésite polie", 40),
            e(Material.DEEPSLATE, "§8Ardoise des abîmes", 35), e(Material.COBBLED_DEEPSLATE, "§8Ardoise brute", 30),
            e(Material.POLISHED_DEEPSLATE, "§8Ardoise polie", 50), e(Material.DEEPSLATE_BRICKS, "§8Briques d'ardoise", 60),
            e(Material.DEEPSLATE_TILES, "§8Tuiles d'ardoise", 60), e(Material.TUFF, "§7Tuf", 25),
            e(Material.CALCITE, "§fCalcite", 40), e(Material.DRIPSTONE_BLOCK, "§7Bloc de stalactite", 40),
            e(Material.BRICKS, "§cBriques", 60), e(Material.SANDSTONE, "§eGrès", 30),
            e(Material.SMOOTH_SANDSTONE, "§eGrès lisse", 45), e(Material.CHISELED_SANDSTONE, "§eGrès sculpté", 55),
            e(Material.CUT_SANDSTONE, "§eGrès taillé", 45), e(Material.RED_SANDSTONE, "§6Grès rouge", 30),
            e(Material.SMOOTH_RED_SANDSTONE, "§6Grès rouge lisse", 45),
            e(Material.QUARTZ_BLOCK, "§fBloc de quartz", 200), e(Material.SMOOTH_QUARTZ, "§fQuartz lisse", 220),
            e(Material.CHISELED_QUARTZ_BLOCK, "§fQuartz sculpté", 240), e(Material.QUARTZ_BRICKS, "§fBriques de quartz", 240),
            e(Material.QUARTZ_PILLAR, "§fColonne de quartz", 220),
            e(Material.PRISMARINE, "§3Prismarine", 150), e(Material.PRISMARINE_BRICKS, "§3Briques de prismarine", 180),
            e(Material.DARK_PRISMARINE, "§3Prismarine sombre", 200),
            e(Material.NETHER_BRICKS, "§4Briques du Nether", 120), e(Material.RED_NETHER_BRICKS, "§4Briques rouges du Nether", 140),
            e(Material.BLACKSTONE, "§8Pierre noire", 80), e(Material.POLISHED_BLACKSTONE, "§8Pierre noire polie", 100),
            e(Material.POLISHED_BLACKSTONE_BRICKS, "§8Briques de pierre noire", 120), e(Material.GILDED_BLACKSTONE, "§6Pierre noire dorée", 300),
            e(Material.BASALT, "§8Basalte", 40), e(Material.POLISHED_BASALT, "§8Basalte poli", 55), e(Material.SMOOTH_BASALT, "§8Basalte lisse", 55),
            e(Material.END_STONE, "§ePierre de l'End", 120), e(Material.END_STONE_BRICKS, "§eBriques de l'End", 150),
            e(Material.PURPUR_BLOCK, "§5Purpur", 160), e(Material.PURPUR_PILLAR, "§5Colonne de purpur", 170),
            e(Material.OBSIDIAN, "§5Obsidienne", 500), e(Material.CRYING_OBSIDIAN, "§5Obsidienne larmoyante", 700)
        ));
        cats.add(new ShopCategory("constr", "§6§lShop §7» §fConstruction", Material.BRICKS,
                "§f§lConstruction", "§7Pierre, briques, quartz, planches...", constr));

        // --- COULEURS : les 16 teintes (laine, verre, béton, terracotta, tapis, panneaux de verre) ---
        java.util.List<ShopEntry> couleurs = new java.util.ArrayList<>();
        String[] colFr = {"Blanche","Orange","Magenta","Bleu clair","Jaune","Vert clair","Rose","Grise",
                          "Gris clair","Cyan","Violette","Bleue","Marron","Verte","Rouge","Noire"};
        String[] colId = {"WHITE","ORANGE","MAGENTA","LIGHT_BLUE","YELLOW","LIME","PINK","GRAY",
                          "LIGHT_GRAY","CYAN","PURPLE","BLUE","BROWN","GREEN","RED","BLACK"};
        for (int i = 0; i < 16; i++) couleurs.add(e(Material.valueOf(colId[i] + "_WOOL"), "§fLaine " + colFr[i], 40));
        for (int i = 0; i < 16; i++) couleurs.add(e(Material.valueOf(colId[i] + "_CONCRETE"), "§fBéton " + colFr[i], 60));
        for (int i = 0; i < 16; i++) couleurs.add(e(Material.valueOf(colId[i] + "_TERRACOTTA"), "§fTerracotta " + colFr[i], 60));
        for (int i = 0; i < 16; i++) couleurs.add(e(Material.valueOf(colId[i] + "_STAINED_GLASS"), "§fVerre " + colFr[i], 50));
        for (int i = 0; i < 16; i++) couleurs.add(e(Material.valueOf(colId[i] + "_CARPET"), "§fTapis " + colFr[i], 25));
        cats.add(new ShopCategory("couleurs", "§6§lShop §7» §dCouleurs", Material.RED_WOOL,
                "§d§lCouleurs", "§7Laine, béton, verre, tapis (16 teintes)...", couleurs));

        // --- NATURE : terre, sable, plantes, feuillages, blocs naturels ---
        java.util.List<ShopEntry> nature = new java.util.ArrayList<>(java.util.Arrays.asList(
            e(Material.DIRT, "§6Terre", 10), e(Material.COARSE_DIRT, "§6Terre stérile", 12), e(Material.ROOTED_DIRT, "§6Terre enracinée", 15),
            e(Material.GRASS_BLOCK, "§aBloc d'herbe", 20), e(Material.PODZOL, "§6Podzol", 25), e(Material.MYCELIUM, "§5Mycélium", 30),
            e(Material.MUD, "§8Boue", 12), e(Material.MUDDY_MANGROVE_ROOTS, "§8Racines boueuses", 20), e(Material.CLAY, "§7Argile", 25),
            e(Material.SAND, "§eSable", 15), e(Material.RED_SAND, "§6Sable rouge", 18), e(Material.GRAVEL, "§7Gravier", 15),
            e(Material.MOSS_BLOCK, "§2Bloc de mousse", 40), e(Material.MOSS_CARPET, "§2Tapis de mousse", 25),
            e(Material.OAK_LEAVES, "§2Feuilles de chêne", 15), e(Material.SPRUCE_LEAVES, "§2Feuilles d'épicéa", 15),
            e(Material.BIRCH_LEAVES, "§2Feuilles de bouleau", 15), e(Material.JUNGLE_LEAVES, "§2Feuilles de jungle", 15),
            e(Material.ACACIA_LEAVES, "§2Feuilles d'acacia", 15), e(Material.DARK_OAK_LEAVES, "§2Feuilles de chêne noir", 15),
            e(Material.MANGROVE_LEAVES, "§2Feuilles de palétuvier", 15), e(Material.CHERRY_LEAVES, "§dFeuilles de cerisier", 25),
            e(Material.AZALEA_LEAVES, "§2Feuilles d'azalée", 20), e(Material.FLOWERING_AZALEA_LEAVES, "§dAzalée fleurie", 30),
            e(Material.SHORT_GRASS, "§aHerbe", 8), e(Material.FERN, "§aFougère", 8), e(Material.DEAD_BUSH, "§6Buisson mort", 8),
            e(Material.DANDELION, "§ePissenlit", 20), e(Material.POPPY, "§cCoquelicot", 20), e(Material.BLUE_ORCHID, "§bOrchidée bleue", 25),
            e(Material.ALLIUM, "§5Allium", 25), e(Material.AZURE_BLUET, "§fHoustonie", 25), e(Material.OXEYE_DAISY, "§fMarguerite", 25),
            e(Material.CORNFLOWER, "§9Bleuet", 25), e(Material.LILY_OF_THE_VALLEY, "§fMuguet", 30), e(Material.SUNFLOWER, "§eTournesol", 35),
            e(Material.LILAC, "§dLilas", 30), e(Material.ROSE_BUSH, "§cBuisson de roses", 30), e(Material.PEONY, "§dPivoine", 30),
            e(Material.BROWN_MUSHROOM, "§6Champignon brun", 25), e(Material.RED_MUSHROOM, "§cChampignon rouge", 25),
            e(Material.PUMPKIN, "§6Citrouille", 40), e(Material.MELON, "§aPastèque", 40), e(Material.HAY_BLOCK, "§eBotte de foin", 35),
            e(Material.BAMBOO, "§2Bambou", 15), e(Material.SUGAR_CANE, "§aCanne à sucre", 20), e(Material.CACTUS, "§2Cactus", 25),
            e(Material.SNOW_BLOCK, "§fBloc de neige", 20), e(Material.ICE, "§bGlace", 30), e(Material.PACKED_ICE, "§bGlace compacte", 45),
            e(Material.BLUE_ICE, "§bGlace bleue", 80), e(Material.GLOWSTONE, "§ePierre lumineuse", 100),
            e(Material.SHROOMLIGHT, "§6Champilum", 120), e(Material.HONEY_BLOCK, "§eBloc de miel", 60), e(Material.HONEYCOMB_BLOCK, "§6Bloc de rayon de miel", 60)
        ));
        cats.add(new ShopCategory("nature", "§6§lShop §7» §2Nature", Material.GRASS_BLOCK,
                "§2§lNature", "§7Terre, sable, plantes, feuilles...", nature));

        // --- PRÉCIEUX & MINERAIS : blocs de stockage et minerais (chers) ---
        java.util.List<ShopEntry> precieux = new java.util.ArrayList<>(java.util.Arrays.asList(
            e(Material.COAL_BLOCK, "§8Bloc de charbon", 300), e(Material.IRON_BLOCK, "§fBloc de fer", 1500),
            e(Material.COPPER_BLOCK, "§6Bloc de cuivre", 800), e(Material.GOLD_BLOCK, "§eBloc d'or", 3000),
            e(Material.REDSTONE_BLOCK, "§cBloc de redstone", 500), e(Material.LAPIS_BLOCK, "§9Bloc de lapis", 700),
            e(Material.EMERALD_BLOCK, "§aBloc d'émeraude", 6000), e(Material.DIAMOND_BLOCK, "§bBloc de diamant", 9000),
            e(Material.NETHERITE_BLOCK, "§8Bloc de Netherite", 50000), e(Material.AMETHYST_BLOCK, "§5Bloc d'améthyste", 400),
            e(Material.COAL_ORE, "§8Minerai de charbon", 60), e(Material.IRON_ORE, "§fMinerai de fer", 150),
            e(Material.COPPER_ORE, "§6Minerai de cuivre", 100), e(Material.GOLD_ORE, "§eMinerai d'or", 300),
            e(Material.REDSTONE_ORE, "§cMinerai de redstone", 100), e(Material.LAPIS_ORE, "§9Minerai de lapis", 120),
            e(Material.EMERALD_ORE, "§aMinerai d'émeraude", 600), e(Material.DIAMOND_ORE, "§bMinerai de diamant", 900),
            e(Material.DEEPSLATE_IRON_ORE, "§fMinerai de fer (ardoise)", 160), e(Material.DEEPSLATE_GOLD_ORE, "§eMinerai d'or (ardoise)", 320),
            e(Material.DEEPSLATE_DIAMOND_ORE, "§bMinerai de diamant (ardoise)", 950), e(Material.DEEPSLATE_EMERALD_ORE, "§aMinerai d'émeraude (ardoise)", 650),
            e(Material.NETHER_QUARTZ_ORE, "§fMinerai de quartz", 90), e(Material.NETHER_GOLD_ORE, "§eMinerai d'or du Nether", 250),
            e(Material.ANCIENT_DEBRIS, "§8Débris antiques", 8000), e(Material.RAW_IRON_BLOCK, "§fBloc de fer brut", 1200),
            e(Material.RAW_COPPER_BLOCK, "§6Bloc de cuivre brut", 700), e(Material.RAW_GOLD_BLOCK, "§eBloc d'or brut", 2500)
        ));
        cats.add(new ShopCategory("precieux", "§6§lShop §7» §bPrécieux", Material.DIAMOND_BLOCK,
                "§b§lPrécieux & Minerais", "§7Blocs de fer/or/diamant, minerais...", precieux));

        // --- PLANTES : cultures à faire pousser. Achat 1 000$ l'unité, RACHAT 2$ l'unité.
        //     (prix de vente provisoires : à réajuster une fois la ferme équilibrée)
        java.util.List<ShopEntry> plantes = new java.util.ArrayList<>(java.util.Arrays.asList(
            e(Material.WHEAT_SEEDS, "§eGraines de blé",   1000, 2), e(Material.WHEAT, "§eBlé", 1000, 2),
            e(Material.CARROT, "§6Carotte", 1000, 2),
            e(Material.POTATO,      "§ePomme de terre",   1000, 2), e(Material.PUMPKIN, "§6Citrouille", 1000, 2),
            e(Material.MELON,       "§aPastèque",         1000, 2), e(Material.BAMBOO, "§2Bambou", 1000, 2),
            e(Material.SUGAR_CANE,  "§aCanne à sucre",    1000, 2), e(Material.CACTUS, "§2Cactus", 1000, 2)
        ));
        cats.add(new ShopCategory("plantes", "§6§lShop §7» §aPlantes", Material.WHEAT,
                "§a§lPlantes", "§7Blé, carotte, patate, citrouille...", plantes));

        return cats;
    }

    // Recherche une catégorie par le titre de son menu.
    private ShopCategory categoryByTitle(String title) {
        for (ShopCategory c : CATEGORIES) if (c.title.equals(title)) return c;
        return null;
    }
    // Prix d'un material dans n'importe quelle catégorie paginée (-1 si absent).
    // Prix de RACHAT d'un item par le shop (0 = le shop ne le reprend pas).
    private double categorySellPrice(Material mat) {
        for (ShopCategory c : CATEGORIES) for (ShopEntry en : c.entries) if (en.mat == mat) return en.sellPrice;
        return 0;
    }

    // Vend TOUT ce que le joueur possède de cet item (inventaire principal, hors armure).
    private void sellAll(Player player, Material mat, String name, double unitPrice) {
        int total = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType() != mat) continue;
            // On ne rachète que des items « bruts » : pas de renommage/enchant (sécurité).
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) continue;
            total += it.getAmount();
        }
        if (total <= 0) {
            player.sendMessage("§cTu n'as aucun " + name + " §cà vendre.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Retrait effectif.
        int restant = total;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && restant > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != mat) continue;
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) continue;
            int pris = Math.min(restant, it.getAmount());
            restant -= pris;
            if (pris >= it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - pris);
        }
        player.getInventory().setStorageContents(contents);
        player.updateInventory();

        double gain = unitPrice * total;
        if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, gain);
        player.sendMessage("§a✔ Vendu §f" + total + "× " + name + " §apour §6"
                + PrivateMines.formatNumber(gain) + "$§a.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private double categoryPrice(Material mat) {
        for (ShopCategory c : CATEGORIES) for (ShopEntry en : c.entries) if (en.mat == mat) return en.price;
        return -1;
    }
    // Page actuellement ouverte par joueur dans une catégorie paginée.
    private final java.util.Map<java.util.UUID, Integer> catPage = new java.util.HashMap<>();
    // Catégorie actuellement ouverte par joueur (pour le retour depuis le clavier).
    private final java.util.Map<java.util.UUID, String> catOpen = new java.util.HashMap<>();

    // Saisie en cours sur le clavier : UUID -> texte tapé (ex: "12")
    private final java.util.Map<java.util.UUID, String> kbInput = new java.util.HashMap<>();
    // Quel bloc est en train d'être acheté sur le clavier : UUID -> Material
    private final java.util.Map<java.util.UUID, Material> kbMaterial = new java.util.HashMap<>();
    // Prix unitaire de l'achat en cours : UUID -> prix (selon la catégorie : bois ou utilitaire)
    private final java.util.Map<java.util.UUID, Double> kbPrice = new java.util.HashMap<>();

    // Petit helper : crée l'icône d'une catégorie du menu principal.
    private ItemStack mainIcon(Material mat, String name, String... loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        java.util.List<String> lore = new java.util.ArrayList<>(java.util.Arrays.asList(loreLines));
        lore.add("§eClic pour ouvrir.");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Menu principal du shop.
    public void openShopMain(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, SHOP_MAIN_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        // Catégories historiques.
        menu.setItem(10, mainIcon(Material.OAK_LOG, "§aBois", "§7Pousses d'arbres à planter."));
        menu.setItem(12, mainIcon(Material.LANTERN, "§dDécoration", "§7Lanternes, chaînes, tonneaux, déco..."));
        menu.setItem(14, mainIcon(Material.ANVIL, "§bUtilitaires", "§7Enclume, table d'enchant, seaux..."));
        // Nouvelles catégories de blocs (icône = celle définie dans la table CATEGORIES).
        menu.setItem(16, mainIcon(CATEGORIES.get(0).icon, CATEGORIES.get(0).iconName, CATEGORIES.get(0).iconDesc));
        menu.setItem(28, mainIcon(CATEGORIES.get(1).icon, CATEGORIES.get(1).iconName, CATEGORIES.get(1).iconDesc));
        menu.setItem(30, mainIcon(CATEGORIES.get(2).icon, CATEGORIES.get(2).iconName, CATEGORIES.get(2).iconDesc));
        menu.setItem(32, mainIcon(CATEGORIES.get(3).icon, CATEGORIES.get(3).iconName, CATEGORIES.get(3).iconDesc));
        // Plantes : la seule catégorie que le shop RACHÈTE (clic droit = vendre).
        menu.setItem(20, mainIcon(CATEGORIES.get(4).icon, CATEGORIES.get(4).iconName, CATEGORIES.get(4).iconDesc));
        // Spawners d'île (Golem de fer / Piglin) — génèrent fer/or pour le niveau d'île.
        menu.setItem(34, mainIcon(Material.SPAWNER, "§5§lSpawners d'Île", "§7Golem de fer, Piglin... génèrent", "§7du fer/or pour ton niveau d'île."));

        player.openInventory(menu);
    }

    // Achat d'un spawner : débite le prix et livre l'item taggé (quantité 1).
    private void buySpawner(Player player, SpawnerManager.SpawnerDef def) {
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        if (economy.getBalance(player) < def.getPrice()) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(def.getPrice()) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, def.getPrice());
        plugin.getSpawnerManager().giveSpawner(player, def, 1);
        player.sendMessage("§aAcheté " + def.getName() + " §apour §6" + PrivateMines.formatNumber(def.getPrice()) + "$ !");
        player.sendMessage("§7Pose-le sur ton §e/ob §7(ton Île) pour qu'il génère des ressources.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // Sous-menu des spawners d'île.
    private void openShopSpawners(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, SHOP_SPAWNER_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        menu.setItem(11, spawnerShopIcon(SpawnerManager.IRON));
        menu.setItem(15, spawnerShopIcon(SpawnerManager.GOLD));

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour au shop");
        back.setItemMeta(bm);
        menu.setItem(26, back);
        player.openInventory(menu);
    }

    // Icône d'un spawner dans le shop (affiche le prix ; l'achat livre l'item taggé).
    private ItemStack spawnerShopIcon(SpawnerManager.SpawnerDef def) {
        ItemStack it = new ItemStack(Material.SPAWNER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(def.getName());
        m.setLore(java.util.List.of(
                "§7Génère du " + (def.getType().equals("iron") ? "§ffer" : "§eor") + " §7sur ta parcelle.",
                "§7Prix : §6" + PrivateMines.formatNumber(def.getPrice()) + "$",
                "§eClic pour acheter (1)."));
        it.setItemMeta(m);
        return it;
    }

    // Ouvre une catégorie paginée à la page donnée.
    private void openCategory(Player player, ShopCategory cat, int page) {
        int totalPages = Math.max(1, (int) Math.ceil(cat.entries.size() / (double) CAT_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        catPage.put(player.getUniqueId(), page);
        catOpen.put(player.getUniqueId(), cat.key);

        Inventory menu = Bukkit.createInventory(null, 54, cat.title);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler); // ligne du bas = barre de navigation

        int startIdx = page * CAT_PER_PAGE;
        for (int i = 0; i < CAT_PER_PAGE; i++) {
            int idx = startIdx + i;
            if (idx >= cat.entries.size()) break;
            ShopEntry en = cat.entries.get(idx);
            ItemStack item = new ItemStack(en.mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(en.name);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§71 unité = §6" + PrivateMines.formatNumber(en.price) + "$");
            lore.add("§eClic gauche §7pour acheter.");
            // Les entrées rachetables (plantes) affichent aussi leur prix de revente.
            if (en.sellPrice > 0) {
                lore.add("");
                lore.add("§7Revente : §a" + PrivateMines.formatNumber(en.sellPrice) + "$ §7l'unité");
                lore.add("§eClic droit §7pour tout vendre.");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            menu.setItem(i, item);
        }

        // Barre de navigation (ligne du bas).
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.setDisplayName("§e‹ Page précédente");
            prev.setItemMeta(pm);
            menu.setItem(45, prev);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour au shop");
        bm.setLore(java.util.List.of("§7Page §f" + (page + 1) + "§7/§f" + totalPages));
        back.setItemMeta(bm);
        menu.setItem(49, back);
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.setDisplayName("§ePage suivante ›");
            next.setItemMeta(nm);
            menu.setItem(53, next);
        }

        player.openInventory(menu);
    }

    // Menu liste des utilitaires.
    private void openShopUtil(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, SHOP_UTIL_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        for (int i = 0; i < UTIL_BLOCKS.length; i++) {
            ItemStack item = new ItemStack(UTIL_BLOCKS[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(UTIL_NAMES[i]);
            meta.setLore(java.util.List.of("§71 unité = §6" + PrivateMines.formatNumber(UTIL_PRICES[i]) + "$", "§eClic pour acheter."));
            item.setItemMeta(meta);
            menu.setItem(i, item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour au shop");
        back.setItemMeta(bm);
        menu.setItem(26, back);

        player.openInventory(menu);
    }

    // Menu liste des blocs de décoration.
    private void openShopDeco(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, SHOP_DECO_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        for (int i = 0; i < DECO_BLOCKS.length; i++) {
            ItemStack item = new ItemStack(DECO_BLOCKS[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(DECO_NAMES[i]);
            meta.setLore(java.util.List.of("§71 unité = §6" + PrivateMines.formatNumber(DECO_PRICES[i]) + "$", "§eClic pour acheter."));
            item.setItemMeta(meta);
            menu.setItem(i, item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour au shop");
        back.setItemMeta(bm);
        menu.setItem(26, back);

        player.openInventory(menu);
    }

    // Menu liste des bûches.
    private void openShopBois(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, SHOP_BOIS_TITLE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        for (int i = 0; i < WOOD_BLOCKS.length; i++) {
            ItemStack item = new ItemStack(WOOD_BLOCKS[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(WOOD_NAMES[i]);
            meta.setLore(java.util.List.of("§71 pousse = §6" + PrivateMines.formatNumber(WOOD_PRICE) + "$", "§eClic pour acheter."));
            item.setItemMeta(meta);
            menu.setItem(i, item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour au shop");
        back.setItemMeta(bm);
        menu.setItem(26, back);

        player.openInventory(menu);
    }

    // Textures Base64 pour les têtes numérotées 0-9
    private static final String[] DIGIT_TEXTURES = {
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTBjZjRlNGMzNDVmZTYxNmIwYTljMDRjZGQyZmVlZjYxZGI0YjFjYjE0YjAwMzVhMjgwMjBkZmU3M2MyZDVkMSJ9fX0=", // 0
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg0MzJhNTc1NmEwNGViZjA2MmQ3MmE2ZjMxYmQ2MmU4ZjRkODJhOTIxMjAzMzZhZTE5NzJmZTE4ZDM4NzBiYSJ9fX0=", // 1
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2U1MGM3MDk3OTk0MzEzZDk0MzIxNDJkYTc2NTFkYzZkZDYzMzU4N2UyZTFkZDlhNTYyYWJiYzc4NzhlZmI2NSJ9fX0=", // 2
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWRkMjJkYjhjNmUyMzhmYjhjYzA4MTlkMDJhNjU0MDMyOTdkNjNiNjdjNmM3Y2U2YjQzYmM4MjkxODk4MzdmNCJ9fX0=", // 3
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODU0YzFkZWQ5MjMxOWJkODM1NzNmMGYwMDQxZTczMDMzOGViN2JiNzk5N2ViNzFmZjU4M2MyOTA4MzIzODg4ZSJ9fX0=", // 4
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRkYWM3Y2YyMDE3YTJhZWZjZGYyOWRjMzgzMmQ0MDdjYmQ5YzhiNmJhMGU1MWEwYTMxNjlmNmZmYjYyYzAxNSJ9fX0=", // 5
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGVkOWJlZGNjMWQxYzQ4Y2FlZTU3MjhlMWVmOWIwMDhkNWE1ZDMwZDJlMTRjMWM3Yjg0OWU4ZDg1NTNiNTI1NyJ9fX0=", // 6
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTBiYmE4ZDU2YmI2YjkwNjY4M2IxOGZiYTQ4MWQ1OGZhMGJiN2QzYjNiMThjNjQ1MmU5MjU3ZGY1NDJmNTNhYSJ9fX0=", // 7
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY5Y2Q3NGMwYThhMmYwMTA1NTg4NmY4MTVmOGUxZWQ2ZDdkZTZlNzg4YWFlOWY4NmJkMzdlMmQ0ZTQ2MjFjNiJ9fX0=", // 8
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTljZDcyNjlhMzE0MDQ4YmM3Zjc5Mjk0NDhhYWJmNGJiYzlkNjk4MTdhOTJmZjUwNTZiMDY1YWRkOTQwODU4OSJ9fX0=", // 9
    };
    private static final String DELETE_TEXTURE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmE2Y2FhMWUxZTlkOWFlZjU5Mjc4NzExNDIyNzg3YTAxNzk5M2M1YjI5MjUxOGM5ZjYzMmQ0MTJmNWE2NTkifX19";
    private static final String CONFIRM_TEXTURE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmY3NWQxYjc4NWQxOGQ0N2IzZWE4ZjBhN2UwZmQ0YTFmYWU5ZTdkMzIzY2YzYjEzOGM4Yzc4Y2ZlMjRlZTU5In19fQ==";

    // Crée une tête (PLAYER_HEAD) avec une texture Base64.
    private ItemStack makeHead(String base64, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile =
                Bukkit.createProfile(java.util.UUID.randomUUID(), "");
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                "textures", base64));
        meta.setPlayerProfile(profile);
        meta.setDisplayName(displayName);
        if (lore != null) meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    // Layout clavier (54 slots, 6 lignes x 9 colonnes) :
    //  Ligne 0 (0-8)  : déco | afficheur slot 4
    //  Ligne 1 (9-17) : D D [7]10 [8]11 [9]12 D D D D
    //  Ligne 2 (18-26): D D [4]19 [5]20 [6]21 D D D D
    //  Ligne 3 (27-35): D D [1]28 [2]29 [3]30 D D D D
    //  Ligne 4 (36-44): D D [⌫]37 [0]38 [✔]39 D D D D
    //  Ligne 5 (45-53): D D D D D D D D [←]53
    private void openKeyboard(Player player, Material mat, String woodName) {
        openKeyboard(player, mat, woodName, WOOD_PRICE, WOOD_MAX_QTY);
    }

    private void openKeyboard(Player player, Material mat, String itemName, double price, int maxQty) {
        kbInput.put(player.getUniqueId(), "");
        kbMaterial.put(player.getUniqueId(), mat);
        kbPrice.put(player.getUniqueId(), price);

        Inventory menu = Bukkit.createInventory(null, 54, SHOP_KB_PREFIX + itemName);
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, filler);

        // Chiffres 7/8/9, 4/5/6, 1/2/3
        int[][] rows = {{10,11,12},{19,20,21},{28,29,30}};
        int[][] vals = {{7,8,9},{4,5,6},{1,2,3}};
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                menu.setItem(rows[r][c], makeKbDigit(vals[r][c]));

        // ⌫ slot 37, 0 slot 38, ✔ slot 33 (ligne 4, colonne 7)
        menu.setItem(37, makeKbDelete());
        menu.setItem(38, makeKbDigit(0));
        // Bouton confirmer (avec le bon prix unitaire)
        menu.setItem(33, makeKbConfirm(0, price));

        // Afficheur ligne 0 : têtes des chiffres saisis (vide au départ)
        refreshKbDisplay(menu, "", mat);

        // Retour slot 53
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour");
        back.setItemMeta(bm);
        menu.setItem(53, back);

        player.openInventory(menu);
    }

    // Rafraîchit l'afficheur (ligne 0) et le bouton confirmer selon la saisie actuelle.
    private void refreshKeyboard(Player player) {
        if (player.getOpenInventory() == null) return;
        String title = player.getOpenInventory().getTitle();
        if (!title.startsWith(SHOP_KB_PREFIX)) return;
        Inventory menu = player.getOpenInventory().getTopInventory();
        String input = kbInput.getOrDefault(player.getUniqueId(), "");
        Material mat = kbMaterial.get(player.getUniqueId());
        double price = kbPrice.getOrDefault(player.getUniqueId(), WOOD_PRICE);
        refreshKbDisplay(menu, input, mat);
        int qty = input.isEmpty() ? 0 : Math.min(Integer.parseInt(input), WOOD_MAX_QTY);
        menu.setItem(33, makeKbConfirm(qty, price));
    }

    // Affiche les chiffres du nombre saisi sous forme de têtes dans la ligne 0.
    // 1 chiffre  -> colonne 6 (slot 6)
    // 2 chiffres -> colonnes 6-7 (slots 6-7)
    // 3 chiffres -> colonnes 5-6-7 (slots 5-6-7)  (500 max = 3 chiffres)
    // Vide -> afficheur texte slot 4
    private void refreshKbDisplay(Inventory menu, String input, Material mat) {
        ItemStack filler = plugin.makeFiller();
        for (int i = 0; i < 9; i++) menu.setItem(i, filler);

        if (input.isEmpty()) {
            menu.setItem(4, makeKbDisplay("", mat));
            return;
        }
        int len = input.length();
        // Colonne de départ selon le nombre de chiffres (colonne = slot sur ligne 0)
        int start = len == 1 ? 6 : len == 2 ? 6 : 5;
        for (int i = 0; i < len; i++) {
            int digit = input.charAt(i) - '0';
            menu.setItem(start + i, makeKbDigit(digit));
        }
    }

    private ItemStack makeKbDigit(int d) {
        return makeHead(DIGIT_TEXTURES[d], "§f§l" + d, null);
    }

    private ItemStack makeKbDelete() {
        return makeHead(DELETE_TEXTURE, "§c§l⌫ Effacer", java.util.List.of("§7Supprime le dernier chiffre."));
    }

    private ItemStack makeKbConfirm(int qty, double price) {
        double cost = qty * price;
        List<String> lore = new ArrayList<>();
        if (qty > 0) {
            lore.add("§7Quantité : §f" + qty);
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(cost) + "$");
            lore.add("§aClic pour confirmer !");
        } else {
            lore.add("§7Tape une quantité d'abord.");
        }
        return makeHead(CONFIRM_TEXTURE, qty > 0 ? "§a§l✔ Acheter §f" + qty : "§7✔ Confirmer", lore);
    }

    private ItemStack makeKbDisplay(String input, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Tape une quantité...");
        meta.setLore(java.util.List.of("§7Max : §f" + WOOD_MAX_QTY));
        item.setItemMeta(meta);
        return item;
    }

    // Gestion des clics dans les menus du shop.
    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        boolean isCategory = categoryByTitle(title) != null;
        // Ne s'applique qu'aux menus du shop/clavier.
        if (!title.equals(SHOP_MAIN_TITLE) && !title.equals(SHOP_BOIS_TITLE)
                && !title.equals(SHOP_UTIL_TITLE) && !title.equals(SHOP_DECO_TITLE)
                && !title.equals(SHOP_SPAWNER_TITLE)
                && !isCategory && !title.startsWith(SHOP_KB_PREFIX)) return;
        event.setCancelled(true);

        // --- Menu principal ---
        if (title.equals(SHOP_MAIN_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.OAK_LOG) { openShopBois(player); return; }
            if (clicked.getType() == Material.LANTERN) { openShopDeco(player); return; }
            if (clicked.getType() == Material.ANVIL) { openShopUtil(player); return; }
            if (clicked.getType() == Material.SPAWNER) { openShopSpawners(player); return; }
            // Nouvelles catégories : on ouvre celle dont l'icône a été cliquée.
            for (ShopCategory c : CATEGORIES) {
                if (clicked.getType() == c.icon) { openCategory(player, c, 0); return; }
            }
            return;
        }

        // --- Sous-menu Spawners ---
        if (title.equals(SHOP_SPAWNER_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openShopMain(player); return; }
            if (clicked.getType() != Material.SPAWNER) return;
            // On distingue IRON/GOLD par le nom affiché de l'icône.
            String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";
            SpawnerManager.SpawnerDef def = name.equals(SpawnerManager.GOLD.getName())
                    ? SpawnerManager.GOLD : SpawnerManager.IRON;
            buySpawner(player, def);
            return;
        }

        // --- Catégorie paginée (Construction / Couleurs / Nature / Précieux) ---
        if (isCategory) {
            ShopCategory cat = categoryByTitle(title);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            int slot = event.getSlot();
            int page = catPage.getOrDefault(player.getUniqueId(), 0);
            if (slot == 49) { openShopMain(player); return; }                 // retour
            if (slot == 45) { openCategory(player, cat, page - 1); return; }   // page précédente
            if (slot == 53) { openCategory(player, cat, page + 1); return; }   // page suivante
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : clicked.getType().name();
            // Clic DROIT sur une entrée rachetable = vendre tout ce que le joueur en possède.
            if (event.isRightClick()) {
                double sell = categorySellPrice(clicked.getType());
                if (sell > 0) { sellAll(player, clicked.getType(), name, sell); return; }
                return; // item non rachetable : le clic droit ne fait rien
            }
            double price = categoryPrice(clicked.getType());
            if (price < 0) return;
            openKeyboard(player, clicked.getType(), name, price, CAT_MAX_QTY);
            return;
        }

        // --- Liste des utilitaires ---
        if (title.equals(SHOP_UTIL_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openShopMain(player); return; }
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            double price = utilPrice(clicked.getType());
            if (price < 0) return;
            String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : clicked.getType().name();
            openKeyboard(player, clicked.getType(), name, price, UTIL_MAX_QTY);
            return;
        }

        // --- Liste de la décoration ---
        if (title.equals(SHOP_DECO_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openShopMain(player); return; }
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            double price = decoPrice(clicked.getType());
            if (price < 0) return;
            String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : clicked.getType().name();
            openKeyboard(player, clicked.getType(), name, price, DECO_MAX_QTY);
            return;
        }

        // --- Liste des bûches ---
        if (title.equals(SHOP_BOIS_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openShopMain(player); return; }
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            // Trouve le nom du bois cliqué.
            String woodName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "Bûche";
            openKeyboard(player, clicked.getType(), woodName);
            return;
        }

        // --- Clavier de quantité ---
        if (title.startsWith(SHOP_KB_PREFIX)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            int slot = event.getSlot();

            // Retour : vers le bon sous-menu selon le type d'item acheté.
            if (slot == 53) {
                Material cur = kbMaterial.get(player.getUniqueId());
                // Si l'achat venait d'une catégorie paginée, on y retourne (même page).
                String catKey = catOpen.get(player.getUniqueId());
                ShopCategory cat = null;
                if (cur != null) for (ShopCategory c : CATEGORIES) if (categoryPrice(cur) >= 0 && c.key.equals(catKey)) cat = c;
                if (cat != null) { openCategory(player, cat, catPage.getOrDefault(player.getUniqueId(), 0)); return; }
                if (cur != null && utilPrice(cur) >= 0) openShopUtil(player);
                else if (cur != null && decoPrice(cur) >= 0) openShopDeco(player);
                else openShopBois(player);
                return;
            }
            // Déco / afficheur : on ignore
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            if (clicked.getType() != Material.PLAYER_HEAD) return;

            // Chiffres par slot
            java.util.Map<Integer, Integer> digitBySlot = new java.util.HashMap<>();
            digitBySlot.put(10, 7); digitBySlot.put(11, 8); digitBySlot.put(12, 9);
            digitBySlot.put(19, 4); digitBySlot.put(20, 5); digitBySlot.put(21, 6);
            digitBySlot.put(28, 1); digitBySlot.put(29, 2); digitBySlot.put(30, 3);
            digitBySlot.put(38, 0);

            if (digitBySlot.containsKey(slot)) {
                int digit = digitBySlot.get(slot);
                String current = kbInput.getOrDefault(player.getUniqueId(), "");
                String next = current + digit;
                try {
                    int val = Math.min(Integer.parseInt(next), WOOD_MAX_QTY);
                    kbInput.put(player.getUniqueId(), String.valueOf(val));
                } catch (NumberFormatException ignored) {}
                refreshKeyboard(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                return;
            }

            // ⌫ effacer
            if (slot == 37) {
                String current = kbInput.getOrDefault(player.getUniqueId(), "");
                if (!current.isEmpty())
                    kbInput.put(player.getUniqueId(), current.substring(0, current.length() - 1));
                refreshKeyboard(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                return;
            }

            // ✔ confirmer
            if (slot == 33) {
                String input = kbInput.getOrDefault(player.getUniqueId(), "");
                if (input.isEmpty()) return;
                int qty = Math.min(Integer.parseInt(input), WOOD_MAX_QTY);
                if (qty <= 0) return;
                Material mat = kbMaterial.get(player.getUniqueId());
                double price = kbPrice.getOrDefault(player.getUniqueId(), WOOD_PRICE);
                double cost = qty * price;
                net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
                if (economy == null) return;
                if (economy.getBalance(player) < cost) {
                    player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                economy.withdrawPlayer(player, cost);

                // Destination : sur sa parcelle -> inventaire ; ailleurs -> backpack /bp.
                boolean onParcelle = plugin.isInParcelleZone(player);
                int remaining = onParcelle ? plugin.getBackpack().giveToInventory(player, mat, qty) : plugin.getBackpack().giveToBackpack(player, mat, qty);

                if (remaining > 0) {
                    economy.depositPlayer(player, remaining * price);
                    player.sendMessage("§e" + (onParcelle ? "Inventaire plein !" : "Backpack plein !")
                            + " §f" + remaining + " remboursé(s).");
                }
                int bought = qty - remaining;
                if (bought > 0) {
                    String where = onParcelle ? "§adans ton inventaire." : "§aOuvre §e/bp §apour les récupérer.";
                    player.sendMessage("§aAcheté §f" + bought + "x §apour §6" + PrivateMines.formatNumber(bought * price) + "$ ! " + where);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
                kbInput.remove(player.getUniqueId());
                kbMaterial.remove(player.getUniqueId());
                kbPrice.remove(player.getUniqueId());
                player.closeInventory();
            }
        }
    }
}
