package fr.garfield.privatemines;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.List;

public final class PrivateMines extends JavaPlugin implements Listener {

    // ===== Réglages de la mine =====
    private static final int MINE_CENTER_X = 500;
    private static final int MINE_CENTER_Z = 500;
    private static final int MINE_FLOOR_Y  = -45; // sol de la mine (fond)
    private static final int MINE_SIZE     = 100; // 100 x 100
    private static final int MINE_HEIGHT   = 50;  // 50 de profondeur
    // Hauteur d'arrivée du joueur = juste au-dessus du bloc le plus haut de la mine.
    // Avec FLOOR=-45 et HEIGHT=50 : la surface est à Y=4, on arrive à Y=5.
    private static final int MINE_TOP_Y    = MINE_FLOOR_Y + MINE_HEIGHT; // = 5

    // L'économie (Vault). C'est notre EconomyManager maison, enregistré comme provider au démarrage.
    private Economy economy;
    // Référence typée vers l'économie maison (pour accéder aux soldes BigInteger exacts + save).
    private EconomyManager customEco;
    EconomyManager getCustomEco() { return customEco; }

    // Gestionnaire des zones protégées.
    private ZoneManager zones;

    // Gestionnaire des hologrammes (Text Display natifs).
    private HologramManager holoManager;
    HologramManager getHoloManager() { return holoManager; }

    // Gestionnaire des classements.
    private LeaderboardManager leaderboardManager;
    LeaderboardManager getLeaderboard() { return leaderboardManager; }

    // Gestionnaire du shop.
    private ShopManager shopManager;
    private IslandBankManager islandBankManager;
    private SpawnerManager spawnerManager;
    private CrateBlockListener crateBlockListener;
    public CrateBlockListener getCrateBlocks() { return crateBlockListener; }
    private IslandAdminMenu islandAdminMenu;
    public IslandAdminMenu getIslandAdmin() { return islandAdminMenu; }
    private CollectionMenu collectionMenu;
    public CollectionMenu getCollections() { return collectionMenu; }
    private OneBlockManager oneBlockManager;
    ShopManager getShop() { return shopManager; }

    // Gestionnaire des enchantements.
    private EnchantManager enchantManager;
    EnchantManager getEnchants() { return enchantManager; }

    // Gestionnaire des clés & crates (coffres à récompenses).
    private CrateManager crateManager;
    CrateManager getCrates() { return crateManager; }
    // Boussole du Log Pose : visite guidée d'Alabasta (Arc II).
    private LogPoseManager logPoseManager;
    private WallManager wallManager;
    private JobsManager jobsManager;
    private DailyManager dailyManager;
    private QuestManager questManager;
    private CommandMenu commandMenu;
    private QuestCompass questCompass;
    LogPoseManager getLogPose() { return logPoseManager; }
    public WallManager getWalls() { return wallManager; }
    public JobsManager getJobs() { return jobsManager; }
    public DailyManager getDaily() { return dailyManager; }
    public QuestManager getQuests() { return questManager; }
    public CommandMenu getCommandMenu() { return commandMenu; }
    public QuestCompass getQuestCompass() { return questCompass; }

    // Gestionnaire du backpack (/bp).
    private BackpackManager backpackManager;

    // Menu /blockvalue.
    private BlockValueMenu blockValueMenu;
    BlockValueMenu getBlockValueMenu() { return blockValueMenu; }
    // Menu /phases (12 phases du OneBlock).
    private PhasesMenu phasesMenu;
    PhasesMenu getPhasesMenu() { return phasesMenu; }
    // Marchand ambulant du OneBlock (la Caravane).
    private MerchantManager merchantManager;
    MerchantManager getMerchant() { return merchantManager; }
    private WarpMenu warpMenu;
    WarpMenu getWarpMenu() { return warpMenu; }

    // Pets (familiers) : menu /pets + fournisseur de têtes HeadDB.
    private PetMenu petMenu;
    public PetMenu getPetMenu() { return petMenu; }
    private PetHeadProvider petHeads;
    public PetHeadProvider getPetHeads() { return petHeads; }
    private PetStorage petStorage;
    public PetStorage getPetStorage() { return petStorage; }
    private PetEquipMenu petEquip;
    public PetEquipMenu getPetEquip() { return petEquip; }
    private PetForgeMenu petForge;
    public PetForgeMenu getPetForge() { return petForge; }

    // Guide BossBar (astuces pour débutants).
    private GuideManager guideManager;
    public GuideManager getGuide() { return guideManager; }

    // Acte I : cinématique d'ouverture (« L'Éveil sur la Grève des Oubliés »).
    private IntroManager introManager;
    public IntroManager getIntro() { return introManager; }

    // Acte I : logique de progression (PNJ, coffre, Jeton de Marée, déblocage /mine).
    private ActeManager acteManager;
    public ActeManager getActe() { return acteManager; }

    // Hôtel des ventes entre joueurs (/ah).
    private AuctionManager auctionManager;
    public AuctionManager getAuction() { return auctionManager; }
    private CoinflipManager coinflipManager;
    public CoinflipManager getCoinflip() { return coinflipManager; }
    // Mini-jeux de chat (calcul mental / nombre mystere) : /testgames pour forcer une partie.
    private ChatGameManager chatGameManager;
    public ChatGameManager getChatGame() { return chatGameManager; }
    private EndManager endManager;
    public EndManager getEnd() { return endManager; }

    // Acte IV : monnaie « Fragments de Souvenir » (recyclage des armures des Oubliés).
    private FragmentManager fragmentManager;
    public FragmentManager getFragments() { return fragmentManager; }

    // Acte IV : armures d'Oublié à bonus (rareté, slots, bonus portés). Fondation.
    private ArmorManager armorManager;
    public ArmorManager getArmor() { return armorManager; }
    private TutorialManager tutorial;
    public TutorialManager getTutorial() { return tutorial; }

    // Acte I : PNJ « joueur » natifs (via packets ProtocolLib, sans plugin externe).
    private NpcManager npcManager;
    public NpcManager getNpc() { return npcManager; }

    // Menu /pnj setting (édition des PNJ : rôle, nom, couleur, skin).
    private NpcSettingMenu npcSettingMenu;
    public NpcSettingMenu getNpcSettingMenu() { return npcSettingMenu; }

    // Gestionnaire des spots de particules décoratifs (/particules).
    private ParticleManager particleManager;
    public ParticleManager getParticles() { return particleManager; }

    // Bibliothèque de skins nommés (skins.yml) réutilisables sur les PNJ.
    private SkinLibrary skinLibrary;
    public SkinLibrary getSkins() { return skinLibrary; }

    // Gestionnaire des commandes.
    private CommandManager commandManager;

    // Quelle zone chaque joueur est en train d'éditer dans le GUI (pour les clics).
    private final java.util.Map<java.util.UUID, String> editingZone = new java.util.HashMap<>();
    // Joueurs en attente de taper un nouveau nom de zone dans le chat.
    private final java.util.Map<java.util.UUID, String> renamingZone = new java.util.HashMap<>();
    // Joueurs qui visualisent des zones en particules : UUID -> ENSEMBLE de zones affichées (plusieurs possibles).
    private final java.util.Map<java.util.UUID, java.util.Set<String>> visualizingZone = new java.util.HashMap<>();

    // Récupère l'ensemble des zones visualisées par un joueur (jamais null).
    private java.util.Set<String> getVisualized(java.util.UUID id) {
        return visualizingZone.computeIfAbsent(id, k -> new java.util.HashSet<>());
    }
    // Joueurs en attente de confirmer la suppression d'une zone (oui/non dans le chat).
    private final java.util.Map<java.util.UUID, String> confirmingDelete = new java.util.HashMap<>();
    // Joueurs en attente de taper le nouveau nom de LEUR Île dans le chat (renommage /ob settings).
    private final java.util.Set<java.util.UUID> renamingIsland = new java.util.HashSet<>();

    // ===== Mines : TABLE CENTRALE =====================================================
    // ⚡ POUR AJOUTER UNE MINE : ajoute UNE SEULE ligne dans MINES ci-dessous. Tout le reste
    //    (prix, %, menu /mine, achat, persistance, /blockvalue, scoreboard, classements,
    //     /resetmines) en découle automatiquement. Voir la recette mémmine].oire [recette-creation-
    //
    // Champs d'une mine :
    //   code           lettre interne unique ("A","B",… ordre = progression)
    //   icon           Material de l'icône dans le menu /mine — DOIT être UNIQUE (le clic route dessus)
    //   colorPrefix    code couleur §x (ex "§3") appliqué au nom
    //   name           nom affiché (sans couleur ni gras — ajoutés automatiquement)
    //   desc           courte description (menu /mine)
    //   cost           coût de déblocage en $ (0 = gratuite, mine de départ)
    //   coalPct        % de charbon (minerai)
    //   coalBlockPct   % de blocs de charbon (0 si la mine n'en a pas)
    //   stonePrice / coalPrice / coalBlockPrice  prix de vente de chaque bloc
    static final class MineDef {
        final String code; final Material icon; final String colorPrefix; final String name; final String desc;
        final double cost; final int coalPct; final int coalBlockPct; final int ironPct; final int abyssalPct;
        final double stonePrice; final double coalPrice; final double coalBlockPrice; final double ironPrice; final double abyssalPrice;
        // Bloc de BASE de la mine (ce qui remplace la pierre). STONE par défaut (mines A→U de l'Arc I).
        Material baseBlock = Material.STONE;
        // Géométrie PROPRE à la mine (Arc II et +). null = zone historique de l'Arc I (constantes MINE_*).
        Integer centerX = null, centerZ = null, floorY = null;
        // Point de spawn FIXE optionnel (accès physique buildé). null = calcul automatique via mineTopSpawn.
        Double spawnX = null, spawnY = null, spawnZ = null; Float spawnYaw = null;
        // Constructeur historique SANS fer : délègue avec ironPct=0, ironPrice=0 (mines A→K inchangées).
        MineDef(String code, Material icon, String colorPrefix, String name, String desc, double cost,
                int coalPct, int coalBlockPct, double stonePrice, double coalPrice, double coalBlockPrice) {
            this(code, icon, colorPrefix, name, desc, cost, coalPct, coalBlockPct, 0, stonePrice, coalPrice, coalBlockPrice, 0);
        }
        // Constructeur AVEC fer (4e minerai) : ironPct + ironPrice. Délègue avec abyssal=0 (mines L→S inchangées).
        MineDef(String code, Material icon, String colorPrefix, String name, String desc, double cost,
                int coalPct, int coalBlockPct, int ironPct,
                double stonePrice, double coalPrice, double coalBlockPrice, double ironPrice) {
            this(code, icon, colorPrefix, name, desc, cost, coalPct, coalBlockPct, ironPct, 0,
                    stonePrice, coalPrice, coalBlockPrice, ironPrice, 0);
        }
        // Constructeur COMPLET AVEC Fer des Abîmes (5e minerai) : abyssalPct + abyssalPrice en fin de liste.
        MineDef(String code, Material icon, String colorPrefix, String name, String desc, double cost,
                int coalPct, int coalBlockPct, int ironPct, int abyssalPct,
                double stonePrice, double coalPrice, double coalBlockPrice, double ironPrice, double abyssalPrice) {
            this.code = code; this.icon = icon; this.colorPrefix = colorPrefix; this.name = name; this.desc = desc;
            this.cost = cost; this.coalPct = coalPct; this.coalBlockPct = coalBlockPct; this.ironPct = ironPct; this.abyssalPct = abyssalPct;
            this.stonePrice = stonePrice; this.coalPrice = coalPrice; this.coalBlockPrice = coalBlockPrice;
            this.ironPrice = ironPrice; this.abyssalPrice = abyssalPrice;
        }
        // Change le bloc de base (ex. SANDSTONE pour l'Arc II) — chaînable dans la table MINES.
        MineDef base(Material m) { this.baseBlock = m; return this; }
        // Place la mine dans SA propre zone (centre X/Z, sol Y) — chaînable. Hauteur = MINE_HEIGHT.
        MineDef geo(int cx, int cz, int fy) { this.centerX = cx; this.centerZ = cz; this.floorY = fy; return this; }
        // Fixe le point d'apparition du joueur (accès physique buildé) — chaînable.
        MineDef spawn(double x, double y, double z, float yaw) { this.spawnX = x; this.spawnY = y; this.spawnZ = z; this.spawnYaw = yaw; return this; }
        // Raccourci ARC II (Alabasta) : applique en UN appel le bloc de base grès + la zone + le spawn
        // COMMUNS à toutes les mines de l'arc. Ainsi une mine Arc II tient en 1 ligne, comme l'Arc I.
        MineDef alabasta() {
            return base(Material.SANDSTONE).geo(-476, 818, -1).spawn(-476.5, 49, 763.5, 180f);
        }
        String displayName()  { return colorPrefix + "§l" + name; }        // gras (messages)
        String shortName()    { return colorPrefix + name; }               // sans gras (menus/scoreboard)
        String menuName()     { return colorPrefix + "§l✦ " + name; }      // tuile du menu /mine
    }

    // ⬇️⬇️⬇️  L'UNIQUE endroit à éditer pour ajouter/modifier une mine.  ⬇️⬇️⬇️
    static final java.util.List<MineDef> MINES = java.util.Arrays.asList(
        //         code  icône                          couleur nom                        description                                                            coût       pierre charb bloc
        new MineDef("A", Material.STONE,              "§a", "Port des Moussaillons", "§7Le point de départ de tout mineur.",         0,      0,   0,    5,    0,    0),   // Pierre 100 — prix: stone 5
        new MineDef("B", Material.COAL_ORE,           "§3", "Baie des Écumeurs",     "§7Une baie sombre battue par les flots.",      500,    10,  0,    5,    15,   0),   // Pierre 90 · Charbon 10 — prix: stone 5 · charbon 15
        new MineDef("C", Material.COAL_BLOCK,         "§6", "Caverne des Forbans",   "§7Une caverne gorgée de filons noirs.",        5000,   20,  0,    5,    20,   0),   // Pierre 80 · Charbon 20
        new MineDef("D", Material.RAW_COPPER_BLOCK,   "§6", "Comptoir des Agrumes",  "§7Un comptoir portuaire animé.",               15000,  30,  0,    5,    20,   0),   // Pierre 70 · Charbon 30
        new MineDef("E", Material.GOLD_ORE,           "§e", "Ruelle des Chapardeurs","§7Un dédale de ruelles où tout se monnaye.",   40000,  40,  0,    8,    25,   0),   // Pierre 60 · Charbon 40
        new MineDef("F", Material.DEEPSLATE_COAL_ORE, "§c", "Chapiteau Englouti",    "§7Un chapiteau noyé aux filons compacts.",     100000, 30,  5,    10,   25,   75),  // Pierre 65 · Charbon 30 · Bloc 5 — prix: stone 10 · charbon 25 · bloc charbon 75
        new MineDef("G", Material.DEEPSLATE_GOLD_ORE, "§2", "Falaises du Guet",      "§7De hautes falaises qui veillent sur la baie.", 175000, 23, 10,   12,   30,   80),  // Pierre 67 · Charbon 23 · Bloc 10 — prix: stone 12 · charbon 30 · bloc charbon 80
        new MineDef("H", Material.DEEPSLATE_IRON_ORE, "§7", "Manoir des Brumes",     "§7Un manoir noyé dans une brume perpétuelle.",   300000, 25, 15,   14,   35,   85),  // Pierre 60 · Charbon 25 · Bloc 15 — prix: stone 14 · charbon 35 · bloc charbon 85
        new MineDef("I", Material.DEEPSLATE_EMERALD_ORE, "§a", "Pente des Menteurs",  "§7Une pente où chaque pas ment sur sa profondeur.", 475000, 25, 20,   18,   45,   95),  // Pierre 55 · Charbon 25 · Bloc 20 — prix: stone 18 · charbon 45 · bloc charbon 95
        new MineDef("J", Material.DEEPSLATE_REDSTONE_ORE, "§e", "Cambuse Flottante",  "§7Une cambuse qui tangue au fil des marées.",       700000, 25, 25,   20,   50,   100),  // Pierre 50 · Charbon 25 · Bloc 25 — prix: stone 20 · charbon 50 · bloc charbon 100
        new MineDef("K", Material.DEEPSLATE_LAPIS_ORE, "§b", "Récif des Marmitons",  "§7Un récif où mijotent les secrets des cuisiniers.", 1000000, 22, 30,   22,   55,   105),  // Pierre 48 · Charbon 22 · Bloc 30 — prix: stone 22 · charbon 55 · bloc charbon 105
        //          code  icône                        couleur nom                    description                                             coût     charb bloc fer  pierre charb bloc fer   → 4e minerai FER (ironPct/ironPrice)
        new MineDef("L", Material.DEEPSLATE_DIAMOND_ORE, "§6", "Criée aux Épices",    "§7Un marché bruyant où s'échangent mille épices.",   1250000, 20,  27,  8,   25,    60,   115,  180),  // Pierre 45 · Charbon 20 · Bloc 27 · Fer 8 — prix: stone 25 · charbon 60 · bloc 115 · fer 180
        new MineDef("M", Material.MAGMA_BLOCK,        "§c", "Braise du Fourneau",   "§7Un fourneau rugissant où le métal chante.",        1550000, 18,  28,  12,  28,    66,   128,  205),  // Pierre 42 · Charbon 18 · Bloc 28 · Fer 12 — prix: stone 28 · charbon 66 · bloc 128 · fer 205
        new MineDef("N", Material.PRISMARINE,         "§9", "Lagune Saumâtre",      "§7Une lagune trouble où l'eau garde ses secrets.",   1900000, 16,  28,  16,  31,    72,   142,  235),  // Pierre 40 · Charbon 16 · Bloc 28 · Fer 16 — prix: stone 31 · charbon 72 · bloc 142 · fer 235
        new MineDef("O", Material.DARK_PRISMARINE,    "§7", "Village des Rançons",  "§7Un village où chaque prise a son prix.",           4140000, 14,  28,  20,  33,    75,   150,  250),  // Pierre 38 · Charbon 14 · Bloc 28 · Fer 20 — prix: stone 33 · charbon 75 · bloc 150 · fer 250
        new MineDef("P", Material.SEA_LANTERN,        "§3", "Bassin des Écailles",  "§7Un bassin où luisent mille écailles.",             5040000, 12,  28,  26,  36,    78,   155,  255),  // Pierre 34 · Charbon 12 · Bloc 28 · Fer 26 — prix: stone 36 · charbon 80 · bloc 160 · fer 268
        new MineDef("Q", Material.PRISMARINE_BRICKS,  "§b", "Tour de la Carte",     "§7Une tour d'où l'on lit toutes les mers.",          5760000, 10,  28,  31,  39,    80,   160,  260),  // Pierre 31 · Charbon 10 · Bloc 28 · Fer 31 — prix: stone 39 · charbon 86 · bloc 172 · fer 288
        new MineDef("R", Material.TUBE_CORAL_BLOCK,   "§8", "Quais du Départ",      "§7Des quais où s'embarquent tous les rêves.",        6840000, 8,   28,  32,  44,    83,   165,  265),  // Pierre 32 · Charbon 8 · Bloc 28 · Fer 32 — prix: stone 44 · charbon 92 · bloc 182 · fer 315
        new MineDef("S", Material.DEEPSLATE_TILES,    "§c", "Place de l'Échafaud",  "§7Une place où se scelle bien des destins.",         8100000, 6,   28,  36,  47,    85,  170,  270),  // Pierre 30 · Charbon 6 · Bloc 28 · Fer 36 — prix: stone 47 · charbon 102 · bloc 204 · fer 340
        //          code  icône                        couleur nom                    description                                            coût     charb bloc fer abysse pierre charb bloc fer  abysse → 5e minerai FER DES ABÎMES (abyssalPct/abyssalPrice)
        new MineDef("T", Material.GOLD_BLOCK,         "§6", "Halle aux Primes",     "§7Une halle où l'on monnaye les têtes.",             9540000, 6,   28,  36,  4,   50,    90,  175,  275,  400),  // Pierre 26 · Charbon 6 · Bloc 28 · Fer 36 · Fer Abîmes 4 — prix: stone 50 · charbon 108 · bloc 216 · fer 360 · abysse 700
        new MineDef("U", Material.BEACON,             "§5", "Passe des Adieux",     "§7La dernière passe avant le grand large.",          11700000, 6,   28,  34,  8,   53,    93,  180,  275,  425),  // Pierre 24 · Charbon 6 · Bloc 28 · Fer 34 · Fer Abîmes 8 — prix: stone 53 · charbon 114 · bloc 228 · fer 380 · abysse 780
        // ===== SAGA II — ALABASTA (mines 22+) : bloc de base = GRÈS, zone propre (Alabasta sur la map) =====
        // Toutes les mines de l'arc partagent 100% grès + la MÊME zone/spawn Alabasta via .alabasta()
        // → une mine Arc II = 1 ligne, comme l'Arc I. Icône du menu UNIQUE par mine (le clic route dessus).
        new MineDef("V", Material.SANDSTONE,          "§e", "Montagne Inversée",    "§7Une montagne qui monte vers l'abîme.",              18000000, 0, 0, 500, 0, 0).alabasta(),
        new MineDef("W", Material.CHISELED_SANDSTONE, "§b", "Canal des Vertiges",   "§7Un canal suspendu au bord du vide.",                45000000, 0, 0, 550, 0, 0).alabasta(),
        new MineDef("X", Material.CUT_SANDSTONE,      "§9", "Antre de la Baleine",  "§7Un antre où sommeille un géant des mers.",          90000000, 0, 0, 250, 0, 0).alabasta(),
        new MineDef("Y", Material.SMOOTH_SANDSTONE,   "§a", "Cité des Toasts",      "§7Une cité où l'on trinque sous le sable.",          180000000, 0, 0, 500, 0, 0).alabasta()
    );
    // ⬆️⬆️⬆️  Fin de la table des mines.  ⬆️⬆️⬆️

    // Retourne la définition d'une mine par son code (A par défaut si code inconnu).
    static MineDef mineDef(String code) {
        for (MineDef d : MINES) if (d.code.equals(code)) return d;
        return MINES.get(0);
    }
    // Rang (1..N) d'un code de mine dans l'ordre de progression.
    static int mineRankOf(String code) {
        for (int i = 0; i < MINES.size(); i++) if (MINES.get(i).code.equals(code)) return i + 1;
        return 1;
    }
    // Code de la mine précédente (celle qu'il faut avoir débloquée avant), null pour la 1re.
    static String previousMineCode(String code) {
        int r = mineRankOf(code); // 1-based
        return (r <= 1) ? null : MINES.get(r - 2).code;
    }

    // ===== GÉOMÉTRIE PAR MINE =========================================================
    // La plupart des mines (Arc I) partagent la zone historique (constantes MINE_*). Une mine
    // peut avoir SA propre zone via .geo(...) dans la table (ex. Arc II à Alabasta). Ces helpers
    // renvoient toujours la géométrie de la mine ACTIVE du joueur → aucun impact sur les mines
    // sans .geo(...), qui retombent sur les constantes globales.
    private int mineCenterX(String code) { Integer v = mineDef(code).centerX; return v != null ? v : MINE_CENTER_X; }
    private int mineCenterZ(String code) { Integer v = mineDef(code).centerZ; return v != null ? v : MINE_CENTER_Z; }
    private int mineFloorY(String code)  { Integer v = mineDef(code).floorY;  return v != null ? v : MINE_FLOOR_Y; }
    private int mineCenterX(Player p) { return mineCenterX(getPlayerMine(p)); }
    private int mineCenterZ(Player p) { return mineCenterZ(getPlayerMine(p)); }
    private int mineFloorY(Player p)  { return mineFloorY(getPlayerMine(p)); }
    // Bloc de base (remplace la pierre) de la mine active du joueur.
    private Material mineBaseBlock(Player p) { return mineDef(getPlayerMine(p)).baseBlock; }

    // Bloc de base d'une mine par son code (pour /blockvalue : afficher le vrai bloc, pas « pierre »).
    public Material getMineBaseBlock(String code) {
        MineDef d = mineDef(code);
        return (d != null && d.baseBlock != null) ? d.baseBlock : Material.STONE;
    }
    // Composition d'une mine, minerai par minerai (en %). Sert à /bv : on n'affiche que les blocs
    // que la mine contient VRAIMENT — inutile de lister le fer dans une mine 100 % grès.
    public int getMineCoalPct(String code)      { MineDef d = mineDef(code); return d == null ? 0 : d.coalPct; }
    public int getMineCoalBlockPct(String code) { MineDef d = mineDef(code); return d == null ? 0 : d.coalBlockPct; }
    public int getMineIronPct(String code)      { MineDef d = mineDef(code); return d == null ? 0 : d.ironPct; }
    public int getMineAbyssalPct(String code)   { MineDef d = mineDef(code); return d == null ? 0 : d.abyssalPct; }

    // Nom coloré du bloc de base d'une mine (pour l'affichage). Grès pour l'Arc II, Pierre sinon.
    public String getMineBaseBlockName(String code) {
        Material m = getMineBaseBlock(code);
        switch (m) {
            case SANDSTONE:      return "§e§lGrès";
            case RED_SANDSTONE:  return "§6§lGrès rouge";
            case NETHERRACK:     return "§c§lRoche du Nether";
            case BLACKSTONE:     return "§8§lPierre noire";
            case END_STONE:      return "§e§lPierre de l'End";
            default:             return "§7§lPierre";
        }
    }

    // Mine active par joueur ("A".."G", cf table MINES)
    private final java.util.Map<java.util.UUID, String> playerMine = new java.util.HashMap<>();
    // Compte à rebours (en secondes) avant le prochain reset de la mine, PAR JOUEUR.
    // (Ré)initialisé à RESET_PERIOD_SEC quand le joueur entre dans une mine (/mine) → chacun
    // a toujours ~60 s pleines avant son premier respawn, au lieu d'un reset global aligné sur
    // le démarrage du serveur (qui pouvait retomber 2 s après l'entrée).
    private final java.util.Map<java.util.UUID, Integer> secondsToReset = new java.util.HashMap<>();
    private static final int RESET_PERIOD_SEC = 60;

    // Seuil de reset (2026-08-21) : la mine ne se régénère plus à l'heure, mais quand le joueur en
    // a cassé au moins RESET_THRESHOLD_PCT %. La vérification reste faite UNE FOIS PAR SECONDE et
    // jamais à l'intérieur de creditMineBlock : une salve d'enchant casse des centaines de blocs
    // dans une seule boucle, et un broken.clear() en plein milieu casserait la boucle.
    private static final int RESET_THRESHOLD_PCT    = 20;
    private static final int MINE_VOLUME            = MINE_SIZE * MINE_SIZE * MINE_HEIGHT; // 500 000
    private static final int RESET_THRESHOLD_BLOCKS = MINE_VOLUME * RESET_THRESHOLD_PCT / 100; // 100 000
    // Lu par l'hologramme /holo set minereset : le panneau affiche TOUJOURS la vraie valeur du code.
    public static int getResetThresholdPct() { return RESET_THRESHOLD_PCT; }
    // Mines débloquées PAR JOUEUR : un seul set de codes ("B","C",…) par UUID.
    // (Remplace les 6 anciens Set unlockedMineB..G : une mine en plus ne rajoute plus de champ.)
    private final java.util.Map<java.util.UUID, java.util.Set<String>> unlockedMines = new java.util.HashMap<>();

