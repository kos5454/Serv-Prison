package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * LE HUB DES QUÊTES (/quest) — remplace l'ancien /quest qui n'affichait qu'une ligne de texte.
 *
 * <p>Le menu regroupe toutes les familles de quêtes du serveur. Aujourd'hui deux sont vivantes :
 * les <b>quotidiennes</b> et la <b>quête d'histoire</b> (l'objectif d'Acte en cours, repris de
 * l'ancien /quest). Les autres sections sont posées en « bientôt » pour que l'ajout ne demande
 * pas de refonte du menu : hebdomadaires, objectif collectif serveur, contrats, quêtes d'île.</p>
 *
 * <h2>Les quotidiennes</h2>
 * <p><b>3 quêtes par jour</b> tirées dans un pool de 5, remises à zéro à <b>minuit heure serveur</b>.
 * Une quête non terminée est <b>perdue</b> : c'est l'échéance qui fait revenir, une quête sans
 * date limite n'est qu'une liste de courses. Le tirage est <b>déterministe</b> (graine = UUID +
 * jour), donc un redémarrage du serveur ne rebat jamais les cartes en cours de journée.</p>
 *
 * <p>Les objectifs de <b>minage</b> sont des nombres FIXES pour tout le monde (choix user) : avec
 * Efficacité 7+, un joueur one-shot les blocs, donc 5 000 blocs coûtent à peu près le même temps
 * à la mine 3 qu'à la mine 25. En revanche « vendre » et « progresser » dépendent violemment de
 * la progression (un bloc vaut 5 $ à la mine 1 et 250 $ à la mine 25) : ces objectifs-là ont donc
 * <b>4 jeux de valeurs fixes</b>, un par palier, réglés à la main dans les tables ci-dessous.</p>
 */
public class QuestManager implements Listener {

    private final PrivateMines plugin;

    public QuestManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    static final String MENU_TITLE  = "§8§lQuêtes";
    static final String DAILY_TITLE = "§8Quêtes » §aQuotidiennes";

    /** Nombre de quêtes tirées chaque jour. */
    public static final int PAR_JOUR = 3;

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LE POOL DE QUÊTES  —  toutes les valeurs à régler sont ici.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int Q_MINER = 0, Q_MINERAI = 1, Q_VENDRE = 2, Q_ENCHANT = 3, Q_SAC = 4, Q_MINES = 5;
    private static final int NB_TYPES = 6;

    // Minage : nombres FIXES, identiques à tous les paliers (choix user).
    private static final long OBJ_MINER   = 5_000;
    private static final long OBJ_MINERAI = 1_000;
    // Débloquer 2 mines dans la journée. Nombre fixe : le coût grimpe tout seul avec la
    // progression (10,8 M $ à la mine 15, bien plus haut ensuite). Sort du tirage quand il
    // reste moins de 2 mines à débloquer, sinon elle serait impossible à finir.
    private static final long OBJ_MINES   = 2;

    // Objectifs qui dépendent de la progression : une valeur PAR PALIER (0 = mines 1-7 … 3 = 22+).
    private static final long[] OBJ_VENDRE  = { 7_500, 150_000, 1_000_000, 3_000_000 };
    private static final long[] OBJ_ENCHANT = { 3, 2, 1, 1 };
    // Le sac : 5 ameliorations a TOUS les paliers (choix user). Contrairement aux autres
    // quetes, celle-ci COUTE de l argent au joueur : c est voulu, elle pousse a depenser.
    private static final long[] OBJ_SAC     = { 3, 3, 3, 3 };

    private long objectif(int type, int palier) {
        switch (type) {
            case Q_MINER:   return OBJ_MINER;
            case Q_MINERAI: return OBJ_MINERAI;
            case Q_MINES:   return OBJ_MINES;
            case Q_VENDRE:  return OBJ_VENDRE[palier];
            case Q_ENCHANT: return OBJ_ENCHANT[palier];
            default:        return OBJ_SAC[palier];
        }
    }

    private String titreQuete(int type) {
        switch (type) {
            case Q_MINER:   return "§b§lMineur du jour";
            case Q_MINERAI: return "§e§lChasseur de filons";
            case Q_MINES:   return "§6§lRuée vers l'or";
            case Q_VENDRE:  return "§6§lMarchand du jour";
            case Q_ENCHANT: return "§d§lÀ la forge";
            default:        return "§a§lSac plus lourd";
        }
    }

