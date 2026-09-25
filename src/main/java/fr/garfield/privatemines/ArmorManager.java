package fr.garfield.privatemines;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * ACTE IV — Armures des anciens mineurs (les Oubliés) À BONUS.
 *
 * <p>Chaque armure retrouvée en minant est une « armure d'Oublié » : une pièce d'armure vanilla
 * marquée d'un tag PDC, dotée d'une RARETÉ (selon la matière) et de 1 à 5 BONUS tirés d'un pool.
 * Les bonus s'appliquent <b>quand l'armure est PORTÉE</b> (type RPG) et se cumulent avec ceux des
 * familiers (même conteneur {@link PetEquipMenu.Bonus}).</p>
 *
 * <p>Stockage : tag PDC {@code oubli_armor} = "1" (identifie l'item), {@code oubli_rarete} = clé de
 * rareté, {@code oubli_bonus} = liste compacte {@code "SELL:3.2;BAG:1.0"} (type:valeur séparés par ';').</p>
 *
 * <p>Cette classe = FONDATION (structure, stats, lore, agrégation portée). La forge (reroll/améliorer/
 * ajouter contre Fragments) et le drop en minant viendront dans des briques suivantes.</p>
 */
public class ArmorManager {

    private final PrivateMines plugin;
    private final NamespacedKey armorKey;   // oubli_armor : marque l'item comme armure d'Oublié
    private final NamespacedKey rareteKey;  // oubli_rarete : clé de rareté
    private final NamespacedKey bonusKey;   // oubli_bonus : liste compacte des bonus
    private final NamespacedKey forgeKey;   // oubli_forge : nb d'améliorations forge déjà faites (coût croissant)

    public ArmorManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.armorKey  = new NamespacedKey(plugin, "oubli_armor");
        this.rareteKey = new NamespacedKey(plugin, "oubli_rarete");
        this.bonusKey  = new NamespacedKey(plugin, "oubli_bonus");
        this.forgeKey  = new NamespacedKey(plugin, "oubli_forge");
    }

    // ================================================================= RARETÉ

    /**
     * Raretés (couleurs façon CrateManager) + nombre de slots de bonus + plage de valeur PAR PIÈCE.
     *
     * <p>{@code maxStat} = valeur PLAFOND (jackpot) d'un bonus sur une pièce de cette rareté, en % ;
     * la courbe monte régulièrement du Cuir (20) à la Netherite (80). {@code minStat} = plancher
     * garanti = 60 % du max (même un mauvais roll reste correct). Chaque bonus est tiré dans
     * [minStat, maxStat], pondéré vers le bas (voir {@link #tirerValeur}). Ces valeurs sont PAR
     * PIÈCE (plus de division /4 : une seule pièce peut atteindre son max).</p>
     */
    public enum Rarete {
        COMMUNE     ("commune",     "§fCommune",      "§f", 1, 20.0),
        PEU_COMMUNE ("peu_commune", "§aPeu commune",  "§a", 2, 30.0),
        RARE        ("rare",        "§9Rare",         "§9", 3, 45.0),
        EPIQUE      ("epique",      "§5Épique",       "§5", 4, 55.0),
        LEGENDAIRE  ("legendaire",  "§6Légendaire",   "§6", 5, 68.0),
        MYTHIQUE    ("mythique",    "§dMythique",     "§d", 6, 80.0);

        public final String key, display, color;
        public final int slots;
        public final double maxStat; // plafond (%) d'un bonus par pièce de cette rareté
        Rarete(String key, String display, String color, int slots, double maxStat) {
            this.key = key; this.display = display; this.color = color;
            this.slots = slots; this.maxStat = maxStat;
        }
        /** Plancher garanti d'un bonus = 60 % du max de la rareté. */
        public double minStat() { return maxStat * 0.60; }
        public static Rarete byKey(String k) {
            for (Rarete r : values()) if (r.key.equalsIgnoreCase(k)) return r;
            return COMMUNE;
        }
    }

    /**
     * Chance de drop d'une armure de cette matière en minant, en % PAR BLOC (valeurs
     * volontairement basses ; la matière rare tombe moins souvent). Le drop est branché dans
     * {@code PrivateMines.triggerArmorDrop} et sert aussi à l'affichage du Codex.
     * Ces % valent pour l'ENSEMBLE des pièces (casque/plastron/jambières/bottes) de la matière.
     */
    public static double dropPercent(Material mat) {
        // Taux ×1,5 (2026-07-27) pour dropper ~50% d'armures en plus, MÊMES proportions entre matières.
        String n = mat.name();
        if (n.startsWith("LEATHER") || mat == Material.TURTLE_HELMET) return 0.75;
        if (n.startsWith("CHAINMAIL")) return 0.525;
        if (n.startsWith("IRON"))      return 0.3;
        if (n.startsWith("GOLDEN"))    return 0.1875;
        if (n.startsWith("DIAMOND"))   return 0.09;
        if (n.startsWith("NETHERITE")) return 0.0375;
        return 0.0;
    }

    // ================================================================= PROGRESSION PAR MATIÈRE

    /**
     * Matières d'armure, dans l'ordre de progression. Chaque matière se débloque à un seuil
     * d'armures recyclées ({@code seuil}) : à la mine 15 on ne drope QUE du cuir, puis on
     * débloque les mailles, le fer, etc. en recyclant. {@code prefix} = préfixe des Material.
     */
    public enum Matiere {
        CUIR      ("Cuir",      "§f", "LEATHER",   0),
        MAILLES   ("Mailles",   "§7", "CHAINMAIL", 200),
        FER       ("Fer",       "§f", "IRON",      450),
        OR        ("Or",        "§6", "GOLDEN",    850),
        DIAMANT   ("Diamant",   "§b", "DIAMOND",   1500),
        NETHERITE ("Netherite", "§8", "NETHERITE", 2500);

        public final String nom, couleur, prefix;
        public final int seuil; // armures recyclées nécessaires pour débloquer cette matière
        Matiere(String nom, String couleur, String prefix, int seuil) {
            this.nom = nom; this.couleur = couleur; this.prefix = prefix; this.seuil = seuil;
        }
        public boolean estDebloquee(int recyclees) { return recyclees >= seuil; }
    }

    /** Retrouve la Matiere d'un Material d'armure (cuir/tortue = CUIR), ou null. */
    public static Matiere matiereDe(Material mat) {
        if (mat == Material.TURTLE_HELMET) return Matiere.CUIR;
        String n = mat.name();
        for (Matiere m : Matiere.values()) if (n.startsWith(m.prefix)) return m;
        return null;
    }

    // Progression du drop par PALIER (1 tous les 50 armures recyclées). Le boost est LINÉAIRE
    // (additif, jamais exponentiel) : chaque palier ajoute un % FIXE du drop de base, propre à la
    // matière. Le cuir (rang 0) BAISSE (−5 %/palier) tandis que les matières hautes montent de plus
    // en plus vite (netherite +12 %/palier). Résultat : plus tu progresses, moins le cuir domine et
    // plus tu as de chances de dropper du bon matériel — mais la montée reste douce et prévisible.
    public static final int PAS_PALIER = 50;
    // Hausse ADDITIVE par palier, par rang de matière : cuir −5% · mailles +2% · fer +5% · or +8%
    // · diamant +10% · netherite +12% (du drop de base, par palier).
    private static final double[] HAUSSE_PALIER = { -0.05, 0.02, 0.05, 0.08, 0.10, 0.12 };

    /** Nombre de paliers de boost franchis (1 tous les 50 armures recyclées). */
    public static int nbPaliersBoost(int recyclees) {
        return recyclees / PAS_PALIER;
    }

    /** Hausse ADDITIVE par palier d'une matière (fraction du drop de base) : cuir −0.05 → netherite +0.12. */
    public static double haussePalier(Matiere m) {
        int i = m.ordinal();
        return (i >= 0 && i < HAUSSE_PALIER.length) ? HAUSSE_PALIER[i] : 0.0;
    }

    /**
     * Seuil (en armures recyclées) à partir duquel une matière COMMENCE à évoluer : c'est le seuil
     * de déblocage de la matière SUIVANTE (plus rare). Tant qu'on n'a rien débloqué de mieux, la
     * matière reste à son taux de base (le cuir seul reste à 1 %). La dernière matière (netherite)
     * n'a pas de suivante → elle prend son propre seuil de déblocage.
     */
    private static int seuilEvolution(Matiere m) {
        Matiere[] all = Matiere.values();
        int next = m.ordinal() + 1;
        return (next < all.length) ? all[next].seuil : m.seuil;
    }

    /**
     * Multiplicateur de drop actuel d'une matière : LINÉAIRE = 1 + haussePalier(m) × (paliers
     * EFFECTIFS), où les paliers effectifs ne sont comptés qu'à partir de {@link #seuilEvolution}
     * (déblocage du niveau supérieur). Avant ce seuil, la matière reste à son taux de base (×1).
     * Plancher à 0 pour que le cuir (hausse négative) ne passe jamais en drop négatif.
     */
    public static double boostDropMatiere(Matiere m, int recyclees) {
        int depart = seuilEvolution(m);
        if (recyclees < depart) return 1.0; // pas encore de « mieux » débloqué → taux de base
        int paliersEffectifs = (recyclees - depart) / PAS_PALIER;
        double boost = 1.0 + haussePalier(m) * paliersEffectifs;
        return Math.max(0.0, boost);
    }

    /**
     * Armures encore à recycler avant que le boost de drop de cette matière passe au palier suivant.
     * Avant le seuil d'évolution, on compte jusqu'à ce seuil ; ensuite, jusqu'au prochain multiple de 50.
     */
    public static int armuresAvantProchainPalier(Matiere m, int recyclees) {
        int depart = seuilEvolution(m);
        if (recyclees < depart) return depart - recyclees;
        int reste = (recyclees - depart) % PAS_PALIER;
        return PAS_PALIER - reste;
    }

    /**
     * Boost « global » indicatif pour l'affichage (moyenne façon repère) : on renvoie le boost
     * du CUIR, la matière de référence de départ. Sert seulement au Codex comme indicateur.
     */
    public static double boostDrop(int recyclees) {
        return boostDropMatiere(Matiere.CUIR, recyclees);
    }

    /**
     * Chance de drop EFFECTIVE d'une matière pour un joueur, en % par bloc : 0 si la matière
     * n'est pas encore débloquée, sinon {@link #dropPercent} × {@link #boostDropMatiere} (le
     * boost dépend de la matière : cuir baisse, matières hautes montent).
     */
    public static double dropPercentEffectif(Material mat, int recyclees) {
        Matiere m = matiereDe(mat);
        if (m == null || !m.estDebloquee(recyclees)) return 0.0;
        return dropPercent(mat) * boostDropMatiere(m, recyclees);
    }

    /** Rareté « de base » d'une pièce selon sa matière (cuir bas → netherite haut). */
    public static Rarete rareteDeBase(Material mat) {
        String n = mat.name();
        if (n.startsWith("LEATHER") || mat == Material.TURTLE_HELMET) return Rarete.COMMUNE;
        if (n.startsWith("CHAINMAIL")) return Rarete.PEU_COMMUNE;
        if (n.startsWith("IRON"))      return Rarete.RARE;
        if (n.startsWith("GOLDEN"))    return Rarete.EPIQUE;
        if (n.startsWith("DIAMOND"))   return Rarete.LEGENDAIRE;
        if (n.startsWith("NETHERITE")) return Rarete.MYTHIQUE;
        return Rarete.COMMUNE;
    }

    // ================================================================= POOL DE BONUS

    /**
     * Pool de bonus v1. Chaque bonus a une plage de valeur MIN..MAX au niveau de rareté le plus BAS
     * (Commune) ; la plage est multipliée par le facteur de rareté (voir {@link #facteurRarete}).
     * Le champ {@code cible} indique dans quel champ de {@link PetEquipMenu.Bonus} il s'agrège.
     */
    public enum ArmorBonus {
        // ── Tes 3 de base ───────────────────────────────────────────────
        RECYCLAGE ("RECY", "§d", "gain de recyclage", 0.5, 2.0),
        VENTE     ("SELL", "§e", "vente des blocs",   0.5, 3.0),
        SAC       ("BAG",  "§b", "taille du sac",      0.5, 2.5),
        // ── Éco & minage ────────────────────────────────────────────────
        ARGENT    ("MONEY","§6", "argent en minant",   0.5, 3.0),
        XP_PIOCHE ("XP",   "§a", "XP de pioche",        1.0, 4.0),
        DOUBLE    ("DBL",  "§f", "chance de double-bloc", 0.5, 2.0),
        CLE       ("KEY",  "§9", "chance de clé",       0.2, 1.0);

        public final String code, couleur, label;
        public final double min, max;
        ArmorBonus(String code, String couleur, String label, double min, double max) {
            this.code = code; this.couleur = couleur; this.label = label; this.min = min; this.max = max;
        }
        public static ArmorBonus byCode(String c) {
            for (ArmorBonus b : values()) if (b.code.equalsIgnoreCase(c)) return b;
            return null;
        }
    }


    // Poids du tirage du NOMBRE de bonus, pour les positions 1..5 (souvent 1-2, rarement 5).
    private static final int[] POIDS_NB_BONUS = {40, 30, 18, 9, 3};

    /**
     * Tire le nombre de bonus (1..maxSlots) avec une pondération décroissante : on a beaucoup
     * plus de chances d'obtenir peu de bonus (le max de la rareté reste un jackpot).
     */
    static int tirerNombreBonus(int maxSlots, long seed) {
        if (maxSlots <= 1) return 1;
        int n = Math.min(maxSlots, POIDS_NB_BONUS.length);
        int total = 0;
        for (int i = 0; i < n; i++) total += POIDS_NB_BONUS[i];
        int r = (int) Math.floorMod(seed >>> 13, total);
        int acc = 0;
        for (int i = 0; i < n; i++) {
            acc += POIDS_NB_BONUS[i];
            if (r < acc) return i + 1;
        }
        return 1;
    }

    /** Facteur multiplicateur des valeurs de bonus selon la rareté (Commune ×1 → Mythique ×5). */
    public static double facteurRarete(Rarete r) {
        switch (r) {
            case COMMUNE:     return 1.0;
            case PEU_COMMUNE: return 1.6;
            case RARE:        return 2.4;
            case EPIQUE:      return 3.4;
            case LEGENDAIRE:  return 4.5;
            case MYTHIQUE:    return 5.5;
            default:          return 1.0;
        }
    }

    // Courbe de rareté des rolls : on veut, sur les 4 quarts de la plage [min,max], une répartition
    // décroissante ~40 % / 30 % / 20 % / 10 % (le quart le plus fort — proche du max — est le plus rare).
    // t = u^EXPO_ROLL réalise ça : EXPO_ROLL ≈ 1.66 donne P(quart bas)=1-0.75^e≈40 %,
    // P(quart haut)=1-0.90^e≈… bref une décroissance proche de 40/30/20/10.
    private static final double EXPO_ROLL = 1.66;

    /**
     * Tire la VALEUR d'un bonus (en %) pour une rareté donnée : uniforme déterministe u∈[0,1[
     * transformé par {@code u^EXPO_ROLL} (pondéré vers le bas), remis à l'échelle sur la plage
     * PAR PIÈCE [minStat, maxStat] de la rareté. Résultat arrondi à 1 décimale, jamais < minStat.
     */
    static double tirerValeur(Rarete rarete, long seed) {
        double u = (Math.floorMod(seed >>> 11, 1000)) / 1000.0; // 0..1 uniforme déterministe
        double t = Math.pow(u, EXPO_ROLL);                       // pondéré vers le bas
        double min = rarete.minStat();
        double max = rarete.maxStat;
        double val = min + t * (max - min);
        val = Math.round(val * 10.0) / 10.0;
        return Math.max(min, val);
    }

    // ================================================================= LECTURE / TEST

    public boolean isArmureOubli(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return "1".equals(it.getItemMeta().getPersistentDataContainer()
                .get(armorKey, PersistentDataType.STRING));
    }

    public Rarete getRarete(ItemStack it) {
        if (!isArmureOubli(it)) return Rarete.COMMUNE;
        return Rarete.byKey(it.getItemMeta().getPersistentDataContainer()
                .get(rareteKey, PersistentDataType.STRING));
    }

    /** Un bonus porté par une armure : quel type + quelle valeur. */
    public static final class BonusInstance {
        public final ArmorBonus type;
        public final double valeur;
        public BonusInstance(ArmorBonus type, double valeur) { this.type = type; this.valeur = valeur; }
    }

    /** Décode la liste de bonus stockée en PDC ("SELL:3.2;BAG:1.0"). */
    public List<BonusInstance> getBonuses(ItemStack it) {
        List<BonusInstance> out = new ArrayList<>();
        if (!isArmureOubli(it)) return out;
        String raw = it.getItemMeta().getPersistentDataContainer().get(bonusKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            ArmorBonus b = ArmorBonus.byCode(kv[0]);
            if (b == null) continue;
            try {
                out.add(new BonusInstance(b, Double.parseDouble(kv[1])));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static String encodeBonuses(List<BonusInstance> list) {
        StringBuilder sb = new StringBuilder();
        for (BonusInstance bi : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(bi.type.code).append(':').append(String.format(Locale.US, "%.2f", bi.valeur));
        }
        return sb.toString();
    }

    // ================================================================= GÉNÉRATION

    /**
     * Transforme une pièce d'armure vanilla en « armure d'Oublié » : rareté selon la matière,
     * 1..5 bonus distincts tirés du pool, valeurs mises à l'échelle de la rareté. Écrit le tout
     * en PDC et pose le lore coloré. {@code index} = graine déterministe (slot) pour éviter
     * Math.random() (indisponible ici) ; on dérive un pseudo-aléa des identifiants du joueur.
     */
    public ItemStack genererArmure(ItemStack base, Rarete rarete, long graine) {
        if (base == null) return null;
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        // Tirage des bonus : on mélange le pool avec un pseudo-aléa déterministe, on prend `nb` premiers.
        List<ArmorBonus> pool = new ArrayList<>(Arrays.asList(ArmorBonus.values()));
        long seed = graine * 2862933555777941757L + 3037000493L;
        for (int i = pool.size() - 1; i > 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int) Math.floorMod(seed >>> 17, i + 1);
            ArmorBonus tmp = pool.get(i); pool.set(i, pool.get(j)); pool.set(j, tmp);
        }
        // NOMBRE de bonus : tirage PONDÉRÉ décroissant (souvent 1-2, rarement le max de la rareté).
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        int nb = tirerNombreBonus(rarete.slots, seed);

        List<BonusInstance> chosen = new ArrayList<>();
        for (int i = 0; i < nb && i < pool.size(); i++) {
            ArmorBonus b = pool.get(i);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            // VALEUR par pièce : dépend UNIQUEMENT de la rareté (plage [min,max] du Cuir 12-20 → Netherite 48-80),
            // pondérée vers le bas (courbe 40/30/20/10). Chaque pièce peut atteindre son max seule.
            double val = tirerValeur(rarete, seed);
            chosen.add(new BonusInstance(b, val));
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(armorKey, PersistentDataType.STRING, "1");
        pdc.set(rareteKey, PersistentDataType.STRING, rarete.key);
        pdc.set(bonusKey, PersistentDataType.STRING, encodeBonuses(chosen));

        appliquerNomEtLore(meta, it.getType(), rarete, chosen);
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Fabrique une armure d'Oublié d'une RARETÉ donnée (utilisé par les CRATES), avec une
     * matière/pièce cohérente avec cette rareté et une pièce d'armure au hasard. La rareté
     * détermine à la fois la matière (Cuir=Commune … Netherite=Mythique) et le nombre de slots.
     */
    public ItemStack genererArmurePourRarete(Rarete rarete, java.util.Random rng) {
        // Matière dont la rareté de base = la rareté voulue (l'inverse de rareteDeBase).
        String prefix;
        switch (rarete) {
            case COMMUNE:     prefix = "LEATHER";   break;
            case PEU_COMMUNE: prefix = "CHAINMAIL"; break;
            case RARE:        prefix = "IRON";      break;
            case EPIQUE:      prefix = "GOLDEN";    break;
            case LEGENDAIRE:  prefix = "DIAMOND";   break;
            case MYTHIQUE:    prefix = "NETHERITE"; break;
            default:          prefix = "LEATHER";   break;
        }
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        String piece = pieces[rng.nextInt(pieces.length)];
        Material concret;
        try {
            concret = Material.valueOf(prefix + "_" + piece);
        } catch (IllegalArgumentException ex) {
            concret = Material.LEATHER_HELMET; // filet de sécurité
        }
        return genererArmure(new ItemStack(concret), rarete, rng.nextLong());
    }

    /** (Re)pose le nom + le lore coloré d'une armure d'Oublié. */
    private void appliquerNomEtLore(ItemMeta meta, Material mat, Rarete rarete, List<BonusInstance> bonuses) {
        meta.setDisplayName(rarete.color + "§lArmure d'Oublié §7» " + nomPiece(mat));
        List<String> lore = new ArrayList<>();
        lore.add("§8Rareté : " + rarete.display);
        lore.add("");
        lore.add("§7Bonus §8(actifs quand portée)§7 :");
        for (BonusInstance bi : bonuses) {
            lore.add("§8• " + bi.type.couleur + "+" + fmt(bi.valeur) + "% §7" + bi.type.label);
        }
        lore.add("");
        lore.add("§8§oL'armure d'un mineur qui n'est jamais remonté.");
        meta.setLore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
    }

    private static String nomPiece(Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET") || mat == Material.TURTLE_HELMET) return "§fCasque";
        if (n.endsWith("_CHESTPLATE")) return "§fPlastron";
        if (n.endsWith("_LEGGINGS"))   return "§fJambières";
        if (n.endsWith("_BOOTS"))      return "§fBottes";
        return "§fArmure";
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.1f", v);
    }

    // ================================================================= AGRÉGATION (portée)

    /**
     * Agrège les bonus de toutes les armures d'Oublié PORTÉES par le joueur, dans un
     * {@link PetEquipMenu.Bonus} (mêmes champs que les pets, pour cumul naturel). Les bonus
     * hors du conteneur pet (XP pioche, chance de clé, gain recyclage) sont exposés à part.
     */
    public WornArmor aggregate(Player p) {
        WornArmor w = new WornArmor();
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (!isArmureOubli(piece)) continue;
            for (BonusInstance bi : getBonuses(piece)) {
                switch (bi.type) {
                    case VENTE:     w.bonus.sellMoneyPct   += bi.valeur; break;
                    case SAC:       w.bonus.capacityPct    += bi.valeur; break;
                    case ARGENT:    w.bonus.blockValuePct  += bi.valeur; break;
                    case DOUBLE:    w.bonus.doubleBlockPct += bi.valeur; break;
                    case RECYCLAGE: w.recyclePct   += bi.valeur; break;
                    case XP_PIOCHE: w.pickaxeXpPct  += bi.valeur; break;
                    case CLE:       w.keyPct        += bi.valeur; break;
                }
            }
        }
        return w;
    }

    /** Bonus portés agrégés : la partie « pet-compatible » + les extras propres aux armures. */
    public static final class WornArmor {
        public final PetEquipMenu.Bonus bonus = new PetEquipMenu.Bonus();
        public double recyclePct;   // +% Fragments au recyclage
        public double pickaxeXpPct; // +% XP de pioche
        public double keyPct;       // +% chance de clé
    }

    // ================================================================= FORGE (Acte IV — brique 2)
    //  Dépenser des Fragments de Souvenir pour retravailler les bonus d'une armure d'Oublié :
    //   • Reroll  : remplace l'effet d'un slot par un autre type (valeur re-tirée).
    //   • Améliorer: +20 % de la valeur de base du bonus, plafonné à ×2 la valeur max de rareté.
    //   • Ajouter : ajoute un bonus d'un type absent, dans un slot libre (selon la rareté).
    //  Coûts = base × facteurRarete(rareté). Toutes ces opérations réécrivent le PDC + le lore.

    /** Coûts de base des actions de forge (avant facteur de rareté). Barème « progressif par rareté ». */
    public static final int FORGE_BASE_REROLL   = 50;
    public static final int FORGE_BASE_AMELIORER = 30;
    public static final int FORGE_BASE_AJOUTER   = 100;
    public static final int FORGE_BASE_RETIRER   = 40;

    /** Coût en Fragments pour rerollen un slot de cette armure. */
    public int coutReroll(ItemStack it)   { return coutForge(FORGE_BASE_REROLL,   it); }
    /** Coût en Fragments pour améliorer un bonus de cette armure. */
    public int coutAmeliorer(ItemStack it){ return coutForge(FORGE_BASE_AMELIORER, it); }
    /** Coût en Fragments pour ajouter un bonus à cette armure. */
    public int coutAjouter(ItemStack it)  { return coutForge(FORGE_BASE_AJOUTER,   it); }
    /** Coût en Fragments pour retirer un bonus au hasard de cette armure. */
    public int coutRetirer(ItemStack it)  { return coutForge(FORGE_BASE_RETIRER,   it); }

    // Le coût monte de +15 % à CHAQUE amélioration déjà faite sur l'armure (compteur gravé
    // dans le PDC → persiste si on quitte le menu, re-dépose l'armure ou redémarre le serveur).
    private static final double FORGE_HAUSSE = 1.15;

    private int coutForge(int base, ItemStack it) {
        Rarete r = getRarete(it);
        double c = base * facteurRarete(r) * Math.pow(FORGE_HAUSSE, getForgeCount(it));
        return (int) Math.round(c);
    }

    /** Nombre d'améliorations forge déjà appliquées à cette armure (0 par défaut). */
    public int getForgeCount(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer n = it.getItemMeta().getPersistentDataContainer().get(forgeKey, PersistentDataType.INTEGER);
        return n == null ? 0 : Math.max(0, n);
    }

    /** Incrémente de 1 le compteur d'améliorations gravé sur l'armure (après une action forge réussie). */
    private void bumpForgeCount(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        int n = getForgeCount(it);
        meta.getPersistentDataContainer().set(forgeKey, PersistentDataType.INTEGER, n + 1);
        it.setItemMeta(meta);
    }

    /** Valeur PLAFOND d'un bonus sur une armure de cette rareté = le max PAR PIÈCE de la rareté. */
    private static double plafondValeur(ArmorBonus b, Rarete rarete) {
        return rarete.maxStat; // Cuir 20 → Netherite 80
    }

    /** Valeur « de base » (départ) d'un bonus fraîchement ajouté, tirée dans la plage de rareté. */
    private static double valeurAjout(ArmorBonus b, Rarete rarete, long seed) {
        return tirerValeur(rarete, seed);
    }

    /** Réécrit la liste de bonus dans le PDC + le lore d'une armure existante (garde la rareté). */
    public void ecrireBonuses(ItemStack it, List<BonusInstance> bonuses) {
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        Rarete rarete = getRarete(it);
        meta.getPersistentDataContainer().set(bonusKey, PersistentDataType.STRING, encodeBonuses(bonuses));
        appliquerNomEtLore(meta, it.getType(), rarete, bonuses);
        it.setItemMeta(meta);
    }

    /**
     * REROLL : remplace le bonus du slot {@code index} par un type tiré au hasard (parmi ceux
     * absents des autres slots) avec une valeur re-tirée. Renvoie true si l'opération a eu lieu.
     */
    public boolean rerollSlot(ItemStack it, int index, long seed) {
        List<BonusInstance> list = getBonuses(it);
        if (index < 0 || index >= list.size()) return false;
        Rarete rarete = getRarete(it);
        // Types déjà présents dans les AUTRES slots (on garde des types distincts).
        List<ArmorBonus> autres = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) if (i != index) autres.add(list.get(i).type);
        List<ArmorBonus> candidats = new ArrayList<>();
        for (ArmorBonus b : ArmorBonus.values()) if (!autres.contains(b)) candidats.add(b);
        if (candidats.isEmpty()) return false;
        long s = seed * 6364136223846793005L + 1442695040888963407L;
        ArmorBonus choisi = candidats.get((int) Math.floorMod(s >>> 15, candidats.size()));
        s = s * 6364136223846793005L + 1442695040888963407L;
        list.set(index, new BonusInstance(choisi, valeurAjout(choisi, rarete, s)));
        ecrireBonuses(it, list);
        return true;
    }

    /**
     * AMÉLIORER : +20 % de la valeur de base du bonus du slot {@code index}, plafonné à ×2 la
     * valeur max de rareté. Renvoie false si déjà au plafond (rien facturé côté appelant).
     */
    public boolean ameliorerSlot(ItemStack it, int index) {
        List<BonusInstance> list = getBonuses(it);
        if (index < 0 || index >= list.size()) return false;
        Rarete rarete = getRarete(it);
        BonusInstance bi = list.get(index);
        double plafond = plafondValeur(bi.type, rarete); // = maxStat de la rareté
        if (bi.valeur >= plafond) return false; // déjà au max
        // +20 % de l'étendue de la plage de rareté (max−min) à chaque amélioration.
        double increment = Math.max(0.1, Math.round((rarete.maxStat - rarete.minStat()) * 0.20 * 10.0) / 10.0);
        double nouvelle = Math.min(plafond, Math.round((bi.valeur + increment) * 10.0) / 10.0);
        list.set(index, new BonusInstance(bi.type, nouvelle));
        ecrireBonuses(it, list);
        return true;
    }

    /**
     * REROLL ALÉATOIRE : choisit UN slot au hasard (le joueur ne choisit pas) et le reroll.
     * Renvoie true si un reroll a eu lieu.
     */
    public boolean rerollAleatoire(ItemStack it, long seed) {
        int n = getBonuses(it).size();
        if (n <= 0) return false;
        long s = seed * 6364136223846793005L + 1442695040888963407L;
        int index = (int) Math.floorMod(s >>> 19, n);
        boolean ok = rerollSlot(it, index, s);
        if (ok) bumpForgeCount(it);
        return ok;
    }

    /**
     * AMÉLIORER ALÉATOIRE : choisit AU HASARD un bonus PAS ENCORE au plafond et l'améliore.
     * Renvoie false si tous les bonus sont déjà au maximum (rien facturé côté appelant).
     */
    public boolean ameliorerAleatoire(ItemStack it, long seed) {
        List<BonusInstance> list = getBonuses(it);
        List<Integer> ameliorables = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) if (!estAuPlafond(it, i)) ameliorables.add(i);
        if (ameliorables.isEmpty()) return false;
        long s = seed * 6364136223846793005L + 1442695040888963407L;
        int index = ameliorables.get((int) Math.floorMod(s >>> 19, ameliorables.size()));
        boolean ok = ameliorerSlot(it, index);
        if (ok) bumpForgeCount(it);
        return ok;
    }

    /** Vrai si le bonus du slot {@code index} est déjà à sa valeur plafond. */
    public boolean estAuPlafond(ItemStack it, int index) {
        List<BonusInstance> list = getBonuses(it);
        if (index < 0 || index >= list.size()) return true;
        BonusInstance bi = list.get(index);
        return bi.valeur >= plafondValeur(bi.type, getRarete(it));
    }

    /** Nombre de slots libres = slots de rareté − bonus déjà présents. */
    public int slotsLibres(ItemStack it) {
        Rarete rarete = getRarete(it);
        return Math.max(0, rarete.slots - getBonuses(it).size());
    }

    /**
     * AJOUTER : ajoute un bonus d'un type ABSENT dans un slot libre (si la rareté en laisse).
     * Renvoie false si plus de slot libre ou plus de type disponible.
     */
    public boolean ajouterBonus(ItemStack it, long seed) {
        List<BonusInstance> list = getBonuses(it);
        Rarete rarete = getRarete(it);
        if (list.size() >= rarete.slots) return false; // plus de slot
        List<ArmorBonus> presents = new ArrayList<>();
        for (BonusInstance bi : list) presents.add(bi.type);
        List<ArmorBonus> candidats = new ArrayList<>();
        for (ArmorBonus b : ArmorBonus.values()) if (!presents.contains(b)) candidats.add(b);
        if (candidats.isEmpty()) return false;
        long s = seed * 6364136223846793005L + 1442695040888963407L;
        ArmorBonus choisi = candidats.get((int) Math.floorMod(s >>> 15, candidats.size()));
        s = s * 6364136223846793005L + 1442695040888963407L;
        list.add(new BonusInstance(choisi, valeurAjout(choisi, rarete, s)));
        ecrireBonuses(it, list);
        bumpForgeCount(it);
        return true;
    }

    /**
     * RETIRER : enlève UN bonus au hasard de l'armure. Garde-fou : on ne retire jamais le DERNIER
     * bonus (l'armure conserve toujours au moins 1 effet). Renvoie false s'il ne reste qu'un bonus
     * (ou aucun) — dans ce cas rien n'est facturé côté appelant.
     */
    public boolean retirerBonusAleatoire(ItemStack it, long seed) {
        List<BonusInstance> list = getBonuses(it);
        if (list.size() <= 1) return false; // on garde au moins 1 bonus
        long s = seed * 6364136223846793005L + 1442695040888963407L;
        int index = (int) Math.floorMod(s >>> 17, list.size());
        list.remove(index);
        ecrireBonuses(it, list);
        bumpForgeCount(it);
        return true;
    }
}