    // Le joueur a-t-il débloqué la mine <code> ? (la mine de départ, rang 1, est toujours ouverte)
    boolean hasUnlockedMine(Player p, String code) {
        if (mineRankOf(code) <= 1) return true;
        java.util.Set<String> s = unlockedMines.get(p.getUniqueId());
        return s != null && s.contains(code);
    }
    // Débloque la mine <code> pour ce joueur.
    void unlockMine(Player p, String code) {
        unlockedMines.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>()).add(code);
    }
    // Retire toutes les mines débloquées (retour à la mine de départ).
    void clearUnlockedMines(Player p) {
        java.util.Set<String> s = unlockedMines.get(p.getUniqueId());
        if (s != null) s.clear();
    }

    // Outil admin (/mine set <n>) : débloque EXACTEMENT les mines 1..n et reverrouille au-delà.
    // n est borné à [1, nombre de mines]. Sauvegarde + met à jour le tab. Ne joue AUCUNE scène.
    // Renvoie le nombre de mines effectivement débloquées.
    public int setMineProgress(Player p, int n) {
        int max = MINES.size();
        if (n < 1) n = 1;
        if (n > max) n = max;
        clearUnlockedMines(p);
        for (int i = 0; i < n; i++) {
            String code = MINES.get(i).code;
            unlockMine(p, code);
            dataConfig.set(p.getUniqueId() + ".unlockedMine" + code, true);
        }
        // Reverrouille explicitement en config les mines au-delà de n (sinon reste "true" du dernier save).
        for (int i = n; i < max; i++) {
            dataConfig.set(p.getUniqueId() + ".unlockedMine" + MINES.get(i).code, false);
        }
        try { dataConfig.save(dataFile); } catch (java.io.IOException ignored) {}
        updateMineTag(p);
        return n;
    }

    // Nom stylé d'une mine selon son code (tout vient de la table MINES).
    private String mineDisplayName(String mine) { return mineDef(mine).displayName(); }
    // Nom court coloré d'une mine (sans gras), pour les menus/textes. Public pour BlockValueMenu.
    public String getMineDisplayNameShort(String mine) { return mineDef(mine).shortName(); }
    // % de charbon (minerai) d'une mine (0 si elle n'en a pas).
    private int mineCoalPct(String mine) { return mineDef(mine).coalPct; }
    // % de BLOCS de charbon d'une mine (0 si elle n'en a pas).
    private int mineCoalBlockPct(String mine) { return mineDef(mine).coalBlockPct; }
    // % de FER (4e minerai) d'une mine (0 si elle n'en a pas).
    private int mineIronPct(String mine) { return mineDef(mine).ironPct; }
    // % de FER DES ABÎMES (5e minerai) d'une mine (0 si elle n'en a pas).
    private int mineAbyssalPct(String mine) { return mineDef(mine).abyssalPct; }

    // Rang du joueur = numéro (dans la table MINES) de la mine la plus haute débloquée.
    int getMineRank(Player p) {
        int rank = 1;
        for (int i = 1; i < MINES.size(); i++) {           // on saute la mine de départ (rang 1)
            if (hasUnlockedMine(p, MINES.get(i).code)) rank = i + 1;
        }
        return rank;
    }

    // Met à jour le nom du joueur dans le tab : [⭐N] (M) Pseudo.
    // ⭐N = niveau de prestige (masqué si 0), (M) = rang de la meilleure mine débloquée.
    void updateMineTag(Player p) {
        int pr = getPrestige(p);
        String etoile = pr > 0 ? "§6⭐" + pr + " " : "";
        p.setPlayerListName(etoile + "§7(§e" + getMineRank(p) + "§7) §f" + p.getName());
    }
    // Rafraîchit l'affichage du prestige dans le tab (alias : le nom du tab est construit par updateMineTag).
    public void refreshPrestigeTab(Player p) { updateMineTag(p); }
    // Positions des charbons par joueur (régénérées aléatoirement à chaque reset)
    private final java.util.Map<java.util.UUID, java.util.Set<Location>> coalPositions = new java.util.HashMap<>();
    // Positions des BLOCS de charbon (3e minerai, mine F+), régénérées à chaque reset.
    private final java.util.Map<java.util.UUID, java.util.Set<Location>> coalBlockPositions = new java.util.HashMap<>();
    // Positions des minerais de FER (4e minerai, mine L+), régénérées à chaque reset.
    private final java.util.Map<java.util.UUID, java.util.Set<Location>> ironPositions = new java.util.HashMap<>();
    // Positions des minerais de FER DES ABÎMES (5e minerai, mine T+), régénérées à chaque reset.
    private final java.util.Map<java.util.UUID, java.util.Set<Location>> abyssalPositions = new java.util.HashMap<>();

    String getPlayerMine(Player p) {
        return playerMine.getOrDefault(p.getUniqueId(), "A");
    }

    java.util.Set<Location> getCoalPositions(Player p) {
        return coalPositions.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
    }
    java.util.Set<Location> getCoalBlockPositions(Player p) {
        return coalBlockPositions.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
    }
    java.util.Set<Location> getIronPositions(Player p) {
        return ironPositions.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
    }
    java.util.Set<Location> getAbyssalPositions(Player p) {
        return abyssalPositions.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
    }

    // Génère (ou régénère) les positions de charbon, blocs de charbon (F+) ET fer (L+) d'un joueur.
    private void generateCoalPositions(Player p, World world) {
        java.util.Set<Location> coals = new java.util.HashSet<>();
        java.util.Set<Location> coalBlocks = new java.util.HashSet<>();
        java.util.Set<Location> irons = new java.util.HashSet<>();
        java.util.Set<Location> abyssals = new java.util.HashSet<>();
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(p), cz = mineCenterZ(p), fy = mineFloorY(p);
        java.util.List<Location> allLocs = new java.util.ArrayList<>();
        for (int x = cx - half; x < cx + half; x++) {
            for (int z = cz - half; z < cz + half; z++) {
                for (int y = fy; y < fy + MINE_HEIGHT; y++) {
                    allLocs.add(new Location(world, x, y, z));
                }
            }
        }
        int total = allLocs.size();
        String mine = getPlayerMine(p);
        int abyssalCount   = (int) (total * mineAbyssalPct(mine) / 100.0);
        int ironCount      = (int) (total * mineIronPct(mine) / 100.0);
        int coalBlockCount = (int) (total * mineCoalBlockPct(mine) / 100.0);
        int coalCount      = (int) (total * mineCoalPct(mine) / 100.0);
        java.util.Collections.shuffle(allLocs);
        int idx = 0;
        // On pose du plus rare au plus commun (sans chevauchement) : fer des abîmes, fer, blocs de charbon, charbon.
        for (int i = 0; i < abyssalCount && idx < allLocs.size(); i++, idx++) {
            abyssals.add(allLocs.get(idx));
        }
        for (int i = 0; i < ironCount && idx < allLocs.size(); i++, idx++) {
            irons.add(allLocs.get(idx));
        }
        for (int i = 0; i < coalBlockCount && idx < allLocs.size(); i++, idx++) {
            coalBlocks.add(allLocs.get(idx));
        }
        for (int i = 0; i < coalCount && idx < allLocs.size(); i++, idx++) {
            coals.add(allLocs.get(idx));
        }
        coalPositions.put(p.getUniqueId(), coals);
        coalBlockPositions.put(p.getUniqueId(), coalBlocks);
        ironPositions.put(p.getUniqueId(), irons);
        abyssalPositions.put(p.getUniqueId(), abyssals);
    }

    private static final String MINE_MENU_TITLE = "§6§l⛏ Voyage des Mines";

    // Une mine du menu « chemin » : icône, code interne, nom, description, coût (0 = gratuite).
    private static final class MineTile {
        final Material icon; final String code; final String name; final String desc; final double cost;
        MineTile(Material icon, String code, String name, String desc, double cost) {
            this.icon = icon; this.code = code; this.name = name; this.desc = desc; this.cost = cost;
        }
    }

    // Les mines du menu, dérivées directement de la table MINES (ordre = progression).
    // Plus rien à toucher ici pour ajouter une mine : il suffit d'ajouter une ligne dans MINES.
    private java.util.List<MineTile> mineTiles() {
        java.util.List<MineTile> l = new java.util.ArrayList<>();
        for (MineDef d : MINES) l.add(new MineTile(d.icon, d.code, d.menuName(), d.desc, d.cost));
        return l;
    }

    // ===== ARCS (pages du menu /mine) =====
    // Le menu /mine est paginé : UNE page par arc, avec le nom de l'arc affiché en haut.
    // Chaque arc = un intervalle de rangs de mines [firstRank..lastRank] (1-based, inclusif).
    // Les arcs déjà nommés sont listés ici ; les rangs au-delà = arcs « à venir » (nom masqué).
    static final class ArcDef {
        final int number; final String name; final String color; final int firstRank; final int lastRank;
        ArcDef(int number, String name, String color, int firstRank, int lastRank) {
            this.number = number; this.name = name; this.color = color;
            this.firstRank = firstRank; this.lastRank = lastRank;
        }
    }
    // Arc I = toute la saga East Blue actuelle (mines 1 à 21). Les arcs suivants seront ajoutés
    // ici au fur et à mesure (nom + bornes). Tant qu'un arc n'est pas listé, sa page est « à venir ».
    static final java.util.List<ArcDef> ARCS = java.util.Arrays.asList(
        new ArcDef(1, "La Mer des Premiers Appels", "§b", 1, 21),
        // Arc II — début (mines 22+). Nom d'ambiance provisoire (à définir dans le lore).
        // lastRank monte au fur et à mesure qu'on code les mines de l'arc.
        new ArcDef(2, "Les Sables du Second Souffle", "§e", 22, 25)
    );
    // Nombre total de pages affichées dans /mine : les arcs connus + 1 page « arc à venir ».
    static int totalArcPages() { return ARCS.size() + 1; }
    // L'arc d'une page (1-based) ou null si c'est la page « à venir ».
    static ArcDef arcOfPage(int page) {
        return (page >= 1 && page <= ARCS.size()) ? ARCS.get(page - 1) : null;
    }

    // Le chemin « serpentin » d'une page : slots des mines, DANS les rangées 1 à 4 (on réserve la
    // rangée 0 pour la bannière d'arc et la rangée 5 pour la navigation). Serpentin de gauche à
    // droite : rangée 1 →, rangée 2 ←, rangée 3 →, rangée 4 ←. 28 cases (7 par rangée, cols 1-7).
    private static final int[] MINE_PATH = {
        10, 11, 12, 13, 14, 15, 16,   // rangée 1 : gauche → droite
        25, 24, 23, 22, 21, 20, 19,   // rangée 2 : droite → gauche
        28, 29, 30, 31, 32, 33, 34,   // rangée 3 : gauche → droite
        43, 42, 41, 40, 39, 38, 37    // rangée 4 : droite → gauche
    };

    // Slots de navigation (rangée 5).
    // Aller-retour /mine <-> enchantements : tête HeadDB dans le menu des enchants (slot 0),
    // pioche de retour dans le menu des mines (slot 0, sur toutes les pages d'arc).
    public static final int MINE_SHORTCUT_HEAD_ID = 2303;
    private static final int MINE_BACK_SLOT = 0;  // ✦ retour aux enchantements
    private static final int MINE_PREV_SLOT = 45; // ◀ arc précédent
    private static final int MINE_INFO_SLOT = 49; // bannière centrale (rappel page)
    private static final int MINE_NEXT_SLOT = 53; // arc suivant ▶

    // Le titre du menu = UNIQUEMENT « Arc <N> · <Nom de l'arc> » (le suffixe (page/total) sert de
    // marqueur discret pour retrouver la page au clic). Plus de préfixe « Voyage des Mines ».
    private static String mineMenuTitle(int page) {
        ArcDef arc = arcOfPage(page);
        String label = (arc != null)
                ? arc.color + "§lArc " + toRoman(arc.number) + " · " + arc.name
                : "§8§lArc " + toRoman(page) + " — à venir";
        return label + " §7(" + page + "/" + totalArcPages() + ")";
    }
    // Retrouve la page (1..N) depuis un titre de vue, ou 0 si ce n'est pas le menu des mines.
    private static int minePageFromTitle(String title) {
        if (title == null) return 0;
        for (int pg = 1; pg <= totalArcPages(); pg++) if (title.equals(mineMenuTitle(pg))) return pg;
        return 0;
    }

    void openMineMenu(Player player) {
        // Ouvre sur la page de l'arc de la mine actuelle du joueur (sinon page 1).
        openMineMenu(player, arcPageOfMine(getPlayerMine(player)));
    }

    // Numéro de page (arc) contenant une mine donnée (par son code), 1 par défaut.
    private int arcPageOfMine(String code) {
        int rank = mineRankOf(code);
        for (int i = 0; i < ARCS.size(); i++) {
            ArcDef a = ARCS.get(i);
            if (rank >= a.firstRank && rank <= a.lastRank) return i + 1;
        }
        return 1;
    }

    void openMineMenu(Player player, int page) {
        if (page < 1) page = 1;
        if (page > totalArcPages()) page = totalArcPages();
        Inventory menu = Bukkit.createInventory(null, 54, mineMenuTitle(page));
        ArcDef arc = arcOfPage(page);

        // Bordure fine haut/bas en vitres, pour cadrer la page.
        ItemStack border = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta(); bm.setDisplayName(" "); border.setItemMeta(bm);
        for (int i = 0; i < 9; i++) menu.setItem(i, border);          // rangée 0
        for (int i = 45; i < 54; i++) menu.setItem(i, border);       // rangée 5

        // Retour aux enchantements (slot 0) : le pendant de la tête /mine du menu des enchants.
        ItemStack back = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta bkm = back.getItemMeta();
        bkm.setDisplayName("§b§l✦ Enchantements");
        bkm.setLore(java.util.Arrays.asList("§7Retour au menu de la pioche.", "", "§eClic §7pour y aller"));
        bkm.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        back.setItemMeta(bkm);
        menu.setItem(MINE_BACK_SLOT, back);

        // Bannière d'arc (rangée 0, centre) : nom de l'arc ou « à venir ».
        ItemStack banner = new ItemStack(arc != null ? Material.HEART_OF_THE_SEA : Material.GRAY_DYE);
        ItemMeta bam = banner.getItemMeta();
        if (arc != null) {
            bam.setDisplayName(arc.color + "§l✦ Arc " + toRoman(arc.number) + " · " + arc.name);
            bam.setLore(java.util.Arrays.asList(
                    "§7Les mines de cet arc.",
                    "§8Tourne la page pour l'arc suivant."));
        } else {
            bam.setDisplayName("§8§l✦ Arc " + toRoman(page) + " — à venir");
            bam.setLore(java.util.Arrays.asList(
                    "§7Un nouvel arc t'attend…",
                    "§8Son nom se révélera bientôt.",
                    "",
                    "§7Termine l'arc précédent pour",
                    "§7ouvrir la voie vers ces mers."));
        }
        banner.setItemMeta(bam);
        menu.setItem(4, banner);

        if (arc != null) {
            // Mines de l'arc, posées sur le chemin serpentin (numérotées par leur rang global).
            java.util.List<MineTile> tiles = mineTiles();
            int slotIdx = 0;
            for (int rank = arc.firstRank; rank <= arc.lastRank && slotIdx < MINE_PATH.length; rank++, slotIdx++) {
                if (rank - 1 >= tiles.size()) {
                    // Rang de l'arc pas encore codé dans la table MINES → case « à venir ».
                    menu.setItem(MINE_PATH[slotIdx], soonTile());
                    continue;
                }
                MineTile t = tiles.get(rank - 1);
                boolean unlocked = isMineUnlocked(player, t.code);
                ItemStack item = new ItemStack(t.icon, Math.max(1, Math.min(64, rank)));
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(t.name);
                // Mine 22 (V) : entrée de l'Arc II gardée par le Chapeau de paille.
                boolean besoinChapeau = "V".equals(t.code);
                boolean aChapeau = !besoinChapeau || hasFoundStrawHat(player);
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(t.desc);
                lore.add("");
                if (besoinChapeau) {
                    lore.add(aChapeau
                            ? "§a✔ 🎩 Chapeau de paille en poche"
                            : "§c🔒 Requiert le §e🎩 Chapeau de paille");
                    if (!aChapeau) lore.add("§8(caché dans la §5Passe des Adieux§8, mine 21)");
                    lore.add("");
                }
                if (unlocked) {
                    lore.add(aChapeau ? "§aCliquez pour y aller !" : "§cAccès scellé sans le Chapeau.");
                } else {
                    lore.add("§cVerrouillée §7— §6" + (int) t.cost + "$§c pour débloquer");
                    lore.add(aChapeau ? "§7Cliquez pour acheter l'accès !" : "§8Trouve d'abord le Chapeau de paille.");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                menu.setItem(MINE_PATH[slotIdx], item);
            }
        } else {
            // Page « arc à venir » : cases mystère.
            ItemStack mystere = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta mm = mystere.getItemMeta();
            mm.setDisplayName("§8§l? ? ?");
            mm.setLore(java.util.Arrays.asList("§7Mine encore inexplorée…"));
            mystere.setItemMeta(mm);
            for (int slot : MINE_PATH) menu.setItem(slot, mystere);
        }

        // Navigation (rangée 5).
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            ArcDef pa = arcOfPage(page - 1);
            pm.setDisplayName("§e◀ Arc précédent");
            pm.setLore(java.util.Arrays.asList(pa != null ? pa.color + pa.name : "§8Arc à venir"));
            prev.setItemMeta(pm);
            menu.setItem(MINE_PREV_SLOT, prev);
        }
        if (page < totalArcPages()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            ArcDef na = arcOfPage(page + 1);
            nm.setDisplayName("§aArc suivant ▶");
            nm.setLore(java.util.Arrays.asList(na != null ? na.color + na.name : "§8Arc à venir §7(nom masqué)"));
            next.setItemMeta(nm);
            menu.setItem(MINE_NEXT_SLOT, next);
        }
        // Rappel central de la page.
        ItemStack info = new ItemStack(Material.COMPASS);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§l⛏ Voyage des Mines");
        im.setLore(java.util.Arrays.asList("§7Page §e" + page + "§7/§e" + totalArcPages(),
                "§8◀ ▶ pour changer d'arc"));
        info.setItemMeta(im);
        menu.setItem(MINE_INFO_SLOT, info);

        player.openInventory(menu);
    }

    // Vitre « mine à venir » (rang non encore codé dans la table MINES).
    private ItemStack soonTile() {
        ItemStack soon = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = soon.getItemMeta();
        m.setDisplayName("§8§l✦ Mine à venir");
        m.setLore(java.util.Arrays.asList("§7Encore inexplorée…", "§7Progresse pour la découvrir !"));
        soon.setItemMeta(m);
        return soon;
    }

    // Petit convertisseur en chiffres romains (pour les numéros d'arc).
    public static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        return (n >= 0 && n < r.length) ? r[n] : String.valueOf(n);
    }

    // Une mine est-elle débloquée pour ce joueur ? (A toujours dispo)
    private boolean isMineUnlocked(Player p, String code) {
        return hasUnlockedMine(p, code);
    }

    // ===== Stockage du Sac de Minage =====
    private static final double STONE_PRICE = 3.0; // prix d'un bloc de pierre

    // ===== Progression des améliorations (ultra lente, beaucoup de niveaux) =====
    private static final int    BASE_CAPACITY   = 100;    // capacité au niveau 1
    private static final double CAPACITY_GROWTH = 1.05;   // +5% de capacité par niveau (chaque up ajoute nettement plus de place)
    private static final int    BASE_SELL       = 1;      // vente/sec au niveau 1
    private static final double SELL_GROWTH      = 1.02;  // +2% de vente par niveau
    private static final double BASE_COST       = 50;     // coût du 1er upgrade
    private static final double COST_GROWTH     = 1.10;   // coût +10% par niveau, JUSQU'AU pivot ci-dessous.
    // Au-delà du niveau 50, la courbe se raidit au ratio des mines (2026-08-23). Avant ça, le sac
    // décrochait complètement : à ×1,10 un niveau de capacité coûtait 13,84 K $ quand un niveau
    // d'enchant en coûtait 3,71 M — soit 250× moins. Pour le prix d'UN enchant on montait la
    // capacité de 60 à 93 (sac ×5). Le sac était devenu le raccourci évident de toute l'économie.
    private static final int    COST_PIVOT      = 50;     // niveau à partir duquel le prix se raidit
    private static final double COST_GROWTH_LATE = 1.39;  // même ratio que les mines et les enchants
    private static final int    MAX_LEVEL       = 1000;   // limite de niveaux (vente/sec)
    // ⚠️ La CAPACITÉ est stockée dans un int : à +5%/niveau elle atteint Integer.MAX_VALUE au
    // niveau 348 (2 145 346 658 blocs au niveau 347). Au-delà, un niveau coûterait des sommes
    // astronomiques et ne donnerait STRICTEMENT RIEN. On arrête donc la vente là.
    // Si un jour la capacité passe en long, remonter cette valeur à MAX_LEVEL.
    private static final int    MAX_CAPACITY_LEVEL = 348;

    // Capacité réelle du sac d'un joueur selon son niveau de capacité.
    public int getCapacity(Player p) {
        double base = BASE_CAPACITY * Math.pow(CAPACITY_GROWTH, getCapacityLevel(p) - 1);
        // Bonus « capacité du sac » des familiers équipés + armures d'Oublié portées.
        double capPct = getWornBonus(p).capacityPct;
        if (capPct != 0) base *= (1.0 + capPct / 100.0);
        // Clamp anti-overflow : à +5%/niveau, la capacité dépasse la limite d'un int vers le niveau 430.
        if (base >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(base);
    }
    // Vente/sec réelle d'un joueur selon son niveau de vente.
    // Courbe ×2 À PARTIR DU NIVEAU 25 (2026-08-24, choix du user) : les points clés au-delà de 25
    // ont tous été doublés, les niveaux 1 à 25 sont INCHANGÉS (le début de jeu ne bouge pas).
    // niv.50=80/s, niv.100=200/s, niv.200=3400/s, niv.1000=100000/s.
    // ⚠️ Ça ne corrige PAS le fond du problème, c'est un choix assumé : la capacité monte de +5%
    // par niveau, donc un multiplicateur constant se fait rattraper. Temps pour vider un sac plein :
    // 14 s au niv.50, 63 s au niv.100, 8 min au niv.200, 3,5 h au niv.300, 9,9 h au niv.500.
    // Interpolation linéaire entre points clés jusqu'au niv.100, puis exponentielle par segment.
    public int getSellPerSec(Player p) {
        int lvl = getSellLevel(p);
        double val;
        if (lvl <= 100) {
            // Points clés jusqu'au niveau 100 : (1,1) (5,5) (10,10) (25,20) (50,80) (100,200).
            // Les 4 premiers sont d'origine ; 50 et 100 sont doublés (×2 dès 25, 2026-08-24).
            int[]    keys = {1, 5, 10, 25, 50, 100};
            double[] vals = {1, 5, 10, 20, 80, 200};
            if (lvl <= keys[0]) return (int) vals[0];
            for (int i = 1; i < keys.length; i++) {
                if (lvl <= keys[i]) {
                    double t = (double)(lvl - keys[i-1]) / (keys[i] - keys[i-1]);
                    val = vals[i-1] + t * (vals[i] - vals[i-1]);
                    return Math.max(1, (int) Math.floor(val));
                }
            }
            val = 100;
        } else {
            // Courbe « pète fort dès 100 » calée sur des POINTS EXACTS (interpolation exponentielle
            // par segment), TOUS DOUBLÉS le 2026-08-24 :
            // (100→200/s) (130→660/s) (200→3400/s) (300→17000/s) (500→60000/s) (1000→100000/s).
            // Chaque segment est composé pour toucher précisément ses deux bornes.
            // ⚠️ Le premier point DOIT rester égal au dernier de la courbe ≤100 (200), sinon marche.
            int[]    keys = {100, 130, 200, 300, 500, 1000};
            double[] vals = {200, 660, 3400, 17000, 60000, 100000};
            if (lvl >= keys[keys.length - 1]) {
                val = vals[vals.length - 1];
            } else {
                val = vals[vals.length - 1];
                for (int i = 1; i < keys.length; i++) {
                    if (lvl <= keys[i]) {
                        // facteur composé du segment : (val_haut/val_bas)^(1/largeur), puissance (lvl - key_bas)
                        double f = Math.pow(vals[i] / vals[i-1], 1.0 / (keys[i] - keys[i-1]));
                        val = vals[i-1] * Math.pow(f, lvl - keys[i-1]);
                        break;
                    }
                }
            }
        }
        return Math.max(1, (int) Math.floor(val));
    }
    // Coût pour passer du niveau actuel au suivant (capacité ET vente/sec partagent cette courbe).
    // Deux régimes, raccordés SANS marche au niveau COST_PIVOT (le niveau 50 coûte 5 336 $ dans
    // les deux formules) : ×1,10 avant, ×1,39 après. Le début de jeu ne bouge donc pas d'un dollar.
    public double getUpgradeCost(int currentLevel) {
        if (currentLevel <= COST_PIVOT) {
            return Math.floor(BASE_COST * Math.pow(COST_GROWTH, currentLevel - 1));
        }
        double auPivot = BASE_COST * Math.pow(COST_GROWTH, COST_PIVOT - 1);   // le raccord
        return Math.floor(auPivot * Math.pow(COST_GROWTH_LATE, currentLevel - COST_PIVOT));
    }

    // Formate un nombre avec suffixes : 1500 -> 1.5K, 2.3M, 4.7B, T,
    // puis au-delà de 1000T on passe aux lettres AA, AB, AC ... AZ, BA ... ZZ (chaque cran = x1000).
    // Échelle : "" K M B T (index 0..4) puis AA=index5 (1000T), AB=6, ... jusqu'à ZZ.
    public static String formatNumber(double value) {
        boolean negatif = value < 0;
        if (negatif) value = -value;
        int index = 0;
        while (value >= 1000 && index < 4 + 676) { // 4 premiers crans (K,M,B,T) + 26*26 crans de lettres
            value /= 1000;
            index++;
        }
        String suffixe = suffixeNombre(index);
        String corps;
        if (index == 0) {
            corps = String.valueOf((long) value);
        } else {
            corps = String.format(java.util.Locale.US, "%.2f%s", value, suffixe);
        }
        return negatif ? "-" + corps : corps;
    }

    // Renvoie le suffixe pour un cran donné : 0="" 1=K 2=M 3=B 4=T, puis 5=AA 6=AB ... 30=AZ 31=BA ... jusqu'à ZZ.
    private static String suffixeNombre(int index) {
        switch (index) {
            case 0: return "";
            case 1: return "K";
            case 2: return "M";
            case 3: return "B";
            case 4: return "T";
            default:
                int n = index - 5; // 0 -> AA, 1 -> AB, ... 25 -> AZ, 26 -> BA ...
                char premiere = (char) ('A' + (n / 26));
                char seconde = (char) ('A' + (n % 26));
                return "" + premiere + seconde;
        }
    }

    // Version EXACTE du formatage pour les très gros soldes (BigInteger, unités entières = dollars).
    // Identique à formatNumber mais sans perte de précision (le double plafonne vers ~9Qa).
    public static String formatNumberBig(java.math.BigInteger value) {
        if (value.signum() == 0) return "0";
        boolean negatif = value.signum() < 0;
        java.math.BigInteger v = value.abs();
        java.math.BigInteger mille = java.math.BigInteger.valueOf(1000);
        int index = 0;
        // On descend par paliers de 1000 tant qu'on a au moins 4 chiffres (>= 1000).
        java.math.BigInteger reste = java.math.BigInteger.ZERO;
        while (v.compareTo(mille) >= 0 && index < 4 + 676) {
            reste = v.mod(mille);        // les 3 chiffres du cran précédent (pour les 2 décimales)
            v = v.divide(mille);
            index++;
        }
        String corps;
        if (index == 0) {
            corps = v.toString();
        } else {
            // 2 décimales = reste/1000 arrondi à 2 chiffres.
            long decim = Math.round(reste.doubleValue() / 1000.0 * 100.0);
            long entier = v.longValue();
            if (decim >= 100) { entier += 1; decim -= 100; } // report d'arrondi
            corps = String.format(java.util.Locale.US, "%d.%02d%s", entier, decim, suffixeNombre(index));
        }
        return negatif ? "-" + corps : corps;
    }

    // Affiche le solde EXACT d'un joueur (BigInteger) en notation courte, quelle que soit l'échelle.
    public String formatBig(Player player) {
        if (customEco == null) return formatNumber(economy != null ? economy.getBalance(player) : 0) + "$";
        return formatNumberBig(customEco.getBalanceBig(player.getUniqueId())) + "$";
    }

    // Formate un montant d'argent avec centimes, à la française : 147,40 · 1 234 567,89
    public static String formatMoney(double value) {
        return String.format(java.util.Locale.FRANCE, "%,.2f", value);
    }

    // Stock par joueur : combien de blocs AU TOTAL chaque joueur a stocké (pierre + charbon).
    private final java.util.Map<java.util.UUID, Integer> stock = new java.util.HashMap<>();

    // Parmi le stock total, combien sont du CHARBON (le reste est de la pierre).
    // Permet de vendre chaque type à SON vrai prix (pas de moyenne).
    private final java.util.Map<java.util.UUID, Integer> coalStock = new java.util.HashMap<>();
    int getCoalStock(Player p) { return coalStock.getOrDefault(p.getUniqueId(), 0); }
    void setCoalStock(Player p, int v) { coalStock.put(p.getUniqueId(), Math.max(0, v)); }
    // Valeur monétaire cumulée du CHARBON stocké (pour le vendre à son vrai prix, mine B/C mélangées).
    private final java.util.Map<java.util.UUID, Double> coalValue = new java.util.HashMap<>();
    double getCoalValue(Player p) { return coalValue.getOrDefault(p.getUniqueId(), 0.0); }
    void setCoalValue(Player p, double v) { coalValue.put(p.getUniqueId(), Math.max(0.0, v)); }
    private void addCoalValue(Player p, double v) { coalValue.merge(p.getUniqueId(), v, Double::sum); }

    // Niveaux d'amélioration par joueur (pour les futurs upgrades du Sac).
    private final java.util.Map<java.util.UUID, Integer> capacityLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> sellLevel = new java.util.HashMap<>();

    // Total de blocs minés (depuis toujours) par joueur, pour le classement.
    private final java.util.Map<java.util.UUID, Long> totalMined = new java.util.HashMap<>();
    public long getTotalMined(Player p) { return totalMined.getOrDefault(p.getUniqueId(), 0L); }
    private void addTotalMined(Player p) { totalMined.merge(p.getUniqueId(), 1L, Long::sum); }
    // Nature du DERNIER bloc passé dans creditMineBlock : true = minerai, false = roche de base.
    // Lu juste après l'appel, sur le même tick et le même thread (tout passe par le main thread).
    private boolean lastMinedWasOre = false;

    // Blocs cassés PAR ENCHANT de zone (depuis toujours), par joueur puis par clé d'enchant.
    // Clés : "explosion","forage","colonne","reflux","gouffre","harpon","cyclone","fracture","vein".
    // Ordre + libellé d'affichage dans /stats.
    static final String[][] ENCHANT_BLOCK_KEYS = {
            {"explosion", "Explosion"}, {"forage", "Forage"}, {"colonne", "Colonne d'Écume"},
            {"reflux", "Reflux"}, {"gouffre", "Gouffre"}, {"harpon", "Pluie de Harpons"},
            {"cyclone", "Œil du Cyclone"}, {"fracture", "Fracture"}, {"vein", "Vein Miner"},
            {"fleche", "Pluie de Flèches"}, {"tnt", "Pluie de TNT"}
    };
    private final java.util.Map<java.util.UUID, java.util.Map<String, Long>> enchantBlocks = new java.util.HashMap<>();
    public long getEnchantBlocks(Player p, String key) {
        java.util.Map<String, Long> m = enchantBlocks.get(p.getUniqueId());
        return (m == null) ? 0L : m.getOrDefault(key, 0L);
    }
    private void addEnchantBlocks(Player p, String key, int n) {
        if (n <= 0) return;
        enchantBlocks.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashMap<>()).merge(key, (long) n, Long::sum);
    }

    // Stats de BONUS (compteurs à vie) : blocs doublés + argent bonus par source.
    //   "dblBlocks"   = nb de blocs doublés par le double-bloc
    //   "dimeBlocks"  = nb de blocs payés en « paie » par la Dîme
    // (les *Money sont des montants cumulés d'argent gagné EN PLUS grâce au bonus)
    private final java.util.Map<java.util.UUID, java.util.Map<String, Long>>   bonusCounts = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Map<String, Double>> bonusMoney  = new java.util.HashMap<>();
    public long   getBonusCount(Player p, String key) {
        java.util.Map<String, Long> m = bonusCounts.get(p.getUniqueId());
        return (m == null) ? 0L : m.getOrDefault(key, 0L);
    }
    public double getBonusMoney(Player p, String key) {
        java.util.Map<String, Double> m = bonusMoney.get(p.getUniqueId());
        return (m == null) ? 0.0 : m.getOrDefault(key, 0.0);
    }
    private void addBonusCount(java.util.UUID id, String key, long n) {
        if (n == 0) return;
        bonusCounts.computeIfAbsent(id, k -> new java.util.HashMap<>()).merge(key, n, Long::sum);
    }
    private void addBonusMoney(java.util.UUID id, String key, double v) {
        if (v <= 0) return;
        bonusMoney.computeIfAbsent(id, k -> new java.util.HashMap<>()).merge(key, v, Double::sum);
    }

    // ===== Suivi PAR TYPE DE BLOC (pour /bv) =====
    //
    // Objectif : répondre à « ce bloc, il m'a rapporté combien ? ». Le sac ne distingue que
    // charbon/pierre, donc on ne peut pas lire la réponse à la vente. On suit donc :
    //   - blocksMined  : combien de ce type le joueur a cassé (compté à la casse)
    //   - blocksPending: la valeur créditée au sac, PAS ENCORE vendue (file d'attente)
    //   - blocksEarned : l'argent RÉELLEMENT encaissé (rempli à la vente, au prorata)
    //   - blocksBestMin: record de gain sur une minute glissante
    // Clés : "stone", "coal", "coalblock", "iron", "abyssal".
    static final String BK_STONE = "stone", BK_COAL = "coal", BK_COALBLOCK = "coalblock",
                        BK_IRON  = "iron",  BK_ABYSSAL = "abyssal";
    static final String[] BLOCK_KEYS = { BK_STONE, BK_COAL, BK_COALBLOCK, BK_IRON, BK_ABYSSAL };

    private final java.util.Map<java.util.UUID, java.util.Map<String, Long>>   blocksMined   = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Map<String, Double>> blocksPending = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Map<String, Double>> blocksEarned  = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Map<String, Double>> blocksBestMin = new java.util.HashMap<>();
    // Gains de la minute en cours, par bloc (remis à zéro chaque minute par startBlockMinuteTask).
    private final java.util.Map<java.util.UUID, java.util.Map<String, Double>> blocksThisMin = new java.util.HashMap<>();

    public long getBlocksMined(Player p, String key) {
        java.util.Map<String, Long> m = blocksMined.get(p.getUniqueId());
        return (m == null) ? 0L : m.getOrDefault(key, 0L);
    }
    public double getBlocksEarned(Player p, String key) {
        java.util.Map<String, Double> m = blocksEarned.get(p.getUniqueId());
        return (m == null) ? 0.0 : m.getOrDefault(key, 0.0);
    }
    public double getBlocksBestMin(Player p, String key) {
        java.util.Map<String, Double> m = blocksBestMin.get(p.getUniqueId());
        return (m == null) ? 0.0 : m.getOrDefault(key, 0.0);
    }
    /** Total encaissé toutes catégories confondues (pour calculer la part de chaque bloc). */
    public double getBlocksEarnedTotal(Player p) {
        java.util.Map<String, Double> m = blocksEarned.get(p.getUniqueId());
        if (m == null) return 0.0;
        double t = 0.0;
        for (double v : m.values()) t += v;
        return t;
    }

    /**
     * /resetbv : efface TOUTES les stats par bloc d'un joueur (minés, encaissés, record sur 1 min,
     * et la file de valeur pas encore vendue).
     * ⚠ savePlayer n'écrit une valeur que si elle est > 0 : sans effacer aussi la section YAML,
     * les anciens chiffres reviendraient au prochain redémarrage.
     */
    public void resetBlockStats(java.util.UUID id) {
        blocksMined.remove(id);
        blocksPending.remove(id);
        blocksEarned.remove(id);
        blocksBestMin.remove(id);
        blocksThisMin.remove(id);
        dataConfig.set(id + ".blockStats", null);
    }

    /** À la CASSE : mémorise le bloc et met sa valeur en attente de vente. */
    private void addBlockMined(java.util.UUID id, String key, int n, double valeur) {
        if (n <= 0) return;
        blocksMined.computeIfAbsent(id, k -> new java.util.HashMap<>()).merge(key, (long) n, Long::sum);
        if (valeur > 0) blocksPending.computeIfAbsent(id, k -> new java.util.HashMap<>()).merge(key, valeur, Double::sum);
    }

    /**
     * À la VENTE : répartit l'argent réellement encaissé entre les types de blocs, au prorata
     * de ce qui attend en file. On ne peut pas savoir quel bloc précis part dans la vente
     * (le sac les fond ensemble), mais le prorata donne le bon total à long terme.
     */
    private void creditBlocksEarned(java.util.UUID id, double pay) {
        if (pay <= 0) return;
        java.util.Map<String, Double> pending = blocksPending.get(id);
        if (pending == null || pending.isEmpty()) return;
        double totalPending = 0.0;
        for (double v : pending.values()) totalPending += v;
        if (totalPending <= 0) return;

        java.util.Map<String, Double> earned = blocksEarned.computeIfAbsent(id, k -> new java.util.HashMap<>());
        java.util.Map<String, Double> minute = blocksThisMin.computeIfAbsent(id, k -> new java.util.HashMap<>());
        for (java.util.Map.Entry<String, Double> e : new java.util.ArrayList<>(pending.entrySet())) {
            double part = e.getValue() / totalPending;   // poids de ce bloc dans la file
            double gain = pay * part;
            if (gain <= 0) continue;
            earned.merge(e.getKey(), gain, Double::sum);
            minute.merge(e.getKey(), gain, Double::sum);
        }
        // On retire de la file la valeur qui vient d'être payée (proportionnellement).
        // La vente paie `pay` mais la file est en valeur de base : on vide au prorata du versement.
        double consomme = Math.min(1.0, pay / totalPending);
        for (java.util.Map.Entry<String, Double> e : new java.util.ArrayList<>(pending.entrySet())) {
            double reste = e.getValue() * (1.0 - consomme);
            if (reste <= 0.000001) pending.remove(e.getKey());
            else pending.put(e.getKey(), reste);
        }
    }

    /** Chaque minute : fige le record par bloc et repart à zéro. */
    private void startBlockMinuteTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (java.util.Map.Entry<java.util.UUID, java.util.Map<String, Double>> e : blocksThisMin.entrySet()) {
                java.util.Map<String, Double> best = blocksBestMin.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>());
                for (java.util.Map.Entry<String, Double> b : e.getValue().entrySet()) {
                    if (b.getValue() > best.getOrDefault(b.getKey(), 0.0)) best.put(b.getKey(), b.getValue());
                }
            }
            blocksThisMin.clear();
        }, 1200L, 1200L); // toutes les 60 s
    }

    // Compteur de blocs depuis la dernière « paie » de la Dîme du Passeur (persistant, anti-abus déco/reco).
    private final java.util.Map<java.util.UUID, Integer> dimeCounter = new java.util.HashMap<>();
    public int getDimeCounter(Player p) { return dimeCounter.getOrDefault(p.getUniqueId(), 0); }
    public void setDimeCounter(java.util.UUID id, int v) { dimeCounter.put(id, v); }

    // ===== Boost VENTE ×N temporaire (récompense de crate) =====
    // Multiplie le prix de vente des blocs minés pendant une durée. NON persisté (temporaire,
    // ~5 min) : s'il saute à un redémarrage, c'est acceptable. En mémoire : mult + timestamp de fin.
    private final java.util.Map<java.util.UUID, Double> sellBoostMult = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long>   sellBoostEnd  = new java.util.HashMap<>();

    /** Applique un boost de vente ×mult pendant `durationMs` millisecondes. Un nouveau boost remplace l'ancien. */
    public void applySellBoost(Player p, double mult, long durationMs) {
        java.util.UUID id = p.getUniqueId();
        sellBoostMult.put(id, mult);
        sellBoostEnd.put(id, System.currentTimeMillis() + durationMs);
    }

    /** Multiplicateur de vente actif (>1.0) ou 1.0 si aucun boost / expiré. Nettoie l'entrée expirée. */
    public double getSellBoostMult(java.util.UUID id) {
        Long end = sellBoostEnd.get(id);
        if (end == null) return 1.0;
        if (System.currentTimeMillis() >= end) {
            sellBoostEnd.remove(id);
            sellBoostMult.remove(id);
            return 1.0;
        }
        return sellBoostMult.getOrDefault(id, 1.0);
    }

    /** Secondes restantes du boost de vente (0 si aucun). */
    public long getSellBoostSecondsLeft(java.util.UUID id) {
        Long end = sellBoostEnd.get(id);
        if (end == null) return 0;
        long left = (end - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    // Vol mémorisé : le joueur a demandé le Vol → on le lui rend automatiquement en mine (reco/entrée),
    // sans qu'il ait à retaper /fly. Persistant. Le vol EFFECTIF reste coupé hors de la zone de mine.
    private final java.util.Set<java.util.UUID> flyWanted = new java.util.HashSet<>();
    public boolean wantsFly(java.util.UUID id) { return flyWanted.contains(id); }
    public void setWantsFly(java.util.UUID id, boolean on) { if (on) flyWanted.add(id); else flyWanted.remove(id); }

    // ===== Niveau de PIOCHE (barre d'XP native) — cosmétique/prestige, monte en minant. =====
    // pickaxeLevel = niveau atteint (départ 1). pickaxeXp = blocs minés DANS le niveau courant.
    private final java.util.Map<java.util.UUID, Integer> pickaxeLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> pickaxeXp = new java.util.HashMap<>();
    public int getPickaxeLevel(Player p) { return pickaxeLevel.getOrDefault(p.getUniqueId(), 1); }
    public int getPickaxeXp(Player p) { return pickaxeXp.getOrDefault(p.getUniqueId(), 0); }
    public void setPickaxeLevel(java.util.UUID id, int v) { pickaxeLevel.put(id, Math.max(1, v)); }
    public void setPickaxeXp(java.util.UUID id, int v) { pickaxeXp.put(id, Math.max(0, v)); }

    // ===== PRESTIGE / Renaissance des Abysses =====
    // prestige = nombre de renaissances (départ 0). Chaque prestige = +50 % de vente (cumulatif, infini).
    // Voir « Serv prison/PRESTIGE_RENAISSANCE.md ». Persisté dans playerdata.yml sous « <uuid>.prestige ».
    private final java.util.Map<java.util.UUID, Integer> prestige = new java.util.HashMap<>();
    public int getPrestige(Player p) { return prestige.getOrDefault(p.getUniqueId(), 0); }
    public void setPrestige(java.util.UUID id, int v) { prestige.put(id, Math.max(0, v)); }
    // Bonus de vente donné par le prestige, en % (formule : 50 % par prestige, sans plafond).
    public int getPrestigeSellPercent(Player p) { return getPrestige(p) * 50; }

    // Niveau de pioche requis pour renaître (au fond du Puits des Souvenirs).
    public static final int PRESTIGE_REQ_PICKAXE = 100;
    // Zone de renaissance au fond du Puits : boîte englobante (coins inclusifs). Marcher dedans déclenche.
    private static final String PRESTIGE_ZONE_WORLD = "world";
    private static final int PRESTIGE_ZONE_X1 = 620, PRESTIGE_ZONE_X2 = 622;
    private static final int PRESTIGE_ZONE_Y1 = -44, PRESTIGE_ZONE_Y2 = -44;
    private static final int PRESTIGE_ZONE_Z1 = 556, PRESTIGE_ZONE_Z2 = 558;
    // Joueurs en attente de CONFIRMATION de renaissance (2e entrée dans la zone dans les 15 s).
    private final java.util.Map<java.util.UUID, Long> renaissanceConfirm = new java.util.HashMap<>();
    // Joueurs actuellement DANS la zone (anti-spam : on ne redéclenche pas à chaque tick tant qu'on reste dedans).
    private final java.util.Set<java.util.UUID> dansZonePrestige = new java.util.HashSet<>();

    // Le joueur est-il éligible à la renaissance ? = pioche 100 ET Arc II terminé (Boussole finie).
    public boolean peutRenaitre(Player p) {
        return getPickaxeLevel(p) >= PRESTIGE_REQ_PICKAXE && aFiniArcII(p);
    }
    // Le joueur a-t-il fini les quêtes de l'Arc II ? = visite d'Alabasta (Boussole du Log Pose) terminée.
    public boolean aFiniArcII(Player p) {
        return logPoseManager != null && logPoseManager.aTermineVisite(p);
    }
    // La position est-elle dans la zone de renaissance du Puits ?
    private boolean estDansZonePrestige(Location loc) {
        if (loc.getWorld() == null || !PRESTIGE_ZONE_WORLD.equals(loc.getWorld().getName())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= PRESTIGE_ZONE_X1 && x <= PRESTIGE_ZONE_X2
            && y >= PRESTIGE_ZONE_Y1 && y <= PRESTIGE_ZONE_Y2
            && z >= PRESTIGE_ZONE_Z1 && z <= PRESTIGE_ZONE_Z2;
    }

    // Appelé (tâche de mouvement) quand un joueur est DANS la zone de renaissance au fond du Puits.
    // ⚠️ Gating Arc II : si le joueur n'a pas fini l'Arc II, on ne dit RIEN sur le prestige (return muet).
    public void tenterRenaissance(Player p) {
        // Pas fini l'Arc II → le prestige n'existe pas encore pour lui : silence total.
        if (!aFiniArcII(p)) return;
        int niv = getPickaxeLevel(p);
        if (niv < PRESTIGE_REQ_PICKAXE) {
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§5§l✦ LE PORTAIL §7reste éteint.");
            p.sendMessage("§7Il ne s'allume que pour les mineurs à la §dmaîtrise totale§7.");
            p.sendMessage("§7Pioche : §e" + niv + "§7/§d" + PRESTIGE_REQ_PICKAXE + " §8— reviens quand ta pioche sera au bout.");
            p.sendMessage("§8§m                                        ");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 0.6f);
            return;
        }
        long now = System.currentTimeMillis();
        Long deadline = renaissanceConfirm.get(p.getUniqueId());
        if (deadline == null || now > deadline) {
            // 1re entrée : on demande confirmation (fenêtre de 15 s).
            renaissanceConfirm.put(p.getUniqueId(), now + 15_000L);
            int apres = getPrestige(p) + 1;
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§5§l✦ LE PORTAIL S'ALLUME §7— tu es prêt à §5renaître§7.");
            p.sendMessage("§7En franchissant le Portail, tu vas §cTOUT OUBLIER§7 :");
            p.sendMessage("   §c• ton niveau de pioche §7(retour au §f1§7)");
            p.sendMessage("   §c• tous tes enchants de pioche");
            p.sendMessage("   §c• tout ton argent §7(retour à §f0 $§7)");
            p.sendMessage("§7Mais tu gagneras à jamais le §6sceau des Renés §8⭐" + apres
                    + " §7(§a+50 %§7 de vente en plus).");
            p.sendMessage("§7Tes mines, tes familiers et tes armures restent §aintacts§7.");
            p.sendMessage("§e➥ §lRESSORS puis reviens dans le Portail §epour confirmer ta renaissance.");
            p.sendMessage("§8§m                                        ");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
            return;
        }
        // 2e entrée dans les temps : on renaît.
        renaissanceConfirm.remove(p.getUniqueId());
        renaitre(p);
    }

    // Effectue la renaissance : reset pioche + enchants + argent, +1 prestige, effets. Voir PRESTIGE_RENAISSANCE.md.
    public void renaitre(Player p) {
        // Reset pioche (niveau 1, XP 0) + enchants.
        setPickaxeLevel(p.getUniqueId(), 1);
        setPickaxeXp(p.getUniqueId(), 0);
        updatePickaxeBar(p);
        if (enchantManager != null) enchantManager.resetAll(p.getUniqueId());
        applyPickaxeEnchants(p);
        // Reset de l'ARGENT : le solde repart à 0 (on repart de rien, mais on vend +50 % plus cher).
        if (customEco != null) customEco.setBalanceBig(p.getUniqueId(), java.math.BigInteger.ZERO);
        // +1 prestige.
        int nouveau = getPrestige(p) + 1;
        setPrestige(p.getUniqueId(), nouveau);
        refreshPrestigeTab(p);
        savePlayer(p);
        // Effets prestige (title + broadcast + son + particules totem, comme le Chapeau de paille).
        p.sendTitle("§6§l⭐ RENÉ " + toRoman(nouveau), "§7Les Abysses t'ont reconnu — §a+50 % §7de vente", 15, 80, 25);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
        try { p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 120, 0.6, 1.0, 0.6, 0.2); } catch (Throwable ignored) {}
        Bukkit.broadcastMessage("§6§l⭐ §e" + p.getName() + " §7a franchi le Portail et renaît §6René " + toRoman(nouveau)
                + " §7(§a+" + (nouveau * 50) + " %§7 de vente) !");
        // Lore par palier (brique F, si défini) — délégué à ActeManager pour rester extensible.
        if (acteManager != null) acteManager.raconterRenaissance(p, nouveau);
    }

    // Annonce (titre + chat + son) que le joueur peut désormais renaître : quand il atteint pioche 100.
    private void annoncerPrestigeDisponible(Player p) {
        p.sendTitle("§5§l✦ LE PORTAIL S'EST OUVERT", "§7Ta pioche a atteint sa maîtrise totale", 15, 90, 25);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§5§l✦ LA RENAISSANCE T'EST OUVERTE");
            p.sendMessage("§7Ta pioche est au §dmaximum§7. Au fond du §5vieux Puits des Souvenirs§7 §8(à la mine de l'Arc 1)§7,");
            p.sendMessage("§7le §5Portail§7 s'est allumé. §fEntre dedans§7 pour §5renaître§7 :");
            p.sendMessage("   §7tu §crepars pioche 1, enchants et argent à 0§7, mais gagnes §6⭐ +50 % de vente §7à jamais.");
            p.sendMessage("§8Tape §7/prestige §8pour tout revoir.");
            p.sendMessage("§8§m                                        ");
        }, 40L);
    }

    // Fixe le niveau de pioche d'un joueur (commande OP /pickaxelevel set) : remet l'XP à 0,
    // rafraîchit la barre, et déclenche l'indice de la Plume si on atteint/dépasse le niveau 45.
    public void setPickaxeLevelPublic(Player p, int level) {
        int lvl = Math.max(1, level);
        pickaxeLevel.put(p.getUniqueId(), lvl);
        pickaxeXp.put(p.getUniqueId(), 0);
        updatePickaxeBar(p);
        if (lvl >= 45 && acteManager != null) acteManager.triggerPlumeHint(p);
        if (lvl >= 50 && acteManager != null) acteManager.triggerOrdinateurQuantique(p);
    }

    // Blocs nécessaires pour passer du niveau N au niveau N+1. Courbe en zones, CONTINUE
    // (chaque zone repart de la valeur exacte de la fin de la précédente, aucun saut, toujours
    // croissante) et adoucie par tranches pour rendre l'up end-game plus jouable :
    //   • niveaux 1..30   : ×1,10 par niveau (INCHANGÉ, 100 au niv.1).
    //   • niveaux 31..34  : ×1,06 par niveau.
    //   • niveaux 35..39  : ×1,03 par niveau.
    //   • niveaux 40..44  : ×1,01 par niveau (montée quasi linéaire, bien plus rapide).
    //   • niveaux 45..50  : ×1,00 par niveau (PALIER PLAT : chaque niveau coûte pareil qu'au 45).
    //   • niveaux 51 et + : ×1,03 par niveau (on reprend la douceur d'avant).
    private static final int    XP_L1  = 30;   // fin zone ×1,10
    private static final int    XP_L2  = 34;   // fin zone ×1,06
    private static final int    XP_L3  = 39;   // fin zone ×1,03 (le ×1,01 démarre au 40)
    private static final int    XP_L4  = 44;   // fin zone ×1,01 (le palier plat démarre au 45)
    private static final int    XP_L5  = 50;   // fin zone ×1,00 (au-delà on reprend ×1,03)
    private static final double XP_R1  = 1.10; // zone 1
    private static final double XP_R2  = 1.06; // zone 2
    private static final double XP_R3  = 1.03; // zone 3
    private static final double XP_R4  = 1.01; // zone 4 (40..44)
    private static final double XP_R5  = 1.00; // zone 5 (45..50, plat)
    private static final double XP_R6  = 1.03; // zone 6 (>=51)
    public int pickaxeXpNeeded(int level) {
        if (level <= XP_L1) {
            return (int) Math.floor(100.0 * Math.pow(XP_R1, level - 1));
        }
        double base1 = 100.0 * Math.pow(XP_R1, XP_L1 - 1);            // valeur au niv.30
        if (level <= XP_L2) {
            return (int) Math.floor(base1 * Math.pow(XP_R2, level - XP_L1));
        }
        double base2 = base1 * Math.pow(XP_R2, XP_L2 - XP_L1);       // valeur au niv.34
        if (level <= XP_L3) {
            return (int) Math.floor(base2 * Math.pow(XP_R3, level - XP_L2));
        }
        double base3 = base2 * Math.pow(XP_R3, XP_L3 - XP_L2);       // valeur au niv.39
        if (level <= XP_L4) {
            return (int) Math.floor(base3 * Math.pow(XP_R4, level - XP_L3));
        }
        double base4 = base3 * Math.pow(XP_R4, XP_L4 - XP_L3);       // valeur au niv.44
        if (level <= XP_L5) {
            return (int) Math.floor(base4 * Math.pow(XP_R5, level - XP_L4)); // plat : constant 45..50
        }
        double base5 = base4 * Math.pow(XP_R5, XP_L5 - XP_L4);       // valeur au niv.50
        return (int) Math.floor(base5 * Math.pow(XP_R6, level - XP_L5));
    }

    // Ajoute 1 bloc à l'XP de pioche du joueur ; gère les montées de niveau (peut en enchaîner plusieurs).
    // Reste fractionnaire d'XP de pioche (Mémoire de la Roche donne un gain non entier par bloc,
    // ex. ×1,37 → on accumule les 0,37 jusqu'à débloquer un point d'XP entier).
    private final java.util.Map<java.util.UUID, Double> pickaxeXpCarry = new java.util.HashMap<>();

    private void addPickaxeXp(Player player) {
        java.util.UUID id = player.getUniqueId();
        // Gain de base = 1 bloc, multiplié par Mémoire de la Roche (1.0 sans enchant).
        double mult = (enchantManager != null) ? enchantManager.memoireXpMult(enchantManager.getMemoireLevel(player)) : 1.0;
        // Bonus « +% XP de pioche » des armures d'Oublié portées.
        double xpPct = getArmorBonus(player).pickaxeXpPct;
        if (xpPct != 0) mult *= (1.0 + xpPct / 100.0);
        double gainD = mult + pickaxeXpCarry.getOrDefault(id, 0.0);
        int gain = (int) Math.floor(gainD);
        pickaxeXpCarry.put(id, gainD - gain); // on garde la partie fractionnaire pour le prochain bloc
        if (gain <= 0) { updatePickaxeBar(player); return; }
        int level = getPickaxeLevel(player);
        int niveauAvant = level;
        int xp = getPickaxeXp(player) + gain;
        boolean leveled = false;
        int needed = pickaxeXpNeeded(level);
        while (xp >= needed) {
            xp -= needed;
            level++;
            leveled = true;
            needed = pickaxeXpNeeded(level);
        }
        pickaxeLevel.put(id, level);
        pickaxeXp.put(id, xp);
        if (leveled) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§b⛏ §7Pioche niveau §b§l" + level + " §7!"));
            // Niveau 45 : cinématique/indice « va parler au Contremaître » (histoire de la Plume).
            if (level >= 45 && acteManager != null) acteManager.triggerPlumeHint(player);
            // Niveau 50 : remet l'Ordinateur Quantique (Acte IV) via une cinématique (idempotent).
            if (level >= 50 && acteManager != null) acteManager.triggerOrdinateurQuantique(player);
            // Niveau 100 atteint pour la 1re fois : annonce le Prestige (SEULEMENT si l'Arc II est fini).
            if (niveauAvant < PRESTIGE_REQ_PICKAXE && level >= PRESTIGE_REQ_PICKAXE && aFiniArcII(player)) {
                annoncerPrestigeDisponible(player);
            }
        }
        updatePickaxeBar(player);
    }

    // Reflète le niveau + progression de la pioche dans la barre d'XP native (verte) du joueur.
    public void updatePickaxeBar(Player player) {
        int level = getPickaxeLevel(player);
        int needed = pickaxeXpNeeded(level);
        float progress = needed <= 0 ? 0f : Math.min(1f, getPickaxeXp(player) / (float) needed);
        player.setLevel(level);
        player.setExp(progress);
    }

    // Valeur monétaire accumulée dans le sac (somme des prix des blocs stockés, vendue par le sac).
    private final java.util.Map<java.util.UUID, Double> bagValue = new java.util.HashMap<>();
    public double getBagValue(Player p) { return bagValue.getOrDefault(p.getUniqueId(), 0.0); }
    private void addBagValue(Player p, double v) { bagValue.merge(p.getUniqueId(), v, Double::sum); }
    void setBagValue(Player p, double v) { bagValue.put(p.getUniqueId(), v); }

    public int getCapacityLevel(Player p) { return capacityLevel.getOrDefault(p.getUniqueId(), 1); }
    public int getSellLevel(Player p) { return sellLevel.getOrDefault(p.getUniqueId(), 1); }

    // Bonus d'argent (sur les ventes) : +0,05 % par niveau, illimité.
    private final java.util.Map<java.util.UUID, Integer> moneyMultLevel = new java.util.HashMap<>();
    public int getMoneyMultLevel(Player p) { return moneyMultLevel.getOrDefault(p.getUniqueId(), 0); }
    // Bonus en POURCENT au niveau N : 0,05 % x N (niv.1 = 0,05 %, niv.5 = 0,25 %...).
    public double getMoneyBonusPercent(Player p) { return 0.05 * getMoneyMultLevel(p); }
    public double moneyBonusPercentFor(int level) { return 0.05 * level; }
    // Multiplicateur appliqué à la vente : 1 + bonus%/100.
    public double getMoneyMult(Player p) { return 1.0 + getMoneyBonusPercent(p) / 100.0; }

    // ===== Agrégation de TOUS les bonus (pour le HUD et /stats) =====
    // Bonus des familiers équipés (null-safe : petEquip peut être null au tout début).
    public PetEquipMenu.Bonus getPetBonus(Player p) {
        return (petEquip != null) ? petEquip.aggregate(p) : new PetEquipMenu.Bonus();
    }

    // Bonus des armures d'Oublié PORTÉES (null-safe).
    public ArmorManager.WornArmor getArmorBonus(Player p) {
        return (armorManager != null) ? armorManager.aggregate(p) : new ArmorManager.WornArmor();
    }

    // Bonus « portés » COMBINÉS pets + armures d'Oublié, dans un seul conteneur Bonus.
    // C'est le point unique où l'on cumule les deux sources, pour que TOUS les calculs
    // (capacité du sac, prix des blocs, vente/sec) profitent des armures sans dupliquer la logique.
    public PetEquipMenu.Bonus getWornBonus(Player p) {
        PetEquipMenu.Bonus pet = getPetBonus(p);
        ArmorManager.WornArmor arm = getArmorBonus(p);
        PetEquipMenu.Bonus b = new PetEquipMenu.Bonus();
        b.blockValuePct  = pet.blockValuePct  + arm.bonus.blockValuePct;
        b.capacityPct    = pet.capacityPct    + arm.bonus.capacityPct;
        b.sellMoneyPct   = pet.sellMoneyPct   + arm.bonus.sellMoneyPct;
        b.doubleBlockPct = pet.doubleBlockPct + arm.bonus.doubleBlockPct;
        b.miningSpeedPct = pet.miningSpeedPct + arm.bonus.miningSpeedPct;
        return b;
    }
    // Gain d'argent MOYEN apporté par la Dîme du Passeur, en % : chaque Nᵉ bloc payé x10
    // -> en moyenne (10-1)/N de gain en plus sur l'ensemble des blocs. 0 si non débloquée.
    public double getDimeAvgMoneyPercent(Player p) {
        if (enchantManager == null) return 0;
        int lvl = enchantManager.getDimeLevel(p);
        int threshold = enchantManager.dimeThreshold(lvl);
        if (threshold <= 0) return 0;
        return (enchantManager.dimeMult(lvl) - 1.0) / threshold * 100.0;
    }
    // Bonus d'ARGENT total (tout ce qui augmente les gains) : multiplicateur de vente du Sac
    // + argent à la vente des pets + valeur des blocs des pets + Dîme (moyenne)
    // + Sel de Contrebande UNIQUEMENT quand il est actif (sac >= 90 % plein).
    public double getTotalMoneyBonusPercent(Player p) {
        PetEquipMenu.Bonus b = getWornBonus(p);
        double total = getMoneyBonusPercent(p) + b.sellMoneyPct + b.blockValuePct + getDimeAvgMoneyPercent(p)
                + getPrestigeSellPercent(p);   // + Prestige (Renaissance des Abysses)
        // Sel de Contrebande : bonus conditionnel, compté seulement si le sac est presque plein.
        if (enchantManager != null) {
            int seLvl = enchantManager.getContrebandeLevel(p);
            int cap = getCapacity(p);
            if (seLvl > 0 && cap > 0 && getStock(p) >= cap * EnchantManager.CONTREBANDE_FILL) {
                total += enchantManager.contrebandeBonus(seLvl) * 100.0;
            }
        }
        return total;
    }

    // Prix de vente FINAL d'un bloc (hors Dîme ponctuelle), en appliquant TOUTE la chaîne réelle :
    // prix de base × (1 + valeur des blocs pets) × mult. de vente Sac × (1 + argent vente pets).
    // Utilisé par /blockvalue pour afficher le vrai prix.
    public double getFinalBlockPrice(Player p, double basePrice) {
        PetEquipMenu.Bonus b = getWornBonus(p);
        double v = basePrice;
        v *= (1.0 + b.blockValuePct / 100.0);
        v *= getMoneyMult(p);
        v *= (1.0 + b.sellMoneyPct / 100.0);
        v *= (1.0 + getPrestigeSellPercent(p) / 100.0);   // + Prestige (Renaissance des Abysses)
        return v;
    }
    // Prix : 25, 50, 77, 104, 133... (+25, puis l'incrément monte de +2 tous les 2 niveaux).
    private double moneyMultCost(int level) {
        double cost = 25;
        for (int n = 2; n <= level + 1; n++) cost += 25 + 2 * ((n - 1) / 2);
        return Math.floor(cost);
    }

    // ===== ENCHANTEMENTS DE LA PIOCHE (par joueur) =====
    private final java.util.Random rng = new java.util.Random();

    // L'Œil du Cyclone : une seule tempête à la fois par joueur (évite d'empiler des tickers)
    // + cooldown interne pour ne pas relancer une tempête en boucle. Valeurs en millisecondes système.
    private final java.util.Map<java.util.UUID, Long> cycloneActiveUntil = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> cycloneCooldownUntil = new java.util.HashMap<>();
    private static final long CYCLONE_COOLDOWN_MS = 90_000L; // 90 s (cf. fiche enchantement).

    // Fichier de sauvegarde des données joueur.
    private java.io.File dataFile;
    private org.bukkit.configuration.file.FileConfiguration dataConfig;

    // Accès pour les managers extraits.
    org.bukkit.configuration.file.FileConfiguration getDataConfig() { return dataConfig; }
    Economy getEconomy() { return economy; }
    boolean isInParcelleZone(Player p) { return "parcelle".equals(playerZone.get(p.getUniqueId())); }
    BackpackManager getBackpack() { return backpackManager; }
    // Prix de base (avant bonus) de la pierre / du charbon / du bloc de charbon selon la mine.
    double getStoneBasePrice(String mine)     { return mineDef(mine).stonePrice; }
    double getCoalBasePrice(String mine)      { return mineDef(mine).coalPrice; }
    // Prix de base du BLOC de charbon (0 si la mine n'en a pas).
    double getCoalBlockBasePrice(String mine) { return mineDef(mine).coalBlockPrice; }
    // Prix de base du FER (4e minerai, 0 si la mine n'en a pas).
    double getIronBasePrice(String mine)      { return mineDef(mine).ironPrice; }
    // Prix de base du FER DES ABÎMES (5e minerai, 0 si la mine n'en a pas).
    double getAbyssalBasePrice(String mine)   { return mineDef(mine).abyssalPrice; }
    // Code de la mine la plus haute débloquée par le joueur (= son rang dans la table MINES).
    public String bestUnlockedMineCode(Player p) {
        return MINES.get(getMineRank(p) - 1).code;
    }
    // Prix de vente MINERAI PAR MINERAI = le MEILLEUR prix parmi TOUTES les mines débloquées.
    //
    // ⚠️ Corrigé le 2026-09-02. Avant, ces 5 prix lisaient la seule « meilleure mine débloquée ».
    // Ça supposait qu'une mine contient toujours au moins autant de minerais que les précédentes —
    // faux dès l'Arc II : la mine V (Montagne Inversée) est 100 % grès et déclare charbon, bloc de
    // charbon, fer et Fer des Abîmes à 0. Résultat, débloquer la mine 22 mettait TOUS ces minerais
    // à 0 partout, y compris en redescendant dans les mines 1 à 21 : le joueur cassait du fer pour
    // rien. Le max minerai par minerai supprime le trou définitivement, et se répare tout seul pour
    // les arcs suivants, qui auront eux aussi leur propre bloc de base.
    //
    // Le BLOC DE BASE (pierre, grès, grès rouge…) est un cas à part : chaque matériau garde son
    // propre prix, sinon le grès à 500 $ tirerait la pierre de l'Arc I à 500 $ et il n'y aurait
    // plus aucune raison de changer d'arc. Voir getBaseBlockSellPrice.
    public double getCoalSellPrice(Player p)      { return maxUnlocked(p, d -> d.coalPrice); }
    public double getCoalBlockSellPrice(Player p) { return maxUnlocked(p, d -> d.coalBlockPrice); }
    public double getIronSellPrice(Player p)      { return maxUnlocked(p, d -> d.ironPrice); }
    public double getAbyssalSellPrice(Player p)   { return maxUnlocked(p, d -> d.abyssalPrice); }

    /** Meilleur prix d'un minerai parmi toutes les mines que le joueur a débloquées. */
    private double maxUnlocked(Player p, java.util.function.ToDoubleFunction<MineDef> prix) {
        int rang = getMineRank(p);
        double best = 0;
        for (int i = 0; i < rang && i < MINES.size(); i++) best = Math.max(best, prix.applyAsDouble(MINES.get(i)));
        return best;
    }

    /**
     * Prix du BLOC DE BASE que le joueur est en train de miner (pierre, grès…).
     * On prend le meilleur prix parmi les mines débloquées QUI ONT LE MÊME bloc de base : la pierre
     * plafonne donc au prix de la mine 21 (53 $) même après Alabasta, et le grès à celui de la
     * mine 22 (500 $). Chaque matériau progresse dans son propre couloir.
     */
    public double getBaseBlockSellPrice(Player p, Material bloc) {
        int rang = getMineRank(p);
        double best = 0;
        for (int i = 0; i < rang && i < MINES.size(); i++) {
            MineDef d = MINES.get(i);
            if (d.baseBlock == bloc) best = Math.max(best, d.stonePrice);
        }
        // Aucune mine débloquée avec ce bloc de base (ne devrait pas arriver) : on retombe sur la
        // mine courante pour ne jamais payer 0.
        return best > 0 ? best : getStoneBasePrice(getPlayerMine(p));
    }

    /** Prix du bloc de base de la mine où le joueur se trouve actuellement. */
    public double getStoneSellPrice(Player p) { return getBaseBlockSellPrice(p, mineBaseBlock(p)); }
    // Accès pour le CommandManager.
    ZoneManager getZones() { return zones; }
    ParcelleManager getParcelleManager() { return parcelleManager; }
    IslandBankManager getIslandBank() { return islandBankManager; }
    OneBlockManager getOneBlock() { return oneBlockManager; }
    SpawnerManager getSpawnerManager() { return spawnerManager; }
    // (Les anciens getUnlockedMineB..G ont été remplacés par hasUnlockedMine/unlockMine/clearUnlockedMines.)
    java.util.Map<java.util.UUID, String> getPlayerMineMap() { return playerMine; }
    java.util.Map<java.util.UUID, String> getPlayerZoneMap() { return playerZone; }
    java.util.Map<java.util.UUID, java.util.UUID> getPendingInvitations() { return pendingInvitations; }
    java.util.Map<java.util.UUID, Integer> getCapacityLevelMap() { return capacityLevel; }
    java.util.Map<java.util.UUID, Integer> getSellLevelMap() { return sellLevel; }
    java.util.Map<java.util.UUID, Integer> getMoneyMultLevelMap() { return moneyMultLevel; }
    // Sauvegarde dataConfig dans le fichier ; `what` décrit ce qu'on sauvegarde (pour le log d'erreur).
    void saveDataConfig(String what) {
        try { dataConfig.save(dataFile); } catch (java.io.IOException e) {
            getLogger().warning("Impossible de sauvegarder " + what + ".");
        }
    }

    // Initialise le fichier playerdata.yml (créé s'il n'existe pas).
    private void initData() {
        dataFile = new java.io.File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try { dataFile.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        dataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
    }

    // Charge les données d'un joueur depuis le fichier (à la connexion).
    private void loadPlayer(Player p) {
        String id = p.getUniqueId().toString();
        if (dataConfig.contains(id)) {
            stock.put(p.getUniqueId(), dataConfig.getInt(id + ".stock", 0));
            bagValue.put(p.getUniqueId(), dataConfig.getDouble(id + ".bagValue", 0.0));
            coalStock.put(p.getUniqueId(), dataConfig.getInt(id + ".coalStock", 0));
            coalValue.put(p.getUniqueId(), dataConfig.getDouble(id + ".coalValue", 0.0));
            capacityLevel.put(p.getUniqueId(), dataConfig.getInt(id + ".capacityLevel", 1));
            sellLevel.put(p.getUniqueId(), dataConfig.getInt(id + ".sellLevel", 1));
            moneyMultLevel.put(p.getUniqueId(), dataConfig.getInt(id + ".moneyMultLevel", 0));
            enchantManager.setEfficiencyLevel(p.getUniqueId(), dataConfig.getInt(id + ".efficiencyLevel", 0));
            enchantManager.setExplosionLevel(p.getUniqueId(), dataConfig.getInt(id + ".explosionLevel", 0));
            totalMined.put(p.getUniqueId(), dataConfig.getLong(id + ".totalMined", 0L));
            maxIncomePerSec.put(p.getUniqueId(), dataConfig.getDouble(id + ".maxIncomePerSec", 0.0));
            // Blocs cassés par enchant (compteurs à vie) — clés sous ".enchantBlocks.<key>".
            java.util.Map<String, Long> eb = new java.util.HashMap<>();
            for (String[] k : ENCHANT_BLOCK_KEYS) {
                long v = dataConfig.getLong(id + ".enchantBlocks." + k[0], 0L);
                if (v > 0) eb.put(k[0], v);
            }
            if (!eb.isEmpty()) enchantBlocks.put(p.getUniqueId(), eb);
            // Stats de bonus (compteurs + argent) sous ".bonusStats.<key>".
            java.util.Map<String, Long>   bc = new java.util.HashMap<>();
            java.util.Map<String, Double> bm = new java.util.HashMap<>();
            for (String key : new String[]{"dblBlocks", "dimeBlocks"}) {
                long v = dataConfig.getLong(id + ".bonusStats." + key, 0L);
                if (v > 0) bc.put(key, v);
            }
            for (String key : new String[]{"dblMoney", "dimeMoney", "contrebandeMoney", "blockValueMoney"}) {
                double v = dataConfig.getDouble(id + ".bonusStats." + key, 0.0);
                if (v > 0) bm.put(key, v);
            }
            if (!bc.isEmpty()) bonusCounts.put(p.getUniqueId(), bc);
            if (!bm.isEmpty()) bonusMoney.put(p.getUniqueId(), bm);
            // /bv : suivi par type de bloc (minés, encaissés, record/min) sous ".blockStats.<key>.*".
            java.util.Map<String, Long>   bMined = new java.util.HashMap<>();
            java.util.Map<String, Double> bEarn  = new java.util.HashMap<>();
            java.util.Map<String, Double> bBest  = new java.util.HashMap<>();
            java.util.Map<String, Double> bPend  = new java.util.HashMap<>();
            for (String key : BLOCK_KEYS) {
                long   n = dataConfig.getLong(  id + ".blockStats." + key + ".mined",   0L);
                double e = dataConfig.getDouble(id + ".blockStats." + key + ".earned",  0.0);
                double b = dataConfig.getDouble(id + ".blockStats." + key + ".bestMin", 0.0);
                double q = dataConfig.getDouble(id + ".blockStats." + key + ".pending", 0.0);
                if (n > 0) bMined.put(key, n);
                if (e > 0) bEarn.put(key, e);
                if (b > 0) bBest.put(key, b);
                if (q > 0) bPend.put(key, q);
            }
            if (!bMined.isEmpty()) blocksMined.put(p.getUniqueId(), bMined);
            if (!bEarn.isEmpty())  blocksEarned.put(p.getUniqueId(), bEarn);
            if (!bBest.isEmpty())  blocksBestMin.put(p.getUniqueId(), bBest);
            if (!bPend.isEmpty())  blocksPending.put(p.getUniqueId(), bPend);
            enchantManager.setForageLevel(p.getUniqueId(), dataConfig.getInt(id + ".forageLevel", 0));
            enchantManager.setFractureLevel(p.getUniqueId(), dataConfig.getInt(id + ".fractureLevel", 0));
            enchantManager.setVeinLevel(p.getUniqueId(), dataConfig.getInt(id + ".veinLevel", 0));
            enchantManager.setColonneLevel(p.getUniqueId(), dataConfig.getInt(id + ".colonneLevel", 0));
            enchantManager.setDimeLevel(p.getUniqueId(), dataConfig.getInt(id + ".dimeLevel", 0));
            dimeCounter.put(p.getUniqueId(), dataConfig.getInt(id + ".dimeCounter", 0));
            setWantsFly(p.getUniqueId(), dataConfig.getBoolean(id + ".flyWanted", false));
            pickaxeLevel.put(p.getUniqueId(), Math.max(1, dataConfig.getInt(id + ".pickaxeLevel", 1)));
            pickaxeXp.put(p.getUniqueId(), Math.max(0, dataConfig.getInt(id + ".pickaxeXp", 0)));
            prestige.put(p.getUniqueId(), Math.max(0, dataConfig.getInt(id + ".prestige", 0)));
            enchantManager.setHarponLevel(p.getUniqueId(), dataConfig.getInt(id + ".harponLevel", 0));
            enchantManager.setFortuneLevel(p.getUniqueId(), dataConfig.getInt(id + ".fortuneLevel", 0));
            enchantManager.setCycloneLevel(p.getUniqueId(), dataConfig.getInt(id + ".cycloneLevel", 0));
            enchantManager.setMemoireLevel(p.getUniqueId(), dataConfig.getInt(id + ".memoireLevel", 0));
            enchantManager.setRefluxLevel(p.getUniqueId(), dataConfig.getInt(id + ".refluxLevel", 0));
            enchantManager.setGouffreLevel(p.getUniqueId(), dataConfig.getInt(id + ".gouffreLevel", 0));
            enchantManager.setContrebandeLevel(p.getUniqueId(), dataConfig.getInt(id + ".contrebandeLevel", 0));
            enchantManager.setFlyLevel(p.getUniqueId(), dataConfig.getInt(id + ".flyLevel", 0));
            enchantManager.setHasteLevel(p.getUniqueId(), dataConfig.getInt(id + ".hasteLevel", 0));
            enchantManager.setFlecheLevel(p.getUniqueId(), dataConfig.getInt(id + ".flecheLevel", 0));
            enchantManager.setTntLevel(p.getUniqueId(), dataConfig.getInt(id + ".tntLevel", 0));
            enchantManager.setAllongeLevel(p.getUniqueId(), dataConfig.getInt(id + ".allongeLevel", 0));
            // Mines débloquées : on lit la clé ".unlockedMine<code>" de chaque mine payante de la table.
            // (Clés inchangées → compatible avec les données joueur existantes.)
            for (MineDef d : MINES) {
                if (mineRankOf(d.code) <= 1) continue; // mine de départ, toujours ouverte
                if (dataConfig.getBoolean(id + ".unlockedMine" + d.code, false)) {
                    unlockMine(p, d.code);
                }
            }
            // Chargement du backpack.
            backpackManager.loadBackpack(p.getUniqueId(), dataConfig);
            // Chargement des familiers (pets) possédés.
            petStorage.load(p.getUniqueId(), dataConfig);
            // Chargement des familiers équipés.
            petEquip.load(p.getUniqueId(), dataConfig);
            // Préférence d'affichage du guide BossBar.
            guideManager.setHidden(p.getUniqueId(), dataConfig.getBoolean(id + ".guideHidden", false));
            // Chargement de l'inventaire hors-mine (spawn/parcelle) sauvegardé sur disque.
            if (dataConfig.contains(id + ".invHorsMine")) {
                java.util.List<?> raw = dataConfig.getList(id + ".invHorsMine");
                if (raw != null) {
                    ItemStack[] arr = new ItemStack[raw.size()];
                    for (int i = 0; i < raw.size(); i++) {
                        Object o = raw.get(i);
                        arr[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
                    }
                    invHorsMine.put(p.getUniqueId(), arr);
                }
            }
            // Chargement de l'inventaire mine (pioche + sac + minerais + Tête de Pioche) sauvegardé sur disque.
            if (dataConfig.contains(id + ".invMine")) {
                java.util.List<?> raw = dataConfig.getList(id + ".invMine");
                if (raw != null) {
                    ItemStack[] arr = new ItemStack[raw.size()];
                    for (int i = 0; i < raw.size(); i++) {
                        Object o = raw.get(i);
                        arr[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
                    }
                    invMine.put(p.getUniqueId(), arr);
                }
            }
        }
    }

    // Sauvegarde les données d'un joueur dans le fichier (à la déconnexion).
    void savePlayer(Player p) {
        String id = p.getUniqueId().toString();
        dataConfig.set(id + ".stock", getStock(p));
        dataConfig.set(id + ".bagValue", getBagValue(p));
        dataConfig.set(id + ".coalStock", getCoalStock(p));
        dataConfig.set(id + ".coalValue", getCoalValue(p));
        dataConfig.set(id + ".capacityLevel", getCapacityLevel(p));
        dataConfig.set(id + ".sellLevel", getSellLevel(p));
        dataConfig.set(id + ".moneyMultLevel", getMoneyMultLevel(p));
        dataConfig.set(id + ".efficiencyLevel", enchantManager.getEfficiencyLevel(p));
        dataConfig.set(id + ".explosionLevel", enchantManager.getExplosionLevel(p));
        dataConfig.set(id + ".forageLevel", enchantManager.getForageLevel(p));
        dataConfig.set(id + ".fractureLevel", enchantManager.getFractureLevel(p));
        dataConfig.set(id + ".veinLevel", enchantManager.getVeinLevel(p));
        dataConfig.set(id + ".colonneLevel", enchantManager.getColonneLevel(p));
        dataConfig.set(id + ".dimeLevel", enchantManager.getDimeLevel(p));
        dataConfig.set(id + ".dimeCounter", getDimeCounter(p));
        dataConfig.set(id + ".flyWanted", wantsFly(p.getUniqueId()));
        dataConfig.set(id + ".pickaxeLevel", getPickaxeLevel(p));
        dataConfig.set(id + ".pickaxeXp", getPickaxeXp(p));
        dataConfig.set(id + ".prestige", getPrestige(p));
        dataConfig.set(id + ".harponLevel", enchantManager.getHarponLevel(p));
        dataConfig.set(id + ".fortuneLevel", enchantManager.getFortuneLevel(p));
        dataConfig.set(id + ".cycloneLevel", enchantManager.getCycloneLevel(p));
        dataConfig.set(id + ".memoireLevel", enchantManager.getMemoireLevel(p));
        dataConfig.set(id + ".refluxLevel", enchantManager.getRefluxLevel(p));
        dataConfig.set(id + ".gouffreLevel", enchantManager.getGouffreLevel(p));
        dataConfig.set(id + ".contrebandeLevel", enchantManager.getContrebandeLevel(p));
        dataConfig.set(id + ".flyLevel", enchantManager.getFlyLevel(p));
        dataConfig.set(id + ".hasteLevel", enchantManager.getHasteLevel(p));
        dataConfig.set(id + ".flecheLevel", enchantManager.getFlecheLevel(p));
        dataConfig.set(id + ".tntLevel", enchantManager.getTntLevel(p));
        dataConfig.set(id + ".allongeLevel", enchantManager.getAllongeLevel(p));
        dataConfig.set(id + ".totalMined", getTotalMined(p));
        dataConfig.set(id + ".maxIncomePerSec", getMaxIncomePerSec(p));
        for (String[] k : ENCHANT_BLOCK_KEYS) {
            long v = getEnchantBlocks(p, k[0]);
            if (v > 0) dataConfig.set(id + ".enchantBlocks." + k[0], v);
        }
        for (String key : new String[]{"dblBlocks", "dimeBlocks"}) {
            long v = getBonusCount(p, key);
            if (v > 0) dataConfig.set(id + ".bonusStats." + key, v);
        }
        for (String key : new String[]{"dblMoney", "dimeMoney", "contrebandeMoney", "blockValueMoney"}) {
            double v = getBonusMoney(p, key);
            if (v > 0) dataConfig.set(id + ".bonusStats." + key, v);
        }
        // /bv : suivi par type de bloc.
        java.util.Map<String, Double> pend = blocksPending.get(p.getUniqueId());
        for (String key : BLOCK_KEYS) {
            long   n = getBlocksMined(p, key);
            double e = getBlocksEarned(p, key);
            double b = getBlocksBestMin(p, key);
            double q = (pend == null) ? 0.0 : pend.getOrDefault(key, 0.0);
            if (n > 0) dataConfig.set(id + ".blockStats." + key + ".mined",   n);
            if (e > 0) dataConfig.set(id + ".blockStats." + key + ".earned",  e);
            if (b > 0) dataConfig.set(id + ".blockStats." + key + ".bestMin", b);
            if (q > 0) dataConfig.set(id + ".blockStats." + key + ".pending", q);
        }
        for (MineDef d : MINES) {
            if (mineRankOf(d.code) <= 1) continue; // mine de départ, pas de flag à sauver
            dataConfig.set(id + ".unlockedMine" + d.code, hasUnlockedMine(p, d.code));
        }
        dataConfig.set(id + ".guideHidden", guideManager.isHidden(p.getUniqueId()));
        ItemStack[] contents = backpackManager.getBpContents(p);
        for (int i = 0; i < 54; i++) {
            dataConfig.set(id + ".bp." + i,
                (contents[i] != null && contents[i].getType() != Material.AIR) ? contents[i] : null);
        }
        // Inventaires selon la zone : l'inventaire COURANT correspond à la zone actuelle du joueur.
        if (isInMine(p)) {
            saveInvMine(p);      // dans la mine -> l'inventaire courant EST l'inventaire mine
        } else {
            saveInvHorsMine(p);  // au spawn/parcelle -> l'inventaire courant EST l'inventaire hors-mine
        }
        // On persiste LES DEUX inventaires sur disque (mine + hors-mine) pour ne rien perdre.
        ItemStack[] hors = invHorsMine.get(p.getUniqueId());
        dataConfig.set(id + ".invHorsMine", hors == null ? null : java.util.Arrays.asList(hors));
        ItemStack[] mine = invMine.get(p.getUniqueId());
        dataConfig.set(id + ".invMine", mine == null ? null : java.util.Arrays.asList(mine));
        // Familiers possédés.
        dataConfig.set(id + ".pets", new java.util.ArrayList<>(petStorage.getPetsRaw(p.getUniqueId())));
        // Familiers équipés.
        petEquip.writeInto(dataConfig, p.getUniqueId());
        try { dataConfig.save(dataFile); } catch (java.io.IOException e) {
            getLogger().warning("Impossible de sauvegarder les donnees de " + p.getName());
        }
    }

    // Sauvegarde TOUS les joueurs connectés (à l'arrêt du serveur).
    // On délègue à savePlayer() pour que TOUT soit sauvé correctement (dont les 2 inventaires).
    private void saveAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            savePlayer(p);
        }
    }

    // Blocs CASSÉS par joueur (positions). Au reset, on ne renvoie QUE ces blocs -> performant.
    private final java.util.Map<java.util.UUID, java.util.Set<Location>> brokenBlocks = new java.util.HashMap<>();

    java.util.Set<Location> getBroken(Player p) {
        return brokenBlocks.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>());
    }

    // ===== SCOREBOARD (sidebar droite) =====
    // Blocs minés sur une fenêtre glissante de 60s (horodatage de chaque bloc) -> b/min en direct.
    private final java.util.Map<java.util.UUID, java.util.ArrayDeque<Long>> blockTimes = new java.util.HashMap<>();
    // Argent gagné à la dernière seconde (mis à jour par la vente) -> $/s.
    private final java.util.Map<java.util.UUID, Double> incomePerSec = new java.util.HashMap<>();
    // Record du meilleur $/s jamais atteint par le joueur (persisté, sert au classement Gains/s).
    private final java.util.Map<java.util.UUID, Double> maxIncomePerSec = new java.util.HashMap<>();
    public double getMaxIncomePerSec(Player p) { return maxIncomePerSec.getOrDefault(p.getUniqueId(), 0.0); }

    private void incrementBlocksMinute(Player p) {
        java.util.ArrayDeque<Long> dq = blockTimes.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayDeque<>());
        long now = System.currentTimeMillis();
        dq.addLast(now);
        while (!dq.isEmpty() && dq.peekFirst() < now - 60000L) dq.pollFirst();
    }

    private int getBlocksPerMinute(Player p) {
        java.util.ArrayDeque<Long> dq = blockTimes.get(p.getUniqueId());
        if (dq == null) return 0;
        long cutoff = System.currentTimeMillis() - 60000L;
        while (!dq.isEmpty() && dq.peekFirst() < cutoff) dq.pollFirst();
        return dq.size();
    }

    public double getIncomePerSec(Player p) { return incomePerSec.getOrDefault(p.getUniqueId(), 0.0); }

    // Entrées invisibles uniques (une par ligne) : on ne change que le texte (préfixe d'équipe) -> pas de scintillement.
    private static final String[] SB_ENTRIES = {
        "§0§r","§1§r","§2§r","§3§r","§4§r","§5§r","§6§r","§7§r","§8§r","§9§r","§a§r","§b§r"
    };
    private static final String SB_SEP = "§8§m──────────────";

    // Met à jour la sidebar SANS la reconstruire (mise à jour des préfixes d'équipe).
    private void updateScoreboard(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        Scoreboard board = player.getScoreboard();
        if (board == mgr.getMainScoreboard()) board = mgr.getNewScoreboard();

        Objective obj = board.getObjective("mine_hud");
        if (obj == null) {
            obj = board.registerNewObjective("mine_hud", "dummy", "§e§l✦ §6§lBLOCK PIECE §e§l✦");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            try { obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()); } catch (Throwable ignored) {}
        }

        String pm = getPlayerMine(player);
        String mineName = getMineDisplayNameShort(pm);

        java.util.List<String> lines = new ArrayList<>();
        lines.add(SB_SEP);
        lines.add("§f● §7Argent  §a" + formatBig(player)); // solde EXACT (BigInteger), quelle que soit l'échelle
        lines.add("§f● §7Sac      §e" + formatNumber(getStock(player)) + "§8/§e" + formatNumber(getCapacity(player)));
        lines.add("§f● §7Vente    §a" + formatNumber(getIncomePerSec(player)) + "§7$/s");
        lines.add("§f● §7Mines    §e" + getMineRank(player) + "§8/§e" + mineTiles().size());
        lines.add(SB_SEP);
        lines.add("§f● §7Mine      §b" + formatNumber(getTotalMined(player)) + " §8blocs");
        lines.add("§f● §7Bonus   §d+" + String.format(java.util.Locale.FRANCE, "%.2f", getTotalMoneyBonusPercent(player)) + "%");
        lines.add("§f● §7Fragments §d" + (fragmentManager != null
                ? formatNumberBig(fragmentManager.getFragments(player)) : "0") + " §5✦");
        lines.add("§f● §7Île       " + mineName);
        lines.add(SB_SEP);
        lines.add("§6      PLAY.BLOCKPIECE.FR");

        renderSidebar(player, board, obj, lines);
    }

    // Scoreboard SPÉCIFIQUE à la parcelle : niveau de l'île (score en points), argent, progression
    // des mines. Pas de Sac / Vente / Blocs / Bonus / Fragments (ceux-là restent dans la mine).
    private void updateParcelleScoreboard(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        Scoreboard board = player.getScoreboard();
        if (board == mgr.getMainScoreboard()) board = mgr.getNewScoreboard();

        Objective obj = board.getObjective("mine_hud");
        if (obj == null) {
            obj = board.registerNewObjective("mine_hud", "dummy", "§e§l✦ §6§lBLOCK PIECE §e§l✦");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            try { obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()); } catch (Throwable ignored) {}
        }

        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        long points = parc != null ? parc.islandPoints() : 0L;

        java.util.List<String> lines = new ArrayList<>();
        lines.add(SB_SEP);
        lines.add("§f● §7Niveau  §b" + formatNumber(points) + " §7pts");
        lines.add("§f● §7Argent  §a" + formatBig(player));
        lines.add("§f● §7Mines   §e" + getMineRank(player) + "§8/§e" + mineTiles().size());
        lines.add(SB_SEP);
        lines.add("§6      PLAY.BLOCKPIECE.FR");

        renderSidebar(player, board, obj, lines);
    }

    // Applique une liste de lignes à la sidebar (préfixes d'équipe, position haut->bas), puis
    // purge les lignes en trop d'un rendu précédent plus long (ex. mine -> parcelle).
    private void renderSidebar(Player player, Scoreboard board, Objective obj, java.util.List<String> lines) {
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            String entry = SB_ENTRIES[i];
            obj.getScore(entry).setScore(n - i); // position : haut = score plus grand
            org.bukkit.scoreboard.Team team = board.getTeam("ln" + i);
            if (team == null) team = board.registerNewTeam("ln" + i);
            if (!team.hasEntry(entry)) team.addEntry(entry);
            team.setPrefix(lines.get(i));
        }
        // Retire les entrées résiduelles d'un scoreboard précédent plus grand.
        for (int i = n; i < SB_ENTRIES.length; i++) {
            board.resetScores(SB_ENTRIES[i]);
        }

        player.setScoreboard(board);
    }

    // Donne un score à une ligne (le texte est le nom d'entrée, le score est sa position).
    private void setLine(Objective obj, String text, int score) {
        Score s = obj.getScore(text);
        s.setScore(score);
    }

    // Supprime le scoreboard du joueur (on lui remet le scoreboard vide du serveur).
    private void removeScoreboard(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        Scoreboard board = player.getScoreboard();
        if (board != mgr.getMainScoreboard()) {
            Objective obj = board.getObjective("mine_hud");
            if (obj != null) obj.unregister();
        }
        player.setScoreboard(mgr.getMainScoreboard());
    }


    private int getStock(Player p) {
        return stock.getOrDefault(p.getUniqueId(), 0);
    }
    void setStock(Player p, int value) {
        stock.put(p.getUniqueId(), Math.max(0, value));
    }

    // ===== PARCELLES =====
    private ParcelleManager parcelleManager;

    // Deux inventaires distincts :
    //  - invMine    : dans la mine (pioche + sac + minerais). Gardé en mémoire.
    //  - invHorsMine: hors de la mine (spawn ET parcelle partagent le MÊME inventaire libre,
    //                 sans pioche ni sac). Sauvegardé sur disque (survit à la déconnexion).
    private final java.util.Map<java.util.UUID, ItemStack[]> invMine     = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, ItemStack[]> invHorsMine = new java.util.HashMap<>();

    /**
     * /resetarmures : retire TOUTES les armures d'Oublié d'un joueur, sans toucher à ses Fragments
     * ni à son compteur d'armures recyclées.
     * <p>On fouille les TROIS endroits : l'inventaire vivant (pièces portées comprises — sur un
     * PlayerInventory, getContents() couvre les 4 slots d'armure et la main gauche) ET les deux
     * inventaires sauvegardés mine / hors-mine. Sans ces deux derniers, une armure rangée dans
     * l'inventaire hors-mine réapparaîtrait en sortant de la mine.
     * @return le nombre de pièces retirées
     */
    public int purgeArmuresOubli(Player p) {
        if (armorManager == null) return 0;
        int retirees = 0;
        ItemStack[] vivant = p.getInventory().getContents();
        for (int i = 0; i < vivant.length; i++) {
            if (armorManager.isArmureOubli(vivant[i])) { p.getInventory().setItem(i, null); retirees++; }
        }
        for (java.util.Map<java.util.UUID, ItemStack[]> sauvegarde : java.util.Arrays.asList(invMine, invHorsMine)) {
            ItemStack[] contenu = sauvegarde.get(p.getUniqueId());
            if (contenu == null) continue;
            for (int i = 0; i < contenu.length; i++) {
                if (armorManager.isArmureOubli(contenu[i])) { contenu[i] = null; retirees++; }
            }
        }
        p.updateInventory();
        return retirees;
    }

    // Zone actuelle du joueur : "mine", "spawn" ou "parcelle".
    // "spawn" et "parcelle" partagent l'inventaire hors-mine ; seule "mine" a la pioche+sac.
    private final java.util.Map<java.util.UUID, String> playerZone = new java.util.HashMap<>();

    // Vrai si le joueur est actuellement dans la MINE (seule zone avec pioche + sac).
    boolean isInMine(Player p) { return "mine".equals(playerZone.get(p.getUniqueId())); }

    // ===== Vol (enchant « Vol ») : actif uniquement dans la mine, si acheté. =====
    // Commande /fly : bascule le vol. Requiert l'enchant acheté ET d'être dans sa mine.
    public void toggleFly(Player p) {
        if (enchantManager == null || !enchantManager.hasFly(p)) {
            p.sendMessage("§cTu n'as pas encore l'enchant §b§lVol§c. Trouve la §e§lPlume§c puis achète-le dans ta pioche.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            p.sendMessage("§7Ton mode de jeu gère déjà le vol.");
            return;
        }
        if (!isInMine(p) || !isInMineFlyZone(p, p.getLocation())) {
            p.sendMessage("§cLe Vol ne fonctionne que §fdans ta mine§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        boolean on = !p.getAllowFlight();
        setWantsFly(p.getUniqueId(), on); // mémorise le choix : réactivé auto en mine à la reco/entrée
        p.setAllowFlight(on);
        p.setFlying(on);
        if (on) {
            // Vitesse selon le niveau : Vol I = 0.05 (posé), Vol II = 0.1 (vol normal vanilla), Vol III = 0.15 (le + rapide).
            int flyLvl = enchantManager.getFlyLevel(p);
            p.setFlySpeed(flyLvl >= 3 ? 0.15f : flyLvl >= 2 ? 0.1f : 0.05f);
            p.sendMessage("§b✦ §aVol §2activé§a.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.3f);
        } else {
            p.setFlySpeed(0.1f);
            p.sendMessage("§b✦ §7Vol §cdésactivé§7.");
        }
    }

    // Coupe le vol d'un joueur (appelé quand il quitte la mine). Ne touche pas au créatif/spectateur.
    public void disableFlyOutsideMine(Player p) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (p.getAllowFlight() || p.isFlying()) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setFlySpeed(0.1f);
        }
    }

    // ── Allonge : portée de minage (attribut natif BLOCK_INTERACTION_RANGE), active UNIQUEMENT en mine. ──
    // Portée vanilla de base = 4,5. En mine : 4,5 + bonus de l'Allonge. Hors mine : on remet la base.
    public void refreshReachAttribute(Player p) {
        if (enchantManager == null) return;
        org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE;
        org.bukkit.attribute.AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return; // sécurité (versions sans cet attribut)
        double bonus = enchantManager.allongeBonus(enchantManager.getAllongeLevel(p));
        // Actif seulement dans la BOÎTE de la mine du joueur (comme le vol) et si un bonus existe.
        boolean enMine = "mine".equals(playerZone.get(p.getUniqueId())) && isInMineFlyZone(p, p.getLocation());
        double cible = (enMine && bonus > 0) ? 4.5 + bonus : 4.5;
        if (Math.abs(inst.getBaseValue() - cible) > 0.001) inst.setBaseValue(cible);
    }

    // ── Célérité (Haste) : effet Haste permanent tant que le joueur est dans sa mine. ──
    // Applique/retire l'effet selon le niveau de Célérité et la présence dans la mine.
    public void refreshHasteEffect(Player p) {
        if (enchantManager == null) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        int lvl = enchantManager.getHasteLevel(p);
        if (lvl > 0 && isInMine(p)) {
            // amplifier = lvl-1 (Célérité I = Haste I). Effet quasi permanent, réappliqué en boucle.
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, 100, Math.min(lvl - 1, 2), false, false, false));
        } else if (p.hasPotionEffect(org.bukkit.potion.PotionEffectType.HASTE)) {
            // On ne retire que NOTRE haste (amplitude bornée) — ici on l'enlève simplement hors mine.
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
        }
    }

    // ── Malus de minage EN VOL : cooldown par joueur pour diviser le débit par ~2. ──
    // Le minage étant validé côté serveur au packet (instamine possible), on force un délai
    // minimal entre deux blocs cassés directement, mais SEULEMENT quand le joueur vole vraiment.
    private final java.util.Map<java.util.UUID, Long> lastMineTickWhileFlying = new java.util.HashMap<>();
    // Cooldown de minage en vol selon le niveau de Vol : niv.1 = 150 ms, niv.2 = 75 ms, niv.3 = 0 (instantané).
    private static final long FLY_MINE_COOLDOWN_MS   = 150L; // Vol I
    private static final long FLY_MINE_COOLDOWN_MS_2 = 75L;  // Vol II
    // Renvoie true si le joueur DOIT être bloqué (il vole et n'a pas attendu le cooldown).
    private boolean isFlyMiningBlocked(Player p) {
        if (!p.isFlying()) return false;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        int flyLvl = (enchantManager != null) ? enchantManager.getFlyLevel(p) : 0;
        if (flyLvl >= 3) return false; // Vol III : aucun malus, casse instantanée comme au sol.
        long cd = (flyLvl >= 2) ? FLY_MINE_COOLDOWN_MS_2 : FLY_MINE_COOLDOWN_MS;
        long now = System.currentTimeMillis();
        Long last = lastMineTickWhileFlying.get(p.getUniqueId());
        if (last != null && now - last < cd) return true;
        lastMineTickWhileFlying.put(p.getUniqueId(), now);
        return false;
    }
    // Invitations en attente : invité -> propriétaire de la parcelle
    private final java.util.Map<java.util.UUID, java.util.UUID> pendingInvitations = new java.util.HashMap<>();

    // Dernier joueur ayant fait sa toute première connexion (cible de /bvn). null si aucun en attente.
    private String lastNewPlayer = null;
    public String getLastNewPlayer() { return lastNewPlayer; }
    public void setLastNewPlayer(String name) { this.lastNewPlayer = name; }

    // GUI settings de parcelle en cours d'édition.
    private static final String PARCELLE_SETTINGS_TITLE = "§6§lMon Île §7» §eParamètres";
    private static final String PARCELLE_FRIENDS_TITLE  = "§6§lMon Île §7» §bAmis";

    // ===== Améliorations de parcelle =====
    private static final int PARC_SIZE_STEP = 10;   // +10 par niveau
    private static final int PARC_SIZE_MAX  = 100;  // taille max (half=50, sûr vs GRID_SPACING=750)
    private static final int PARC_INVITED_MAX = 10; // slots d'invités max

    // Prix pour PASSER à une taille donnée (taille -> coût). Courbe ×5 depuis 5000$.
    private double parcSizeUpgradeCost(int currentSize) {
        // currentSize=10 -> passe à 20 = 5000 ; 20->30 = 25000 ; 30->40 = 125000 ; etc.
        int levelsDone = (currentSize - PARC_SIZE_STEP) / PARC_SIZE_STEP; // 0 quand size=10
        return 5000.0 * Math.pow(5.0, levelsDone);
    }

    // Prix pour acheter le slot d'invité suivant. Courbe croissante.
    private double parcInvitedUpgradeCost(int currentMax) {
        // currentMax=1 -> 2e slot = 5000 ; 3e = 12500 ; etc. (même courbe ×2.5)
        int levelsDone = currentMax - 1; // 0 quand max=1
        return 5000.0 * Math.pow(2.5, levelsDone);
    }

    private boolean isOnParcelle(Player p) {
        Parcelle parc = parcelleManager.getParcelle(p.getUniqueId());
        if (parc == null) return false;
        return parc.contains(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
    }

    // Téléporte le joueur sur sa parcelle et swap l'inventaire.
    void tpToParcelle(Player player) {
        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        if (parc == null) parc = parcelleManager.createParcelle(player.getUniqueId());

        // Parcelle et spawn partagent le MÊME inventaire (hors-mine).
        // Si on vient de la mine : on swap ; si on vient du spawn : rien à faire (même inventaire).
        leaveMineIfNeeded(player);

        playerZone.put(player.getUniqueId(), "parcelle");

        org.bukkit.World world = Bukkit.getWorld("world");
        org.bukkit.Location dest = new org.bukkit.Location(world,
                parc.getCenterX() + 0.5, parcelleManager.getFloorY() + 1, parc.getCenterZ() + 0.5);
        player.teleport(dest);
        player.sendMessage("§aBienvenue sur ton Île ! §7(/ob setting pour les paramètres)");
        // Parcours de découverte (Acte III) : étape « va sur ta parcelle ».
        tutorial.onEnterParcelle(player);

        // Applique la WorldBorder limitée à la taille de la parcelle.
        org.bukkit.WorldBorder wb = player.getWorld().getWorldBorder();
        // Paper permet une WorldBorder par joueur via player#setWorldBorder.
        org.bukkit.WorldBorder personal = Bukkit.createWorldBorder();
        personal.setCenter(parc.getCenterX() + 0.5, parc.getCenterZ() + 0.5);
        personal.setSize(parc.getSize() + 1); // +1 pour englober les blocs de bord (centre sur +0.5)
        personal.setWarningDistance(0);
        personal.setWarningTime(0);
        player.setWorldBorder(personal);

        // OneBlock : le Bloc du Grand Appel EST le sol du centre (pile sous les pieds du joueur).
        // Il remplace l'ancien bloc de sol (bedrock) — apparence = phase courante.
        if (oneBlockManager != null) oneBlockManager.spawnBloc(parc);
    }

    // Téléporte le joueur (depuis la parcelle) vers le spawn, en gardant l'inventaire hors-mine.
    // Parcelle -> spawn : même inventaire, aucun swap nécessaire.
    private void tpFromParcelle(Player player, org.bukkit.Location dest) {
        playerZone.put(player.getUniqueId(), "spawn");
        player.teleport(dest);
    }

    void saveInvMine(Player p) {
        invMine.put(p.getUniqueId(), p.getInventory().getContents().clone());
    }

    private void saveInvHorsMine(Player p) {
        invHorsMine.put(p.getUniqueId(), p.getInventory().getContents().clone());
    }

    // Entre dans la MINE : sauve l'inventaire hors-mine si on venait du spawn/parcelle,
    // puis restaure l'inventaire mine (avec pioche + sac). Pose la zone sur "mine".
    void enterMine(Player p) {
        if (!isInMine(p)) {
            saveInvHorsMine(p);
        }
        p.getInventory().clear();
        ItemStack[] saved = invMine.get(p.getUniqueId());
        if (saved != null) p.getInventory().setContents(saved);
        // Tête des Familiers : donnée à TOUS dès l'entrée en mine (sans condition d'Acte).
        givePetsHead(p);
        // Pioche + sac SEULEMENT si l'Acte II est terminé (sinon les PNJ de l'île les remettent).
        if (acteManager.isActe2Done(p)) {
            giveBag(p);
            givePickaxe(p);
            giveOrdi(p); // Ordinateur Quantique (slot 8) si débloqué — en mine uniquement.
        } else if (acteManager.isActeDone(p)) {
            // Acte I fini mais pas l'Acte II : sur l'île, on guide vers le bon PNJ/objectif.
            acteManager.restoreObjective(p);
        }
        playerZone.put(p.getUniqueId(), "mine");
        // Le niveau de pioche reste visible dans la barre d'XP (au cas où un reset l'aurait effacée).
        updatePickaxeBar(p);
    }

    // Quitte la MINE vers le hors-mine (spawn/parcelle) : sauve l'inventaire mine,
    // restaure l'inventaire hors-mine (sans pioche/sac). Ne fait rien si déjà hors-mine.
    void leaveMineIfNeeded(Player p) {
        if (!isInMine(p)) return;
        // On quitte la mine : on arrête son compte à rebours de reset (il repartira à 60 s
        // à la prochaine entrée via /mine).
        secondsToReset.remove(p.getUniqueId());
        saveInvMine(p);
        p.getInventory().clear();
        ItemStack[] saved = invHorsMine.get(p.getUniqueId());
        if (saved != null) p.getInventory().setContents(saved);
        // Sécurité : jamais de pioche/sac hors mine.
        stripBagAndPickaxe(p);
    }

    // Retire toute pioche/sac/ordinateur de l'inventaire (utilisé hors mine).
    private void stripBagAndPickaxe(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (isBag(it) || isPickaxe(it) || isOrdi(it)) p.getInventory().setItem(i, null);
        }
    }

    // Ouvre le GUI /parcelle setting.
    void openParcelleSettings(Player player) {
        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        if (parc == null) { player.sendMessage("§cTu n'as pas encore d'Île !"); return; }

        Inventory menu = Bukkit.createInventory(null, 54, PARCELLE_SETTINGS_TITLE);
        ItemStack filler = makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, filler);

        // Slot 10 : Accès public (oui/non)
        menu.setItem(10, makeToggle("§eAccès public",
                parc.isVisitorsAllowed(), "§7Tout le monde peut visiter ton Île."));
        // Slot 13 : Visiteurs peuvent ouvrir coffres
        menu.setItem(13, makeToggle("§eOuvrir les coffres",
                parc.isVisitorsCanChests(), "§7Les visiteurs peuvent ouvrir les coffres."));
        // Slot 16 : Visiteurs peuvent interagir
        menu.setItem(16, makeToggle("§eInteragir (boutons...)",
                parc.isVisitorsCanInteract(), "§7Les visiteurs peuvent utiliser boutons/leviers."));

        // ===== Améliorations (ligne du milieu) =====
        // Slot 29 : Agrandir la parcelle
        ItemStack grow = new ItemStack(org.bukkit.Material.GRASS_BLOCK);
        ItemMeta gm = grow.getItemMeta();
        gm.setDisplayName("§a§l⬆ Agrandir l'Île");
        java.util.List<String> gl = new java.util.ArrayList<>();
        gl.add("§7Taille actuelle : §f" + parc.getSize() + "×" + parc.getSize());
        if (parc.getSize() >= PARC_SIZE_MAX) {
            gl.add("");
            gl.add("§6§lTAILLE MAXIMALE atteinte !");
        } else {
            int next = parc.getSize() + PARC_SIZE_STEP;
            gl.add("§7Prochaine : §f" + next + "×" + next);
            gl.add("§7Coût : §6" + formatNumber(parcSizeUpgradeCost(parc.getSize())) + "$");
            gl.add("");
            gl.add("§eClic pour agrandir !");
        }
        gm.setLore(gl);
        grow.setItemMeta(gm);
        menu.setItem(29, grow);

        // Slot 31 : Renommer l'Île
        ItemStack rename = new ItemStack(org.bukkit.Material.NAME_TAG);
        ItemMeta rm = rename.getItemMeta();
        rm.setDisplayName("§d§l✎ Renommer l'Île");
        java.util.List<String> rl = new java.util.ArrayList<>();
        rl.add("§7Nom actuel : " + islandDisplayName(parc, player.getName()));
        rl.add("");
        if (hasGrade(player)) {
            rl.add("§a✦ Grade : couleurs autorisées §7(&a, &c…)");
        } else {
            rl.add("§8Couleurs réservées aux grades.");
        }
        rl.add("§eClic pour renommer !");
        rm.setLore(rl);
        rename.setItemMeta(rm);
        menu.setItem(31, rename);

        // Slot 33 : Amis (ouvre la page d'amis)
        ItemStack inv = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        ItemMeta im = inv.getItemMeta();
        im.setDisplayName("§b§l👥 Amis");
        im.setLore(java.util.List.of(
                "§7Amis actuels : §f" + parc.getInvited().size() + "§7/§f" + parc.getMaxInvited(),
                "§7Gère les permissions de tes amis",
                "§7et achète plus de slots.",
                "",
                "§eClic pour ouvrir !"));
        inv.setItemMeta(im);
        menu.setItem(33, inv);

        // Slot 49 : Retour
        ItemStack back = new ItemStack(org.bukkit.Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Fermer");
        back.setItemMeta(bm);
        menu.setItem(49, back);

        player.openInventory(menu);
    }

    private ItemStack makeToggle(String label, boolean enabled, String desc) {
        ItemStack item = new ItemStack(enabled ? org.bukkit.Material.LIME_WOOL : org.bukkit.Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(label + " : " + (enabled ? "§aON" : "§cOFF"));
        meta.setLore(java.util.List.of(desc, "§eClic pour basculer."));
        item.setItemMeta(meta);
        return item;
    }

    // Clics dans le menu parcelle settings.
    @EventHandler
    public void onParcelleSettingsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!PARCELLE_SETTINGS_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        if (parc == null) return;

        switch (event.getSlot()) {
            case 10: parc.setVisitorsAllowed(!parc.isVisitorsAllowed()); break;
            case 13: parc.setVisitorsCanChests(!parc.isVisitorsCanChests()); break;
            case 16: parc.setVisitorsCanInteract(!parc.isVisitorsCanInteract()); break;
            case 29: buyParcelleSize(player, parc); openParcelleSettings(player); return;
            case 31: startIslandRename(player); return;
            case 33: openParcelleFriends(player); return;
            case 49: player.closeInventory(); return;
            default: return;
        }
        parcelleManager.save();
        openParcelleSettings(player); // rafraîchit
    }

    // Achète l'agrandissement de la parcelle (+10).
    private void buyParcelleSize(Player player, Parcelle parc) {
        if (parc.getSize() >= PARC_SIZE_MAX) {
            player.sendMessage("§6Ton Île est déjà à la taille maximale (" + PARC_SIZE_MAX + "×" + PARC_SIZE_MAX + ").");
            return;
        }
        double cost = parcSizeUpgradeCost(parc.getSize());
        if (economy == null) return;
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + formatNumber(cost) + "$§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        parc.setSize(parc.getSize() + PARC_SIZE_STEP);
        parcelleManager.save();
        // Met à jour la WorldBorder si le joueur est sur sa parcelle.
        if ("parcelle".equals(playerZone.get(player.getUniqueId()))) {
            org.bukkit.WorldBorder wb = Bukkit.createWorldBorder();
            wb.setCenter(parc.getCenterX() + 0.5, parc.getCenterZ() + 0.5);
            wb.setSize(parc.getSize() + 1);
            wb.setWarningDistance(0);
            wb.setWarningTime(0);
            player.setWorldBorder(wb);
        }
        player.sendMessage("§a✦ Île agrandie à §f" + parc.getSize() + "×" + parc.getSize()
                + " §a! §6-" + formatNumber(cost) + "$");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ===== Renommer l'Île =====

    private static final int ISLAND_NAME_MAX = 24; // longueur max du nom d'Île

    /**
     * Le joueur a-t-il un grade ? (provisoire) — sert à autoriser les couleurs dans le nom d'Île,
     * la météo et l'heure d'Île (à venir). À BRANCHER plus tard sur le vrai système de grades ;
     * pour l'instant on se base sur une permission, faux par défaut.
     */
    public boolean hasGrade(Player p) {
        return p.hasPermission("privatemines.grade");
    }

    /** Nom affiché de l'Île : le nom personnalisé s'il existe, sinon « Île de <pseudo> ». */
    public String islandDisplayName(Parcelle parc, String ownerName) {
        if (parc != null && parc.getIslandName() != null && !parc.getIslandName().isBlank()) {
            return parc.getIslandName();
        }
        return "§bÎle de " + ownerName;
    }

    // Démarre la saisie du nouveau nom d'Île dans le chat (clic sur « Renommer »).
    private void startIslandRename(Player player) {
        renamingIsland.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§d✎ Écris le nouveau nom de ton Île dans le chat.");
        if (hasGrade(player)) player.sendMessage("§7Tu peux utiliser des couleurs : §e&a &b &c…");
        player.sendMessage("§7Max §f" + ISLAND_NAME_MAX + "§7 caractères. Tape §eannuler§7 pour annuler.");
    }

    // Nettoie un nom d'Île proposé : longueur, couleurs (grade only), anti-insulte basique.
    // Renvoie le nom nettoyé, ou null si refusé.
    private String nettoieNomIle(Player player, String brut) {
        String nom = brut.trim();
        if (nom.isEmpty()) return null;
        // Couleurs : autorisées seulement pour les grades ; sinon on retire les codes '&x'.
        if (hasGrade(player)) {
            nom = org.bukkit.ChatColor.translateAlternateColorCodes('&', nom);
        } else {
            nom = nom.replaceAll("[&§][0-9a-fk-orA-FK-OR]", ""); // supprime les codes couleur/format
        }
        // Longueur (on compte sans les codes couleur pour être juste).
        if (org.bukkit.ChatColor.stripColor(nom).length() > ISLAND_NAME_MAX) return null;
        // Anti-insulte basique.
        String test = org.bukkit.ChatColor.stripColor(nom).toLowerCase();
        for (String mot : new String[]{"pute","connard","enculé","encule","salope","nigger","pd"}) {
            if (test.contains(mot)) return null;
        }
        return nom;
    }

    // Achète un slot d'invité supplémentaire.
    private void buyParcelleInvited(Player player, Parcelle parc) {
        if (parc.getMaxInvited() >= PARC_INVITED_MAX) {
            player.sendMessage("§6Tu as déjà le maximum de slots d'invités (" + PARC_INVITED_MAX + ").");
            return;
        }
        double cost = parcInvitedUpgradeCost(parc.getMaxInvited());
        if (economy == null) return;
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + formatNumber(cost) + "$§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        parc.setMaxInvited(parc.getMaxInvited() + 1);
        parcelleManager.save();
        player.sendMessage("§b✦ Slot d'ami acheté ! Tu peux ajouter §f" + parc.getMaxInvited()
                + " §bamis. §6-" + formatNumber(cost) + "$");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // Page de gestion des amis (permissions globales + achat de slots).
    private void openParcelleFriends(Player player) {
        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        if (parc == null) { player.sendMessage("§cTu n'as pas encore d'Île !"); return; }

        Inventory menu = Bukkit.createInventory(null, 54, PARCELLE_FRIENDS_TITLE);
        ItemStack filler = makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, filler);

        // Permissions globales des amis (toggles)
        menu.setItem(10, makeToggle("§bOuvrir portes/portails",
                parc.isFriendsCanDoors(), "§7Tes amis peuvent ouvrir portes, trappes, portillons."));
        menu.setItem(12, makeToggle("§bCasser des blocs",
                parc.isFriendsCanBreak(), "§7Tes amis peuvent casser des blocs."));
        menu.setItem(14, makeToggle("§bPoser des blocs",
                parc.isFriendsCanPlace(), "§7Tes amis peuvent poser des blocs."));
        menu.setItem(16, makeToggle("§bOuvrir les coffres",
                parc.isFriendsCanChests(), "§7Tes amis peuvent ouvrir coffres/tonneaux."));

        // Slot 31 : achat de slots d'amis
        ItemStack slots = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        ItemMeta sm = slots.getItemMeta();
        sm.setDisplayName("§b§l➕ Slots d'amis");
        java.util.List<String> sl = new java.util.ArrayList<>();
        sl.add("§7Amis : §f" + parc.getInvited().size() + "§7/§f" + parc.getMaxInvited());
        if (parc.getMaxInvited() >= PARC_INVITED_MAX) {
            sl.add(""); sl.add("§6§lMAXIMUM atteint (" + PARC_INVITED_MAX + ") !");
        } else {
            sl.add("§7Prochain slot : §f" + (parc.getMaxInvited() + 1));
            sl.add("§7Coût : §6" + formatNumber(parcInvitedUpgradeCost(parc.getMaxInvited())) + "$");
            sl.add(""); sl.add("§eClic pour acheter un slot !");
        }
        sm.setLore(sl);
        slots.setItemMeta(sm);
        menu.setItem(31, slots);

        // Infos commandes (slot 22)
        ItemStack info = new ItemStack(org.bukkit.Material.BOOK);
        ItemMeta infm = info.getItemMeta();
        infm.setDisplayName("§eGérer tes amis");
        infm.setLore(java.util.List.of(
                "§7Ajouter : §f/ob friend <joueur>",
                "§7Retirer : §f/ob unfriend <joueur>"));
        info.setItemMeta(infm);
        menu.setItem(22, info);

        // Retour
        ItemStack back = new ItemStack(org.bukkit.Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour aux paramètres");
        back.setItemMeta(bm);
        menu.setItem(49, back);

        player.openInventory(menu);
    }

    // Clics dans la page d'amis.
    @EventHandler
    public void onParcelleFriendsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!PARCELLE_FRIENDS_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
        if (parc == null) return;

        switch (event.getSlot()) {
            case 10: parc.setFriendsCanDoors(!parc.isFriendsCanDoors()); break;
            case 12: parc.setFriendsCanBreak(!parc.isFriendsCanBreak()); break;
            case 14: parc.setFriendsCanPlace(!parc.isFriendsCanPlace()); break;
            case 16: parc.setFriendsCanChests(!parc.isFriendsCanChests()); break;
            case 31: buyParcelleInvited(player, parc); openParcelleFriends(player); return;
            case 49: openParcelleSettings(player); return;
            default: return;
        }
        parcelleManager.save();
        openParcelleFriends(player); // rafraîchit
    }

    // ===== Protections de parcelle (casser/poser/coffres/portes/PvP/mort) =====
    // Handlers extraits dans ParcelleListener (enregistré dans onEnable).
    // Les MENUS de parcelle (paramètres/amis) et leur logique restent ici.

    // ===== Spawners d'île (interaction / mort de mob / spawn par zone) =====
    // Handlers extraits dans SpawnerListener (enregistré dans onEnable).

    // ===== Règles globales du monde (dégâts de chute, blocs à gravité) =====
    // Handlers extraits dans WorldRulesListener (enregistré dans onEnable).
    // (Conservation de l'inventaire à la mort sur parcelle : extraite dans ParcelleListener.)

    @Override
    public void onEnable() {
        getLogger().info("PrivateMines est active ! Pret a creer des mines.");
        commandManager = new CommandManager(this);
        String[] cmds = {"mine","spawn","setspawn","money","zone","shop","bp","givemoney","resetenchants","resetsac","resetmines","resetlevelpioche","resetordi","resetfragment","holo","classements","blockvalue","bvn","resetbv","resetarmures","guide","intro","pnj","particules","quest","pets","stats","banque","keys","crate","logpose","mur","jobs","daily","commande","fly","pickaxelevel","fracturelevel","prestige","warp","spawner","ah","coinflip","end","ob","phases","collections","testgames"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(commandManager);
            getCommand(cmd).setTabCompleter(commandManager);
        }

        // Initialise le gestionnaire de zones protégées.
        zones = new ZoneManager(this);
        // Nettoie les mobs qui ENTRENT dans une zone protégée (le listener ne bloque que le spawn).
        zones.startPurgeTask();

        // Initialise le gestionnaire de parcelles.
        parcelleManager = new ParcelleManager(this);

        // Initialise le fichier de sauvegarde des données joueur.
        initData();
        // Charge les hologrammes (Text Display natifs).
        holoManager = new HologramManager(this);
        holoManager.initHolograms();
        holoManager.startEndTimerTask(); // rafraîchit le timer du dragon dans l'hologramme « end »
        // Gestionnaire des classements.
        leaderboardManager = new LeaderboardManager(this);
        // Gestionnaire du shop.
        shopManager = new ShopManager(this);
        // Banque d'île (niveau de parcelle : dépôt fer/or -> points).
        islandBankManager = new IslandBankManager(this);
        // Spawners d'île (Golem de fer / Piglin) : items achetables au shop.
        spawnerManager = new SpawnerManager(this);
        spawnerManager.loadSpawners();
        // OneBlock de parcelle (« Le Bloc du Grand Appel »).
        oneBlockManager = new OneBlockManager(this);
        // Gestionnaire des enchantements.
        enchantManager = new EnchantManager(this);
        // Clés & crates (coffres à récompenses).
        crateManager = new CrateManager(this);
        crateManager.load();
        // Hôtel des ventes entre joueurs (/ah).
        auctionManager = new AuctionManager(this);
        auctionManager.load();
        // Pile ou face entre joueurs (/coinflip).
        coinflipManager = new CoinflipManager(this);
        coinflipManager.load();
        // Mini-jeux de chat : une question toutes les 15 min, le gagnant prend +10 % de son argent.
        chatGameManager = new ChatGameManager(this);
        // L'End : event quotidien du Dragon du Vide (/warp → End).
        endManager = new EndManager(this);
        endManager.load();
        // Boussole du Log Pose : visite guidée d'Alabasta (donnée par un PNJ conteur).
        logPoseManager = new LogPoseManager(this);
        // Murs invisibles (barrières admin) : /mur, particules + repousse les non-OP.
        wallManager = new WallManager(this);
        // Métiers (/jobs) : pour l'instant le métier Mineur (paliers de blocs minés).
        jobsManager = new JobsManager(this);
        // Récompenses quotidiennes : série de 7 jours réclamée auprès d'un PNJ (rôle « quotidien »).
        dailyManager = new DailyManager(this);
        // Hub des quetes (/quest) : quotidiennes + quete d'histoire, le reste est prevu dans le menu.
        questManager = new QuestManager(this);
        // Annuaire des commandes (/commande) : la liste de TOUTES nos commandes.
        commandMenu = new CommandMenu(this);
        // Boussole de quête : décore la BossBar d'objectif d'une flèche + distance vers la cible.
        questCompass = new QuestCompass(this);
        questCompass.start();
        // Gestionnaire du backpack.
        backpackManager = new BackpackManager(this);
        // Menu /blockvalue.
        blockValueMenu = new BlockValueMenu(this);
        // Menu /phases (les 12 phases du OneBlock).
        phasesMenu = new PhasesMenu(this);
        // Marchand ambulant du OneBlock (la Caravane).
        merchantManager = new MerchantManager(this);
        // Menu /warp (téléportation rapide).
        warpMenu = new WarpMenu(this);
        // Item-clé d'arc : Chapeau de paille (East Blue). Clés PDC (item + « déjà trouvé »).
        strawHatItemKey  = new org.bukkit.NamespacedKey(this, "arc_key");
        strawHatTakenKey = new org.bukkit.NamespacedKey(this, "strawhat_taken");
        // Pets : fournisseur de têtes (HeadDB via réflexion) + menu /pets.
        petHeads = new PetHeadProvider(this);
        petMenu = new PetMenu(this);
        petStorage = new PetStorage(this);
        petEquip = new PetEquipMenu(this);
        petForge = new PetForgeMenu(this);
        // Guide BossBar (astuces débutants).
        guideManager = new GuideManager(this);
        // Acte I : cinématique d'ouverture (ambiance simple, sans caméra ni plugin externe).
        introManager = new IntroManager(this);
        // Acte IV : monnaie Fragments de Souvenir (recyclage des armures). Avant l'ActeManager
        // qui ouvre le menu de recyclage de l'Ordinateur Quantique.
        fragmentManager = new FragmentManager(this);
        // Acte IV : armures d'Oublié à bonus (fondation : rareté, slots, bonus portés).
        armorManager = new ArmorManager(this);
        // Acte I : logique de progression (PNJ, coffre partagé, Jeton, déblocage /mine).
        acteManager = new ActeManager(this);
        // Acte III : parcours de découverte guidé (« L'Apprentissage »), démarre après l'Acte II.
        tutorial = new TutorialManager(this);
        // Bibliothèque de skins nommés (skins.yml). Le skin "veilleur" y est pré-inscrit.
        skinLibrary = new SkinLibrary(this);
        // Acte I : PNJ « joueur » natifs (packets ProtocolLib). Toujours actifs, pas de dépendance.
        npcManager = new NpcManager(this, acteManager);
        // Menu /pnj setting.
        npcSettingMenu = new NpcSettingMenu(this);
        // Gestionnaire des particules décoratives (/particules).
        particleManager = new ParticleManager(this);
        // Charge les données des joueurs déjà connectés (cas d'un /reload).
        for (Player p : Bukkit.getOnlinePlayers()) loadPlayer(p);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CrystalProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(this), this);
        getServer().getPluginManager().registerEvents(new ParcelleListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldRulesListener(), this);
        getServer().getPluginManager().registerEvents(new LockedItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatTagListener(this), this);
        getServer().getPluginManager().registerEvents(jobsManager, this);
        getServer().getPluginManager().registerEvents(dailyManager, this);
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(commandMenu, this);
        getServer().getPluginManager().registerEvents(holoManager, this);
        getServer().getPluginManager().registerEvents(leaderboardManager, this);
        getServer().getPluginManager().registerEvents(shopManager, this);
        getServer().getPluginManager().registerEvents(islandBankManager, this);
        getServer().getPluginManager().registerEvents(spawnerManager, this);
        crateBlockListener = new CrateBlockListener(this);
        getServer().getPluginManager().registerEvents(crateBlockListener, this);
        islandAdminMenu = new IslandAdminMenu(this);
        getServer().getPluginManager().registerEvents(islandAdminMenu, this);
        collectionMenu = new CollectionMenu(this);
        getServer().getPluginManager().registerEvents(collectionMenu, this);
        getServer().getPluginManager().registerEvents(auctionManager, this);
        auctionManager.startExpiryTask();
        getServer().getPluginManager().registerEvents(coinflipManager, this);
        coinflipManager.startExpiryTask();
        getServer().getPluginManager().registerEvents(endManager, this);
        endManager.startDailyTask();
        getServer().getPluginManager().registerEvents(chatGameManager, this);
        chatGameManager.startAutoTask();
        getServer().getPluginManager().registerEvents(enchantManager, this);
        getServer().getPluginManager().registerEvents(crateManager, this);
        getServer().getPluginManager().registerEvents(backpackManager, this);
        getServer().getPluginManager().registerEvents(blockValueMenu, this);
        getServer().getPluginManager().registerEvents(phasesMenu, this);
        getServer().getPluginManager().registerEvents(merchantManager, this);
        getServer().getPluginManager().registerEvents(warpMenu, this);
        getServer().getPluginManager().registerEvents(petMenu, this);
        getServer().getPluginManager().registerEvents(petStorage, this);
        getServer().getPluginManager().registerEvents(petEquip, this);
        getServer().getPluginManager().registerEvents(petForge, this);
        // Précharge les têtes HeadDB des pets/arcs (différé d'1 tick : HeadDB doit être prêt).
        getServer().getScheduler().runTaskLater(this, () -> {
            // On précharge les têtes des pets ET des crates.
            int[] petIds = PetMenu.ALL_HEAD_IDS;
            int extra = CrateManager.RANKS.length + 3; // +1 Chapeau de paille, +1 Tête des Familiers (/pets), +1 raccourci /mine
            int[] all = java.util.Arrays.copyOf(petIds, petIds.length + extra);
            for (int i = 0; i < CrateManager.RANKS.length; i++) all[petIds.length + i] = CrateManager.RANKS[i].headId;
            all[all.length - 3] = STRAW_HAT_HEAD_ID;      // Chapeau de paille (tête HeadDB)
            all[all.length - 2] = PETS_HEAD_ID;           // Tête des Familiers (ouvre /pets)
            all[all.length - 1] = MINE_SHORTCUT_HEAD_ID;  // Raccourci /mine du menu des enchants
            petHeads.init(all);
        }, 1L);
        // Acte I : coffre partagé + PNJ natifs.
        getServer().getPluginManager().registerEvents(acteManager, this);
        getServer().getPluginManager().registerEvents(tutorial, this);
        getServer().getPluginManager().registerEvents(npcManager, this);
        getServer().getPluginManager().registerEvents(npcSettingMenu, this);
        getServer().getPluginManager().registerEvents(particleManager, this);
        // Log Pose : annule le navwand WorldEdit (téléportation) sur notre boussole.
        getServer().getPluginManager().registerEvents(logPoseManager, this);
        // Murs invisibles : sélection au bâton + anti-franchissement + rendu particules.
        getServer().getPluginManager().registerEvents(wallManager, this);
        wallManager.startTask();

        // On enregistre notre économie MAISON (BigInteger) comme provider Vault.
        setupEconomy();
        // Sauvegarde périodique du fichier economy.yml.
        startEconomyAutosave();

        // On branche notre écouteur de packets de minage.
        registerDigListener();

        // Tâche répétée : masquer entre eux les joueurs présents dans la mine.
        startHidePlayersTask();

        // Tâche répétée : reset global synchronisé de la mine toutes les 60s.
        startResetTask();

        // Tâche répétée : vente automatique du stock chaque seconde.
        startSellTask();
        // /bv : fige chaque minute le record de gain par type de bloc.
        startBlockMinuteTask();

        // Tâche répétée : afficher "SAC PLEIN" en actionbar tant que le sac est plein.
        startFullBagTask();

        // Tâche répétée : afficher les particules de zone pour ceux qui visualisent.
        startZoneParticleTask();

        // Tâche répétée : mise à jour du scoreboard sidebar (chaque seconde).
        startScoreboardTask();

        // Acte I : lueur bleue permanente sur le coffre-quête (pour ceux qui doivent le trouver).
        acteManager.startChestParticles();
        // Acte II : lueur sur le coffre de la Tête de Pioche.
        acteManager.startChest2Particles();
        // NOTE : plus de surveillance du fond du Puits. Le secret de la pioche est maintenant
        // déclenché 4 s après la prise de la Tête au coffre de l'Acte II (voir takeTete).

        // Affichage en continu des spots de particules décoratifs (/particules).
        particleManager.startParticleTask();

        // Tâche répétée : rotation des astuces du guide BossBar (toutes les 15s).
        guideManager.startTipTask();

        // Tâche répétée : BossBar de progression du OneBlock quand le joueur est sur son Île (0,5s).
        if (oneBlockManager != null) oneBlockManager.startBossBarTask();

        // Tâche répétée : la Caravane (marchand ambulant) passe sur les Îles (~30 min).
        if (merchantManager != null) merchantManager.startCaravaneTask();

        // (Plus de reset 60s : les blocs/min sont calculés en fenêtre glissante.)

        // Tâche répétée : recalcul des classements + mise à jour des hologrammes de top (60s).
        leaderboardManager.startLeaderboardTask();

        // Tâche répétée : génération des mobs par les spawners d'île (chaque seconde).
        spawnerManager.startSpawnerTask();

        // Tâche répétée : détection d'approche des lieux de la visite du Log Pose (Alabasta).
        logPoseManager.startTask();

        // Vider la VRAIE zone de la mine UNE SEULE FOIS au démarrage (air réel côté serveur).
        // Ensuite chaque joueur voit ses propres faux blocs ; plus besoin de re-vider.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World world = Bukkit.getWorld("world");
            if (world != null) {
                clearRealZone(world);
                getLogger().info("Zone de mine videe (une fois).");
            }
            // Nettoie d'abord les TextDisplay d'hologramme « orphelins » (restés après un /reload),
            // PUIS fait apparaître les hologrammes une fois les mondes chargés → pas de doublon.
            holoManager.nettoyerOrphelins();
            holoManager.spawnAllHolograms();
            // Fait apparaître les crates (grosses têtes) une fois HeadDB prêt.
            crateManager.spawnAllCrates();
            // Acte I : charge et affiche les PNJ natifs sauvegardés.
            npcManager.loadAll();
        }, 100L); // après 5s, le temps que le monde soit bien chargé

        // Tâche répétée : les PNJ tournent la tête vers le joueur proche (toutes les 0,25s).
        Bukkit.getScheduler().runTaskTimer(this, () -> npcManager.tickLook(), 120L, 5L);
    }

    // Toutes les 0,5s : avertissement "sac plein".
    // (Le sac/pioche/ordi sont désormais déplaçables librement : plus de re-placement forcé ici.
    //  Ils sont donnés à l'entrée en mine s'ils manquent, et ne peuvent pas être jetés.)
    private void startFullBagTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (getStock(player) >= getCapacity(player)) {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize("§c§lSAC PLEIN §7- §cBlocs perdus !"));
                }
                // Renaissance (Prestige) : détecte l'ENTRÉE dans la zone du Puits (une fois par entrée).
                boolean dedans = estDansZonePrestige(player.getLocation());
                boolean etaitDedans = dansZonePrestige.contains(player.getUniqueId());
                if (dedans && !etaitDedans) {
                    dansZonePrestige.add(player.getUniqueId());
                    tenterRenaissance(player);   // gère lui-même le gating Arc II + pioche 100 + confirmation
                } else if (!dedans && etaitDedans) {
                    dansZonePrestige.remove(player.getUniqueId());
                }
            }
        }, 10L, 10L); // toutes les 10 ticks = 0,5s
    }

    // Chaque seconde : chaque joueur vend SELL_PER_SEC blocs de son stock -> argent.
    private void startSellTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                int current = getStock(player);
                if (current <= 0) { incomePerSec.put(player.getUniqueId(), 0.0); continue; }

                int toSell = Math.min(getSellPerSec(player), current);

                // On vend d'abord le CHARBON (à son vrai prix), puis la pierre (à son vrai prix).
                // Ainsi le charbon rapporte toujours sa vraie valeur, jamais une moyenne diluée.
                int coalCount = getCoalStock(player);
                int coalToSell = Math.min(toSell, coalCount);
                int stoneToSell = toSell - coalToSell;

                double base = 0.0;
                // Charbon : valeur moyenne par bloc de charbon (mine B et C mélangées éventuellement).
                if (coalToSell > 0 && coalCount > 0) {
                    double coalAvg = getCoalValue(player) / coalCount;
                    double coalBase = coalAvg * coalToSell;
                    base += coalBase;
                    setCoalStock(player, coalCount - coalToSell);
                    setCoalValue(player, getCoalValue(player) - coalBase);
                }
                // Pierre : le reste de la valeur du sac réparti sur les blocs de pierre.
                int stoneCount = current - coalCount;
                if (stoneToSell > 0 && stoneCount > 0) {
                    double stoneValueTotal = getBagValue(player) - getCoalValue(player);
                    double stoneAvg = stoneValueTotal / stoneCount;
                    base += stoneAvg * stoneToSell;
                }

                double pay = base * getMoneyMult(player);      // + bonus d'argent
                // Bonus « argent à la vente » des familiers équipés + armures d'Oublié portées.
                double sellPct = getWornBonus(player).sellMoneyPct;
                if (sellPct != 0) pay *= (1.0 + sellPct / 100.0);
                // Bonus de PRESTIGE (Renaissance des Abysses) : +50 % de vente par prestige, cumulatif.
                int prestigePct = getPrestigeSellPercent(player);
                if (prestigePct != 0) pay *= (1.0 + prestigePct / 100.0);
                // Boost VENTE ×N temporaire (récompense de crate) : multiplie le paiement de la vente.
                double sellBoost = getSellBoostMult(player.getUniqueId());
                if (sellBoost > 1.0) pay *= sellBoost;
                int remaining = current - toSell;
                setStock(player, remaining);
                if (remaining <= 0) { setBagValue(player, 0.0); setCoalStock(player, 0); setCoalValue(player, 0.0); }
                else setBagValue(player, getBagValue(player) - base);
                if (economy != null && pay > 0) economy.depositPlayer(player, pay);
                // Quêtes quotidiennes : « vendre pour X $ ».
                if (questManager != null && pay > 0) questManager.onSold(player, pay);
                // /bv : répartit l'argent encaissé entre les types de blocs (au prorata de la file).
                creditBlocksEarned(player.getUniqueId(), pay);
                // Parcours de découverte (Acte III) : compte les blocs vendus (étape « vendre 25 blocs »).
                if (pay > 0 && toSell > 0) tutorial.onMoneyEarned(player, toSell);
                incomePerSec.put(player.getUniqueId(), pay); // gain de cette seconde -> $/s
                // Record du meilleur $/s (pour le classement Gains/s).
                if (pay > getMaxIncomePerSec(player)) maxIncomePerSec.put(player.getUniqueId(), pay);

                // Si le joueur regarde son menu de Sac, on le rafraîchit en direct.
                if (player.getOpenInventory() != null
                        && "§6Sac de Minage".equals(player.getOpenInventory().getTitle())) {
                    refreshBagMenu(player, player.getOpenInventory().getTopInventory());
                }
            }
        }, 20L, 20L); // chaque seconde
    }

    // Chaque seconde : regarde, pour chaque joueur armé (= entré dans une mine), s'il a cassé assez
    // de blocs pour déclencher la régénération de SA mine. Plus aucun compte à rebours : le déclencheur
    // est le SEUIL (RESET_THRESHOLD_PCT % du volume de la mine), pas le temps. Le tick d'une seconde
    // ne sert plus qu'à sortir le reset des boucles de salve d'enchant — le délai ressenti est nul.
    private void startResetTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                java.util.UUID id = player.getUniqueId();
                if (!secondsToReset.containsKey(id)) continue; // pas dans une mine / pas armé

                // Seuil pas encore atteint : on laisse le joueur continuer à creuser.
                // size() sur un HashSet est en O(1) — ce test coûte moins qu'une décrémentation.
                if (getBroken(player).size() < RESET_THRESHOLD_BLOCKS) continue;

                // On ne reset (et on ne notifie) QUE si le joueur est réellement dans la zone de SA mine.
                // Parti ailleurs (Alabasta, spawn, boutique…) → pas de reset à distance ni de spam.
                if (!isPlayerInMineArea(player.getLocation())) continue;

                // resetMineForPlayer ne régénère que s'il y a des blocs cassés ; sinon rien + pas de message.
                boolean didReset = resetMineForPlayer(player);
                if (!didReset) continue;
                player.sendMessage("§bTa mine a ete reinitialisee ! ⛏");
                // Si le joueur est dans la zone mine et sous la surface, on le remonte au sommet.
                // On ne téléporte PAS s'il a un menu (GUI) ouvert : ça le fermerait malgré lui
                // (ex: /pets) alors qu'il n'est même pas en train de miner à ce moment.
                boolean hasMenuOpen = player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING;
                int topY = mineFloorY(player) + MINE_HEIGHT; // plafond de LA mine du joueur
                if (!hasMenuOpen && isPlayerInMineArea(player.getLocation()) && player.getLocation().getY() < topY) {
                    Location safe = player.getLocation().clone();
                    safe.setY(topY);
                    player.teleport(safe);
                    player.setFallDistance(0f);
                    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        }, 20L, 20L); // chaque seconde
    }

    // Arme le reset de la mine pour ce joueur. Depuis le passage au seuil (2026-08-21) la valeur
    // stockée n'est plus un compte à rebours : seule la PRÉSENCE de la clé compte (= « il est dans
    // une mine »). On la retire quand il en sort (leaveMineIfNeeded) et à la déconnexion.
    void armMineReset(Player player) {
        secondsToReset.put(player.getUniqueId(), RESET_PERIOD_SEC);
    }

    // Point d'arrivée AU-DESSUS de la mine du joueur (plateforme d'entrée au bord ouest, même yaw
    // que l'Arc I). Généralisé par mine : les mines de l'Arc I gardent EXACTEMENT le même spot qu'avant.
    private Location mineTopSpawn(Player player) {
        // Spawn FIXE défini sur la mine (accès physique buildé) : prioritaire sur le calcul auto.
        MineDef md = mineDef(getPlayerMine(player));
        if (md != null && md.spawnX != null) {
            return new Location(Bukkit.getWorld("world"), md.spawnX, md.spawnY, md.spawnZ,
                    md.spawnYaw != null ? md.spawnYaw : 0f, 0f);
        }
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(player), cz = mineCenterZ(player), fy = mineFloorY(player);
        double x = (cx - half) - 3.5;          // ~3,5 blocs à l'ouest du bord ouest de la zone
        double y = fy + MINE_HEIGHT;            // au niveau du plafond de la mine
        double z = cz + 0.5;                    // centré en Z
        return new Location(Bukkit.getWorld("world"), x, y, z, 90f, 0f);
    }

    // Reset de la mine du joueur — ENVOI DIFFÉRENTIEL (léger, ne renvoie que ce qui a changé).
    //
    // ⚠️ Bug corrigé : pour les mines à charbon, on régénère les positions de charbon à chaque reset
    // (nouveau placement aléatoire). Si on ne renvoyait que les blocs CASSÉS, les cases jamais minées
    // gardaient leur ancien affichage (pierre) alors que le serveur les considérait désormais comme du
    // charbon → désynchro : le bloc ne se cassait plus, et un clic droit révélait le charbon caché.
    //
    // Pour rester léger (une mine = 100×100×50 = 500 000 blocs — impensable à renvoyer en entier à
    // chaque reset et pour chaque joueur), on ne renvoie QUE les cases dont le matériau a réellement
    // changé : l'ancien charbon qui redevient pierre, la nouvelle case qui devient charbon, plus les
    // blocs cassés à re-remplir. En pratique ~15 % de la mine au lieu de 100 %.
    // Renvoie true si un vrai reset a eu lieu (au moins un bloc régénéré), false si la mine était intacte.
    private boolean resetMineForPlayer(Player player) {
        java.util.Set<Location> broken = getBroken(player);
        World world = Bukkit.getWorld("world");
        String pm = getPlayerMine(player);

        // Aucun bloc cassé → rien à régénérer : on ne reset pas (ni pierre, ni charbon) et pas de message.
        if (broken.isEmpty()) return false;

        if (!mineHasCoal(pm)) {
            // Mine sans charbon : le matériau ne change jamais → il suffit de re-remplir les cassés.
            List<BlockState> toRefill = new ArrayList<>();
            for (Location loc : broken) {
                BlockState state = loc.getBlock().getState();
                state.setType(getBlockForPlayer(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), world));
                toRefill.add(state);
            }
            player.sendBlockChanges(toRefill);
            broken.clear();
            return true;
        }

        // Mine à charbon : on mémorise le matériau EXACT vu par le client à chaque case
        // occupée par un minerai AVANT régénération, on régénère, puis on ne renvoie que les
        // cases dont le matériau a réellement changé.
        //
        // ⚠ Ancien bug : on comparait des ENSEMBLES de positions « minerai / pas minerai ».
        // Une case qui passait de fer à charbon (ou charbon → bloc de charbon) restait dans les
        // deux ensembles → aucun paquet envoyé → le client gardait l'ANCIEN bloc (ex. fer) alors
        // que le serveur pensait charbon. En minant à la main, le bloc « changeait » sous les yeux
        // du joueur (fer → charbon). On compare désormais le MATÉRIAU, pas la simple présence.
        java.util.Map<Location, Material> oldMats = new java.util.HashMap<>();
        for (Location l : getAbyssalPositions(player))   oldMats.put(l, Material.DEEPSLATE_IRON_ORE);
        for (Location l : getIronPositions(player))       oldMats.put(l, Material.IRON_ORE);
        for (Location l : getCoalBlockPositions(player))  oldMats.put(l, Material.COAL_BLOCK);
        for (Location l : getCoalPositions(player))       oldMats.put(l, Material.COAL_ORE);
        generateCoalPositions(player, world);

        // Cases candidates = toutes celles qui portaient un minerai avant OU après la régénération.
        java.util.Set<Location> candidates = new java.util.HashSet<>(oldMats.keySet());
        candidates.addAll(getAbyssalPositions(player));
        candidates.addAll(getIronPositions(player));
        candidates.addAll(getCoalBlockPositions(player));
        candidates.addAll(getCoalPositions(player));

        // On renvoie une case si son matériau a changé (l'absence = pierre) OU si elle était cassée.
        java.util.Set<Location> toUpdate = new java.util.HashSet<>();
        for (Location l : candidates) {
            Material was = oldMats.getOrDefault(l, Material.STONE);
            Material now = getBlockForPlayer(player, l.getBlockX(), l.getBlockY(), l.getBlockZ(), world);
            if (was != now) toUpdate.add(l);
        }
        toUpdate.addAll(broken); // trous → re-remplis (même si le matériau n'a pas changé)

        if (!toUpdate.isEmpty()) {
            List<BlockState> toRefill = new ArrayList<>();
            for (Location loc : toUpdate) {
                BlockState state = loc.getBlock().getState();
                state.setType(getBlockForPlayer(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), world));
                toRefill.add(state);
            }
            player.sendBlockChanges(toRefill);
        }
        broken.clear();
        return true;
    }

    // Renvoie le BON état d'une seule case au joueur, en respectant ses trous déjà minés :
    // - case déjà cassée  -> AIR (on garde le trou)
    // - case dans la mine et intacte -> STONE
    // - case hors mine -> le vrai bloc du monde
    private void refreshSingleBlock(Player player, Location loc) {
        if (getBroken(player).contains(loc)) {
            player.sendBlockChange(loc, Material.AIR.createBlockData());
        } else if (isInMine(loc)) {
            // Respecte le charbon : on renvoie le bon matériau (charbon ou stone) pour ce joueur.
            Material mat = getBlockForPlayer(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    loc.getWorld());
            player.sendBlockChange(loc, mat.createBlockData());
        } else {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    // Toutes les secondes : les joueurs DANS la mine deviennent invisibles entre eux,
    // ceux qui sortent se revoient normalement.
    private void startHidePlayersTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player a : Bukkit.getOnlinePlayers()) {
                boolean aInMine = isPlayerInMineArea(a.getLocation());

                // (On NE passe PAS en Aventure : ça bloquerait aussi le minage.
                //  Le placement est bloqué par packet + BlockPlaceEvent à la place.)

                // --- Masquage des joueurs entre eux dans la mine ---
                for (Player b : Bukkit.getOnlinePlayers()) {
                    if (a == b) continue;
                    boolean bInMine = isPlayerInMineArea(b.getLocation());
                    if (aInMine || bInMine) {
                        a.hidePlayer(this, b);
                    } else {
                        a.showPlayer(this, b);
                    }
                }
            }
        }, 20L, 20L); // démarre après 1s, répète chaque 1s
    }

    @Override
    public void onDisable() {
        // On sauvegarde tous les joueurs connectés avant l'arrêt.
        if (dataConfig != null) saveAllPlayers();
        // Sauvegarde des parcelles (progression OneBlock : phase/blocs/cycle, banque, permissions...).
        if (parcelleManager != null) parcelleManager.save();
        // Retire les entités d'hologrammes (non persistantes) et sauvegarde leur définition.
        if (holoManager != null) holoManager.shutdown();
        // Retire les mobs de spawner (recréés au démarrage) + sauvegarde leur état.
        if (spawnerManager != null) { spawnerManager.removeAllMobs(); spawnerManager.saveSpawners(); }
        // Retire les marchands (Caravane) encore présents (entités non persistantes).
        if (merchantManager != null) merchantManager.removeAll();
        if (auctionManager != null) auctionManager.save();
        if (coinflipManager != null) coinflipManager.save();
        if (endManager != null) endManager.save();
        if (crateManager != null) crateManager.shutdown();
        // Sauvegarde de la progression de la visite du Log Pose.
        if (logPoseManager != null) logPoseManager.save();
        if (wallManager != null) wallManager.save();
        // Sauvegarde de l'économie maison (soldes BigInteger).
        if (customEco != null) customEco.save();
        // Sauvegarde des Fragments de Souvenir (Acte IV).
        if (fragmentManager != null) fragmentManager.save();
        // Progression des quêtes quotidiennes (tenue en mémoire pendant la partie).
        if (questManager != null) questManager.saveAll();
        getLogger().info("PrivateMines est desactive. A bientot !");
    }

    // Récupère le service d'économie via Vault. Si absent, on prévient dans la console.
    private void setupEconomy() {
        // Économie MAISON en BigInteger (remplace Essentials). On l'enregistre comme provider Vault
        // prioritaire pour que tout le plugin (et les autres) l'utilisent. Précision infinie, max 999ZZ+.
        customEco = new EconomyManager(this);
        getServer().getServicesManager().register(Economy.class, customEco, this,
                org.bukkit.plugin.ServicePriority.Highest);
        economy = customEco;
        getLogger().info("Economie MAISON (BigInteger) connectee : " + economy.getName());
    }

    // Sauvegarde périodique de l'économie maison (toutes les 2 min) pour ne rien perdre en cas de crash.
    private void startEconomyAutosave() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (customEco != null) customEco.save();
            if (fragmentManager != null) fragmentManager.save();
            // Parcelles (progression OneBlock, banque d'île...) : évite de tout perdre en cas de crash.
            if (parcelleManager != null) parcelleManager.save();
        }, 2400L, 2400L); // 2400 ticks = 2 min
    }

    // Swap inventaire vers la mine si le joueur vient du spawn ou de sa parcelle.
    void swapToMineIfNeeded(Player player) {
        if (!isInMine(player)) {
            enterMine(player);
            player.setWorldBorder(null); // supprime la WorldBorder personnelle (parcelle)
        }
    }

    // Téléporte le joueur vers la mine choisie ("A" ou "B").
    private void tpToMine(Player player, String mine) {
        swapToMineIfNeeded(player);
        // Filet de sécurité : swapToMineIfNeeded ne fait RIEN si le joueur est déjà dans la mine,
        // donc l équipement n était jamais revérifié. Un joueur qui perdait son sac (créatif, ou
        // toute autre voie que le drop, bloqué par LockedItemListener) refaisait /mine sans effet.
        // Ces trois appels sont idempotents : ils ne donnent que ce qui manque réellement.
        if (acteManager != null && acteManager.isActe2Done(player)) {
            giveBag(player);
            givePickaxe(player);
            giveOrdi(player);
        }
        playerMine.put(player.getUniqueId(), mine);
        // Parcours de découverte (Acte III) : étape finale « pars vers une nouvelle terre ».
        tutorial.onMineReached(player);
        World world = Bukkit.getWorld("world");

        // Point d'arrivée : au-dessus de la surface de LA mine choisie (zone propre pour l'Arc II).
        Location tp = mineTopSpawn(player);

        player.teleport(tp);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            // On change de mine : on repart d'une mine PLEINE et FRAÎCHE.
            // - charbon régénéré à CHAQUE fois (sinon on garde le charbon de l'ancienne mine
            //   -> mauvais % : la Mine C gardait ses positions en passant à D, etc.),
            // - pour la Mine A (0% charbon) on vide le charbon résiduel d'une mine précédente.
            if (mineHasCoal(mine)) {
                generateCoalPositions(player, world);
            } else {
                getCoalPositions(player).clear();
                getCoalBlockPositions(player).clear();
                getIronPositions(player).clear();
                getAbyssalPositions(player).clear();
            }
            sendFakeMine(player, world);
            getBroken(player).clear();
            // On (re)démarre le compte à rebours de reset : 60 s pleines à partir de MAINTENANT.
            armMineReset(player);
            player.teleport(tp);
            player.setFallDistance(0f);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            player.sendMessage("§7» " + mineDisplayName(mine) + " §7— Bon minage ! ⛏");
        }, 20L);
    }

    // Chaque seconde : met à jour le scoreboard des joueurs DANS la mine ; le retire pour les autres.
    private void startScoreboardTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Célérité (Haste) : effet actif tant que le joueur est dans sa mine.
                refreshHasteEffect(player);
                // Allonge : portée de minage bonus active uniquement dans la mine.
                refreshReachAttribute(player);
                // Vol (enchant « Vol ») : autorisé UNIQUEMENT dans la BOÎTE PHYSIQUE de la mine du joueur
                // (100×100×50 centrée sur SA mine), pas dans la zone logique élargie — sinon on volait
                // « autour » de la mine. On se coupe dès qu'on sort de ce volume précis.
                boolean enMine = isInMine(player);
                boolean dansBoiteMine = enMine && isInMineFlyZone(player, player.getLocation());
                if (!dansBoiteMine) disableFlyOutsideMine(player);
                // Vol mémorisé : si le joueur avait activé /fly et qu'il est (re)venu DANS sa mine,
                // on le lui rend tout seul (reco, mort, re-tp) — sans qu'il retape la commande.
                else if (wantsFly(player.getUniqueId()) && enchantManager != null && enchantManager.hasFly(player)
                        && !player.getAllowFlight()
                        && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    int flyLvl = enchantManager.getFlyLevel(player);
                    player.setFlySpeed(flyLvl >= 3 ? 0.15f : flyLvl >= 2 ? 0.1f : 0.05f);
                }

                // Malus de minage du Vol : il ne doit exister QUE pendant un vol réel DANS la mine.
                // Toute autre situation (hors mine, au sol, Vol désarmé, créatif) le retire — sinon
                // il restait collé au joueur à la parcelle ou au spawn.
                boolean malusMerite = dansBoiteMine
                        && player.isFlying()
                        && player.getAllowFlight()
                        && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
                boolean hasFatigue = player.hasPotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
                if (malusMerite && !hasFatigue) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 0, false, false, false));
                } else if (!malusMerite && hasFatigue) {
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
                }
                // Scoreboard : version parcelle (niveau d'île) sur la parcelle, version complète ailleurs.
                if ("parcelle".equals(playerZone.get(player.getUniqueId()))) {
                    updateParcelleScoreboard(player);
                } else {
                    updateScoreboard(player);
                }
            }
        }, 20L, 6L);
    }

    // Toutes les 60 secondes : remet à zéro les compteurs blocs/min de tous les joueurs.

    // Affiche, chaque seconde, les arêtes de la zone visualisée en particules (pour le joueur seul).
    private void startZoneParticleTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (java.util.Map.Entry<java.util.UUID, java.util.Set<String>> entry : visualizingZone.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;
                // On dessine CHAQUE zone visualisée par ce joueur.
                for (String zoneName : entry.getValue()) {
                    int[] b = zones.getBounds(zoneName);
                    if (b == null) continue;
                    World world = Bukkit.getWorld(zones.getWorld(zoneName));
                    if (world == null || player.getWorld() != world) continue;
                    drawZoneEdges(player, world, b);
                }
            }
        }, 20L, 20L); // chaque seconde
    }

    // Dessine les 4 MURS verticaux pleins de la zone en particules, pour CE joueur seulement.
    private void drawZoneEdges(Player player, World world, int[] b) {
        double minX = b[0], minZ = b[2];
        double maxX = b[3] + 1, maxZ = b[5] + 1; // +1 pour englober le dernier bloc

        // Hauteur : 10 blocs sous le joueur -> 10 au-dessus (le mur suit sa hauteur).
        double minY = player.getLocation().getY() - 10;
        double maxY = player.getLocation().getY() + 10;

        double step = 2.0; // espacement (2 = plus léger, évite le lag sur grandes zones)
        org.bukkit.Particle particle = org.bukkit.Particle.HAPPY_VILLAGER;

        // Murs Nord et Sud (le long de X, aux deux Z extrêmes), remplis verticalement.
        for (double x = minX; x <= maxX; x += step) {
            for (double y = minY; y <= maxY; y += step) {
                spawn(player, world, x, y, minZ, particle);
                spawn(player, world, x, y, maxZ, particle);
            }
        }
        // Murs Est et Ouest (le long de Z, aux deux X extrêmes), remplis verticalement.
        for (double z = minZ; z <= maxZ; z += step) {
            for (double y = minY; y <= maxY; y += step) {
                spawn(player, world, minX, y, z, particle);
                spawn(player, world, maxX, y, z, particle);
            }
        }
    }

    // Envoie une particule à UN seul joueur (les autres ne la voient pas).
    private void spawn(Player player, World world, double x, double y, double z, org.bukkit.Particle particle) {
        player.spawnParticle(particle, new Location(world, x, y, z), 1, 0, 0, 0, 0);
    }

    // ===== GUI DES ZONES =====
    private static final String ZONE_LIST_TITLE = "§8Zones protegees";
    private static final String ZONE_EDIT_PREFIX = "§8Zone : ";

    // Menu listant toutes les zones (1 item par zone).
    void openZoneListMenu(Player player) {
        java.util.Set<String> names = zones.getZoneNames();
        int size = Math.max(9, ((names.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        Inventory menu = Bukkit.createInventory(null, size, ZONE_LIST_TITLE);

        for (String name : names) {
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Casser : " + (zones.getFlag(name, "break") ? "§aprotege" : "§cautorise"));
            lore.add("§7Poser : " + (zones.getFlag(name, "place") ? "§aprotege" : "§cautorise"));
            lore.add("§7PvP : " + (zones.getFlag(name, "pvp") ? "§aprotege" : "§cautorise"));
            boolean viz = getVisualized(player.getUniqueId()).contains(name);
            lore.add("§7Particules : " + (viz ? "§avisibles" : "§7cachees"));
            lore.add("§8Clic pour gerer.");
            meta.setLore(lore);
            item.setItemMeta(meta);
            menu.addItem(item);
        }
        player.openInventory(menu);
    }

    // Sous-menu d'une zone : 3 laines (flags) + renommer + supprimer.
    private void openZoneEditMenu(Player player, String zoneName) {
        editingZone.put(player.getUniqueId(), zoneName);
        Inventory menu = Bukkit.createInventory(null, 27, ZONE_EDIT_PREFIX + zoneName);

        // Déco vitres noires.
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler);

        // Laines : vert = protégé (action bloquée), rouge = autorisé.
        menu.setItem(10, flagWool("break", "Casser", zones.getFlag(zoneName, "break")));
        menu.setItem(12, flagWool("place", "Poser", zones.getFlag(zoneName, "place")));
        menu.setItem(14, flagWool("pvp", "PvP", zones.getFlag(zoneName, "pvp")));
        // Spawn des mobs : vert = spawn BLOQUÉ, rouge = spawn AUTORISÉ.
        menu.setItem(19, flagWool("mobs", "Mobs hostiles", zones.getFlag(zoneName, "mobs")));
        menu.setItem(20, flagWool("animals", "Animaux/neutres", zones.getFlag(zoneName, "animals")));

        // Renommer (enclume) + Supprimer (barrière).
        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta rm = rename.getItemMeta();
        rm.setDisplayName("§eRenommer la zone");
        rm.setLore(java.util.List.of("§7Clic : tape le nouveau nom dans le chat."));
        rename.setItemMeta(rm);
        menu.setItem(16, rename);

        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta dm = delete.getItemMeta();
        dm.setDisplayName("§cSupprimer la zone");
        delete.setItemMeta(dm);
        menu.setItem(22, delete);

        // Bouton de visualisation par particules (vert si actif, gris sinon).
        boolean visualizing = getVisualized(player.getUniqueId()).contains(zoneName);
        ItemStack view = new ItemStack(visualizing ? Material.ENDER_EYE : Material.ENDER_PEARL);
        ItemMeta vm = view.getItemMeta();
        vm.setDisplayName(visualizing ? "§aParticules : ACTIVEES" : "§bVoir la zone (particules)");
        vm.setLore(java.util.List.of("§7Clic pour " + (visualizing ? "cacher" : "afficher") + " le contour."));
        view.setItemMeta(vm);
        menu.setItem(18, view);

        // Bouton RETOUR vers la liste des zones.
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7« Retour a la liste");
        back.setItemMeta(bm);
        menu.setItem(26, back);

        player.openInventory(menu);
    }

    // Crée une laine verte (protégé) ou rouge (autorisé) pour un flag donné.
    private ItemStack flagWool(String flag, String label, boolean prot) {
        ItemStack wool = new ItemStack(prot ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName((prot ? "§a" : "§c") + label + " : " + (prot ? "PROTEGE" : "AUTORISE"));
        meta.setLore(java.util.List.of("§7Clic pour basculer."));
        // On stocke le nom du flag dans le lore caché via le displayName ? On le retrouvera par le label.
        wool.setItemMeta(meta);
        return wool;
    }

    ItemStack makeFiller() {
        ItemStack f = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta(); m.setDisplayName(" "); f.setItemMeta(m);
        return f;
    }

    // Helper partagé : item avec nom + lore (utilisé par holos, classements, blockvalue...).
    // Item nommé avec lore optionnel (0, 1 ou plusieurs lignes via varargs).
    // Un unique argument null (namedItem(mat, name, null)) = pas de lore.
    ItemStack namedItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String s : lore) if (s != null) lines.add(s);
            if (!lines.isEmpty()) m.setLore(lines);
        }
        it.setItemMeta(m);
        return it;
    }

    // Gestion des clics dans le menu de sélection de mine.
    @EventHandler
    public void onMineMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        int page = minePageFromTitle(event.getView().getTitle());
        if (page == 0) return; // pas le menu des mines
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();

        // Retour au menu des enchantements (slot 0).
        if (raw == MINE_BACK_SLOT) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
            enchantManager.openEnchantMenu(player);
            return;
        }

        // Navigation entre arcs (flèches).
        if (raw == MINE_PREV_SLOT && page > 1) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
            openMineMenu(player, page - 1);
            return;
        }
        if (raw == MINE_NEXT_SLOT && page < totalArcPages()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
            openMineMenu(player, page + 1);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // On retrouve la mine cliquée par l'icône (chaque MineDef a une icône UNIQUE).
        MineDef def = null;
        for (MineDef d : MINES) if (d.icon == clicked.getType()) { def = d; break; }
        if (def == null) return; // clic sur une vitre / bouton du menu

        // ARC II — la Montagne Inversée (mine 22, code V) exige le Chapeau de paille (item-clé
        // East Blue), aussi bien pour la débloquer que pour y entrer une fois débloquée.
        if ("V".equals(def.code) && !hasFoundStrawHat(player)) {
            player.sendMessage("§c🎩 Il te faut le §e§lChapeau de paille §c(caché dans la §5Passe des Adieux§c) pour franchir la §eMontagne Inversée§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Déjà débloquée (ou mine de départ) → on y va directement.
        if (hasUnlockedMine(player, def.code)) {
            player.closeInventory();
            tpToMine(player, def.code);
            return;
        }

        // Tant que l'Apprentissage n'est pas terminé, on ne peut acheter aucune nouvelle mine.
        // (Progression linéaire : bloquer la 1re mine payante bloque toutes les suivantes.)
        if (tutorial != null && !tutorial.isDone(player)) {
            player.sendMessage("§cTermine d'abord l'§dApprentissage§c pour débloquer les nouvelles mines.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Progression linéaire : il faut avoir débloqué la mine précédente.
        String prev = previousMineCode(def.code);
        if (prev != null && !hasUnlockedMine(player, prev)) {
            player.sendMessage("§cDébloque d'abord " + mineDef(prev).shortName() + "§c avant " + def.shortName() + "§c.");
            return;
        }

        // Achat.
        if (economy == null) return;
        double bal = economy.getBalance(player);
        if (bal < def.cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + (int) def.cost + "$§c (tu as §6" + formatNumber(bal) + "$§c).");
            return;
        }
        economy.withdrawPlayer(player, def.cost);
        unlockMine(player, def.code);
        // Quête quotidienne « Ruée vers l or » : débloquer 2 mines dans la journée.
        if (questManager != null) questManager.onMineUnlocked(player);
        dataConfig.set(player.getUniqueId() + ".unlockedMine" + def.code, true);
        try { dataConfig.save(dataFile); } catch (java.io.IOException ignored) {}
        updateMineTag(player);
        player.sendMessage(def.menuName() + " §adébloquée ! §6-" + (int) def.cost + "$");
        player.closeInventory();
        tpToMine(player, def.code);
        // Indice : débloquer la mine 21 (code U) révèle la quête du Chapeau de paille (clé de l'Arc II)
        // et son taux de drop par bloc miné.
        if (STRAW_HAT_MINE_CODE.equals(def.code) && !hasFoundStrawHat(player)) {
            String pct = String.format(java.util.Locale.FRANCE, "%.2f", STRAW_HAT_DROP_PCT).replace(".", ",");
            player.sendMessage("");
            player.sendMessage("§e🎩 §6§lLégende de la Passe des Adieux");
            player.sendMessage("§7Un §e🎩 Chapeau de paille §7dort dans ces profondeurs.");
            player.sendMessage("§7Chaque bloc miné ici a §e" + pct + "% §7de le révéler.");
            player.sendMessage("§7Trouve-le : c'est la §eclé§7 pour franchir la §eMontagne Inversée §7(mine 22).");
            player.sendMessage("");
        }
        // ARC II (Alabasta) : débloquer la mine 22 (code "V" = Montagne Inversée) allume l'objectif
        // « Trouve le Gardien du Seuil » sur la BossBar (départ de la visite du Log Pose).
        if ("V".equals(def.code) && logPoseManager != null && !logPoseManager.isVisitDone(player)) {
            logPoseManager.refreshObjective(player);
        }
    }

    // Lit le spawn depuis config.yml, ou null s'il n'existe pas.
    Location getSpawnLocation() {
        if (!getConfig().contains("spawn.world")) {
            return null;
        }
        World w = Bukkit.getWorld(getConfig().getString("spawn.world"));
        if (w == null) return null;
        return new Location(w,
                getConfig().getDouble("spawn.x"),
                getConfig().getDouble("spawn.y"),
                getConfig().getDouble("spawn.z"),
                (float) getConfig().getDouble("spawn.yaw"),
                (float) getConfig().getDouble("spawn.pitch"));
    }

    // ===== Ecoute des packets de minage (BLOCK_DIG) et de clic droit (BLOCK_PLACE) =====
    private void registerDigListener() {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // --- Clic droit / placement : on bloque TOUT placement dès que le joueur est dans la mine ---
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_PLACE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                com.comphenix.protocol.wrappers.MovingObjectPositionBlock mop =
                        packet.getMovingBlockPositions().read(0);
                BlockPosition pos = mop.getBlockPosition();
                Location loc = new Location(event.getPlayer().getWorld(),
                        pos.getX(), pos.getY(), pos.getZ());
                Player player = event.getPlayer();

                // On bloque si la face cliquée est dans la mine OU si le JOUEUR est dans/sur la mine.
                if (isInMine(loc) || isPlayerInMineArea(player.getLocation())) {
                    event.setCancelled(true);

                    // On efface UNIQUEMENT le bloc fantôme posé (la case cliquée + au-dessus),
                    // SANS reboucher les trous déjà minés (sinon la mine se "reset" trop tôt).
                    Bukkit.getScheduler().runTask(PrivateMines.this, () -> {
                        // Case cliquée : pierre si dans la mine ET pas déjà cassée, sinon air.
                        refreshSingleBlock(player, loc);
                        refreshSingleBlock(player, loc.clone().add(0, 1, 0));
                        player.updateInventory();
                    });
                }
            }
        });

        // --- Minage (clic gauche) ---
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                // La phase du minage (début, fin, annulation...).
                EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

                // La position du bloc visé.
                BlockPosition pos = packet.getBlockPositionModifier().read(0);
                Location loc = new Location(event.getPlayer().getWorld(),
                        pos.getX(), pos.getY(), pos.getZ());

                // On ne réagit que si le bloc est dans la mine.
                if (!isInMine(loc)) {
                    return;
                }

                Player player = event.getPlayer();

                // On ne mine QU'AVEC la Pioche du Mineur en main : mains nues / épée / autre outil
                // ne cassent rien (corrige le « one-shot à la main »). En créatif le clic gauche casse
                // instantanément côté client, donc on bloque aussi l'événement pour empêcher le
                // cassage réel non crédité.
                if (!isPickaxe(player.getInventory().getItemInMainHand())) {
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) event.setCancelled(true);
                    Bukkit.getScheduler().runTask(PrivateMines.this, () -> refreshSingleBlock(player, loc));
                    return;
                }

                // Selon le mode de jeu, le bloc est "cassé" à un moment différent :
                // - CRÉATIF : cassage instantané -> on valide au START.
                // - SURVIE  : le client mine progressivement (vitesse pioche vanilla)
                //             et envoie STOP quand l'animation est terminée -> on valide au STOP.
                boolean broken;
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    broken = (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK);
                } else if (digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
                    // Minage normal : validé à la fin de l'animation.
                    broken = true;
                } else if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
                    // Instamine (haute Efficacité) : cassé en 1 tick, aucun STOP envoyé -> on valide ici.
                    Material represented;
                    if (getAbyssalPositions(player).contains(loc)) {
                        represented = Material.DEEPSLATE_IRON_ORE; // 5e minerai = fer des abîmes (dureté ardoise)
                    } else if (getIronPositions(player).contains(loc)) {
                        represented = Material.IRON_ORE; // 4e minerai = fer (même dureté que le client)
                    } else if (getCoalBlockPositions(player).contains(loc)) {
                        represented = Material.COAL_BLOCK; // 3e minerai = bloc de charbon (même dureté que le client)
                    } else if (getCoalPositions(player).contains(loc)) {
                        represented = Material.COAL_ORE;
                    } else {
                        represented = Material.STONE;
                    }
                    broken = isInstaMine(player, represented);
                } else {
                    broken = false;
                }

                // Minage EN VOL : ~2× plus lent. On ignore le bloc si le cooldown de vol n'est pas écoulé.
                if (broken && isFlyMiningBlocked(player)) {
                    // Le bloc reste : on le renvoie au client pour qu'il ne disparaisse pas visuellement.
                    Bukkit.getScheduler().runTask(PrivateMines.this, () -> refreshSingleBlock(player, loc));
                    broken = false;
                }

                if (broken) {
                    Bukkit.getScheduler().runTask(PrivateMines.this, () -> {
                        // Crédite le bloc central. Si déjà cassé, on n'enchaîne pas l'explosion.
                        if (!creditMineBlock(player, loc.clone())) return;
                        // Quêtes quotidiennes : MÊME RÈGLE que l'XP de pioche ci-dessous — on ne compte
                        // que le bloc cassé à la pioche. Sinon une salve d'Explosion (250 blocs) ferait
                        // tomber les 5 000 blocs de la quête en une minute.
                        if (questManager != null) questManager.onMined(player, lastMinedWasOre);
                        // Niveau de pioche (barre d'XP) : uniquement le bloc miné DIRECTEMENT par le
                        // joueur, PAS les blocs cassés par les enchants de zone (Explosion, Fracture...).
                        addPickaxeXp(player);
                        // Compétences : explosion (zone), forage (tunnel), fracture (éclairs), vein (autour).
                        triggerExplosion(player, loc.clone());
                        triggerForage(player, loc.clone());
                        triggerFracture(player, loc.clone());
                        triggerVein(player, loc.clone());
                        triggerColonne(player, loc.clone());
                        triggerHarpon(player, loc.clone());
                        triggerFleche(player, loc.clone());
                        triggerTnt(player, loc.clone());
                        triggerReflux(player, loc.clone());
                        triggerGouffre(player, loc.clone());
                        triggerCyclone(player, loc.clone());
                        triggerFortune(player);
                        triggerArmorDrop(player);
                        triggerStrawHat(player);
                        triggerPlume(player);
                    });
                }
            }
        });

        getLogger().info("Ecouteur de minage (ProtocolLib) active.");
    }

    // Crédite UN bloc de la mine au joueur (sac ou charbon), met à jour l'affichage fantôme.
    // Renvoie false si le bloc était déjà cassé depuis le dernier reset (anti double-comptage).
    private boolean creditMineBlock(Player player, Location loc) {
        if (getBroken(player).contains(loc)) return false;
        player.sendBlockChange(loc, Material.AIR.createBlockData());
        getBroken(player).add(loc);
        String mine = getPlayerMine(player);
        boolean isAbyssal   = getAbyssalPositions(player).contains(loc);
        boolean isIron      = !isAbyssal && getIronPositions(player).contains(loc);
        boolean isCoalBlock = !isAbyssal && !isIron && getCoalBlockPositions(player).contains(loc);
        boolean isCoalOre   = !isAbyssal && !isIron && !isCoalBlock && getCoalPositions(player).contains(loc);
        if (isAbyssal)   getAbyssalPositions(player).remove(loc);
        if (isIron)      getIronPositions(player).remove(loc);
        if (isCoalBlock) getCoalBlockPositions(player).remove(loc);
        if (isCoalOre)   getCoalPositions(player).remove(loc);
        // Fer des abîmes + fer + bloc de charbon = « minerai précieux » (à part, à leur vrai prix).
        boolean isCoal = isCoalOre || isCoalBlock || isIron || isAbyssal;
        // Valeur du bloc selon son type, au PRIX de la meilleure mine débloquée (appliqué
        // partout : miner de la pierre en mine 1 rapporte le prix de ta dernière mine).
        double value;
        if (isAbyssal)      value = getAbyssalSellPrice(player);
        else if (isIron)         value = getIronSellPrice(player);
        else if (isCoalBlock)    value = getCoalBlockSellPrice(player);
        else if (isCoalOre) value = getCoalSellPrice(player);
        else                value = getStoneSellPrice(player);
        // Bonus « valeur des blocs » des familiers équipés + armures d'Oublié portées.
        double blockValuePct = getWornBonus(player).blockValuePct;
        double valAvantBlockValue = value; // pour mesurer l'argent bonus « valeur des blocs »
        if (blockValuePct != 0) value *= (1.0 + blockValuePct / 100.0);
        double gainBlockValue = value - valAvantBlockValue; // argent en plus grâce à ce bonus (par unité)
        // Sel de Contrebande : bonus de valeur SI le sac est rempli à >= 90 % (risque/récompense).
        double gainContrebande = 0.0;
        int contrebandeLvl = enchantManager.getContrebandeLevel(player);
        if (contrebandeLvl > 0) {
            int cap0 = getCapacity(player);
            if (cap0 > 0 && getStock(player) >= cap0 * EnchantManager.CONTREBANDE_FILL) {
                double valAvantContre = value;
                value *= (1.0 + enchantManager.contrebandeBonus(contrebandeLvl));
                gainContrebande = value - valAvantContre; // argent en plus grâce au Sel (par unité)
            }
        }
        // Dîme du Passeur : chaque Nᵉ bloc miné est « la paie » -> payé x10 (péage).
        boolean dimePaid = false;
        double gainDime = 0.0;
        int dimeLvl = enchantManager.getDimeLevel(player);
        if (dimeLvl > 0) {
            int threshold = enchantManager.dimeThreshold(dimeLvl);
            int c = getDimeCounter(player) + 1;
            if (c >= threshold) {
                c = 0;
                dimePaid = true;
                double valAvantDime = value;
                value *= enchantManager.dimeMult(dimeLvl); // ×10 (niv 1-20) puis +1/niveau jusqu'à ×15 au niv 25
                gainDime = value - valAvantDime; // argent en plus grâce à la Dîme (par unité)
            }
            setDimeCounter(player.getUniqueId(), c);
        }
        // Chance de DOUBLE-BLOC (familiers + armures d'Oublié portées) : le bloc compte double
        // (2× argent ET 2× remplissage du sac), comme si on avait miné 2 blocs. Tirage PAR bloc,
        // vaut aussi pour les blocs cassés par les enchants de zone (tous passent par ici).
        int units = 1;
        double dblPct = getWornBonus(player).doubleBlockPct;
        if (dblPct > 0 && rng.nextDouble() < dblPct / 100.0) units = 2;
        // Tout passe par le sac : on ajoute les unités + leur valeur (si le sac n'est pas plein).
        int current = getStock(player);
        int cap = getCapacity(player);
        if (current < cap) {
            int added = Math.min(units, cap - current); // on ne dépasse jamais la capacité du sac
            setStock(player, current + added);
            addBagValue(player, value * added);
            // On mémorise à part le charbon (nombre + valeur) pour le vendre à SON vrai prix.
            if (isCoal) {
                setCoalStock(player, getCoalStock(player) + added);
                addCoalValue(player, value * added);
            }
            // ── Stats de BONUS (seulement sur ce qui a réellement été encaissé) ──
            java.util.UUID uid = player.getUniqueId();
            // Double-bloc : l'unité SUPPLÉMENTAIRE (added-1 si le double a tenu dans le sac) compte.
            int extraUnits = added - 1; // 0 ou 1 selon le tirage + la place restante
            if (extraUnits > 0) {
                addBonusCount(uid, "dblBlocks", extraUnits);
                addBonusMoney(uid, "dblMoney", value * extraUnits);
            }
            if (gainBlockValue  > 0) addBonusMoney(uid, "blockValueMoney",  gainBlockValue  * added);
            if (gainContrebande > 0) addBonusMoney(uid, "contrebandeMoney", gainContrebande * added);
            if (dimePaid && gainDime > 0) {
                addBonusCount(uid, "dimeBlocks", added);
                addBonusMoney(uid, "dimeMoney", gainDime * added);
            }
            // Suivi PAR TYPE pour /bv : on note le bloc + sa valeur en attente de vente.
            String bk = isAbyssal ? BK_ABYSSAL : isIron ? BK_IRON
                      : isCoalBlock ? BK_COALBLOCK : isCoalOre ? BK_COAL : BK_STONE;
            addBlockMined(uid, bk, added, value * added);
        }
        // Feedback de la paie : seulement le gong (pop-up texte retirée à la demande du joueur).
        if (dimePaid && current < cap) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BELL_USE, 1f, 0.6f);
        }
        incrementBlocksMinute(player);
        addTotalMined(player);
        // Quêtes quotidiennes : on RETIENT seulement la nature du bloc ; le comptage se fait
        // à l'appelant, pour ne compter QUE le bloc cassé à la pioche (voir lastMinedWasOre).
        lastMinedWasOre = isAbyssal || isIron || isCoalBlock || isCoalOre;
        // Parcours de découverte (Acte III) : compte les blocs pour l'étape « miner ».
        tutorial.onBlockMined(player);
        // Affichage discret du boost de vente actif (ActionBar), throttlé à ~1×/seconde
        // pour ne pas spammer et ne pas écraser en continu les autres ActionBars ponctuelles.
        showSellBoostActionBar(player);
        return true;
    }

    // Throttle par joueur pour l'ActionBar du boost de vente (dernier affichage en ms).
    private final java.util.Map<java.util.UUID, Long> sellBoostBarThrottle = new java.util.HashMap<>();

    private void showSellBoostActionBar(Player player) {
        java.util.UUID id = player.getUniqueId();
        long secs = getSellBoostSecondsLeft(id);
        if (secs <= 0) return;
        long now = System.currentTimeMillis();
        Long last = sellBoostBarThrottle.get(id);
        if (last != null && now - last < 1000L) return; // max 1 affichage/seconde
        sellBoostBarThrottle.put(id, now);
        double mult = getSellBoostMult(id);
        String multTxt = (mult == Math.floor(mult))
                ? String.valueOf((long) mult)
                : String.format(java.util.Locale.US, "%.1f", mult).replace('.', ',');
        String timer = String.format("%d:%02d", secs / 60, secs % 60);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("§6⚡ Vente §e×" + multTxt + " §7— §e" + timer));
    }

    // Déclenche l'explosion autour du bloc central selon le niveau de la compétence Explosion.
    // Hybride : la CHANCE de déclenchement et le RAYON augmentent tous deux avec le niveau,
    // et le rayon est fractionnaire -> la zone grandit en continu à chaque niveau (pas par paliers).
    private void triggerExplosion(Player player, Location center) {
        int lvl = enchantManager.getExplosionLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.explosionChance(lvl)) return;
        // N blocs exactement, pris du plus PROCHE au plus loin -> sphère toujours pleine.
        // (Un rayon fixe donnerait des paliers en escalier sur la grille : cf. explosionBlocks.)
        int count = enchantManager.explosionBlocks(lvl);
        int R = Math.max(1, (int) Math.ceil(Math.cbrt(count / 4.18879)) + 1);
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        java.util.List<int[]> pool = new ArrayList<>();   // {distance², dx, dy, dz}
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // centre déjà crédité
                    if (!isInMine(new Location(w, cx + dx, cy + dy, cz + dz))) continue;
                    pool.add(new int[]{dx * dx + dy * dy + dz * dz, dx, dy, dz});
                }
            }
        }
        pool.sort(java.util.Comparator.comparingInt(k -> k[0]));
        int blown = 0;
        for (int[] k : pool) {
            if (blown >= count) break;
            if (creditMineBlock(player, new Location(w, cx + k[1], cy + k[2], cz + k[3]))) blown++;
        }
        if (blown > 0) {
            addEnchantBlocks(player, "explosion", blown);
            player.playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            // POOF = la PETITE explosion vanilla. EXPLOSION (le gros nuage) noyait l'écran :
            // l'enchant se déclenche jusqu'à 10 % des coups minés, il faut que ça reste discret.
            player.spawnParticle(org.bukkit.Particle.POOF, center, 3, 0.25, 0.25, 0.25, 0.01);
        }
    }

    // Forage : creuse un tunnel droit (largeur 1) dans la direction où regarde le joueur.
    // Longueur fractionnaire -> le tunnel s'allonge en continu à chaque niveau.
    private void triggerForage(Player player, Location center) {
        int lvl = enchantManager.getForageLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.forageChance(lvl)) return;
        double length = enchantManager.forageLength(lvl);

        // Axe dominant du regard du joueur -> direction du tunnel.
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection();
        double ax = Math.abs(dir.getX()), ay = Math.abs(dir.getY()), az = Math.abs(dir.getZ());
        int sx = 0, sy = 0, sz = 0;
        if (ax >= ay && ax >= az)      sx = dir.getX() >= 0 ? 1 : -1;
        else if (ay >= ax && ay >= az) sy = dir.getY() >= 0 ? 1 : -1;
        else                           sz = dir.getZ() >= 0 ? 1 : -1;

        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int dug = 0;
        int max = (int) Math.ceil(length);
        for (int i = 1; i <= max; i++) {
            if (i > length) break;
            Location l = new Location(w, cx + sx * i, cy + sy * i, cz + sz * i);
            if (!isInMine(l)) continue;
            if (creditMineBlock(player, l)) dug++;
        }
        if (dug > 0) {
            addEnchantBlocks(player, "forage", dug);
            player.playSound(center, org.bukkit.Sound.BLOCK_BASALT_BREAK, 0.8f, 0.6f);
        }
    }

    // Colonne d'Écume : creuse un puits vertical droit (largeur 1) VERS LE BAS sous le bloc miné.
    // Profondeur fractionnaire -> la colonne s'allonge en continu à chaque niveau (jusqu'à 50 au niv.100).
    private void triggerColonne(Player player, Location center) {
        int lvl = enchantManager.getColonneLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.colonneChance(lvl)) return;
        double depth = enchantManager.colonneDepth(lvl);

        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int dug = 0;
        int max = (int) Math.ceil(depth);
        for (int i = 1; i <= max; i++) {
            if (i > depth) break;
            Location l = new Location(w, cx, cy - i, cz);
            if (!isInMine(l)) break; // au bord bas de la mine : on arrête net.
            if (creditMineBlock(player, l)) dug++;
        }
        if (dug > 0) {
            addEnchantBlocks(player, "colonne", dug);
            player.playSound(center, org.bukkit.Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 0.9f, 0.8f);
            player.spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP, center, 6, 0.2, 0.4, 0.2, 0.02);
        }
    }

    // Reflux (vague « Abîme ») : ouvre un couloir 1x2 (largeur 1, hauteur 2) à l'OPPOSÉ du regard
    // du joueur (derrière lui) — on creuse le chemin de retour sans se retourner.
    private void triggerReflux(Player player, Location center) {
        int lvl = enchantManager.getRefluxLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.refluxChance(lvl)) return;
        double length = enchantManager.refluxLength(lvl);

        // Direction horizontale OPPOSÉE au regard (yaw), arrondie aux 4 points cardinaux.
        org.bukkit.util.Vector look = player.getLocation().getDirection();
        int stepX, stepZ;
        if (Math.abs(look.getX()) >= Math.abs(look.getZ())) {
            stepX = look.getX() >= 0 ? -1 : 1; stepZ = 0; // opposé de l'axe X
        } else {
            stepX = 0; stepZ = look.getZ() >= 0 ? -1 : 1; // opposé de l'axe Z
        }

        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int dug = 0;
        int max = (int) Math.ceil(length);
        for (int i = 1; i <= max; i++) {
            if (i > length) break;
            int lx = cx + stepX * i, lz = cz + stepZ * i;
            // Colonne 1x2 : bloc au niveau du bloc miné + celui juste au-dessus.
            for (int dy = 0; dy <= 1; dy++) {
                Location l = new Location(w, lx, cy + dy, lz);
                if (!isInMine(l)) continue;
                if (creditMineBlock(player, l)) dug++;
            }
        }
        if (dug > 0) {
            addEnchantBlocks(player, "reflux", dug);
            player.playSound(center, org.bukkit.Sound.WEATHER_RAIN, 0.7f, 0.6f);
            player.spawnParticle(org.bukkit.Particle.SPLASH, center, 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // Gouffre (vague « Abîme ») : ouvre un PUITS SPHÉRIQUE centré sous les pieds du joueur — le sol
    // s'effondre. Courte lévitation pour ne pas tomber dedans. Effet rare, gros volume, end-game.
    private void triggerGouffre(Player player, Location center) {
        int lvl = enchantManager.getGouffreLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.gouffreChance(lvl)) return;
        // N blocs exactement, pris du plus PROCHE au plus loin -> sphère toujours pleine.
        // (Un rayon fixe donnerait des paliers en escalier sur la grille : cf. gouffreBlocks.)
        int count = enchantManager.gouffreBlocks(lvl);

        World w = player.getWorld();
        // Centre de la sphère : ~1 bloc sous les pieds du joueur.
        int cx = player.getLocation().getBlockX();
        int cy = player.getLocation().getBlockY() - 1;
        int cz = player.getLocation().getBlockZ();
        int R = Math.max(1, (int) Math.ceil(Math.cbrt(count / 4.18879)) + 1);
        java.util.List<int[]> pool = new ArrayList<>();   // {distance², dx, dy, dz}
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (!isInMine(new Location(w, cx + dx, cy + dy, cz + dz))) continue;
                    pool.add(new int[]{dx * dx + dy * dy + dz * dz, dx, dy, dz});
                }
            }
        }
        pool.sort(java.util.Comparator.comparingInt(k -> k[0]));
        int broken = 0;
        for (int[] k : pool) {
            if (broken >= count) break;
            if (creditMineBlock(player, new Location(w, cx + k[1], cy + k[2], cz + k[3]))) broken++;
        }
        if (broken > 0) {
            addEnchantBlocks(player, "gouffre", broken);
            // Petite lévitation pour ne pas chuter dans le trou qui vient de s'ouvrir.
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.LEVITATION, 20, 0, false, false));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 0.6f);
            player.spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP, player.getLocation(), 20, 0.5, 0.3, 0.5, 0.05);
        }
    }

    // Pluie de Harpons : N harpons tombent du ciel sur des points aléatoires de la SURFACE HAUTE
    // de la mine (la couche que le joueur ne mine jamais), chacun cassant une croix de 5.
    // La chance et le nombre de harpons montent avec le niveau.
    private void triggerHarpon(Player player, Location center) {
        int lvl = enchantManager.getHarponLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.harponChance(lvl)) return;
        int harpons = enchantManager.harponCount(lvl);
        World w = center.getWorld();
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(player), cz = mineCenterZ(player);
        // La croix de 5 est cassée sur la couche du haut (plafond de la mine du joueur).
        int surfaceY = mineFloorY(player) + MINE_HEIGHT - 1;
        // Décalages d'une croix de 5 (centre + 4 orthogonaux horizontaux).
        int[][] cross = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // Traînées + impacts + sons : 2,5 salves par seconde au plus (voir canShowFx). Au-delà,
        // les harpons tombent quand même, ils sont juste invisibles — et on économise jusqu'à
        // 360 appels de particules par salve sautée.
        boolean fx = canShowFx(player, "harpon", FX_COOLDOWN_HARPON);
        int broken = 0;
        for (int h = 0; h < harpons; h++) {
            int ix = cx - half + rng.nextInt(MINE_SIZE);
            int iz = cz - half + rng.nextInt(MINE_SIZE);
            Location impact = new Location(w, ix + 0.5, surfaceY + 0.5, iz + 0.5);
            // Traînée visuelle : le harpon « tombe » du ciel jusqu'au point d'impact.
            // Salve dense (> 20 harpons) : traînée deux fois plus courte, sinon 40 harpons =
            // 320 appels de particules dans le même tick.
            int trailSteps = harpons <= 20 ? 8 : 4;
            if (fx) {
                for (int t = 1; t <= trailSteps; t++) {
                    Location trail = new Location(w, ix + 0.5, surfaceY + t * 2.0, iz + 0.5);
                    player.spawnParticle(org.bukkit.Particle.CRIT, trail, 2, 0.02, 0.4, 0.02, 0.0);
                }
                player.spawnParticle(org.bukkit.Particle.CLOUD, impact, 6, 0.2, 0.05, 0.2, 0.01);
            }
            for (int[] d : cross) {
                Location l = new Location(w, ix + d[0], surfaceY, iz + d[1]);
                if (!isInMine(l)) continue;
                if (creditMineBlock(player, l)) broken++;
            }
        }
        if (broken > 0) {
            addEnchantBlocks(player, "harpon", broken);
            if (fx) {
                player.playSound(center, org.bukkit.Sound.ENTITY_ARROW_HIT, 0.8f, 0.7f);
                player.playSound(center, org.bukkit.Sound.ITEM_TRIDENT_HIT_GROUND, 0.7f, 1.0f);
            }
        }
    }

    // Pluie de Flèches (niv.pioche 55) : une salve de N flèches tombe du ciel sur des points
    // aléatoires de la SURFACE HAUTE de la mine. Chaque flèche PERCE une colonne verticale vers le
    // bas (profondeur = flechePierce). Chance, nombre de flèches et perce montent avec le niveau ;
    // au niveau max (100) : 40 flèches × 25 de perce = ~1000 blocs cassés d'un coup.
    private void triggerFleche(Player player, Location center) {
        int lvl = enchantManager.getFlecheLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.flecheChance(lvl)) return;
        int fleches = enchantManager.flecheCount(lvl);
        int pierce = enchantManager.flechePierce(lvl);
        if (fleches <= 0 || pierce <= 0) return;

        World w = center.getWorld();
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(player), cz = mineCenterZ(player);
        // Point de départ de chute = plafond de la mine ; on perce vers le bas depuis là.
        int surfaceY = mineFloorY(player) + MINE_HEIGHT - 1;
        int broken = 0;
        for (int f = 0; f < fleches; f++) {
            int ix = cx - half + rng.nextInt(MINE_SIZE);
            int iz = cz - half + rng.nextInt(MINE_SIZE);
            // Traînée visuelle : la flèche « tombe » du ciel jusqu'à la surface.
            for (int t = 1; t <= 8; t++) {
                Location trail = new Location(w, ix + 0.5, surfaceY + t * 2.0, iz + 0.5);
                player.spawnParticle(org.bukkit.Particle.CRIT, trail, 1, 0.02, 0.5, 0.02, 0.0);
            }
            // Colonne percée vers le BAS depuis la surface (perce blocs de profondeur).
            for (int d = 0; d < pierce; d++) {
                Location l = new Location(w, ix, surfaceY - d, iz);
                if (!isInMine(l)) break; // atteint le fond de la mine : cette flèche s'arrête.
                if (creditMineBlock(player, l)) broken++;
            }
            player.spawnParticle(org.bukkit.Particle.CLOUD,
                    new Location(w, ix + 0.5, surfaceY + 0.5, iz + 0.5), 4, 0.15, 0.05, 0.15, 0.01);
        }
        if (broken > 0) {
            addEnchantBlocks(player, "fleche", broken);
            player.playSound(center, org.bukkit.Sound.ENTITY_ARROW_SHOOT, 0.9f, 0.8f);
            player.playSound(center, org.bukkit.Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
        }
    }

    // Clé PDC posée sur une TNT amorcée issue de l'enchant : stocke "uuidJoueur;rayon" pour retrouver
    // le propriétaire et la taille de la sphère au moment de l'explosion (voir onEnchantTntExplode).
    private org.bukkit.NamespacedKey tntEnchantKey;

    // Pluie de TNT (niv.pioche 60) : chance faible et stable qu'une VRAIE TNT amorcée spawn 10 blocs
    // au-dessus de la mine, tombe (fuse vanilla ~4 s) et explose. À l'explosion, on annule la casse
    // vanilla et on casse nous-même une SPHÈRE de blocs (rayon = tntRadius) via creditMineBlock (sac).
    private void triggerTnt(Player player, Location center) {
        int lvl = enchantManager.getTntLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.tntChance(lvl)) return;
        double radius = enchantManager.tntRadius(lvl);
        if (radius <= 0) return;

        World w = center.getWorld();
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(player), cz = mineCenterZ(player);
        // Point de spawn aléatoire dans la mine, 10 blocs AU-DESSUS de son plafond.
        int ix = cx - half + rng.nextInt(MINE_SIZE);
        int iz = cz - half + rng.nextInt(MINE_SIZE);
        int spawnY = mineFloorY(player) + MINE_HEIGHT - 1 + 10;
        Location spawn = new Location(w, ix + 0.5, spawnY + 0.5, iz + 0.5);

        if (tntEnchantKey == null) tntEnchantKey = new org.bukkit.NamespacedKey(this, "tnt_enchant");
        org.bukkit.entity.TNTPrimed tnt = w.spawn(spawn, org.bukkit.entity.TNTPrimed.class);
        tnt.setFuseTicks(80); // ~4 s (fuse vanilla)
        tnt.setIsIncendiary(false);
        // On marque la TNT : propriétaire + rayon, pour l'explosion détournée.
        tnt.getPersistentDataContainer().set(tntEnchantKey,
                org.bukkit.persistence.PersistentDataType.STRING,
                player.getUniqueId() + ";" + String.format(java.util.Locale.US, "%.3f", radius));
        player.playSound(spawn, org.bukkit.Sound.ENTITY_TNT_PRIMED, 1f, 1f);

        // Les blocs de la mine sont des FAUX blocs (air côté serveur) : la TNT physique les traverse.
        // On la surveille pendant sa chute et, dès qu'un bloc ENCORE PRÉSENT (non miné) se trouve juste
        // sous elle dans sa colonne, on la fige dessus. Ainsi elle se pose sur le vrai relief creusé
        // (au fond d'un trou déjà miné, pas en l'air à hauteur fixe).
        final int floorY = mineFloorY(player);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (tnt.isDead() || !tnt.isValid()) { cancel(); return; }
                Location p = tnt.getLocation();
                int bx = p.getBlockX(), bz = p.getBlockZ();
                int poseY = premierBlocPleinSous(player, w, bx, p.getBlockY(), bz, floorY);
                // poseY = Y du 1er bloc plein rencontré en descendant ; on se pose juste au-dessus.
                if (p.getY() <= poseY + 1 + 0.02) {
                    tnt.setGravity(false);
                    tnt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    tnt.teleport(new Location(w, bx + 0.5, poseY + 1.0, bz + 0.5));
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    // Cherche, en descendant depuis fromY, le Y du PREMIER bloc encore présent (non miné) de la colonne
    // (bx,bz) dans la mine du joueur. Un bloc est « présent » s'il est dans les bornes de la mine ET
    // n'est PAS dans getBroken (les blocs cassés sont de l'air). Renvoie floorY-1 si toute la colonne
    // est vide (la TNT ira jusqu'au fond).
    private int premierBlocPleinSous(Player player, World w, int bx, int fromY, int bz, int floorY) {
        java.util.Set<Location> broken = getBroken(player);
        for (int y = fromY; y >= floorY; y--) {
            Location bloc = new Location(w, bx, y, bz);
            if (!isInMine(bloc)) continue;          // hors mine : on ignore
            if (broken.contains(bloc)) continue;    // déjà miné (air) : on continue de descendre
            return y;                               // 1er bloc plein rencontré
        }
        return floorY - 1; // colonne entièrement creusée : fond de la mine
    }

    // Explosion d'une TNT de l'enchant : on empêche la casse vanilla (qui ne crédite pas le sac et
    // ignore les bornes de la mine) et on casse nous-même une sphère de blocs autour de l'impact.
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onEnchantTntExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (tntEnchantKey == null) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.TNTPrimed)) return;
        org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) event.getEntity();
        String tag = tnt.getPersistentDataContainer().get(tntEnchantKey,
                org.bukkit.persistence.PersistentDataType.STRING);
        if (tag == null) return;

        // Annule la destruction vanilla des blocs (on gère tout à la main).
        event.blockList().clear();
        event.setYield(0f);

        String[] parts = tag.split(";");
        java.util.UUID owner;
        double radius;
        try {
            owner = java.util.UUID.fromString(parts[0]);
            radius = Double.parseDouble(parts[1]);
        } catch (Exception ex) { return; }
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) return;

        World w = event.getLocation().getWorld();
        int ix = event.getLocation().getBlockX();
        int iy = event.getLocation().getBlockY();
        int iz = event.getLocation().getBlockZ();
        int r = (int) Math.ceil(radius);
        double r2 = radius * radius;
        int broken = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue; // hors de la sphère
                    Location l = new Location(w, ix + dx, iy + dy, iz + dz);
                    if (!isInMine(l)) continue; // reste dans les bornes de la mine
                    if (creditMineBlock(player, l)) broken++;
                }
            }
        }
        if (broken > 0) addEnchantBlocks(player, "tnt", broken);
    }

    // La TNT de l'enchant ne doit PAS blesser le joueur qui mine juste à côté : on annule les dégâts
    // d'explosion causés par une TNT taggée « tnt_enchant ».
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onEnchantTntDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (tntEnchantKey == null) return;
        if (!(event.getDamager() instanceof org.bukkit.entity.TNTPrimed)) return;
        org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) event.getDamager();
        if (tnt.getPersistentDataContainer().has(tntEnchantKey,
                org.bukkit.persistence.PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // L'Œil du Cyclone : à un coup, chance rare de déclencher TA tempête personnelle pendant
    // quelques secondes. Pendant la tempête, des impacts (éclairs visuels) tombent AUTOMATIQUEMENT
    // sur la mine à intervalle régulier et pulvérisent une petite zone (rayon 1-2), sans que le
    // joueur ait à miner. Une seule tempête à la fois par joueur + cooldown interne de 90 s.
    private void triggerCyclone(Player player, Location center) {
        int lvl = enchantManager.getCycloneLevel(player);
        if (lvl <= 0) return;
        java.util.UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        // Déjà une tempête en cours ? on ne relance pas.
        Long activeUntil = cycloneActiveUntil.get(id);
        if (activeUntil != null && activeUntil > now) return;
        // Cooldown pas encore écoulé ?
        Long cdUntil = cycloneCooldownUntil.get(id);
        if (cdUntil != null && cdUntil > now) return;
        // Tirage de déclenchement.
        if (rng.nextDouble() >= enchantManager.cycloneChance(lvl)) return;

        // On lance la tempête.
        double durationSec = enchantManager.cycloneDuration(lvl);
        long durationTicks = Math.round(durationSec * 20.0);
        cycloneActiveUntil.put(id, now + (long) (durationSec * 1000));
        cycloneCooldownUntil.put(id, now + (long) (durationSec * 1000) + CYCLONE_COOLDOWN_MS);

        final World w = center.getWorld();
        final int half = MINE_SIZE / 2;
        final int cx = mineCenterX(player), cz = mineCenterZ(player), fy = mineFloorY(player);
        // Budget de blocs de CETTE tempête : elle s'arrête dès qu'il est épuisé, même si la durée
        // n'est pas écoulée. 200 blocs au niveau max (plafond voulu par le user, refonte 2026-08-22).
        final int[] budget = {enchantManager.cycloneBlocks(lvl)};
        final int blocksPerBolt = enchantManager.cycloneBlocksPerBolt(lvl);

        // Annonce visuelle + sonore du début de tempête.
        player.sendTitle("§c§lCœur de la Tempête", "§7Ta tempête personnelle se déchaîne !", 5, 40, 15);
        player.playSound(center, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        player.playSound(center, org.bukkit.Sound.WEATHER_RAIN, 1.0f, 1.0f);

        // Ticker : toutes les 15 ticks (~0,75 s) on fait tomber `boltsPerBurst` impacts.
        // BukkitRunnable pour pouvoir s'auto-annuler (this.cancel()) une fois la durée écoulée.
        final long[] elapsed = {0L};
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                // Fin de la tempête (durée écoulée OU budget de blocs épuisé OU joueur parti) :
                // on arrête la task et on nettoie l'état actif (le cooldown, lui, est conservé).
                if (elapsed[0] >= durationTicks || budget[0] <= 0 || !player.isOnline()) {
                    cycloneActiveUntil.remove(id);
                    cancel();
                    return;
                }
                elapsed[0] += 15;
                // Le joueur est-il encore dans sa mine ? sinon on laisse filer (pas d'impacts).
                if (!isPlayerInMineArea(player.getLocation())) return;
                // UN éclair par rafale, qui pulvérise les N blocs les plus PROCHES de son point
                // d'impact (même algo que triggerExplosion / triggerGouffre : la boule reste pleine
                // et grandit en continu, là où un rayon fixe avancerait par paliers en escalier).
                int count = Math.min(blocksPerBolt, budget[0]);
                int ix = cx - half + rng.nextInt(MINE_SIZE);
                int iz = cz - half + rng.nextInt(MINE_SIZE);
                int iy = fy + rng.nextInt(MINE_HEIGHT);
                w.strikeLightningEffect(new Location(w, ix, iy, iz));
                int R = Math.max(1, (int) Math.ceil(Math.cbrt(count / 4.18879)) + 1);
                java.util.List<int[]> pool = new ArrayList<>();   // {distance², dx, dy, dz}
                for (int dx = -R; dx <= R; dx++)
                    for (int dy = -R; dy <= R; dy++)
                        for (int dz = -R; dz <= R; dz++) {
                            if (!isInMine(new Location(w, ix + dx, iy + dy, iz + dz))) continue;
                            pool.add(new int[]{dx * dx + dy * dy + dz * dz, dx, dy, dz});
                        }
                pool.sort(java.util.Comparator.comparingInt(k -> k[0]));
                int broken = 0;
                for (int[] k : pool) {
                    if (broken >= count) break;
                    if (creditMineBlock(player, new Location(w, ix + k[1], iy + k[2], iz + k[3]))) broken++;
                }
                budget[0] -= broken;
                if (broken > 0) {
                    addEnchantBlocks(player, "cyclone", broken);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
                }
            }
        }.runTaskTimer(this, 15L, 15L);
    }

    // Fortune des Abysses : à chaque bloc miné, chance de dropper une clé de crate (Commune/Rare/Légendaire).
    // Les 3 tirages sont indépendants ; la plus rare l'emporte pour le message.
    private void triggerFortune(Player player) {
        int lvl = enchantManager.getFortuneLevel(player);
        if (lvl <= 0 || crateManager == null) return;
        // Bonus « +% chance de clé » des armures d'Oublié portées (multiplie les chances de base).
        double keyMult = 1.0 + getArmorBonus(player).keyPct / 100.0;
        CrateManager.CrateRank won = null;
        // On teste de la plus rare à la plus commune : une seule clé max par bloc.
        if (rng.nextDouble() < enchantManager.fortuneChanceLegendaire(lvl) * keyMult)      won = CrateManager.LEGEND;
        else if (rng.nextDouble() < enchantManager.fortuneChanceRare(lvl) * keyMult)       won = CrateManager.RARE;
        else if (rng.nextDouble() < enchantManager.fortuneChanceCommune(lvl) * keyMult)    won = CrateManager.COMMUNE;
        if (won == null) return;
        crateManager.giveKey(player.getUniqueId(), won.key, 1);
        player.sendMessage("§6✦ §7Tu as trouvé une §f" + won.display.replaceAll("Coffre ", "Clé ") + " §7en minant !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1.6f);
    }

    // Les 4 pièces possibles d'une armure (suffixe du Material). La matière fournit le préfixe.
    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    // Drop d'armures d'Oublié en minant (Acte IV). Appelé une fois par bloc miné DIRECTEMENT.
    // On tire d'abord une PIÈCE au hasard, puis une MATIÈRE au hasard parmi celles débloquées
    // (pondérée par sa rareté : le cuir tombe souvent, la netherite très rarement), puis on roule
    // la chance de drop effective (qui intègre le boost de collection). En cas de succès, on
    // génère une armure d'Oublié à bonus et on la donne au joueur.
    private void triggerArmorDrop(Player player) {
        if (armorManager == null || fragmentManager == null) return;
        // Aucune armure ne drope tant que le joueur n'a pas reçu l'Ordinateur Quantique (mine 15,
        // Acte IV) : sans le recyclage débloqué, les armures d'Oublié n'ont aucun sens.
        if (acteManager == null || !acteManager.hasReceivedPc(player)) return;
        int recyclees = fragmentManager.getArmuresRecyclees(player);

        // Matières débloquées + leur poids = drop EFFECTIF (dropPercent × boost de la matière) :
        // ainsi la progression par paliers ne fait pas que changer la fréquence globale, elle
        // déplace aussi le TIRAGE vers les matières hautes (le cuir pèse de moins en moins).
        java.util.List<ArmorManager.Matiere> debloquees = new java.util.ArrayList<>();
        java.util.List<Double> poids = new java.util.ArrayList<>();
        double totalPoids = 0;
        for (ArmorManager.Matiere m : ArmorManager.Matiere.values()) {
            if (!m.estDebloquee(recyclees)) continue;
            // Poids = drop effectif du casque de la matière (intègre le boost par palier propre à la matière).
            double w = ArmorManager.dropPercentEffectif(Material.valueOf(m.prefix + "_HELMET"), recyclees);
            if (w <= 0) continue;
            debloquees.add(m);
            poids.add(w);
            totalPoids += w;
        }
        if (debloquees.isEmpty() || totalPoids <= 0) return;

        // Tire la MATIÈRE (pondérée).
        double r = rng.nextDouble() * totalPoids;
        ArmorManager.Matiere mat = debloquees.get(debloquees.size() - 1);
        double acc = 0;
        for (int i = 0; i < debloquees.size(); i++) {
            acc += poids.get(i);
            if (r < acc) { mat = debloquees.get(i); break; }
        }

        // Tire la PIÈCE au hasard puis fabrique le Material concret.
        String piece = ARMOR_PIECES[rng.nextInt(ARMOR_PIECES.length)];
        Material concret;
        try {
            concret = Material.valueOf(mat.prefix + "_" + piece);
        } catch (IllegalArgumentException ex) {
            return; // sécurité (toutes les combinaisons existent normalement)
        }

        // Chance de drop EFFECTIVE (par bloc), intègre le boost de collection. En %.
        double chance = ArmorManager.dropPercentEffectif(concret, recyclees);
        if (chance <= 0) return;
        if (rng.nextDouble() >= chance / 100.0) return;

        // Génère l'armure d'Oublié (rareté = rareté de base de la matière).
        ArmorManager.Rarete rarete = ArmorManager.rareteDeBase(concret);
        long graine = rng.nextLong();
        ItemStack armure = armorManager.genererArmure(new ItemStack(concret), rarete, graine);
        if (armure == null) return;

        // TRI AUTO : si le joueur a coché cette matière dans le hopper de l'Ordinateur, l'armure est
        // recyclée DIRECT en Fragments (n'entre jamais dans l'inventaire), même rendement que le
        // recyclage manuel (rendement pièce×matière + bonus « +% recyclage » des armures portées).
        // Deux filtres INDÉPENDANTS (logique OU) : l'armure est recyclée auto si SA MATIÈRE est cochée
        // (peu importe les bonus), OU si son nombre de bonus est ≤ au seuil (peu importe la matière).
        ArmorManager.Matiere matDrop = ArmorManager.matiereDe(concret);
        int nbBonus = armorManager.getBonuses(armure).size();
        int seuil = fragmentManager.getAutoRecycleSeuil(player.getUniqueId());
        boolean matiereCochee = matDrop != null
                && fragmentManager.isAutoRecycle(player.getUniqueId(), matDrop.name());
        // Seuil 0 = désactivé (ne recycle rien) ; sinon recycle toute armure ayant ≤ seuil bonus.
        boolean sousSeuil = seuil > 0 && nbBonus <= seuil;
        if (matiereCochee || sousSeuil) {
            int gain = ActeManager.fragmentsPour(armure);
            double recyPct = getArmorBonus(player).recyclePct;
            if (recyPct != 0) gain = (int) Math.round(gain * (1.0 + recyPct / 100.0));
            fragmentManager.addFragments(player, gain);
            fragmentManager.addArmuresRecyclees(player.getUniqueId(), 1);
            String nomMat = matDrop != null ? matDrop.couleur + matDrop.nom : "§7l'Oublié";
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§d♻ §7Armure de " + nomMat
                            + " §7recyclée auto §8» §d+" + gain + " ✦"));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
            logArmorDrop(player, concret, rarete, armure, true); // recyclé auto
            return;
        }

        // Sinon : on donne l'armure au joueur (drop au sol si l'inventaire est plein).
        java.util.Map<Integer, ItemStack> reste = player.getInventory().addItem(armure);
        for (ItemStack drop : reste.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);

        logArmorDrop(player, concret, rarete, armure, false); // gardé en inventaire

        player.sendMessage("§b⛏ §7Tu exhumes une " + rarete.color + "§lArmure d'Oublié §7dans la roche…");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        player.spawnParticle(org.bukkit.Particle.SOUL, player.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
    }

    // ===== LOG DES DROPS D'ARMURE (uniquement Garfieldd_54, pour analyse des taux) =====
    private static final String ARMOR_LOG_JOUEUR = "Garfieldd_54";

    /**
     * Écrit une ligne dans plugins/PrivateMines/armor_drops_garfieldd.log à chaque armure dropée
     * par Garfieldd_54. Format lisible : date/heure, matière, pièce, rareté, nb + détail des bonus,
     * et si l'armure a été recyclée auto ou gardée. Sert à mesurer les taux de drop réels a posteriori.
     */
    private void logArmorDrop(Player player, Material concret, ArmorManager.Rarete rarete,
                              ItemStack armure, boolean recycleAuto) {
        if (player == null || !ARMOR_LOG_JOUEUR.equalsIgnoreCase(player.getName())) return;
        try {
            java.io.File f = new java.io.File(getDataFolder(), "armor_drops_garfieldd.log");
            ArmorManager.Matiere mat = ArmorManager.matiereDe(concret);
            String matNom = mat != null ? mat.nom : concret.name();
            String piece = concret.name().substring(concret.name().lastIndexOf('_') + 1);
            // Détail des bonus : "Vente +72%, Sac +51%".
            StringBuilder bonusStr = new StringBuilder();
            java.util.List<ArmorManager.BonusInstance> bonuses = armorManager.getBonuses(armure);
            for (ArmorManager.BonusInstance bi : bonuses) {
                if (bonusStr.length() > 0) bonusStr.append(", ");
                bonusStr.append(bi.type.label).append(" +").append(bi.valeur).append('%');
            }
            String horodatage = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String ligne = horodatage
                    + " | " + matNom + " " + piece
                    + " | " + rarete.name()
                    + " | " + bonuses.size() + " bonus"
                    + (bonusStr.length() > 0 ? " (" + bonusStr + ")" : "")
                    + " | " + (recycleAuto ? "RECYCLE_AUTO" : "GARDE")
                    + System.lineSeparator();
            java.nio.file.Files.write(f.toPath(), ligne.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { /* le log ne doit jamais casser le drop */ }
    }

    // ===== ITEM-CLÉ D'ARC : le Chapeau de paille (East Blue) =====
    // Objet unique par arc, caché dans la DERNIÈRE mine de l'arc. Ici : drop rare (0,1 %/bloc) en
    // minant la mine 21 « Passe des Adieux » (code U), UNE seule fois par joueur (tag PDC). Gardé en
    // inventaire (pas consommé) — il servira de clé de passage pour débloquer l'arc suivant.
    static final int STRAW_HAT_HEAD_ID = 43053; // tête HeadDB du Chapeau de paille
    private static final String STRAW_HAT_MINE_CODE = "U"; // Passe des Adieux (fin d'arc East Blue)
    private static final double STRAW_HAT_DROP_PCT = 0.05;  // % par bloc miné dans la mine 21
    private org.bukkit.NamespacedKey strawHatItemKey;  // marque l'item (arc_key = east_blue)
    private org.bukkit.NamespacedKey strawHatTakenKey; // le joueur a déjà trouvé son chapeau

    // Crée LA copie du Chapeau de paille du joueur (tête HeadDB + nom/lore + tag PDC anti-triche).
    private ItemStack createStrawHat() {
        ItemStack hat = petHeads.getHead(STRAW_HAT_HEAD_ID);
        ItemMeta meta = hat.getItemMeta();
        meta.setDisplayName("§e§l🎩 Chapeau de paille");
        meta.setLore(java.util.Arrays.asList(
                "§7La coiffe d'un rêveur parti trop loin,",
                "§7oubliée dans les profondeurs de l'East Blue.",
                "",
                "§6✦ Clé de passage §7— la preuve que tu es",
                "§7prêt à quitter ces mers pour le grand large.",
                "",
                "§8Garde-le sur toi : il t'ouvrira l'arc suivant."));
        meta.getPersistentDataContainer().set(strawHatItemKey, org.bukkit.persistence.PersistentDataType.STRING, "east_blue");
        hat.setItemMeta(meta);
        return hat;
    }

    // Vrai si l'item est le Chapeau de paille (par tag PDC, pas par nom — anti-triche).
    boolean isStrawHat(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return "east_blue".equals(it.getItemMeta().getPersistentDataContainer()
                .get(strawHatItemKey, org.bukkit.persistence.PersistentDataType.STRING));
    }

    private boolean hasStrawHatInInventory(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isStrawHat(it)) return true;
        return false;
    }

    // Le joueur a-t-il DÉJÀ trouvé le Chapeau de paille une fois ? (tag PDC posé au drop — fiable,
    // survit aux swaps d'inventaire spawn/mine, aux morts, etc.). Sert de clé d'accès à l'Arc II.
    boolean hasFoundStrawHat(Player p) {
        if (strawHatTakenKey == null) strawHatTakenKey = new org.bukkit.NamespacedKey(this, "strawhat_taken");
        return p.getPersistentDataContainer().has(strawHatTakenKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    // Drop rare du Chapeau de paille en minant la mine 21. Appelé une fois par bloc miné directement.
    // La Plume : 1 chance sur 1000 par bloc miné de la trouver, UNIQUEMENT si le joueur a atteint
    // le niveau 45 ET écouté l'histoire du Contremaître ET ne l'a pas déjà trouvée. Gros message.
    private void triggerPlume(Player player) {
        if (acteManager == null) return;
        if (getPickaxeLevel(player) < 45) return;
        if (!acteManager.hasHeardPlumeStory(player)) return;
        if (acteManager.hasFoundPlume(player)) return;
        if (rng.nextInt(1000) != 0) return; // 1/1000
        acteManager.triggerPlumeFound(player);
    }

    private void triggerStrawHat(Player player) {
        // Uniquement dans la mine de fin d'arc (Passe des Adieux).
        if (!STRAW_HAT_MINE_CODE.equals(getPlayerMine(player))) return;
        // Une seule fois par joueur (tag PDC + sécurité inventaire).
        if (player.getPersistentDataContainer().has(strawHatTakenKey, org.bukkit.persistence.PersistentDataType.BYTE)) return;
        if (hasStrawHatInInventory(player)) return;
        // Tirage.
        if (rng.nextDouble() >= STRAW_HAT_DROP_PCT / 100.0) return;

        // Trouvé ! On donne SA copie et on marque le joueur.
        player.getPersistentDataContainer().set(strawHatTakenKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        java.util.Map<Integer, ItemStack> reste = player.getInventory().addItem(createStrawHat());
        for (ItemStack drop : reste.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);

        // Grande annonce (item de prestige, fin d'arc).
        player.sendTitle("§e§l🎩 Chapeau de paille", "§7Tu l'as exhumé des abysses de l'East Blue…", 10, 70, 20);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        player.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0.1);
        player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, player.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.02);
        player.sendMessage("§e🎩 §fTu as trouvé le §e§lChapeau de paille§f, caché dans la §5Passe des Adieux§f !");
        player.sendMessage("§7   §oGarde-le : il t'ouvrira les portes de l'arc suivant.");
        Bukkit.broadcastMessage("§e🎩 §f" + player.getName() + " §7a exhumé le §e§lChapeau de paille §7des profondeurs de l'East Blue !");
    }

    // ===== Throttle des effets visuels d'enchants =====
    // Certains enchants se déclenchent PLUSIEURS FOIS PAR SECONDE (Fracture : 12 % de chance par
    // bloc miné au niveau 1, et on mine 10-20 blocs/s avec Célérité). Leur rendu devient alors un
    // stroboscope : l'éclair de Fracture est un VRAI éclair (flash blanc plein écran + tonnerre),
    // et une salve de Harpons peut lancer 40 traînées de particules dans le même tick.
    //
    // ⚠ On borne l'AFFICHAGE, JAMAIS l'effet : les blocs sont cassés à chaque déclenchement, seul
    // le rendu est sauté quand deux salves sont trop rapprochées. L'équilibrage (§19/§21 de
    // refonte.economie.md) n'est donc pas touché — le joueur gagne exactement autant qu'avant.
    private static final long FX_COOLDOWN_FRACTURE = 1000L; // 1 flash d'éclair par seconde au plus
    private static final long FX_COOLDOWN_HARPON   = 400L;  // 2,5 pluies de harpons par seconde au plus
    private final java.util.Map<java.util.UUID, java.util.Map<String, Long>> lastFx = new java.util.HashMap<>();

    // true si l'effet visuel « key » peut être joué MAINTENANT pour ce joueur (et arme le délai
    // suivant). false = salve silencieuse et invisible, mais les blocs cassent quand même.
    private boolean canShowFx(Player player, String key, long cooldownMs) {
        java.util.Map<String, Long> m = lastFx.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashMap<>());
        long now = System.currentTimeMillis();
        Long last = m.get(key);
        if (last != null && now - last < cooldownMs) return false;
        m.put(key, now);
        return true;
    }

    // Fracture : fait tomber des éclairs (visuels) dans la mine qui pulvérisent des zones autour
    // de leur point d'impact. Surtout le RAYON augmente avec le niveau (jusqu'à 1000).
    private void triggerFracture(Player player, Location center) {
        int lvl = enchantManager.getFractureLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.fractureChance(lvl)) return;
        int total = enchantManager.fractureBlocks(lvl);
        int bolts = enchantManager.fractureBolts(lvl);
        World w = center.getWorld();
        int half = MINE_SIZE / 2;
        int cx = mineCenterX(player), cz = mineCenterZ(player), fy = mineFloorY(player);
        // Le flash + le tonnerre ne sont joués qu'une fois par seconde au plus (voir canShowFx).
        boolean fx = canShowFx(player, "fracture", FX_COOLDOWN_FRACTURE);
        int broken = 0;
        for (int b = 0; b < bolts; b++) {
            // Part de cet éclair : le reste de la division est réparti sur les premiers.
            int part = total / bolts + (b < total % bolts ? 1 : 0);
            if (part <= 0) continue;
            // Point d'impact tiré au hasard dans TOUTE la mine (pas autour du joueur).
            int ix = cx - half + rng.nextInt(MINE_SIZE);
            int iz = cz - half + rng.nextInt(MINE_SIZE);
            int iy = fy + rng.nextInt(MINE_HEIGHT);
            // Éclair purement visuel (pas de feu ni de dégâts) — sauté si la salve précédente
            // date de moins d'une seconde, sinon ça stroboscope au niveau 1 (12 % de chance).
            if (fx) w.strikeLightningEffect(new Location(w, ix, iy, iz));
            // Les blocs les plus PROCHES de l'impact, comme Explosion : un rayon fixe avancerait
            // par paliers en escalier sur la grille et des niveaux entiers ne changeraient rien.
            int R = Math.max(1, (int) Math.ceil(Math.cbrt(part / 4.18879)) + 1);
            java.util.List<int[]> pool = new ArrayList<>();
            for (int dx = -R; dx <= R; dx++)
                for (int dy = -R; dy <= R; dy++)
                    for (int dz = -R; dz <= R; dz++) {
                        if (!isInMine(new Location(w, ix + dx, iy + dy, iz + dz))) continue;
                        pool.add(new int[]{dx * dx + dy * dy + dz * dz, dx, dy, dz});
                    }
            pool.sort(java.util.Comparator.comparingInt(k -> k[0]));
            int pris = 0;
            for (int[] k : pool) {
                if (pris >= part) break;
                if (creditMineBlock(player, new Location(w, ix + k[1], iy + k[2], iz + k[3]))) { pris++; broken++; }
            }
        }
        if (broken > 0) {
            addEnchantBlocks(player, "fracture", broken);
            if (fx) player.playSound(center, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);
        }
    }

    // Vein Miner : chance décroissante (7 % au niv.1 -> 2 % au niv.200) de creuser un FILON de N blocs
    // autour du bloc cassé. N = 1 au niveau 1 -> 100 au niveau 200, et le rayon s'ouvre tout seul avec N.
    // ⚠ L'ancienne version ne balayait que le cube 3x3x3 : elle était bridée à 26 blocs quoi qu'annonce le menu.
    private void triggerVein(Player player, Location center) {
        int lvl = enchantManager.getVeinLevel(player);
        if (lvl <= 0) return;
        if (rng.nextDouble() >= enchantManager.veinChance(lvl)) return;
        int count = enchantManager.veinBlocks(lvl);
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        // Rayon de la boule qui contient au moins N blocs (+1 de marge : les bords de mine en mangent).
        int r = Math.max(1, (int) Math.ceil(Math.cbrt(count / 4.18879)) + 1);
        // {clé de tri, dx, dy, dz} — la clé est le carré de la distance BROUILLÉE : les bords deviennent
        // irréguliers au lieu d'une sphère parfaite, ça ressemble à un vrai filon. Clé calculée UNE fois
        // (un comparateur qui tire au sort à chaque appel casse le contrat de sort()).
        java.util.List<double[]> pool = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (!isInMine(new Location(w, cx + dx, cy + dy, cz + dz))) continue;
                    pool.add(new double[]{dx * dx + dy * dy + dz * dz + rng.nextDouble() * 2.5, dx, dy, dz});
                }
        pool.sort(java.util.Comparator.comparingDouble(k -> k[0]));   // les plus proches du centre d'abord
        int mined = 0;
        for (double[] k : pool) {
            if (mined >= count) break;
            if (creditMineBlock(player, new Location(w, cx + (int) k[1], cy + (int) k[2], cz + (int) k[3]))) mined++;
        }
        if (mined > 0) { addEnchantBlocks(player, "vein", mined); player.playSound(center, org.bukkit.Sound.BLOCK_STONE_BREAK, 0.7f, 1.4f); }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = Bukkit.getWorld("world");
        // On charge ses données sauvegardées (stock, niveaux).
        loadPlayer(player);
        // Affiche son rang (numéro de mine) dans le tab.
        updateMineTag(player);
        // Reflète le niveau de pioche dans la barre d'XP native dès la connexion.
        updatePickaxeBar(player);

        // Détecte la 1ère connexion (pour jouer la cinématique d'ouverture de l'Acte I).
        boolean firstJoin = parcelleManager.getParcelle(player.getUniqueId()) == null;

        // Crée la parcelle automatiquement à la 1ère connexion.
        if (firstJoin) {
            parcelleManager.createParcelle(player.getUniqueId());

            // Compteur global de joueurs : le nouveau joueur reçoit le numéro suivant.
            int playerNumber = dataConfig.getInt("meta.totalPlayers", 0) + 1;
            dataConfig.set("meta.totalPlayers", playerNumber);
            saveDataConfig("le compteur de joueurs");

            // Annonce de bienvenue diffusée à tout le serveur dans le chat.
            Bukkit.broadcastMessage("§aBienvenue §e" + player.getName() + " §7#" + playerNumber);

            // Mémorise ce joueur comme dernier nouveau arrivé (cible de /bvn).
            lastNewPlayer = player.getName();

            // Invite tous les AUTRES joueurs à l'accueillir.
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.sendMessage("§7Faites §e/bvn §7pour accueillir §e" + player.getName() + " §7!");
                }
            }

            // NOTE : on ne dit RIEN au joueur sur son Île ici. Elle est créée en silence et
            // c'est l'Acte III (TutorialManager, étape « /ob ») qui la lui fera découvrir en
            // temps voulu. Annoncer l'Île dès la connexion cassait la découverte progressive.
        }

        // À la connexion, le joueur arrive au SPAWN (zone "spawn" = hors-mine, pas de pioche/sac).
        playerZone.put(player.getUniqueId(), "spawn");
        player.setWorldBorder(null);

        // TP immédiat au spawn (avant que le client charge son ancienne position).
        Location spawn = getSpawnLocation();
        if (spawn != null) player.teleport(spawn);

        // Restaure l'inventaire hors-mine IMMÉDIATEMENT (invHorsMine est déjà chargé par loadPlayer
        // ci-dessus) : sinon le client affiche 2-3 s l'inventaire mine sauvegardé par le vanilla
        // avant qu'on le remplace → « flash » d'inventaire. On le corrige donc dès maintenant.
        player.getInventory().clear();
        ItemStack[] savedHors = invHorsMine.get(player.getUniqueId());
        if (savedHors != null) player.getInventory().setContents(savedHors);
        // Jamais de pioche/sac/ordinateur au spawn.
        stripBagAndPickaxe(player);

        // Le reste (affichage de la fausse mine, guide, actes) attend le chargement des chunks.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            sendFakeMine(player, world);
            // Affiche le guide BossBar (sauf si le joueur l'a masqué).
            guideManager.show(player);
            // Acte I : à la toute première connexion, joue la cinématique d'ouverture
            // (« L'Éveil sur la Grève des Oubliés »). Ne se rejoue jamais ensuite.
            if (firstJoin && !introManager.hasSeenIntro(player)) {
                introManager.playIntro(player, true);
            } else if (!acteManager.isActeDone(player)) {
                // Joueur qui revient au milieu de l'Acte I : on réaffiche son objectif en cours.
                acteManager.restoreObjective(player);
            } else if (!acteManager.isActe2Done(player)) {
                // Acte I fini, Acte II en cours : objectif de l'Acte II.
                acteManager.restoreObjective(player);
            } else if (!tutorial.isDone(player)) {
                // Actes I & II finis, parcours de découverte en cours : on réaffiche son étape.
                tutorial.restoreObjective(player);
            } else if (logPoseManager != null && hasUnlockedMine(player, "V")
                    && !logPoseManager.isVisitFullyDone(player)) {
                // ARC II (Alabasta) : mine 22 débloquée et visite du Log Pose pas encore clôturée
                // → on réaffiche l'objectif (Trouve le Gardien / Suis ton Log Pose / Parle au Témoin).
                logPoseManager.refreshObjective(player);
            }
            // ACTE IV — filet de rattrapage : un joueur qui a DÉJÀ débloqué la mine 15 avant l'ajout
            // de cette feature (ou dont la scène a échoué) est marqué comme ayant reçu l'Ordinateur.
            // On ne pose PAS l'item ici (spawn hors mine) : giveOrdi le placera au slot 8 dès l'entrée en mine.
            if (hasUnlockedMine(player, "O") && !acteManager.hasReceivedPc(player)) {
                player.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(this, "pc_quantique_donne"),
                        org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                player.sendMessage("§b✦ §fL'§bOrdinateur Quantique§f t'attend dans ta mine §7(slot 9)§f.");
            }
        }, 40L);
    }

    // ===== Faim gélée dans la mine (2026-08-21) =====
    // Dans SA mine, la barre de faim ne DESCEND plus : miner ne doit pas forcer à remonter manger.
    // On ne bloque que les BAISSES — manger continue de fonctionner normalement.
    // ⚠ Choix du user : la barre n'est PAS remplie à l'entrée. Elle est figée au niveau qu'avait le
    // joueur en arrivant, pour que la mine ne serve pas de « restaurant gratuit » avant l'End ou le PvP.
    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInMine(player)) return;
        if (event.getFoodLevel() >= player.getFoodLevel()) return; // hausse (nourriture) : on laisse passer
        event.setCancelled(true);
    }

    // À la déconnexion : on sauvegarde les données du joueur.
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        savePlayer(event.getPlayer());
        // Retire la BossBar OneBlock (si sur l'Île) AVANT le guide, pour éviter de
        // recréer une barre d'astuces juste avant de la supprimer.
        if (oneBlockManager != null) oneBlockManager.hideBar(event.getPlayer());
        // Retire la BossBar du guide.
        guideManager.remove(event.getPlayer());
        // Stoppe son compte à rebours de reset de mine.
        secondsToReset.remove(event.getPlayer().getUniqueId());
        // Oublie ses derniers effets visuels d'enchants (throttle canShowFx).
        lastFx.remove(event.getPlayer().getUniqueId());
    }

    // ===== Accès public pour l'Acte II (les PNJ remettent le sac / la pioche) =====
    public void giveBagPublic(Player p) { giveBag(p); }
    public void givePickaxePublic(Player p) { givePickaxe(p); }
    // Retire sac + pioche de l'inventaire ET de l'inventaire mine sauvegardé (reset Acte II).
    public void stripToolsPublic(Player p) {
        stripBagAndPickaxe(p);
        // Nettoie aussi la copie sauvegardée de l'inventaire mine, sinon ils réapparaissent au swap.
        ItemStack[] saved = invMine.get(p.getUniqueId());
        if (saved != null) {
            for (int i = 0; i < saved.length; i++) {
                if (isBag(saved[i]) || isPickaxe(saved[i])) saved[i] = null;
            }
        }
    }

    // ===== LE SAC DE MINAGE (item dans la hotbar) =====
    static final int BAG_SLOT = 4; // slot du milieu de la hotbar (0 à 8) — package-visible (LockedItemListener)

    // Crée l'item "Sac de Minage" (un tonneau).
    private ItemStack createBag() {
        ItemStack bag = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bag.getItemMeta();
        meta.setDisplayName("§6Sac de Minage");
        List<String> lore = new ArrayList<>();
        lore.add("§7Clic droit pour ouvrir.");
        meta.setLore(lore);
        bag.setItemMeta(meta);
        return bag;
    }

    // Vrai si l'item est notre Sac de Minage.
    // Package-visible : utilisé aussi par ParcelleListener (protection de pose).
    boolean isBag(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§6Sac de Minage");
    }

    // Le sac est déplaçable librement : on le donne seulement s'il est ABSENT de l'inventaire.
    // Si un exemplaire existe déjà (n'importe où), on ne fait rien (pas de re-placement forcé,
    // pas d'anti-dup slot par slot — le joueur ne peut de toute façon pas le dupliquer).
    private void giveBag(Player player) {
        if (findBag(player) == null) {
            // BAG_SLOT reste le slot par défaut à la 1re remise, si libre ; sinon on l'ajoute.
            if (isEmptySlot(player, BAG_SLOT)) player.getInventory().setItem(BAG_SLOT, createBag());
            else addOrDrop(player, createBag());
        }
    }

    // Vrai si le slot donné est vide (air).
    private boolean isEmptySlot(Player player, int slot) {
        ItemStack it = player.getInventory().getItem(slot);
        return it == null || it.getType() == Material.AIR;
    }

    // Ajoute l'item à l'inventaire, ou le laisse tomber au sol s'il est plein.
    private void addOrDrop(Player player, ItemStack it) {
        java.util.Map<Integer, ItemStack> reste = player.getInventory().addItem(it);
        for (ItemStack r : reste.values()) player.getWorld().dropItemNaturally(player.getLocation(), r);
    }

    // Cherche le Sac / la Pioche / l'Ordi n'importe où dans l'inventaire (null si absent).
    ItemStack findBag(Player player) {
        for (ItemStack it : player.getInventory().getContents()) if (isBag(it)) return it;
        return null;
    }
    ItemStack findPickaxe(Player player) {
        for (ItemStack it : player.getInventory().getContents()) if (isPickaxe(it)) return it;
        return null;
    }
    ItemStack findOrdi(Player player) {
        for (ItemStack it : player.getInventory().getContents()) if (isOrdi(it)) return it;
        return null;
    }

    // ===== LA PIOCHE DU MINEUR (slot 0, verrouillée) =====
    static final int PICK_SLOT = 0; // tout premier slot de la hotbar — package-visible (LockedItemListener)

    private ItemStack createPickaxe() {
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.setDisplayName("§3§lRappel des Abysses");
        List<String> lore = new ArrayList<>();
        lore.add("§7Chaque coup résonne dans les");
        lore.add("§7profondeurs et réveille les Oubliés.");
        meta.setLore(lore);
        // Incassable : elle ne perd jamais de durabilité.
        meta.setUnbreakable(true);
        // Masque le tooltip vanilla des enchants (on affiche nos propres lignes colorées).
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        pick.setItemMeta(meta);
        return pick;
    }

    // Package-visible : utilisé aussi par LockedItemListener (verrouillage d'items).
    boolean isPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§3§lRappel des Abysses");
    }

    // Pioche déplaçable librement : donnée seulement si ABSENTE de l'inventaire, sinon on ne
    // fait que ré-appliquer ses enchantements (où qu'elle soit rangée).
    private void givePickaxe(Player player) {
        if (findPickaxe(player) == null) {
            if (isEmptySlot(player, PICK_SLOT)) player.getInventory().setItem(PICK_SLOT, createPickaxe());
            else addOrDrop(player, createPickaxe());
        }
        applyPickaxeEnchants(player);
    }

    // ===== L'ORDINATEUR QUANTIQUE (slot 8 = 9e slot de la hotbar, verrouillé) =====
    static final int ORDI_SLOT = 8; // dernier slot de la hotbar (0 à 8) — package-visible (LockedItemListener)

    // Vrai si l'item est l'Ordinateur Quantique (tag PDC via ActeManager). Null-safe.
    boolean isOrdi(ItemStack item) {
        return acteManager != null && acteManager.isOrdinateurQuantique(item);
    }

    // Ordinateur Quantique déplaçable librement : donné seulement s'il est ABSENT et si le
    // joueur l'a débloqué (a reçu son PC).
    private void giveOrdi(Player player) {
        if (acteManager == null || !acteManager.hasReceivedPc(player)) return;
        if (findOrdi(player) == null) {
            if (isEmptySlot(player, ORDI_SLOT)) player.getInventory().setItem(ORDI_SLOT, acteManager.createOrdinateurQuantique());
            else addOrDrop(player, acteManager.createOrdinateurQuantique());
        }
    }
    public void giveOrdiPublic(Player p) { giveOrdi(p); }

    // ===== LA TÊTE DES FAMILIERS (ouvre /pets, verrouillée comme le sac) =====
    static final int PETS_HEAD_SLOT = 7; // slot par défaut à la 1re remise (avant l'ordi en 8)
    private static final int PETS_HEAD_ID = 349; // id HeadDB de la tête
    private org.bukkit.NamespacedKey petsHeadKey;

    // Crée l'item « Familiers » : un OS (BONE) tagué en PDC. C'est un ITEM, pas un bloc → impossible
    // à poser par nature (contrairement à la tête ou l'œuf, posables sur les faux blocs de la mine).
    ItemStack createPetsHead() {
        if (petsHeadKey == null) petsHeadKey = new org.bukkit.NamespacedKey(this, "pets_head");
        ItemStack head = new ItemStack(Material.BONE);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName("§d§l🐾 Familiers");
        List<String> lore = new ArrayList<>();
        lore.add("§7Clic droit pour ouvrir le");
        lore.add("§7menu des familiers.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(petsHeadKey,
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        head.setItemMeta(meta);
        return head;
    }

    // Vrai si l'item est la tête des Familiers (tag PDC). Null-safe.
    boolean isPetsHead(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (petsHeadKey == null) petsHeadKey = new org.bukkit.NamespacedKey(this, "pets_head");
        return meta.getPersistentDataContainer().has(petsHeadKey,
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    // Cherche la tête des Familiers n'importe où dans l'inventaire (null si absente).
    ItemStack findPetsHead(Player player) {
        for (ItemStack it : player.getInventory().getContents()) if (isPetsHead(it)) return it;
        return null;
    }

    // Item des Familiers (œuf de tortue) déplaçable librement : donné à l'entrée en mine si absent.
    // Si une ANCIENNE version (tête HeadDB) traîne encore, on la convertit en œuf au même emplacement.
    private void givePetsHead(Player player) {
        ItemStack existante = findPetsHead(player);
        if (existante == null) {
            if (isEmptySlot(player, PETS_HEAD_SLOT)) player.getInventory().setItem(PETS_HEAD_SLOT, createPetsHead());
            else addOrDrop(player, createPetsHead());
            return;
        }
        // Migration : l'item taggé existe mais n'est plus le bon type (ancienne tête/œuf) → on le remplace.
        if (existante.getType() != Material.BONE) {
            int slot = player.getInventory().first(existante);
            if (slot >= 0) player.getInventory().setItem(slot, createPetsHead());
        }
    }

    // (Ré)applique les enchantements custom sur la Pioche du Mineur (slot 0) selon les niveaux du joueur.
    void applyPickaxeEnchants(Player player) {
        ItemStack pick = findPickaxe(player); // pioche déplaçable : on la cherche partout
        if (!isPickaxe(pick)) return;
        ItemMeta meta = pick.getItemMeta();

        int eff = enchantManager.getEfficiencyLevel(player);
        meta.removeEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY);
        if (eff > 0) meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, eff, true);
        // Masque le tooltip vanilla de l'enchant (« Efficacité enchantment.level.25 ») : on affiche
        // nos propres lignes colorées. Sans ce flag, Minecraft ajoute sa ligne grise en doublon.
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("§7Chaque coup résonne dans les");
        lore.add("§7profondeurs et réveille les Oubliés.");
        if (eff > 0) lore.add("§b✦ Efficacité §3" + eff);
        int explo = enchantManager.getExplosionLevel(player);
        if (explo > 0) lore.add("§c✦ Explosion §4" + explo);
        int forage = enchantManager.getForageLevel(player);
        if (forage > 0) lore.add("§e✦ Forage §6" + forage);
        int fracture = enchantManager.getFractureLevel(player);
        if (fracture > 0) lore.add("§3✦ Fracture §b" + fracture);
        int vein = enchantManager.getVeinLevel(player);
        if (vein > 0) lore.add("§2✦ Vein Miner §a" + vein);
        int allonge = enchantManager.getAllongeLevel(player);
        if (allonge > 0) lore.add("§d✦ Allonge §5" + allonge);
        lore.add("");
        lore.add("§e§oClic droit : enchantements");
        meta.setLore(lore);
        pick.setItemMeta(meta);
        player.updateInventory();
    }

    // Crée un item "vitre" coloré pour la déco du menu.
    ItemStack pane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta m = p.getItemMeta();
        m.setDisplayName(" ");
        p.setItemMeta(m);
        return p;
    }
    // Clic droit avec le sac -> ouvre le menu d'améliorations.
    // Priorité LOWEST : on tranche AVANT tout autre plugin (et avant que le placement d'un item
    // posable comme l'œuf des Familiers ne soit validé, y compris sur les FAUX blocs de la mine).
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player joueur = event.getPlayer();
        // L'item réellement en main : plus fiable que event.getItem() (qui peut être null sur un faux bloc).
        ItemStack enMain = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? joueur.getInventory().getItemInOffHand()
                : joueur.getInventory().getItemInMainHand();
        // Clic droit avec la Pioche du Mineur -> menu d'enchantements.
        if (isPickaxe(enMain) || isPickaxe(event.getItem())) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            enchantManager.openEnchantMenu(joueur);
            return;
        }
        // Clic droit avec l'Os des Familiers -> menu /pets. (BONE est un item non-posable : rien à bloquer.)
        if (isPetsHead(enMain) || isPetsHead(event.getItem())) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            joueur.updateInventory();
            if (getPetMenu() != null) getPetMenu().openMain(joueur);
            return;
        }
        if (!isBag(enMain) && !isBag(event.getItem())) return;
        // On bloque l'utilisation ET le placement du baril (sinon il se pose en survie).
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        openBagMenu(joueur);
    }

    // Garde PRIORITAIRE : les items-menu (sac / pioche / ordi / tête des Familiers) ne se posent
    // JAMAIS comme des blocs, où que ce soit. Priorité LOWEST + ignoreCancelled=false pour couper
    // net toute pose avant qu'un autre handler ne la valide. On teste les DEUX mains (main + offhand)
    // car selon la main active BlockPlaceEvent#getItemInHand peut renvoyer l'une ou l'autre.
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onLockedItemPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        ItemStack main = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack off  = event.getPlayer().getInventory().getItemInOffHand();
        ItemStack used = event.getItemInHand();
        if (estItemMenu(used) || estItemMenu(main) || estItemMenu(off)) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }

    // Vrai si l'item est un des items-menu protégés (jamais posable, jamais jetable).
    private boolean estItemMenu(ItemStack it) {
        return isBag(it) || isPickaxe(it) || isOrdi(it) || isPetsHead(it);
    }

    // Placement de blocs : interdit dans la mine, et interdit pour le Sac partout.
    @EventHandler
    public void onBagPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        // 1) Le Sac de Minage ne se pose jamais.
        if (isBag(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory(); // resync : le sac réapparaît dans la main
            return;
        }
        // 1bis) La Tête des Familiers (comme le sac) ne se pose jamais : c'est un item-menu.
        if (isPetsHead(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
            return;
        }
        // 2) Impossible de poser quoi que ce soit dans la zone de la mine.
        if (isInMine(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
            return;
        }
        // 3) Zones protégées : pas de pose si le flag "place" est actif (sauf OP).
        if (!event.getPlayer().isOp()
                && zones.getProtectingZone(event.getBlock().getLocation(), "place") != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cTu ne peux pas construire ici (zone protegee).");
        }
    }

    // ===== Protection des zones (spawn/PvP) + respawn =====
    // Handlers extraits dans ProtectionListener (enregistré dans onEnable).

    // ===== Protection des cristaux de l'End (déco du Puits des Souvenirs) =====
    // Handlers extraits dans CrystalProtectionListener (enregistré dans onEnable).

    // Ouvre le menu (GUI) du sac.
    private void openBagMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, "§6Sac de Minage");
        refreshBagMenu(player, menu);
        player.openInventory(menu);
    }

    // Remplit des slots avec des piles représentant `total` blocs d'un type donné.
    // Une pile visuelle plafonne à 64 (limite Minecraft) mais son lore indique le VRAI total du type,
    // et on n'utilise qu'un nombre limité de slots pour ne pas "remplir" le sac visuellement trop vite.
    // Retourne l'index de slot suivant libre.
    private int fillStorageStacks(Inventory menu, int[] slots, int slotIndex, Material mat, String label, int total) {
        if (total <= 0) return slotIndex;
        int remaining = total;
        while (remaining > 0 && slotIndex < slots.length) {
            int amount = Math.min(64, remaining);
            ItemStack pile = new ItemStack(mat, amount);
            ItemMeta pm = pile.getItemMeta();
            pm.setDisplayName(label + " §7×§f" + formatNumber(total));
            pile.setItemMeta(pm);
            menu.setItem(slots[slotIndex], pile);
            remaining -= amount;
            slotIndex++;
        }
        return slotIndex;
    }

    // (Re)remplit le contenu du menu selon le stock actuel. Sert à l'ouverture ET au rafraîchissement.
    private void refreshBagMenu(Player player, Inventory menu) {
        int current = getStock(player);

        // 1) Déco : vitres noires partout.
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < menu.getSize(); i++) {
            menu.setItem(i, filler);
        }

        int coal = getCoalStock(player);
        int stone = Math.max(0, current - coal);

        // 2) Compteur en haut (slot 4) : "Stock : 73/100" + détail par type.
        ItemStack counter = new ItemStack(Material.PAPER);
        ItemMeta counterMeta = counter.getItemMeta();
        counterMeta.setDisplayName("§6Stockage : §e" + formatNumber(current) + "§7/§e" + formatNumber(getCapacity(player)));
        List<String> counterLore = new ArrayList<>();
        counterLore.add("§8▪ §7Pierre : §f" + formatNumber(stone) + " §7(§a" + formatNumber(STONE_PRICE) + "$§7/bloc)");
        counterLore.add("§8▪ §7Charbon : §f" + formatNumber(coal)
                + (coal > 0 ? " §7(§a" + formatNumber(getCoalValue(player) / coal) + "$§7/bloc)" : ""));
        counterLore.add("");
        counterLore.add("§7Vente : §a" + formatNumber(getSellPerSec(player)) + " bloc/sec");
        counterLore.add("§7Solde : §a" + formatBig(player));
        counterMeta.setLore(counterLore);
        counter.setItemMeta(counterMeta);
        menu.setItem(4, counter);

        // 3) Le stock affiché dans la zone centrale : d'abord la pierre, puis le charbon.
        //    Chaque pile porte la quantité RÉELLE dans son lore (les piles peuvent représenter
        //    plus de 64 : on met un stack visuel de 64 mais on écrit le vrai total, pour ne pas
        //    remplir le sac visuel trop vite).
        int[] storageSlots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int slotIndex = 0;
        slotIndex = fillStorageStacks(menu, storageSlots, slotIndex, Material.STONE, "§7Pierre", stone);
        slotIndex = fillStorageStacks(menu, storageSlots, slotIndex, Material.COAL_ORE, "§8Charbon", coal);

        // 4) Améliorations en bas.
        int sellLvl = getSellLevel(player);
        int capLvl = getCapacityLevel(player);

        // Bouton Vente/sec.
        ItemStack sell = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.setDisplayName("§eVente / seconde §7(niv. " + sellLvl + ")");
        List<String> sellLore = new ArrayList<>();
        sellLore.add("§7Actuel : §a" + formatNumber(getSellPerSec(player)) + " bloc/sec");
        if (sellLvl >= MAX_LEVEL) {
            sellLore.add("§6Niveau MAX atteint !");
        } else {
            sellLore.add("§7Cout : §6" + formatNumber(getUpgradeCost(sellLvl)) + "$");
            sellLore.add("§eClic pour ameliorer.");
        }
        sellMeta.setLore(sellLore);
        sell.setItemMeta(sellMeta);
        menu.setItem(47, sell);

        // Bouton Capacité.
        ItemStack cap = new ItemStack(Material.CHEST);
        ItemMeta capMeta = cap.getItemMeta();
        capMeta.setDisplayName("§aCapacite §7(niv. " + capLvl + ")");
        List<String> capLore = new ArrayList<>();
        capLore.add("§7Actuel : §a" + formatNumber(getCapacity(player)) + " blocs");
        if (capLvl >= MAX_CAPACITY_LEVEL) {
            capLore.add("§6Niveau MAX atteint !");
        } else {
            capLore.add("§7Cout : §6" + formatNumber(getUpgradeCost(capLvl)) + "$");
            capLore.add("§eClic pour ameliorer.");
        }
        capMeta.setLore(capLore);
        cap.setItemMeta(capMeta);
        menu.setItem(49, cap);

        // Bouton Bonus d'argent (multiplicateur de vente).
        int multLvl = getMoneyMultLevel(player);
        ItemStack mult = new ItemStack(Material.SUNFLOWER);
        ItemMeta multMeta = mult.getItemMeta();
        multMeta.setDisplayName("§6✦ Bonus d'argent §7(niv. " + multLvl + ")");
        List<String> multLore = new ArrayList<>();
        multLore.add("§7Actuel : §d+" + String.format(java.util.Locale.FRANCE, "%.2f", getMoneyBonusPercent(player)) + "% §7sur tes ventes");
        multLore.add("§7Prochain : §d+" + String.format(java.util.Locale.FRANCE, "%.2f", moneyBonusPercentFor(multLvl + 1)) + "%");
        multLore.add("§7Cout : §6" + formatNumber(moneyMultCost(multLvl)) + "$");
        multLore.add("§eClic pour ameliorer.");
        multMeta.setLore(multLore);
        mult.setItemMeta(multMeta);
        menu.setItem(51, mult);
    }

    // Clics dans le menu du Sac : achat des améliorations (capacité / vente).
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6Sac de Minage")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == 47) {          // bouton Vente/sec
            buyUpgrade(player, "sell");
            refreshBagMenu(player, event.getView().getTopInventory());
        } else if (event.getSlot() == 49) {   // bouton Capacité
            buyUpgrade(player, "capacity");
            refreshBagMenu(player, event.getView().getTopInventory());
        } else if (event.getSlot() == 51) {   // bouton Bonus d'argent
            buyUpgrade(player, "mult");
            refreshBagMenu(player, event.getView().getTopInventory());
        }
    }

    // Achète un niveau d'amélioration ("sell" ou "capacity") si le joueur a assez d'argent.
    private void buyUpgrade(Player player, String type) {
        if (economy == null) return;
        // Bonus d'argent : illimité, prix propre.
        if (type.equals("mult")) {
            int lvl = getMoneyMultLevel(player);
            double c = moneyMultCost(lvl);
            if (economy.getBalance(player) < c) {
                player.sendMessage("§cPas assez d'argent ! Il te faut §6" + formatNumber(c) + "$");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            economy.withdrawPlayer(player, c);
            moneyMultLevel.put(player.getUniqueId(), lvl + 1);
            // ⚠ Volontairement PAS compté pour la quête « Sac plus lourd » : le bonus d'argent
            // coûte ~5 800 $ le niveau à tout avancement, là où capacité/vente suivent le ×1,39.
            // L'inclure permettait de boucler la quête pour moins cher que sa propre récompense.
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            tutorial.onSacUpgrade(player); // parcours de découverte : étape « améliore ton sac »
            return;
        }
        int level = type.equals("sell") ? getSellLevel(player) : getCapacityLevel(player);
        // Plafond différent selon le type : la capacité s'arrête à 348 (limite de l'int), voir
        // MAX_CAPACITY_LEVEL. Sans ça le joueur paie des niveaux qui n'ajoutent aucun bloc.
        int plafond = type.equals("sell") ? MAX_LEVEL : MAX_CAPACITY_LEVEL;
        if (level >= plafond) {
            player.sendMessage("§6Niveau maximum deja atteint !");
            return;
        }
        double cost = getUpgradeCost(level);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (type.equals("sell")) {
            sellLevel.put(player.getUniqueId(), level + 1);
        } else {
            capacityLevel.put(player.getUniqueId(), level + 1);
        }
        if (questManager != null) questManager.onBagUpgrade(player); // quête « Sac plus lourd »
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        tutorial.onSacUpgrade(player); // parcours de découverte : étape « améliore ton sac »
    }

    // Clics dans les menus de ZONES (liste + édition).
    @EventHandler
    public void onZoneMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();

        // --- Menu LISTE des zones : clic sur une zone -> ouvre son édition ---
        if (title.equals(ZONE_LIST_TITLE)) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() != Material.MAP) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String zoneName = meta.getDisplayName().replace("§e", "");
            if (zones.zoneExists(zoneName)) {
                openZoneEditMenu(player, zoneName);
            }
            return;
        }

        // --- Sous-menu d'ÉDITION d'une zone ---
        if (title.startsWith(ZONE_EDIT_PREFIX)) {
            event.setCancelled(true);
            if (clicked == null) return;
            String zoneName = editingZone.get(player.getUniqueId());
            if (zoneName == null || !zones.zoneExists(zoneName)) return;

            Material type = clicked.getType();
            // Basculer un flag (laine).
            if (type == Material.LIME_WOOL || type == Material.RED_WOOL) {
                String flag = flagFromSlot(event.getSlot());
                if (flag != null) {
                    boolean now = !zones.getFlag(zoneName, flag);
                    zones.setFlag(zoneName, flag, now);
                    openZoneEditMenu(player, zoneName); // rafraîchit le menu
                }
            } else if (type == Material.NAME_TAG) {
                // Renommer : on ferme et on attend le nom dans le chat.
                renamingZone.put(player.getUniqueId(), zoneName);
                player.closeInventory();
                player.sendMessage("§eTape le nouveau nom de la zone dans le chat (ou 'annuler').");
            } else if (type == Material.BARRIER) {
                // Supprimer : on DEMANDE confirmation dans le chat (oui/non).
                confirmingDelete.put(player.getUniqueId(), zoneName);
                player.closeInventory();
                player.sendMessage("§c§lSupprimer la zone '" + zoneName + "' ?");
                player.sendMessage("§7Tape §aoui §7pour confirmer, ou §cnon §7pour annuler.");
            } else if (type == Material.ARROW) {
                // Retour à la liste des zones.
                openZoneListMenu(player);
            } else if (type == Material.ENDER_PEARL || type == Material.ENDER_EYE) {
                // Basculer la visualisation : on AJOUTE/RETIRE cette zone (plusieurs possibles).
                java.util.Set<String> viz = getVisualized(player.getUniqueId());
                if (viz.contains(zoneName)) {
                    viz.remove(zoneName);
                    player.sendMessage("§7Particules cachees pour '" + zoneName + "'.");
                } else {
                    viz.add(zoneName);
                    player.sendMessage("§aParticules affichees pour '" + zoneName + "'.");
                }
                openZoneEditMenu(player, zoneName); // rafraîchit le bouton
            }
        }
    }

    // Retrouve le flag selon le slot du sous-menu.
    private String flagFromSlot(int slot) {
        if (slot == 10) return "break";
        if (slot == 12) return "place";
        if (slot == 14) return "pvp";
        if (slot == 19) return "mobs";
        if (slot == 20) return "animals";
        return null;
    }

    // Préfixe (N) de chat = numéro de mine : handler extrait dans ChatTagListener.

    // Saisie du nouveau nom de zone dans le chat (après clic sur Renommer).
    @EventHandler
    public void onRenameChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // --- Renommage de l'Île (via /ob settings) ---
        if (renamingIsland.contains(player.getUniqueId())) {
            event.setCancelled(true);
            renamingIsland.remove(player.getUniqueId());
            String saisie = event.getMessage().trim();
            Bukkit.getScheduler().runTask(this, () -> {
                if (saisie.equalsIgnoreCase("annuler")) {
                    player.sendMessage("§7Renommage annulé.");
                    return;
                }
                Parcelle parc = parcelleManager.getParcelle(player.getUniqueId());
                if (parc == null) { player.sendMessage("§cTu n'as pas d'Île."); return; }
                String nom = nettoieNomIle(player, saisie);
                if (nom == null) {
                    player.sendMessage("§cNom refusé (trop long, interdit, ou vide). Réessaie via §e/ob settings§c.");
                    return;
                }
                parc.setIslandName(nom);
                parcelleManager.save();
                player.sendMessage("§a✦ Ton Île s'appelle désormais : " + nom);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            });
            return;
        }

        // --- Confirmation de suppression (oui/non) ---
        String toDelete = confirmingDelete.get(player.getUniqueId());
        if (toDelete != null) {
            event.setCancelled(true);
            confirmingDelete.remove(player.getUniqueId());
            String answer = event.getMessage().trim().toLowerCase();
            Bukkit.getScheduler().runTask(this, () -> {
                if (answer.equals("oui")) {
                    zones.deleteZone(toDelete);
                    editingZone.remove(player.getUniqueId());
                    getVisualized(player.getUniqueId()).remove(toDelete); // retire juste cette zone
                    player.sendMessage("§aZone '" + toDelete + "' supprimee.");
                } else {
                    player.sendMessage("§7Suppression annulee.");
                }
            });
            return;
        }

        // --- Renommage ---
        String oldName = renamingZone.get(player.getUniqueId());
        if (oldName == null) return;

        event.setCancelled(true); // ce message n'est pas un chat normal
        renamingZone.remove(player.getUniqueId());
        String newName = event.getMessage().trim();

        if (newName.equalsIgnoreCase("annuler")) {
            player.sendMessage("§7Renommage annule.");
            return;
        }
        // On revient sur le thread principal (l'event chat est asynchrone).
        Bukkit.getScheduler().runTask(this, () -> {
            if (zones.renameZone(oldName, newName)) {
                player.sendMessage("§aZone renommee en '" + newName + "'.");
            } else {
                player.sendMessage("§cImpossible (nom deja pris ou invalide).");
            }
        });
    }

    // ===== Verrouillage des items fixes (sac/pioche/ordi : anti-jet, anti-déplacement) =====
    // Handlers extraits dans LockedItemListener (enregistré dans onEnable).
    // (Le MENU du sac — achats capacité/vente — reste ici : onMenuClick/buyUpgrade.)

    // Vrai si loc est dans la zone (bloc) d'UNE mine centrée en (cx,cz) sol fy.
    private boolean inZone(Location loc, int cx, int cz, int fy) {
        int half = MINE_SIZE / 2;
        return loc.getBlockX() >= cx - half && loc.getBlockX() < cx + half
            && loc.getBlockZ() >= cz - half && loc.getBlockZ() < cz + half
            && loc.getBlockY() >= fy && loc.getBlockY() < fy + MINE_HEIGHT;
    }

    // Frontière SANS joueur : vrai si loc tombe dans la zone d'une mine QUELCONQUE (Arc I ou zone custom).
    // Les zones ne se chevauchent pas, donc tester toutes les zones connues est sûr.
    private boolean isInMine(Location loc) {
        if (inZone(loc, MINE_CENTER_X, MINE_CENTER_Z, MINE_FLOOR_Y)) return true; // zone historique Arc I
        for (MineDef d : MINES) {
            if (d.centerX != null && inZone(loc, d.centerX, d.centerZ, d.floorY)) return true;
        }
        return false;
    }

    // Frontière POUR un joueur : vrai si loc est dans la zone de SA mine active (plus précis pour les enchants).
    private boolean isInMine(Player p, Location loc) {
        return inZone(loc, mineCenterX(p), mineCenterZ(p), mineFloorY(p));
    }

    // Zone de VOL d'un joueur : le carré X/Z réel de SA mine (108 blocs : 446→553 pour l'Arc I),
    // SANS contrainte de hauteur (toute la colonne Y). Le vol reste actif tant qu'on est au-dessus
    // ou dans ce carré, et se coupe dès qu'on en sort horizontalement.
    private static final int MINE_FLY_SIZE = 108; // largeur réelle de la mine (446→553 inclus)
    private boolean isInMineFlyZone(Player p, Location loc) {
        int cx = mineCenterX(p), cz = mineCenterZ(p);
        int half = MINE_FLY_SIZE / 2; // 54 → couvre cx-54 .. cx+53 = 446..553 pour cx=500
        return loc.getBlockX() >= cx - half && loc.getBlockX() < cx + half
            && loc.getBlockZ() >= cz - half && loc.getBlockZ() < cz + half;
    }

    // Zone ELARGIE pour détecter qu'un JOUEUR est "dans/sur" une mine (Y jusqu'à 5 blocs au-dessus du sommet).
    boolean isPlayerInMineArea(Location loc) {
        int half = MINE_SIZE / 2;
        for (MineDef d : MINES) {
            int cx = d.centerX != null ? d.centerX : MINE_CENTER_X;
            int cz = d.centerZ != null ? d.centerZ : MINE_CENTER_Z;
            int fy = d.floorY  != null ? d.floorY  : MINE_FLOOR_Y;
            if (loc.getBlockX() >= cx - half && loc.getBlockX() < cx + half
             && loc.getBlockZ() >= cz - half && loc.getBlockZ() < cz + half
             && loc.getBlockY() >= fy && loc.getBlockY() <= fy + MINE_HEIGHT + 5) return true;
        }
        return false;
    }

    // Convertit une direction de face cliquée en décalage de case (pour trouver où le bloc irait).
    private org.bukkit.util.Vector directionToVector(com.comphenix.protocol.wrappers.EnumWrappers.Direction dir) {
        switch (dir) {
            case UP:    return new org.bukkit.util.Vector(0, 1, 0);
            case DOWN:  return new org.bukkit.util.Vector(0, -1, 0);
            case NORTH: return new org.bukkit.util.Vector(0, 0, -1);
            case SOUTH: return new org.bukkit.util.Vector(0, 0, 1);
            case WEST:  return new org.bukkit.util.Vector(-1, 0, 0);
            case EAST:  return new org.bukkit.util.Vector(1, 0, 0);
            default:    return new org.bukkit.util.Vector(0, 0, 0);
        }
    }

    // Renvoie au joueur le BON bloc à une position : charbon/pierre si c'est dans la mine, sinon le vrai bloc.
    private void sendCorrectBlock(Player player, Location loc) {
        if (isInMine(loc)) {
            Material mat = getBlockForPlayer(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    loc.getWorld());
            player.sendBlockChange(loc, mat.createBlockData());
        } else {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    private void clearRealZone(World world) {
        // Vide la zone historique de l'Arc I…
        clearZone(world, MINE_CENTER_X, MINE_CENTER_Z, MINE_FLOOR_Y);
        // …et chaque mine ayant SA propre zone (Arc II et +).
        for (MineDef d : MINES) {
            if (d.centerX != null) clearZone(world, d.centerX, d.centerZ, d.floorY);
        }
    }

    private void clearZone(World world, int cx, int cz, int fy) {
        int half = MINE_SIZE / 2;
        for (int x = cx - half; x < cx + half; x++) {
            for (int y = fy; y < fy + MINE_HEIGHT; y++) {
                for (int z = cz - half; z < cz + half; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    private Material getBlockForPlayer(Player player, int x, int y, int z, World world) {
        String mine = getPlayerMine(player);
        if (mineHasCoal(mine)) {
            Location loc = new Location(world, x, y, z);
            if (getAbyssalPositions(player).contains(loc)) return Material.DEEPSLATE_IRON_ORE; // 5e minerai = fer des abîmes
            if (getIronPositions(player).contains(loc)) return Material.IRON_ORE;       // 4e minerai = fer
            if (getCoalBlockPositions(player).contains(loc)) return Material.COAL_BLOCK; // 3e minerai = bloc de charbon
            if (getCoalPositions(player).contains(loc)) return Material.COAL_ORE;
        }
        return mineDef(mine).baseBlock; // bloc de base de la mine (pierre par défaut, grès pour l'Arc II…)
    }

    // Vrai si la mine contient du charbon, des blocs de charbon, du fer et/ou du fer des abîmes.
    private boolean mineHasCoal(String mine) {
        MineDef d = mineDef(mine);
        return d.coalPct > 0 || d.coalBlockPct > 0 || d.ironPct > 0 || d.abyssalPct > 0;
    }
    // Version publique pour les autres classes (ex : CommandManager /resetmines).
    public boolean mineHasCoalPublic(String mine) { return mineHasCoal(mine); }

    // Détermine si le joueur casse le bloc en 1 tick (instamine). Dans ce cas le client
    // n'envoie que START_DESTROY_BLOCK (pas de STOP) -> il faut créditer au START.
    //
    // IMPORTANT : ce calcul doit coller EXACTEMENT à celui du client vanilla, sinon un
    // bloc que le client casse en 1 tick n'est jamais crédité côté serveur -> il « clignote »
    // et réapparaît (bug observé avec Efficacité 7 : incohérent selon le bloc).
    private boolean isInstaMine(Player player, Material blockMat) {
        float hardness = blockMat.getHardness();
        if (hardness <= 0f) return true;

        // Source de vérité pour l'Efficacité = le niveau du PLUGIN (enchantManager), pas le NBT
        // de l'item : le NBT pouvait être désynchronisé (pioche pas ré-appliquée, main hand ≠
        // pioche enchantée) → Efficacité sous-estimée → instamine ratée alors que le client
        // voyait bien le bon niveau. On prend le max des deux pour être sûr de ne jamais sous-estimer.
        int effPlugin = enchantManager.getEfficiencyLevel(player);
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) {
            tool = findPickaxe(player); // pioche déplaçable : on la cherche partout
        }
        int effItem = (tool != null)
                ? tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY) : 0;
        int eff = Math.max(effPlugin, effItem);

        // Vitesse de la pioche diamant (outil correct) + bonus d'Efficacité (E^2 + 1).
        double speed = 8.0;
        if (eff > 0) speed += eff * eff + 1;
        // Effet Célérité (Haste) éventuel : +20% par niveau (comme vanilla).
        org.bukkit.potion.PotionEffect haste = player.getPotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
        if (haste != null) speed *= 1.0 + 0.2 * (haste.getAmplifier() + 1);

        // Outil correct (diamant sur pierre/charbon) -> diviseur 30. Instamine si progrès >= 1 / tick.
        // Marge (0.05) pour absorber les arrondis flottants aux valeurs LIMITES : Efficacité 6 sur
        // pierre donne pile 1.0 (45 / 45), et la moindre imprécision faisait échouer la détection
        // → le bloc "restait à l'infini". La marge garantit que le cas pile-limite passe toujours.
        return speed / (hardness * 30.0) >= 1.0 - 0.05;
    }

    void sendFakeMine(Player player, World world) {
        int half = MINE_SIZE / 2;
        int mcx = mineCenterX(player), mcz = mineCenterZ(player), floorY = mineFloorY(player);
        int startX = mcx - half;
        int startZ = mcz - half;
        int endX   = mcx + half;
        int endZ   = mcz + half;

        // Collecte tous les chunks couverts par la mine et force leur chargement
        java.util.Set<String> chunksToLoad = new java.util.LinkedHashSet<>();
        for (int x = startX; x < endX; x += 16) {
            for (int z = startZ; z < endZ; z += 16) {
                chunksToLoad.add((x >> 4) + "," + (z >> 4));
            }
        }
        chunksToLoad.add((endX >> 4) + "," + (endZ >> 4));
        chunksToLoad.add((startX >> 4) + "," + (endZ >> 4));
        chunksToLoad.add((endX >> 4) + "," + (startZ >> 4));

        for (String key : chunksToLoad) {
            String[] parts = key.split(",");
            world.getChunkAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        java.util.List<java.util.List<BlockState>> vagues = new java.util.ArrayList<>();
        for (String key : chunksToLoad) {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            List<BlockState> vague = new java.util.ArrayList<>();
            for (int x = cx * 16; x < cx * 16 + 16; x++) {
                if (x < startX || x >= endX) continue;
                for (int z = cz * 16; z < cz * 16 + 16; z++) {
                    if (z < startZ || z >= endZ) continue;
                    for (int y = floorY; y < floorY + MINE_HEIGHT; y++) {
                        BlockState state = world.getBlockAt(x, y, z).getState();
                        state.setType(getBlockForPlayer(player, x, y, z, world));
                        vague.add(state);
                    }
                }
            }
            if (!vague.isEmpty()) vagues.add(vague);
        }

        for (int i = 0; i < vagues.size(); i++) {
            final List<BlockState> v = vagues.get(i);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) player.sendBlockChanges(v);
            }, 2L * i);
        }

        // Passe de fiabilité 1s après la fin
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            List<BlockState> all = new ArrayList<>();
            for (int x = startX; x < endX; x++) {
                for (int z = startZ; z < endZ; z++) {
                    for (int y = floorY; y < floorY + MINE_HEIGHT; y++) {
                        BlockState state = world.getBlockAt(x, y, z).getState();
                        state.setType(getBlockForPlayer(player, x, y, z, world));
                        all.add(state);
                    }
                }
            }
            player.sendBlockChanges(all);
        }, 2L * vagues.size() + 20L);
    }
}