    private Material iconeQuete(int type) {
        switch (type) {
            case Q_MINER:   return Material.STONE;
            case Q_MINERAI: return Material.RAW_IRON;
            case Q_MINES:   return Material.DIAMOND_PICKAXE;
            case Q_VENDRE:  return Material.GOLD_INGOT;
            case Q_ENCHANT: return Material.ENCHANTED_BOOK;
            default:        return Material.CHEST;
        }
    }

    private String consigne(int type, long obj) {
        switch (type) {
            case Q_MINER:   return "Mine §f" + obj + " §7blocs.";
            case Q_MINERAI: return "Mine §f" + obj + " §7minerais.";
            case Q_MINES:   return "Débloque §f" + obj + " §7mines.";
            case Q_VENDRE:  return "Vends pour §6" + PrivateMines.formatNumber(obj) + " $§7.";
            case Q_ENCHANT: return "Monte §f" + obj + " §7niveau(x) d'enchant.";
            default:        return "Améliore ton sac §f" + obj + " §7fois §8(capacité ou vente/s).";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LES RÉCOMPENSES  —  une par quête, tirée à la génération et affichée d'avance.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int R_ARGENT = 0, R_FRAGMENTS = 1, R_CLE = 2, R_ENCHANT = 3;
    // Poids du tirage : argent 50 %, fragments 20 %, clé 20 %, niveau d'enchant 10 %.
    private static final int[] POIDS_RECOMPENSE = { 50, 20, 20, 10 };

    // Argent = ce % du prix de la PROCHAINE mine (même logique que les récompenses quotidiennes).
    private static final double PCT_ARGENT = 0.03;
    private static final int[] REC_FRAGMENTS = { 50, 75, 100, 150 };
    private static final String[] REC_CLE_RANG = { "commune", "commune", "commune", "rare" };

    private int tirerRecompense(Random rng) {
        int total = 0;
        for (int poids : POIDS_RECOMPENSE) total += poids;
        int r = rng.nextInt(total);
        for (int i = 0; i < POIDS_RECOMPENSE.length; i++) {
            r -= POIDS_RECOMPENSE[i];
            if (r < 0) return i;
        }
        return R_ARGENT;
    }

    private String descRecompense(Player p, int rec, int palier) {
        switch (rec) {
            case R_ARGENT:
                return "§6" + PrivateMines.formatNumber(montantArgent(p)) + " $";
            case R_FRAGMENTS:
                return "§d" + REC_FRAGMENTS[palier] + " ✦ §7Fragments";
            case R_CLE:
                return "rare".equals(REC_CLE_RANG[palier]) ? "§9Clé Rare §7×1" : "§fClé Commune §7×1";
            default:
                return "§b+1 niveau §7sur un enchant au hasard";
        }
    }

    private double montantArgent(Player p) {
        DailyManager daily = plugin.getDaily();
        if (daily == null) return 0;
        return Math.floor(daily.prixProchaineMine(p) * PCT_ARGENT);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LE TIRAGE DU JOUR  —  déterministe : (UUID, jour) donne toujours le même jeu de quêtes.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /** Le jeu de quêtes d'un joueur pour une journée : les types tirés et leurs récompenses. */
    private static final class Jeu {
        final long jour; final int[] types; final int[] recompenses;
        Jeu(long jour, int[] types, int[] recompenses) { this.jour = jour; this.types = types; this.recompenses = recompenses; }
    }

    private final Map<UUID, Jeu> jeuxDuJour = new HashMap<>();
    // Progression EN MÉMOIRE : onMined() est appelé à chaque bloc cassé, hors de question
    // d'écrire dans la config à cette fréquence. Chargé à la connexion, écrit à la déconnexion.
    private final Map<UUID, long[]> progression = new HashMap<>();
    private final Map<UUID, Integer> reclamees = new HashMap<>();   // bitmask des quêtes déjà encaissées

    private static long aujourdhui() { return LocalDate.now().toEpochDay(); }

    /** La mine du joueur contient-elle des minerais ? (les mines de l'Arc II sont 100 % grès) */
    private boolean mineADesMinerais(Player p) {
        PrivateMines.MineDef d = PrivateMines.mineDef(plugin.bestUnlockedMineCode(p));
        return d.coalPct + d.coalBlockPct + d.ironPct + d.abyssalPct > 0;
    }

    private int palier(Player p) {
        DailyManager daily = plugin.getDaily();
        return daily == null ? 0 : daily.palierDe(p);
    }

    /** Le jeu de quêtes du jour, régénéré (et progression remise à zéro) si la journée a changé. */
    private Jeu jeu(Player p) {
        UUID id = p.getUniqueId();
        long jour = aujourdhui();
        Jeu j = jeuxDuJour.get(id);
        if (j != null && j.jour == jour) return j;

        // Graine stable : même joueur + même jour = même tirage, même après un redémarrage.
        long seed = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits() + jour * 0x9E3779B97F4A7C15L;
        Random rng = new Random(seed);

        List<Integer> pool = new ArrayList<>();
        for (int t = 0; t < NB_TYPES; t++) {
            if (t == Q_MINERAI && !mineADesMinerais(p)) continue;   // rien à miner dans l'Arc II
            if (t == Q_MINES && plugin.getMineRank(p) + OBJ_MINES > PrivateMines.MINES.size()) continue;
            pool.add(t);
        }
        Collections.shuffle(pool, rng);

        int n = Math.min(PAR_JOUR, pool.size());
        int[] types = new int[n];
        int[] recs = new int[n];
        for (int i = 0; i < n; i++) {
            types[i] = pool.get(i);
            recs[i] = tirerRecompense(rng);
        }
        j = new Jeu(jour, types, recs);
        jeuxDuJour.put(id, j);
        // Nouveau jour → la progression et les réclamations de la veille sont effacées.
        progression.put(id, new long[n]);
        reclamees.put(id, 0);
        plugin.getDataConfig().set(id + ".questDay", jour);
        return j;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  PROGRESSION  —  appelée depuis le minage, la vente, l'achat d'enchant et l'up du sac.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private void avancer(Player p, int type, long combien) {
        if (combien <= 0) return;
        Jeu j = jeu(p);
        long[] prog = progression.get(p.getUniqueId());
        if (prog == null) return;
        for (int i = 0; i < j.types.length; i++) {
            if (j.types[i] != type) continue;
            // Les objectifs de MINAGE sont fixes : inutile de recalculer le palier du joueur
            // (une boucle sur les 25 mines) a chaque bloc casse. Explosion en casse 250 d un coup.
            boolean fixe = (type == Q_MINER || type == Q_MINERAI || type == Q_MINES);
            long obj = objectif(type, fixe ? 0 : palier(p));
            if (prog[i] >= obj) return;                       // déjà rempli, on ne compte plus
            prog[i] = Math.min(obj, prog[i] + combien);
            if (prog[i] >= obj) {
                p.sendMessage("§a✔ Quête terminée : " + titreQuete(type)
                        + " §8— §7réclame-la dans §f/quest§7.");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
            return;
        }
    }

    /**
     * Un bloc miné À LA PIOCHE. {@code minerai} = ce n'était pas de la roche de base.
     * ⚠ N'est PAS appelé pour les blocs cassés par les enchants de zone (Explosion, Gouffre,
     * Fracture…) : une seule salve en casse jusqu'à 250, les 5 000 blocs tomberaient en 1 min.
     */
    public void onMined(Player p, boolean minerai) {
        avancer(p, Q_MINER, 1);
        if (minerai) avancer(p, Q_MINERAI, 1);
        avancerHebdo(p, W_MINER, 1);
    }

    /** Le sac vient d'être vendu pour {@code montant} $. */
    public void onSold(Player p, double montant) {
        avancer(p, Q_VENDRE, (long) montant);
    }

    /** Un niveau d'enchant vient d'être acheté. */
    public void onEnchantLevel(Player p) { avancer(p, Q_ENCHANT, 1); }

    /** Une mine vient d'être achetée (quête « Ruée vers l'or »). */
    public void onMineUnlocked(Player p) { avancer(p, Q_MINES, 1); }

    /** Le sac vient d'être amélioré. ⚠ Capacité et vente/sec seulement — pas le bonus d'argent. */
    public void onBagUpgrade(Player p) { avancer(p, Q_SAC, 1); }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  PERSISTANCE  (progression gardée en mémoire, écrite à la connexion/déconnexion)
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { charger(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { sauver(event.getPlayer()); }

    private void charger(Player p) {
        UUID id = p.getUniqueId();
        // ── Quotidiennes (rien à charger si la journée enregistrée est périmée : jeu() régénère).
        if (plugin.getDataConfig().getLong(id + ".questDay", -1L) == aujourdhui()) {
            List<Integer> lus = plugin.getDataConfig().getIntegerList(id + ".questProgress");
            long[] prog = new long[PAR_JOUR];
            for (int i = 0; i < prog.length && i < lus.size(); i++) prog[i] = lus.get(i);
            // On force la reconstruction du jeu AVANT d'injecter la progression (jeu() la remettrait à zéro).
            jeu(p);
            progression.put(id, prog);
            reclamees.put(id, plugin.getDataConfig().getInt(id + ".questClaimed", 0));
        }
        // ── Hebdomadaire. ⚠ Le RELEVÉ DE DÉPART doit venir de la config : le recalculer ici
        // remettrait la progression à zéro à chaque reconnexion (pioche, armures recyclées).
        if (plugin.getDataConfig().getLong(id + ".questWeek", -1L) == semaine()) {
            Hebdo h = hebdo(p);
            long base = plugin.getDataConfig().getLong(id + ".questWeekBase", h.base);
            hebdos.put(id, new Hebdo(h.semaine, h.type, base));
            progHebdo.put(id, plugin.getDataConfig().getLong(id + ".questWeekProg", 0L));
            hebdoReclamee.put(id, plugin.getDataConfig().getBoolean(id + ".questWeekClaimed", false));
        }
    }

    private void sauver(Player p) {
        UUID id = p.getUniqueId();
        long[] prog = progression.get(id);
        if (prog == null) return;
        List<Integer> out = new ArrayList<>();
        for (long v : prog) out.add((int) Math.min(Integer.MAX_VALUE, v));
        plugin.getDataConfig().set(id + ".questDay", aujourdhui());
        plugin.getDataConfig().set(id + ".questProgress", out);
        plugin.getDataConfig().set(id + ".questClaimed", reclamees.getOrDefault(id, 0));
        Hebdo h = hebdos.get(id);
        if (h != null) {
            plugin.getDataConfig().set(id + ".questWeek", h.semaine);
            plugin.getDataConfig().set(id + ".questWeekBase", h.base);
            plugin.getDataConfig().set(id + ".questWeekProg", progHebdo.getOrDefault(id, 0L));
            plugin.getDataConfig().set(id + ".questWeekClaimed", hebdoReclamee.getOrDefault(id, false));
        }
    }

    /** Écrit la progression de tous les joueurs connectés (appelé à l'arrêt du plugin). */
    public void saveAll() {
        for (Player p : Bukkit.getOnlinePlayers()) sauver(p);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LES HEBDOMADAIRES  —  UNE seule grosse quête par semaine, reset SAMEDI à minuit.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int W_MINER = 0, W_PIOCHE = 1, W_QUOTIDIENNES = 2, W_ARMURES = 3;
    private static final int NB_TYPES_HEBDO = 4;

    // Objectifs, dans l'ordre des constantes ci-dessus. Valeurs FIXES pour tous : « miner » et
    // « recycler » ne dépendent pas de la richesse, et « niveaux de pioche » s'auto-équilibre
    // (la courbe d'XP monte avec le niveau : ~780 blocs au niveau 10, ~20 600 au niveau 84).
    private static final long[] OBJ_HEBDO = { 100_000, 3, 10, 200 };

    // Le lot : les trois d'un coup, une fois par semaine.
    private static final int REC_HEBDO_FRAGMENTS = 1_000;
    private static final String REC_HEBDO_CLE = "legendaire";
    // Plage de rareté du pet/armure, par palier (mêmes valeurs que le J7 des récompenses quotidiennes).
    private static final int[] HEBDO_RARETE_MIN = { 0, 1, 2, 3 };
    private static final int[] HEBDO_RARETE_MAX = { 1, 2, 3, 4 };

    /**
     * Numéro de la semaine courante. Le 1970-01-03 était un SAMEDI (jour 2 de l'epoch), donc
     * décaler de 2 avant de diviser par 7 fait tomber le changement de semaine samedi à minuit.
     */
    private static long semaine() { return Math.floorDiv(aujourdhui() - 2, 7); }

    /** L'hebdo d'un joueur : sa semaine, le type tiré, et le relevé de départ des compteurs cumulatifs. */
    private static final class Hebdo {
        final long semaine; final int type; final long base;
        Hebdo(long semaine, int type, long base) { this.semaine = semaine; this.type = type; this.base = base; }
    }

    private final Map<UUID, Hebdo> hebdos = new HashMap<>();
    private final Map<UUID, Long> progHebdo = new HashMap<>();          // pour les compteurs incrémentaux
    private final Map<UUID, Boolean> hebdoReclamee = new HashMap<>();

    private String titreHebdo(int type) {
        switch (type) {
            case W_MINER:        return "§b§lMineur de la semaine";
            case W_PIOCHE:       return "§d§lMaître pioche";
            case W_QUOTIDIENNES: return "§a§lAssidu";
            default:             return "§6§lFerrailleur";
        }
    }

    private Material iconeHebdo(int type) {
        switch (type) {
            case W_MINER:        return Material.NETHERITE_PICKAXE;
            case W_PIOCHE:       return Material.EXPERIENCE_BOTTLE;
            case W_QUOTIDIENNES: return Material.CLOCK;
            default:             return Material.IRON_CHESTPLATE;
        }
    }

    private String consigneHebdo(int type, long obj) {
        switch (type) {
            case W_MINER:        return "Mine §f" + obj + " §7blocs §8(à la pioche).";
            case W_PIOCHE:       return "Gagne §f" + obj + " §7niveaux de pioche.";
            case W_QUOTIDIENNES: return "Termine §f" + obj + " §7quêtes quotidiennes.";
            default:             return "Recycle §f" + obj + " §7armures d'Oublié.";
        }
    }

    /**
     * Relevé de départ d'un compteur CUMULATIF (niveau de pioche, armures recyclées à vie).
     * La progression de la semaine est la différence avec ce relevé — aucun nouveau compteur
     * à brancher, on réutilise ceux qui existent déjà.
     */
    private long releveDepart(Player p, int type) {
        if (type == W_PIOCHE) return plugin.getPickaxeLevel(p);
        if (type == W_ARMURES) {
            return plugin.getFragments() == null ? 0 : plugin.getFragments().getArmuresRecyclees(p);
        }
        return 0;
    }

    /** L'hebdo du joueur, régénérée si la semaine a changé. Tirage déterministe (UUID + semaine). */
    private Hebdo hebdo(Player p) {
        UUID id = p.getUniqueId();
        long sem = semaine();
        Hebdo h = hebdos.get(id);
        if (h != null && h.semaine == sem) return h;

        long seed = id.getMostSignificantBits() * 17 + id.getLeastSignificantBits() + sem * 0x7F4A7C15L;
        int type = new Random(seed).nextInt(NB_TYPES_HEBDO);
        h = new Hebdo(sem, type, releveDepart(p, type));
        hebdos.put(id, h);
        progHebdo.put(id, 0L);
        hebdoReclamee.put(id, false);
        plugin.getDataConfig().set(id + ".questWeek", sem);
        return h;
    }

    /** Progression de l'hebdo : différence avec le relevé pour les compteurs cumulatifs, compteur sinon. */
    private long progressionHebdo(Player p) {
        Hebdo h = hebdo(p);
        if (h.type == W_PIOCHE) return Math.max(0, plugin.getPickaxeLevel(p) - h.base);
        if (h.type == W_ARMURES) {
            if (plugin.getFragments() == null) return 0;
            return Math.max(0, plugin.getFragments().getArmuresRecyclees(p) - h.base);
        }
        return progHebdo.getOrDefault(p.getUniqueId(), 0L);
    }

    /** Fait avancer l'hebdo si (et seulement si) c'est bien ce type qui a été tiré cette semaine. */
    private void avancerHebdo(Player p, int type, long combien) {
        if (combien <= 0) return;
        Hebdo h = hebdo(p);
        if (h.type != type) return;
        if (type == W_PIOCHE || type == W_ARMURES) return;   // calculés par différence, rien à incrémenter
        long obj = OBJ_HEBDO[type];
        long avant = progHebdo.getOrDefault(p.getUniqueId(), 0L);
        if (avant >= obj) return;
        long apres = Math.min(obj, avant + combien);
        progHebdo.put(p.getUniqueId(), apres);
        if (apres >= obj) annoncerHebdoFinie(p);
    }

    private void annoncerHebdoFinie(Player p) {
        p.sendMessage("§b✔ Quête hebdomadaire terminée §8— §7réclame-la dans §f/quest§7.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    private boolean hebdoTerminee(Player p) {
        return progressionHebdo(p) >= OBJ_HEBDO[hebdo(p).type];
    }

    static final String HEBDO_TITLE = "§8Quêtes » §bHebdomadaire";
    private static final int SLOT_HEBDO_QUETE = 13;

    public void openHebdo(Player p) {
        Inventory menu = Bukkit.createInventory(null, 45, HEBDO_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);

        Hebdo h = hebdo(p);
        int palier = palier(p);
        long obj = OBJ_HEBDO[h.type];
        long fait = Math.min(progressionHebdo(p), obj);
        boolean fini = fait >= obj;
        boolean prise = hebdoReclamee.getOrDefault(p.getUniqueId(), false);

        menu.setItem(4, plugin.namedItem(Material.PAPER, "§b§lQuête de la semaine",
                "", "§7Une seule quête, une seule fois.",
                "§7Nouvelle quête chaque §fsamedi à minuit§7.",
                "§8Ce qui n'est pas terminé est perdu."));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + consigneHebdo(h.type, obj));
        lore.add("");
        lore.add("§7Progression : " + (fini ? "§a" : "§e") + fait + " §7/ §f" + obj);
        lore.add(barre(fait, obj));
        lore.add("");
        lore.add("§7Récompenses :");
        lore.add("  §8» §6Clé Légendaire §7×1");
        lore.add("  §8» §bUn familier ou une armure §7(" + plageRareteHebdo(palier) + "§7)");
        lore.add("  §8» §d" + REC_HEBDO_FRAGMENTS + " ✦ §7Fragments");
        lore.add("");
        if (prise)      lore.add("§a✔ Récompenses encaissées");
        else if (fini)  lore.add("§e▶ Clique pour réclamer");
        else            lore.add("§8Quête en cours");

        ItemStack it = plugin.namedItem(
                prise ? Material.LIME_STAINED_GLASS_PANE : iconeHebdo(h.type),
                (prise ? "§a" : fini ? "§e" : "") + titreHebdo(h.type),
                lore.toArray(new String[0]));
        menu.setItem(SLOT_HEBDO_QUETE, brillant(it, fini && !prise));

        menu.setItem(36, plugin.namedItem(Material.ARROW, "§7← Retour", (String) null));
        menu.setItem(40, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    private String plageRareteHebdo(int palier) {
        ArmorManager.Rarete[] all = ArmorManager.Rarete.values();
        return all[HEBDO_RARETE_MIN[palier]].display + " §7→ " + all[HEBDO_RARETE_MAX[palier]].display;
    }

    private void reclamerHebdo(Player p) {
        UUID id = p.getUniqueId();
        if (hebdoReclamee.getOrDefault(id, false)) { p.sendMessage("§cTu as déjà encaissé l'hebdo de cette semaine."); return; }
        if (!hebdoTerminee(p)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        hebdoReclamee.put(id, true);
        int palier = palier(p);

        p.sendMessage("");
        p.sendMessage("§b§l✔ Quête hebdomadaire §8— " + titreHebdo(hebdo(p).type));
        if (plugin.getCrates() != null) plugin.getCrates().giveKey(id, REC_HEBDO_CLE, 1);
        p.sendMessage("  §8» §6Clé Légendaire §7×1");
        if (plugin.getFragments() != null) plugin.getFragments().addFragments(p, REC_HEBDO_FRAGMENTS);
        p.sendMessage("  §8» §d" + REC_HEBDO_FRAGMENTS + " ✦ §7Fragments de Souvenir");
        donnerPetOuArmure(p, HEBDO_RARETE_MIN[palier], HEBDO_RARETE_MAX[palier]);
        p.sendMessage("");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        openHebdo(p);
    }

    /** Un familier ou une armure d'Oublié, dans la plage de rareté donnée (tirage 50/50). */
    private void donnerPetOuArmure(Player p, int min, int max) {
        Random rng = new Random();
        if (rng.nextBoolean() && plugin.getArmor() != null) {
            ArmorManager.Rarete[] all = ArmorManager.Rarete.values();
            int lo = Math.max(0, Math.min(min, all.length - 1));
            int hi = Math.max(lo, Math.min(max, all.length - 1));
            ArmorManager.Rarete rarete = all[lo + rng.nextInt(hi - lo + 1)];
            ItemStack it = plugin.getArmor().genererArmurePourRarete(rarete, rng);
            if (it != null) {
                for (ItemStack reste : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), reste);
                }
                p.sendMessage("  §8» " + rarete.display + " §8armure d'Oublié");
                return;
            }
        }
        if (plugin.getPetMenu() != null) plugin.getPetMenu().givePetInRarityRange(p, min, max);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LE HUB  /quest
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int SLOT_QUOTIDIENNES = 11, SLOT_HISTOIRE = 13, SLOT_HEBDO = 15;
    private static final int SLOT_SERVEUR = 20, SLOT_CONTRATS = 22, SLOT_ILE = 24;

    public void openMenu(Player p) {
        Inventory menu = Bukkit.createInventory(null, 45, MENU_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);

        // ── Quotidiennes (vivant).
        Jeu j = jeu(p);
        int restantes = 0;
        for (int i = 0; i < j.types.length; i++) if (!estReclamee(p, i)) restantes++;
        int finies = 0;
        for (int i = 0; i < j.types.length; i++) if (estTerminee(p, i) && !estReclamee(p, i)) finies++;
        List<String> lq = new ArrayList<>();
        lq.add("");
        lq.add("§7" + PAR_JOUR + " quêtes tirées chaque jour.");
        lq.add("§8Remises à zéro à minuit — une quête");
        lq.add("§8non terminée est perdue.");
        lq.add("");
        lq.add(finies > 0 ? "§a✔ " + finies + " récompense(s) à réclamer"
                          : "§7Quêtes restantes : §f" + restantes + "§7/§f" + j.types.length);
        lq.add("");
        lq.add("§e▶ Clique pour voir tes quêtes");
        menu.setItem(SLOT_QUOTIDIENNES, brillant(
                plugin.namedItem(Material.CLOCK, "§a§lQuêtes quotidiennes", lq.toArray(new String[0])), finies > 0));

        // ── Quête d'histoire (vivant) : reprend l'objectif d'Acte affiché par l'ancien /quest.
        String obj = plugin.getGuide() == null ? null : plugin.getGuide().getQuestObjective(p.getUniqueId());
        List<String> lh = new ArrayList<>();
        lh.add("");
        if (obj != null) {
            lh.add("§7Ton objectif en cours :");
            lh.add("  " + obj);
        } else {
            lh.add("§7Tu as terminé l'introduction.");
            lh.add("§8Aventure libre — creuse avec §7/mine§8.");
        }
        menu.setItem(SLOT_HISTOIRE, plugin.namedItem(Material.WRITTEN_BOOK, "§6§lQuête d'histoire",
                lh.toArray(new String[0])));

        // ── Les sections prévues mais pas encore codées : la place est réservée.
        // ── Hebdomadaire (vivant).
        Hebdo h = hebdo(p);
        boolean hFini = hebdoTerminee(p) && !hebdoReclamee.getOrDefault(p.getUniqueId(), false);
        List<String> lhe = new ArrayList<>();
        lhe.add("");
        lhe.add("§7" + consigneHebdo(h.type, OBJ_HEBDO[h.type]));
        lhe.add("§8Nouvelle quête chaque samedi à minuit.");
        lhe.add("");
        lhe.add(hFini ? "§a✔ Récompenses à réclamer"
                      : "§7Progression : §f" + Math.min(progressionHebdo(p), OBJ_HEBDO[h.type])
                        + " §7/ §f" + OBJ_HEBDO[h.type]);
        lhe.add("");
        lhe.add("§e▶ Clique pour voir la quête");
        menu.setItem(SLOT_HEBDO, brillant(
                plugin.namedItem(Material.PAPER, "§b§lQuête hebdomadaire", lhe.toArray(new String[0])), hFini));
        menu.setItem(SLOT_SERVEUR, bientot(Material.BEACON, "§d§lObjectif du serveur",
                "§7Tout le serveur vise un même but ;", "§7atteint, tout le monde est récompensé."));
        menu.setItem(SLOT_CONTRATS, bientot(Material.NAME_TAG, "§6§lContrats",
                "§7Trois contrats proposés, un seul", "§7à la fois : à toi de choisir."));
        menu.setItem(SLOT_ILE, bientot(Material.GRASS_BLOCK, "§a§lQuêtes d'île",
                "§7Objectifs liés à ta parcelle et", "§7à ton Bloc du Grand Appel."));

        menu.setItem(40, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    private ItemStack bientot(Material mat, String nom, String... lignes) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        Collections.addAll(lore, lignes);
        lore.add("");
        lore.add("§8✖ Bientôt disponible");
        return plugin.namedItem(mat, "§8" + org.bukkit.ChatColor.stripColor(nom), lore.toArray(new String[0]));
    }

    private ItemStack brillant(ItemStack it, boolean brille) {
        if (!brille) return it;
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LE MENU DES QUOTIDIENNES
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int[] SLOTS_QUETES = { 11, 13, 15 };

    private boolean estTerminee(Player p, int i) {
        Jeu j = jeu(p);
        long[] prog = progression.get(p.getUniqueId());
        if (prog == null || i >= j.types.length) return false;
        return prog[i] >= objectif(j.types[i], palier(p));
    }

    private boolean estReclamee(Player p, int i) {
        return (reclamees.getOrDefault(p.getUniqueId(), 0) & (1 << i)) != 0;
    }

    public void openDaily(Player p) {
        Inventory menu = Bukkit.createInventory(null, 45, DAILY_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);

        Jeu j = jeu(p);
        int palier = palier(p);
        long[] prog = progression.get(p.getUniqueId());

        menu.setItem(4, plugin.namedItem(Material.CLOCK, "§a§lQuêtes du jour",
                "", "§7Nouvelles quêtes chaque jour à §fminuit§7.",
                "§8Ce qui n'est pas terminé est perdu.",
                "", "§8Palier §f" + (palier + 1) + "§8/4 — les objectifs d'argent",
                "§8et de progression suivent ta mine."));

        for (int i = 0; i < j.types.length && i < SLOTS_QUETES.length; i++) {
            int type = j.types[i];
            long obj = objectif(type, palier);
            long fait = prog == null ? 0 : Math.min(prog[i], obj);
            boolean fini = fait >= obj;
            boolean prise = estReclamee(p, i);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7" + consigne(type, obj));
            lore.add("");
            lore.add("§7Progression : " + (fini ? "§a" : "§e") + affiche(type, fait)
                    + " §7/ §f" + affiche(type, obj));
            lore.add(barre(fait, obj));
            lore.add("");
            lore.add("§7Récompense : " + descRecompense(p, j.recompenses[i], palier));
            lore.add("");
            if (prise)      lore.add("§a✔ Récompense encaissée");
            else if (fini)  lore.add("§e▶ Clique pour réclamer");
            else            lore.add("§8Quête en cours");

            ItemStack it = plugin.namedItem(
                    prise ? Material.LIME_STAINED_GLASS_PANE : iconeQuete(type),
                    (prise ? "§a" : fini ? "§e" : "") + titreQuete(type),
                    lore.toArray(new String[0]));
            menu.setItem(SLOTS_QUETES[i], brillant(it, fini && !prise));
        }

        menu.setItem(36, plugin.namedItem(Material.ARROW, "§7← Retour", (String) null));
        menu.setItem(40, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    /** Un nombre lisible : les $ passent par le formateur, le reste s'affiche brut. */
    private String affiche(int type, long v) {
        return type == Q_VENDRE ? PrivateMines.formatNumber(v) + " $" : String.valueOf(v);
    }

    private String barre(long fait, long obj) {
        int cases = 20;
        int pleines = obj <= 0 ? cases : (int) Math.min(cases, (fait * cases) / obj);
        StringBuilder sb = new StringBuilder("§8[");
        sb.append("§a");
        for (int i = 0; i < pleines; i++) sb.append('|');
        sb.append("§7");
        for (int i = pleines; i < cases; i++) sb.append('|');
        sb.append("§8] §f").append(obj <= 0 ? 100 : Math.min(100, (fait * 100) / obj)).append(" %");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LA RÉCLAMATION
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private void reclamer(Player p, int i) {
        Jeu j = jeu(p);
        if (i >= j.types.length) return;
        if (estReclamee(p, i)) { p.sendMessage("§cTu as déjà encaissé cette quête."); return; }
        if (!estTerminee(p, i)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        UUID id = p.getUniqueId();
        reclamees.put(id, reclamees.getOrDefault(id, 0) | (1 << i));

        int palier = palier(p);
        p.sendMessage("");
        p.sendMessage("§a§l✔ Quête terminée §8— " + titreQuete(j.types[i]));
        switch (j.recompenses[i]) {
            case R_ARGENT: {
                double m = montantArgent(p);
                if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(p, m);
                p.sendMessage("  §8» §6" + PrivateMines.formatNumber(m) + " $");
                break;
            }
            case R_FRAGMENTS: {
                int n = REC_FRAGMENTS[palier];
                if (plugin.getFragments() != null) plugin.getFragments().addFragments(p, n);
                p.sendMessage("  §8» §d" + n + " ✦ §7Fragments de Souvenir");
                break;
            }
            case R_CLE: {
                String rang = REC_CLE_RANG[palier];
                if (plugin.getCrates() != null) plugin.getCrates().giveKey(id, rang, 1);
                p.sendMessage("  §8» " + ("rare".equals(rang) ? "§9Clé Rare" : "§fClé Commune") + " §7×1");
                break;
            }
            default: {
                if (plugin.getCrates() != null) plugin.getCrates().grantRandomEnchantLevels(p, 1);
                break;
            }
        }
        p.sendMessage("");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        avancerHebdo(p, W_QUOTIDIENNES, 1);   // hebdo « Assidu » : X quotidiennes dans la semaine
        openDaily(p);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  CLICS
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        Player p = (Player) event.getWhoClicked();

        if (MENU_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == SLOT_QUOTIDIENNES) { openDaily(p); return; }
            if (slot == SLOT_HEBDO) { openHebdo(p); return; }
            if (slot == 40) { p.closeInventory(); return; }
            return;
        }

        if (DAILY_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 36) { openMenu(p); return; }
            if (slot == 40) { p.closeInventory(); return; }
            for (int i = 0; i < SLOTS_QUETES.length; i++) {
                if (SLOTS_QUETES[i] == slot) { reclamer(p, i); return; }
            }
            return;
        }

        if (HEBDO_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 36) { openMenu(p); return; }
            if (slot == 40) { p.closeInventory(); return; }
            if (slot == SLOT_HEBDO_QUETE) { reclamerHebdo(p); return; }
        }
    }
}
