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

/**
 * Enchantements de la pioche (Efficacité, Explosion, Forage, Fracture, Vein Miner) :
 * niveaux par joueur, courbes de coût, fonctions de stat (chance/rayon/...) et menu GUI.
 * Les effets de minage (triggers) restent dans PrivateMines (couplés au cœur BLOCK_DIG)
 * et appellent les getters/stats de ce manager.
 */
public class EnchantManager implements Listener {

    private final PrivateMines plugin;

    public EnchantManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ===== Niveaux par joueur =====
    // Efficacité plafonnée à 100 (pour miner l'obsidienne à venir). Les autres enchants
    // disponibles montent jusqu'à 1000. La Dîme reste un enchant court (10 niveaux).
    public static final int EFFICIENCY_MAX = 100;
    public static final int EXPLOSION_MAX  = 200;
    public static final int FORAGE_MAX     = 200;
    public static final int FRACTURE_MAX   = 200;
    public static final int VEIN_MAX       = 200;
    public static final int COLONNE_MAX    = 200;
    public static final int DIME_MAX       = 25;
    public static final int HARPON_MAX     = 200;
    public static final int FORTUNE_MAX    = 200;
    public static final int CYCLONE_MAX    = 100;   // refonte 2026-08-22 : cap 100, 200 blocs max par tempête.
    public static final int MEMOIRE_MAX    = 200;   // bonus d'XP de pioche : 0 -> +1500% (×16) au niv.200.
    public static final int REFLUX_MAX     = 1000;  // vague « Abîme » : couloir 1x2 DERRIÈRE le joueur.
    public static final int GOUFFRE_MAX    = 100;   // refonte 2026-08-22 : cap 100, jusqu'à 250 blocs d'un coup.
    public static final int CONTREBANDE_MAX = 100;  // Sel de Contrebande : bonus valeur quand le sac est presque plein.
    public static final int ALLONGE_MAX    = 10;   // Allonge : portée de minage +0,4 bloc/niveau → +4 au niv.10 (actif en mine).

    // ===== Paliers de déblocage : niveau de PIOCHE requis pour débloquer chaque enchant. =====
    // Efficacité dispo dès le début (1). Les autres se débloquent en montant sa pioche.
    public static final int REQ_EFFICIENCY = 1;
    public static final int REQ_VEIN       = 2;
    public static final int REQ_FORAGE     = 4;
    public static final int REQ_COLONNE    = 6;
    public static final int REQ_HARPON     = 13;
    public static final int REQ_EXPLOSION  = 21;
    public static final int REQ_FRACTURE   = 29;
    public static final int REQ_FORTUNE    = 30;
    public static final int REQ_DIME       = 33;
    public static final int REQ_CYCLONE    = 58;   // Cœur de la Tempête (refonte 2026-08-22) : niveau de pioche 58.
    public static final int REQ_MEMOIRE    = 35;   // Mémoire de la Roche : débloqué au niveau de pioche 35.
    public static final int REQ_REFLUX     = 30;   // vague « Abîme » : niveau de pioche 30.
    public static final int REQ_GOUFFRE    = 52;   // Gouffre (refonte 2026-08-22) : niveau de pioche 52.
    public static final int REQ_CONTREBANDE = 40;  // Sel de Contrebande : niveau de pioche 40.
    public static final int REQ_FLY        = 45;   // Vol : niveau de pioche 45 (+ Plume trouvée).
    public static final int FLY_MAX        = 3;    // Vol niv.1 (base) → niv.2 (plus rapide) → niv.3 (casse instantanée + vol plus rapide).
    public static final double FLY_PRICE   = 360_000;    // Prix du Vol niveau 1. ×1,8 (hausse des mines 2026-08-22).
    public static final double FLY_PRICE_2 = 900_000;    // Prix du Vol niveau 2 (casse + rapide). ×1,8.
    public static final double FLY_PRICE_3 = 1_800_000;  // Prix du Vol niveau 3 (casse instantanée + vol le + rapide). ×1,8.
    public static final int REQ_HASTE      = 50;   // Célérité (Haste) : niveau de pioche 50.
    public static final int HASTE_MAX      = 3;    // Célérité I / II / III (effet Haste en mine).
    // Prix par niveau de Célérité (niv.1=500K, niv.2=1M, niv.3=2M).
    public static final double[] HASTE_PRICES = { 900_000, 1_800_000, 3_600_000 };  // ×1,8 (hausse des mines 2026-08-22)
    public static final int REQ_FLECHE     = 65;   // Pluie de Flèches (refonte 2026-08-22) : niveau de pioche 65.
    public static final int FLECHE_MAX     = 100;  // 100 niveaux : chance, nb de flèches et perce montent avec le niveau.
    public static final double FLECHE_PRICE = 2_250_000; // Prix de base (niv.1) ; courbe ×1,39. ×1,8 (hausse des mines 2026-08-22).
    public static final int REQ_TNT        = 60;   // Pluie de TNT : niveau de pioche 60.
    public static final int TNT_MAX        = 100;  // 100 niveaux : surtout la PUISSANCE (rayon) monte, chance faible et stable.
    public static final double TNT_PRICE   = 1_350_000; // Prix de base (niv.1) ; courbe ×1.14. ×1,8 (hausse des mines 2026-08-22).
    public static final int REQ_ALLONGE    = 75;   // Allonge : niveau de pioche 75 (dernier enchant débloqué).
    public static final double ALLONGE_PRICE = 9_000_000; // Prix niv.1, puis ×5 par niveau. ×1,8 (hausse des mines 2026-08-22).
    public static final double ALLONGE_MULT  = 5.0;       // multiplicateur de prix par niveau
    public static final double ALLONGE_PER_LEVEL = 0.4;   // +0,4 bloc de portée par niveau (→ +4 au niv.10)

    private final java.util.Map<java.util.UUID, Integer> efficiencyLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> explosionLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> forageLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> fractureLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> veinLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> colonneLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> dimeLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> harponLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> fortuneLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> cycloneLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> memoireLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> refluxLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> gouffreLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> contrebandeLevel = new java.util.HashMap<>();
    // Vol : achat unique (0 = non acheté, 1 = acheté). Stocké comme "niveau" pour réutiliser la persistance.
    private final java.util.Map<java.util.UUID, Integer> flyLevel = new java.util.HashMap<>();
    // Célérité (Haste) : niveau 0..3, effet Haste appliqué dans la mine.
    private final java.util.Map<java.util.UUID, Integer> hasteLevel = new java.util.HashMap<>();
    // Pluie de Flèches : niveau 0..100, salve de flèches qui tombent et percent vers le bas.
    private final java.util.Map<java.util.UUID, Integer> flecheLevel = new java.util.HashMap<>();
    // Pluie de TNT : niveau 0..100, une TNT tombe et explose en cassant une sphère de blocs.
    private final java.util.Map<java.util.UUID, Integer> tntLevel = new java.util.HashMap<>();
    // Allonge : niveau 0..10, portée de minage +0,4 bloc/niveau (attribut block_interaction_range, en mine).
    private final java.util.Map<java.util.UUID, Integer> allongeLevel = new java.util.HashMap<>();

    public int getEfficiencyLevel(Player p) { return efficiencyLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getExplosionLevel(Player p) { return explosionLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getForageLevel(Player p) { return forageLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getFractureLevel(Player p) { return fractureLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getVeinLevel(Player p) { return veinLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getColonneLevel(Player p) { return colonneLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getDimeLevel(Player p) { return dimeLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getHarponLevel(Player p) { return harponLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getFortuneLevel(Player p) { return fortuneLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getCycloneLevel(Player p) { return cycloneLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getMemoireLevel(Player p) { return memoireLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getRefluxLevel(Player p) { return refluxLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getGouffreLevel(Player p) { return gouffreLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getContrebandeLevel(Player p) { return contrebandeLevel.getOrDefault(p.getUniqueId(), 0); }
    public boolean hasFly(Player p) { return flyLevel.getOrDefault(p.getUniqueId(), 0) > 0; }
    public int getFlyLevel(Player p) { return flyLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getHasteLevel(Player p) { return hasteLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getFlecheLevel(Player p) { return flecheLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getTntLevel(Player p) { return tntLevel.getOrDefault(p.getUniqueId(), 0); }
    public int getAllongeLevel(Player p) { return allongeLevel.getOrDefault(p.getUniqueId(), 0); }

    /** Portée de minage BONUS (en blocs) donnée par l'Allonge : +0,4 par niveau (0 si non acheté). */
    public double allongeBonus(int level) { return Math.max(0, level) * ALLONGE_PER_LEVEL; }

    /** Somme de tous les niveaux d'enchant de la pioche (pour détecter un achat réussi). */
    public int totalEnchantLevels(Player p) {
        return getEfficiencyLevel(p) + getExplosionLevel(p) + getForageLevel(p) + getFractureLevel(p)
                + getVeinLevel(p) + getColonneLevel(p) + getDimeLevel(p) + getHarponLevel(p)
                + getFortuneLevel(p) + getCycloneLevel(p) + getMemoireLevel(p)
                + getRefluxLevel(p) + getGouffreLevel(p) + getContrebandeLevel(p);
    }

    // Setters utilisés par le chargement/reset depuis PrivateMines.
    public void setEfficiencyLevel(java.util.UUID id, int lvl) { efficiencyLevel.put(id, lvl); }
    public void setExplosionLevel(java.util.UUID id, int lvl) { explosionLevel.put(id, lvl); }
    public void setForageLevel(java.util.UUID id, int lvl) { forageLevel.put(id, lvl); }
    public void setFractureLevel(java.util.UUID id, int lvl) { fractureLevel.put(id, lvl); }
    public void setVeinLevel(java.util.UUID id, int lvl) { veinLevel.put(id, lvl); }
    public void setColonneLevel(java.util.UUID id, int lvl) { colonneLevel.put(id, lvl); }
    public void setDimeLevel(java.util.UUID id, int lvl) { dimeLevel.put(id, lvl); }
    public void setHarponLevel(java.util.UUID id, int lvl) { harponLevel.put(id, lvl); }
    public void setFortuneLevel(java.util.UUID id, int lvl) { fortuneLevel.put(id, lvl); }
    // ⚠ Clamp au cap : d'anciennes sauvegardes portent des niveaux d'avant la refonte (cap 1000).
    // Sans ça le menu afficherait « 183/100 ». Les formules clampaient déjà, mais pas l'affichage.
    public void setCycloneLevel(java.util.UUID id, int lvl) { cycloneLevel.put(id, Math.min(lvl, CYCLONE_MAX)); }
    public void setMemoireLevel(java.util.UUID id, int lvl) { memoireLevel.put(id, lvl); }
    public void setRefluxLevel(java.util.UUID id, int lvl) { refluxLevel.put(id, lvl); }
    public void setGouffreLevel(java.util.UUID id, int lvl) { gouffreLevel.put(id, Math.min(lvl, GOUFFRE_MAX)); }
    public void setContrebandeLevel(java.util.UUID id, int lvl) { contrebandeLevel.put(id, lvl); }
    public void setFlyLevel(java.util.UUID id, int lvl) { flyLevel.put(id, lvl); }
    public void setHasteLevel(java.util.UUID id, int lvl) { hasteLevel.put(id, lvl); }
    public void setFlecheLevel(java.util.UUID id, int lvl) { flecheLevel.put(id, Math.min(lvl, FLECHE_MAX)); }
    public void setTntLevel(java.util.UUID id, int lvl) { tntLevel.put(id, lvl); }
    public void setAllongeLevel(java.util.UUID id, int lvl) { allongeLevel.put(id, lvl); }

    // Remet tous les enchants d'un joueur à 0 (commande /resetenchants).
    public void resetAll(java.util.UUID id) {
        efficiencyLevel.put(id, 0);
        explosionLevel.put(id, 0);
        forageLevel.put(id, 0);
        fractureLevel.put(id, 0);
        veinLevel.put(id, 0);
        colonneLevel.put(id, 0);
        dimeLevel.put(id, 0);
        harponLevel.put(id, 0);
        fortuneLevel.put(id, 0);
        cycloneLevel.put(id, 0);
        memoireLevel.put(id, 0);
        refluxLevel.put(id, 0);
        gouffreLevel.put(id, 0);
        contrebandeLevel.put(id, 0);
        flyLevel.put(id, 0);
        hasteLevel.put(id, 0);
        flecheLevel.put(id, 0);
        tntLevel.put(id, 0);
        allongeLevel.put(id, 0);
    }

    // ===== Courbes de coût =====

    // Lissage du DÉBUT : les 10 premiers niveaux (achats currentLevel 0→9) bénéficient d'une
    // réduction qui part de -40% au tout premier niveau et s'estompe progressivement pour
    // rejoindre la courbe normale au niveau 11 (currentLevel >= 10 : prix inchangé).
    // But : rendre le démarrage moins long/frustrant sans toucher les hauts niveaux.
    private static final double START_DISCOUNT = 0.40; // -40% au niveau 1
    private static final int    START_SPAN     = 10;   // fondu sur les 10 premiers niveaux
    private double startDiscounted(double raw, int currentLevel) {
        if (currentLevel >= START_SPAN) return raw;
        double factor = 1.0 - START_DISCOUNT * (1.0 - (double) currentLevel / START_SPAN);
        return Math.floor(raw * factor);
    }

    // Courbe DOUCE continue (Explosion/Forage/Colonne/Harpon) : prix = base * ratio^currentLevel.
    // Croissance multiplicative fixe → aucune marche, montée régulière du niveau 1 jusqu'au cap 1000.
    // Ratio 1.14 calibré pour que le NIVEAU 10 (currentLevel 9) reste <= 1000$ avec les bases ci-dessous.
    private static final double SMOOTH_RATIO = 1.14;
    private double smoothCurve(double base, int currentLevel) {
        return Math.floor(base * Math.pow(SMOOTH_RATIO, currentLevel));
    }

    // Efficacité : niv.1=100, 2=180, 3=280, 4=390, 5=550, puis x1,5 par niveau.
    // Efficacité : 5 prix écrits en dur (niveaux 1 à 5) puis courbe géométrique à partir du niveau 5.
    // Ratio passé de 1,5 à **1,7** le 2026-08-21 (demande du user) : les 5 premiers niveaux ne bougent
    // pas et il n'y a AUCUNE marche au niveau 6 (660 → 748 $). Cumul 1→30 : 41,66 M → 770,79 M $.
    // ⚠ startDiscounted allège encore les niveaux 6-9 (remise de démarrage) : le user l'a laissée
    // en place volontairement (« on verra pour plus tard »).
    private static final double EFFICIENCY_RATIO = 1.7;
    public double efficiencyCost(int currentLevel) {
        double raw;
        switch (currentLevel) {
            // Paliers ×1,8 (hausse des mines 2026-08-22) : 100/180/280/390/550 → 180/324/504/702/990.
            case 0: raw = 180; break;
            case 1: raw = 324; break;
            case 2: raw = 504; break;
            case 3: raw = 702; break;
            case 4: raw = 990; break;
            default: raw = Math.floor(990.0 * Math.pow(EFFICIENCY_RATIO, currentLevel - 4));
        }
        return startDiscounted(raw, currentLevel);
    }

    // Explosion : déblocage à 10 000 $ pile (pas de remise de démarrage), puis la MÊME courbe que
    // les enchants refondus — ×1,39 par niveau, le rythme des prix de mines. Base élevée parce qu'il
    // se débloque tard (niveau de pioche 21) : à ce stade 10 000 $ se ramassent en quelques minutes.
    private static final double EXPLOSION_BASE = 18_000.0;   // ×1,8 (hausse des mines 2026-08-22)
    public double explosionCost(int currentLevel) {
        return Math.floor(EXPLOSION_BASE * Math.pow(MINE_PACED_RATIO, currentLevel));
    }

    // Forage : même barème calé sur les mines que Vein (cap 200).
    public double forageCost(int currentLevel) { return minePacedCost(currentLevel); }

    // Colonne d'Écume : mono-directionnelle (vers le bas), un peu moins chère que Forage.
    // Courbe douce, base 200 → niv.10 ≈ 650$, montée continue jusqu'au cap 1000.
    public double colonneCost(int currentLevel) { return minePacedCost(currentLevel); }

    // Reflux : couloir fin (1x2) derrière le joueur. Courbe douce, base 220.
    public double refluxCost(int currentLevel) {
        return smoothCurve(396.0, currentLevel);   // 220 × 1,8 (hausse des mines 2026-08-22)
    }

    // Gouffre (refonte 2026-08-22) : courbe géométrique ×1,39 calée sur les mines, base 300 000 $
    // (ancre donnée par le user). Il se débloque à pioche 52 : son niveau 1 ne peut pas coûter 900 $.
    // Comme Fracture et Fortune, la fin de courbe est un HORIZON, pas un objectif : le prix d'un
    // niveau atteint celui de la mine 25 (100 M $) dès le niveau 19.
    private static final double GOUFFRE_BASE  = 540_000.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double GOUFFRE_RATIO = 1.39;
    public double gouffreCost(int currentLevel) {
        return Math.floor(GOUFFRE_BASE * Math.pow(GOUFFRE_RATIO, currentLevel));
    }

    // Sel de Contrebande (refonte 2026-08-21) : bonus économique conditionnel (sac presque plein).
    // Courbe géométrique ×1,39 comme les mines, base 210 000 $ (ancre donnée par le user) : l'enchant
    // se débloque à pioche 40, son niveau 1 ne peut pas coûter 260 $. Le prix d'un niveau atteint
    // celui de la mine 25 (100 M $) dès le niveau 20 — la fin de courbe est un horizon, pas un but.
    private static final double CONTREBANDE_BASE  = 378_000.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double CONTREBANDE_RATIO = 1.39;
    public double contrebandeCost(int currentLevel) {
        return Math.floor(CONTREBANDE_BASE * Math.pow(CONTREBANDE_RATIO, currentLevel));
    }

    // Célérité (Haste) : prix fixe par niveau (500K, 1M, 2M). currentLevel = niveau actuel (0..2).
    public double hasteCost(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= HASTE_PRICES.length) return 0;
        return HASTE_PRICES[currentLevel];
    }

    // Dîme du Passeur (refonte 2026-08-21). Coût pour passer de currentLevel à currentLevel+1.
    // Courbe géométrique calée sur les mines (même ratio 1,39 que minePacedCost), mais base très
    // haute : l'enchant se débloque au niveau de pioche 33 et se paye au prix des grosses mines.
    //   niv 1 : 75 000 $ · niv 10 : 1,45 M · niv 20 : 39,1 M · niv 25 : 203 M (cumul 723 M $).
    private static final double DIME_BASE  = 135_000.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double DIME_RATIO = 1.39;
    public double dimeCost(int currentLevel) {
        return Math.floor(DIME_BASE * Math.pow(DIME_RATIO, currentLevel));
    }

    // Pluie de Harpons : courbe calée sur les mines, mais PLUS RAIDE que minePacedCost — l'enchant
    // se débloque tôt (2 000 $, soit le budget de la mine 2) et se paye vite au prix des grosses
    // mines : niveau 30 ≈ 95,6 M $ (mine 25), niveau 40 ≈ 3,93 B $ (ce que coûtera la mine 35).
    // Pas de remise de démarrage : le niveau 1 doit coûter 2 000 $ pile.
    private static final double HARPON_RATIO = 1.45;
    private static final double HARPON_BASE  = 3600.0;  // ×1,8 (hausse des mines 2026-08-22)
    public double harponCost(int currentLevel) {
        return Math.floor(HARPON_BASE * Math.pow(HARPON_RATIO, currentLevel));
    }

    // Cœur de la Tempête (refonte 2026-08-22) : dernier enchant de l'échelle (pioche 58, après le
    // Gouffre à 52). Courbe géométrique ×1,39 calée sur les mines, base 1 000 000 $ : c'est l'achat
    // le plus prestigieux du jeu, son niveau 1 ne peut pas coûter 5 000 $.
    private static final double CYCLONE_BASE  = 1_800_000.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double CYCLONE_RATIO = 1.39;
    public double cycloneCost(int currentLevel) {
        return Math.floor(CYCLONE_BASE * Math.pow(CYCLONE_RATIO, currentLevel));
    }

    // Mémoire de la Roche (refonte 2026-08-21) : bonus d'XP de pioche, cap 200, débloquée à pioche 35.
    // Courbe géométrique ×1,10 par niveau, base 100 000 $ : le niveau 1 coûte ce que gagne un joueur
    // de pioche 35 en quelques minutes, et le prix d'un niveau atteint celui de la mine 25 (100 M $)
    // vers le niveau 74. Cumul 1 → 200 : ~189,91 T $.
    private static final double MEMOIRE_BASE  = 180_000.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double MEMOIRE_RATIO = 1.10;
    public double memoireCost(int currentLevel) {
        return Math.floor(MEMOIRE_BASE * Math.pow(MEMOIRE_RATIO, currentLevel));
    }

    // Fracture : base 30 000 $ au niveau 1 (pas de remise de démarrage), ratio calé pour que le
    // NIVEAU 20 coûte 100 M $ — le prix de la mine 25. Pente volontairement plus raide que les
    // autres : Fracture se débloque au niveau de pioche 29, elle rattrape donc le prix des mines
    // en deux fois moins de niveaux que Vein.
    private static final double FRACTURE_BASE  = 54_000.0;   // ×1,8 (hausse des mines 2026-08-22)
    private static final double FRACTURE_RATIO = 1.5326;
    public double fractureCost(int currentLevel) {
        return Math.floor(FRACTURE_BASE * Math.pow(FRACTURE_RATIO, currentLevel));
    }

    // Barème « CALÉ SUR LES MINES », partagé par tous les enchants passés au cap 200.
    // C'est la courbe des mines reprojetée sur les niveaux : ×1,39 par niveau, calée pour que le
    // NIVEAU 40 coûte 100 M $ = le prix de la mine 25 (Cité des Toasts), et que le niveau 41 tombe
    // sur ce que coûtera la mine 26. On monte donc un enchant au rythme où on change de mine.
    private static final double MINE_PACED_RATIO = 1.39;
    // ⚠ Le numérateur EST le prix de la mine 25 : il a suivi la hausse ×1,8 du 2026-08-22
    // (100 M → 180 M), ce qui garde l'ancre « niveau 40 = prix de la mine 25 » VRAIE.
    private static final double MINE_PACED_BASE  = 180_000_000.0 / Math.pow(MINE_PACED_RATIO, 39); // ≈ 475 $ le niveau 1
    private double minePacedCost(int currentLevel) {
        return startDiscounted(Math.floor(MINE_PACED_BASE * Math.pow(MINE_PACED_RATIO, currentLevel)), currentLevel);
    }
    public double veinCost(int currentLevel) { return minePacedCost(currentLevel); }

    // Pluie de Flèches : base 500K au niv.1, courbe douce ×1.14 par niveau (enchant end-game, niv.55 pioche).
    // Pluie de Flèches (refonte 2026-08-22) : enchant le plus haut de l'échelle (pioche 65).
    // Courbe géométrique ×1,39 calée sur les mines, base 1 250 000 $ — au-dessus du Cœur de la
    // Tempête (1 M $ à pioche 58), qui est lui-même au-dessus du Gouffre (300 K $ à pioche 52).
    private static final double FLECHE_RATIO = 1.39;
    public double flecheCost(int currentLevel) {
        return Math.floor(FLECHE_PRICE * Math.pow(FLECHE_RATIO, currentLevel));
    }

    // Pluie de TNT : prix de base 750 000 au niv.1, courbe ×1.14 comme les autres enchants chers.
    public double tntCost(int currentLevel) {
        return smoothCurve(TNT_PRICE, currentLevel);
    }

    // Allonge : coût du PROCHAIN niveau = 5M × 5^(niveau actuel). 5M → 25M → 125M → ... (enchant premium).
    public double allongeCost(int currentLevel) {
        return Math.floor(ALLONGE_PRICE * Math.pow(ALLONGE_MULT, currentLevel));
    }

    // ===== Fonctions de stat (appelées aussi par les triggers de minage) =====
    // Chance qu'une explosion se déclenche à un coup, selon le niveau (0 si non débloqué).
    // Recalibré sur EXPLOSION_MAX=1000 (2026-07-05) : avant c'était /100 -> rayon délirant au niv.700 (one-shot).
    // Chance DÉCROISSANTE (même système que Vein/Forage/Colonne/Harpons) : 10 % au niv.1 → 2 % au niv.200.
    public double explosionChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, EXPLOSION_MAX);
        return 0.10 - 0.08 * (l - 1) / (double) (EXPLOSION_MAX - 1);
    }
    // Blocs soufflés par explosion : +1 tous les 2 niveaux → 1 au niv.1-2 … 100 au niv.199-200.
    // ⚠ On pilote le NOMBRE de blocs, pas le rayon : une sphère définie par son rayon avance par
    // paliers en escalier sur une grille (7, 19, 27, 81, 93…), donc des dizaines de niveaux ne
    // changeraient rien. Le trigger prend les N blocs les plus PROCHES → sphère toujours pleine.
    public int explosionBlocks(int level) {
        return level <= 0 ? 0 : (Math.min(level, EXPLOSION_MAX) + 1) / 2;
    }
    // Rayon correspondant, pour l'AFFICHAGE seulement (rayon de la boule qui contient N blocs).
    public double explosionRadius(int level) {
        return level <= 0 ? 0 : Math.cbrt(explosionBlocks(level) / 4.18879);
    }
    // Nombre de blocs détruits par une explosion de ce rayon (sphère, centre inclus).
    public int explosionBlockCount(double radius) {
        int R = (int) Math.ceil(radius);
        int c = 0;
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++)
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius) c++;
        return c;
    }

    // Chance DÉCROISSANTE (même système que Vein) : 9 % au niveau 1 → 2 % au niveau 200.
    // Le tunnel part de moins en moins souvent, mais il est de plus en plus long.
    public double forageChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, FORAGE_MAX);
        return 0.09 - 0.07 * (l - 1) / (double) (FORAGE_MAX - 1);
    }
    // Longueur du tunnel : +1 tous les 2 niveaux → 1 bloc au niv.1-2, 2 au niv.3-4 … 100 au niv.199-200.
    public double forageLength(int level) {
        return level <= 0 ? 0 : (Math.min(level, FORAGE_MAX) + 1) / 2;
    }

    // Chance de déclencher Colonne d'Écume selon le niveau (0 si non débloqué) : 2% -> 5% (niv.100).
    public double colonneChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, COLONNE_MAX);
        return 0.12 - 0.08 * (l - 1) / (double) (COLONNE_MAX - 1);
    }
    // Profondeur : +1 tous les 4 niveaux → 1 bloc au niv.1-4, 2 au niv.5-8 … 50 au niv.197-200.
    // 50 = la HAUTEUR de la mine : au-delà la colonne taperait le sol, aller plus loin ne creuse rien.
    public double colonneDepth(int level) {
        return level <= 0 ? 0 : (Math.min(level, COLONNE_MAX) + 3) / 4;
    }

    // Chance DÉCROISSANTE (même système que Vein/Forage/Colonne) : 6 % au niveau 1 → 1 % au niveau 200.
    public double harponChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, HARPON_MAX);
        return 0.06 - 0.05 * (l - 1) / (double) (HARPON_MAX - 1);
    }
    // Nombre de harpons tombés : +1 tous les 5 niveaux → 1 au niv.1-5 … 40 au niv.196-200.
    // Chacun casse une croix de 5 blocs → 5 blocs au niveau 1, 200 blocs au niveau 200.
    public int harponCount(int level) {
        if (level <= 0) return 0;
        return 1 + (Math.min(level, HARPON_MAX) - 1) / 5;
    }

    // ===== Reflux (vague « Abîme ») =====
    // Chance qu'un coup ouvre un couloir 1x2 DERRIÈRE le joueur : 2 % (niv.1) -> 6 % (niv.1000).
    public double refluxChance(int level) {
        if (level <= 0) return 0.0;
        return 0.02 + 0.04 * ((level - 1) / (double) (REFLUX_MAX - 1));
    }
    // Longueur du couloir (fractionnaire) : ~4 (niv.1) -> 30 (niv.1000).
    public double refluxLength(int level) {
        if (level <= 0) return 0.0;
        return 4.0 + 26.0 * ((level - 1) / (double) (REFLUX_MAX - 1));
    }

    // ===== Gouffre (vague « Abîme ») =====
    // Chance qu'un coup ouvre un puits sphérique sous les pieds.
    // Chance DÉCROISSANTE (même système que Vein/Forage/Colonne/Harpons/Explosion/Fracture) :
    // 7 % au niv.1 -> 1 % au niv.100. Le puits grossit, il se déclenche moins souvent.
    public double gouffreChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, GOUFFRE_MAX);
        return 0.07 - 0.06 * (l - 1) / (double) (GOUFFRE_MAX - 1);
    }
    // Blocs effondrés par gouffre : +2,5 par niveau -> 2 au niv.1 ... 250 au niv.100 (plafond voulu).
    // ⚠ On pilote le NOMBRE de blocs, pas le rayon : une sphère définie par son rayon avance par
    // paliers en escalier sur la grille (7, 19, 27, 81...), donc des dizaines de niveaux ne
    // changeraient rien. Le trigger prend les N blocs les plus PROCHES -> sphère toujours pleine.
    public int gouffreBlocks(int level) {
        return level <= 0 ? 0 : (Math.min(level, GOUFFRE_MAX) * 5) / 2;
    }
    // Rayon correspondant, pour l'AFFICHAGE seulement (rayon de la boule qui contient N blocs).
    public double gouffreRadius(int level) {
        return level <= 0 ? 0.0 : Math.cbrt(gouffreBlocks(level) / 4.18879);
    }

    // ===== Sel de Contrebande =====
    // Le bonus ne s'applique que si le sac est rempli à >= ce taux (90 %).
    public static final double CONTREBANDE_FILL = 0.90;
    // Bonus de valeur des blocs, en FRACTION (0,08 = +8 %) : +8 % (niv.1) -> +30 % (niv.100), linéaire.
    public double contrebandeBonus(int level) {
        if (level <= 0) return 0.0;
        return 0.08 + 0.22 * ((level - 1) / (double) (CONTREBANDE_MAX - 1));
    }

    // ===== L'Œil du Cyclone =====
    // Chance qu'un coup déclenche TA tempête personnelle.
    // Chance DÉCROISSANTE (même système que les autres refontes) : 3 % au niv.1 -> 0,5 % au niv.100.
    // ⚠ Plage volontairement plus basse que le Gouffre (7 → 1 %) : le cooldown interne de 90 s
    // (CYCLONE_COOLDOWN_MS) borne déjà la cadence, une chance haute ferait tourner la tempête en continu.
    public double cycloneChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, CYCLONE_MAX);
        return 0.03 - 0.025 * (l - 1) / (double) (CYCLONE_MAX - 1);
    }
    // Budget de blocs d'UNE tempête : 20 (niv.1) -> 200 (niv.100), linéaire. Plafond voulu par le user.
    // ⚠ C'est ce budget qui pilote tout : la tempête s'arrête dès qu'il est épuisé, même si la
    // durée n'est pas écoulée. Durée et taille des éclairs ne sont plus que la MISE EN SCÈNE.
    public int cycloneBlocks(int level) {
        if (level <= 0) return 0;
        int l = Math.min(level, CYCLONE_MAX);
        return (int) Math.round(20.0 + 180.0 * (l - 1) / (double) (CYCLONE_MAX - 1));
    }
    // Durée de la tempête en secondes : 6,0 s (niv.1) -> 15,0 s (niv.100).
    // ⚠ Raccourcie à la refonte : avec 200 blocs de budget, 20 s d'éclairs auraient donné moins
    // d'un bloc par impact — un feu d'artifice vide.
    public double cycloneDuration(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, CYCLONE_MAX);
        return 6.0 + 9.0 * (l - 1) / (double) (CYCLONE_MAX - 1);
    }
    // Nombre d'impacts d'une tempête : 1 éclair toutes les 0,75 s pendant toute la durée.
    public int cycloneBoltCount(int level) {
        if (level <= 0) return 0;
        return Math.max(1, (int) Math.floor(cycloneDuration(level) / 0.75));
    }
    // Blocs pulvérisés par ÉCLAIR : le budget réparti sur toute la durée -> 3 (niv.1) -> 10 (niv.100).
    // Les éclairs se font donc à la fois plus nombreux ET plus gros en montant de niveau.
    public int cycloneBlocksPerBolt(int level) {
        if (level <= 0) return 0;
        return Math.max(1, (int) Math.round(cycloneBlocks(level) / (double) cycloneBoltCount(level)));
    }

    // ===== Mémoire de la Roche =====
    // Bonus d'XP de pioche : +0 % (niv.0) -> +500 % (niv.1000), montée linéaire (+0,5 %/niveau).
    // Renvoie le bonus en fraction (0.0 = aucun, 5.0 = +500% = XP ×6).
    public double memoireBonus(int level) {
        if (level <= 0) return 0.0;
        return 15.0 * (Math.min(level, MEMOIRE_MAX) / (double) MEMOIRE_MAX);  // 0 -> +1500% au niv.200 (×16 sur l'XP de pioche), +7,5%/niveau
    }
    // Multiplicateur total d'XP de pioche (1.0 sans enchant, 2.0 au niv.1000).
    public double memoireXpMult(int level) {
        return 1.0 + memoireBonus(level);
    }

    // Fortune des Abysses : chance par bloc miné de faire tomber une CLÉ (crates).
    // ⚠ SEUL enchant refondu dont la chance MONTE avec le niveau : il ne casse aucun bloc, il n'y a
    // donc rien à faire grossir en échange d'une chance qui descendrait.
    // Montée linéaire du niveau 1 au cap 200. Au niveau MAX : commune 0,25 % / rare 0,125 % /
    // légendaire 0,05 % -> une clé tous les ~235 blocs MINÉS DIRECTEMENT (taux divisés par 2 le
    // 2026-08-21 : l'ancien barème donnait une clé toutes les 118 secondes de minage).
    public double fortuneChanceCommune(int level) {
        if (level <= 0) return 0.0;
        return 0.00250 * (Math.min(level, FORTUNE_MAX) / (double) FORTUNE_MAX);
    }
    public double fortuneChanceRare(int level) {
        if (level <= 0) return 0.0;
        return 0.00125 * (Math.min(level, FORTUNE_MAX) / (double) FORTUNE_MAX);
    }
    public double fortuneChanceLegendaire(int level) {
        if (level <= 0) return 0.0;
        return 0.00050 * (Math.min(level, FORTUNE_MAX) / (double) FORTUNE_MAX);
    }
    // Blocs à miner en moyenne pour UNE clé, toutes raretés confondues (affichage du menu).
    public int fortuneBlocsParCle(int level) {
        double p = fortuneChanceCommune(level) + fortuneChanceRare(level) + fortuneChanceLegendaire(level);
        return p <= 0 ? 0 : (int) Math.round(1.0 / p);
    }

    // Fortune des Abysses : base 22 000 $ au niveau 1 (pas de remise), ratio calé pour que le
    // NIVEAU 20 coûte 100 M $ — le prix de la mine 25, comme Fracture.
    // Le niveau 1 est volontairement SOUS Fracture (22 K contre 30 K) : Fortune ne casse aucun bloc,
    // elle ne fait que donner des clés.
    private static final double FORTUNE_BASE  = 39_600.0;  // ×1,8 (hausse des mines 2026-08-22)
    private static final double FORTUNE_RATIO = 1.5578;
    public double fortuneCost(int currentLevel) {
        return Math.floor(FORTUNE_BASE * Math.pow(FORTUNE_RATIO, currentLevel));
    }

    // Dîme du Passeur : multiplicateur de base ×10 sur le bloc « payé ».
    public static final double DIME_MULT = 10.0;

    // Multiplicateur EFFECTIF selon le niveau : ×10 jusqu'au niveau 20, puis +1 par niveau
    // au-delà (niv 21 = ×11, 22 = ×12, 23 = ×13, 24 = ×14, 25 = ×15). 0 si non débloqué.
    public double dimeMult(int level) {
        if (level <= 0) return 0.0;
        if (level <= 20) return DIME_MULT;
        return DIME_MULT + (level - 20); // +1 par niveau au-dessus de 20
    }
    // Seuil de blocs entre deux paies : 200 (niv.1) -> 100 (niv.20). Plus haut niveau = paie plus
    // fréquente. Courbe linéaire de 200 à 100 sur les niveaux 1..20 ; au-delà (21..25) le seuil reste
    // à 100 et ce sont le multiplicateur (dimeMult) qui monte. 0 si non débloqué.
    private static final int DIME_SEUIL_PALIER = 20; // niveau où le seuil atteint son minimum (100)
    public int dimeThreshold(int level) {
        if (level <= 0) return 0;
        if (level >= DIME_SEUIL_PALIER) return 100;
        return (int) Math.round(200 - (200 - 100) * (level - 1) / (double) (DIME_SEUIL_PALIER - 1));
    }

    // Chance de déclencher une salve d'éclairs. Buff modéré (2026-07-05) : 1% -> 3,5% (niv.1000).
    // Chance DÉCROISSANTE (même système que les autres refontes) : 12 % au niv.1 -> 2 % au niv.200.
    public double fractureChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, FRACTURE_MAX);
        return 0.12 - 0.10 * (l - 1) / (double) (FRACTURE_MAX - 1);
    }
    // Blocs cassés au TOTAL par salve : +1 tous les 2 niveaux -> 1 au niv.1-2 ... 100 au niv.199-200.
    public int fractureBlocks(int level) {
        return level <= 0 ? 0 : (Math.min(level, FRACTURE_MAX) + 1) / 2;
    }
    // Éclairs par salve : un pour 10 blocs, plafonné à 10 -> au niveau 200, 10 éclairs de 10 blocs.
    // (Chaque éclair est un strikeLightningEffect : au-delà de 10 le flash et le tonnerre saturent.)
    public int fractureBolts(int level) {
        if (level <= 0) return 0;
        return Math.max(1, Math.min(10, (int) Math.ceil(fractureBlocks(level) / 10.0)));
    }
    // Rayon d'un impact, pour l'AFFICHAGE seulement (boule contenant les blocs d'UN éclair).
    public double fractureRadius(int level) {
        int b = fractureBolts(level);
        return b <= 0 ? 0 : Math.cbrt((fractureBlocks(level) / (double) b) / 4.18879);
    }

    // Chance DÉCROISSANTE : 7 % au niveau 1 → 2 % au niveau 200. Chaque amélioration troque un peu
    // de fréquence contre beaucoup de blocs : les déclenchements se font rares mais énormes.
    public double veinChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, VEIN_MAX);
        return 0.07 - 0.05 * (l - 1) / (double) (VEIN_MAX - 1);
    }
    // Blocs minés autour : +1 tous les 2 niveaux → 1 au niv.1-2, 2 au niv.3-4 … 100 au niv.199-200.
    public int veinBlocks(int level) { return level <= 0 ? 0 : (Math.min(level, VEIN_MAX) + 1) / 2; }

    // ── Pluie de Flèches (débloquée au niv.55 de pioche, 100 niveaux) ──
    // Chance de déclencher la salve à un coup miné : 5% (niv.1) -> 100% (niv.100).
    // Chance DÉCROISSANTE (même système que les autres refontes) : 4 % au niv.1 -> 0,5 % au niv.100.
    // ⚠ Avant la refonte elle MONTAIT jusqu'à 100 % : la salve tombait à CHAQUE coup de pioche.
    public double flecheChance(int level) {
        if (level <= 0) return 0.0;
        int l = Math.min(level, FLECHE_MAX);
        return 0.04 - 0.035 * (l - 1) / (double) (FLECHE_MAX - 1);
    }
    // Nombre de flèches qui tombent : 1 (niv.1) -> 20 (niv.100).
    public int flecheCount(int level) {
        if (level <= 0) return 0;
        int l = Math.min(level, FLECHE_MAX);
        return 1 + (int) Math.floor(19.0 * (l - 1) / (double) (FLECHE_MAX - 1));
    }
    // Profondeur percée par chaque flèche (blocs traversés vers le bas) : 3 (niv.1) -> 15 (niv.100).
    public int flechePierce(int level) {
        if (level <= 0) return 0;
        int l = Math.min(level, FLECHE_MAX);
        return 3 + (int) Math.floor(12.0 * (l - 1) / (double) (FLECHE_MAX - 1));
    }
    // Total de blocs par salve = flecheCount × flechePierce -> 3 (niv.1) ... 20 × 15 = 300 (niv.100).
    // Les deux courbes sont calées pour tomber PILE sur le plafond de 300 voulu par le user.
    public int flecheTotalBlocks(int level) {
        return flecheCount(level) * flechePierce(level);
    }

    // Pluie de TNT : chance faible et stable (2 % niv.1 → 4 % niv.100), par bloc miné. 0 si non débloqué.
    public double tntChance(int level) {
        if (level <= 0) return 0.0;
        return 0.02 + 0.02 * (level / (double) TNT_MAX);
    }

    // Rayon de la sphère cassée par l'explosion : 2 (niv.1) → 6 (niv.100). C'est SURTOUT ça qui monte.
    public double tntRadius(int level) {
        if (level <= 0) return 0.0;
        return 2.0 + 4.0 * ((level - 1) / (double) (TNT_MAX - 1));
    }

    // Estimation du nombre de blocs cassés par une explosion (volume d'une sphère du rayon donné).
    public int tntBlocksEstimes(int level) {
        double r = tntRadius(level);
        return (int) Math.round(4.0 / 3.0 * Math.PI * r * r * r);
    }

    // ===== Menu des enchantements =====
    private static final String ENCHANT_MENU_TITLE = "§b✦ §3§lEnchantements §b✦";

    // Raccourci vers /mine, en slot 0 des DEUX pages : une tête HeadDB (id fourni par PrivateMines).
    // Si HeadDB n'est pas là ou n'a pas résolu l'id, on retombe sur une boussole — le bouton marche
    // quand même, il est juste moins joli.
    static final int MINE_SHORTCUT_SLOT = 0;
    private ItemStack mineShortcutTile() {
        ItemStack it = plugin.getPetHeads().isCached(PrivateMines.MINE_SHORTCUT_HEAD_ID)
                ? plugin.getPetHeads().getHead(PrivateMines.MINE_SHORTCUT_HEAD_ID)
                : new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§6§l⛏ Voyage des Mines");
        m.setLore(java.util.Arrays.asList("§7Ouvre directement le menu des mines.", "", "§eClic §7pour voyager"));
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    public void openEnchantMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, ENCHANT_MENU_TITLE);
        refreshEnchantMenu(player, menu);
        player.openInventory(menu);
    }

    // Un enchant est débloqué si le niveau de PIOCHE du joueur atteint son palier requis.
    private boolean isUnlocked(Player player, int req) {
        return plugin.getPickaxeLevel(player) >= req;
    }

    // Vérifie le déblocage AVANT un achat. Renvoie true si OK ; sinon prévient le joueur et renvoie false.
    private boolean tryUnlock(Player player, int req, String name) {
        if (isUnlocked(player, req)) return true;
        player.sendMessage("§c🔒 §7" + name + " se débloque au §bniveau de pioche " + req
                + " §7(ta pioche : §e" + plugin.getPickaxeLevel(player) + "§7).");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return false;
    }

    // Tuile « verrouillée » : cadenas gris, NOM BROUILLÉ (§k) tant que l'enchant n'est pas débloqué.
    private ItemStack lockedTile(Player player, String name, int req) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        // Nom brouillé : cadenas + lettres qui changent (même longueur que le vrai nom).
        String scrambled = "§8§k" + org.bukkit.ChatColor.stripColor(name).replaceAll(".", "?");
        m.setDisplayName("§8§l🔒 " + scrambled);
        java.util.List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§c🔒 Verrouillé");
        lore.add("§7Débloqué au §bniveau de pioche " + req);
        lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
        m.setLore(lore);
        // Masque les attributs/infos parasites Minecraft dans le tooltip.
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    // Place une tuile d'enchant SI débloqué (niveau de pioche suffisant), sinon un cadenas.
    private void placeEnchant(Player player, Inventory menu, int slot, int req, String name, ItemStack tile) {
        if (isUnlocked(player, req)) menu.setItem(slot, tile);
        else menu.setItem(slot, lockedTile(player, name, req));
    }

    /**
     * Ajoute une ligne au récapitulatif de la pioche (header du menu) UNIQUEMENT si le joueur possède
     * l'enchant (niveau ≥ 1). Un enchant à 0 (pas encore appris) n'apparaît pas ici — il reste à
     * découvrir dans les cases du menu.
     *
     * @param nameColor couleur du symbole ✦ et du nom
     * @param lvlColor  couleur du compteur niveau/max
     */
    private void addHeaderLine(java.util.List<String> lore, String nameColor, String lvlColor,
                               String name, int level, int max) {
        if (level < 1) return;
        lore.add(nameColor + "✦ " + name + " " + lvlColor + level + "§7/" + lvlColor + max);
    }

    private void refreshEnchantMenu(Player player, Inventory menu) {
        // Fond entièrement en verre bleu clair -> chaque enchant est naturellement ENTOURÉ de verre bleu.
        ItemStack border = plugin.pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) menu.setItem(i, border);

        // Raccourci /mine (slot 0).
        menu.setItem(MINE_SHORTCUT_SLOT, mineShortcutTile());

        // En-tête (slot 4) : aperçu de la pioche.
        ItemStack header = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName("§b§lPioche du Mineur");
        java.util.List<String> hl = new ArrayList<>();
        hl.add("§7Améliore tes enchantements ici.");
        hl.add("");
        // On n'affiche QUE les enchants réellement possédés (niveau ≥ 1). Les autres n'apparaissent pas
        // dans ce récapitulatif — ils restent à découvrir dans les cases du menu.
        addHeaderLine(hl, "§b", "§3", "Efficacité", getEfficiencyLevel(player), EFFICIENCY_MAX);
        addHeaderLine(hl, "§2", "§a", "Vein Miner", getVeinLevel(player), VEIN_MAX);
        addHeaderLine(hl, "§e", "§6", "Forage", getForageLevel(player), FORAGE_MAX);
        addHeaderLine(hl, "§a", "§a", "Colonne d'Écume", getColonneLevel(player), COLONNE_MAX);
        addHeaderLine(hl, "§5", "§5", "Pluie de Harpons", getHarponLevel(player), HARPON_MAX);
        addHeaderLine(hl, "§c", "§4", "Explosion", getExplosionLevel(player), EXPLOSION_MAX);
        addHeaderLine(hl, "§3", "§b", "Fracture", getFractureLevel(player), FRACTURE_MAX);
        addHeaderLine(hl, "§f", "§f", "Dîme du Passeur", getDimeLevel(player), DIME_MAX);
        addHeaderLine(hl, "§c", "§4", "Reflux", getRefluxLevel(player), REFLUX_MAX);
        addHeaderLine(hl, "§c", "§4", "Gouffre", getGouffreLevel(player), GOUFFRE_MAX);
        addHeaderLine(hl, "§a", "§2", "Sel de Contrebande", getContrebandeLevel(player), CONTREBANDE_MAX);
        if (hl.size() == 2) hl.add("§8Aucun enchant appris pour l'instant."); // que le titre + ligne vide
        hm.setLore(hl);
        hm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        header.setItemMeta(hm);
        menu.setItem(4, header);

        // ===== Les 9 enchants sur une seule page =====
        // Efficacité (slot 10).
        placeEnchant(player, menu, 10, REQ_EFFICIENCY, "Efficacité", makeEnchantTile(Material.ENCHANTED_BOOK,
                "§b§lEfficacité",
                "§7Augmente ta vitesse de minage.",
                getEfficiencyLevel(player), EFFICIENCY_MAX,
                efficiencyCost(getEfficiencyLevel(player)), null));

        // Vein Miner (slot 12).
        int veLvl = getVeinLevel(player);
        java.util.List<String> veStats = new ArrayList<>();
        if (veLvl <= 0) {
            veStats.add("§7Chance : §e0%");
            veStats.add("§7Blocs autour : §einactif");
        } else {
            veStats.add("§7Chance : §e" + String.format(java.util.Locale.US, "%.2f", veinChance(veLvl) * 100) + "%");
            veStats.add("§7Mine §e" + veinBlocks(veLvl) + " §7bloc(s) autour");
            veStats.add("§8La chance baisse, les blocs montent.");
        }
        placeEnchant(player, menu, 12, REQ_VEIN, "Vein Miner", makeEnchantTile(Material.DIAMOND_ORE,
                "§2§lVein Miner",
                "§7Petite chance de miner les blocs autour.",
                veLvl, VEIN_MAX,
                veinCost(veLvl), veStats));

        // Forage (slot 14).
        int foLvl = getForageLevel(player);
        java.util.List<String> foStats = new ArrayList<>();
        if (foLvl <= 0) {
            foStats.add("§7Chance de forage : §e0%");
            foStats.add("§7Tunnel : §einactif");
        } else {
            double foLen = forageLength(foLvl);
            foStats.add("§7Chance de forage : §e" + String.format(java.util.Locale.US, "%.2f", forageChance(foLvl) * 100) + "%");
            foStats.add("§7Tunnel : §e" + (int) Math.floor(foLen) + " blocs de long");
            foStats.add("§8La chance baisse, le tunnel s'allonge.");
        }
        placeEnchant(player, menu, 14, REQ_FORAGE, "Forage", makeEnchantTile(Material.NETHERITE_PICKAXE,
                "§e§lForage",
                "§7Creuse un tunnel droit dans la direction minée.",
                foLvl, FORAGE_MAX,
                forageCost(foLvl), foStats));

        // Colonne d'Écume (slot 16).
        int coLvl = getColonneLevel(player);
        java.util.List<String> coStats = new ArrayList<>();
        if (coLvl <= 0) {
            coStats.add("§7Chance de colonne : §e0%");
            coStats.add("§7Profondeur : §einactif");
        } else {
            double coDepth = colonneDepth(coLvl);
            coStats.add("§7Chance de colonne : §e" + String.format(java.util.Locale.US, "%.2f", colonneChance(coLvl) * 100) + "%");
            coStats.add("§7Profondeur : §e" + (int) Math.floor(coDepth) + " blocs vers le bas");
            coStats.add("§8La chance baisse, la colonne s'enfonce.");
        }
        placeEnchant(player, menu, 16, REQ_COLONNE, "Colonne d'Écume", makeEnchantTile(Material.PRISMARINE,
                "§a§lColonne d'Écume",
                "§7Creuse un puits vertical droit sous le bloc miné.",
                coLvl, COLONNE_MAX,
                colonneCost(coLvl), coStats));

        // Pluie de Harpons (slot 20).
        int haLvl = getHarponLevel(player);
        java.util.List<String> haStats = new ArrayList<>();
        if (haLvl <= 0) {
            haStats.add("§7Chance de pluie : §e0%");
            haStats.add("§7Harpons : §einactif");
        } else {
            haStats.add("§7Chance de pluie : §e" + String.format(java.util.Locale.US, "%.2f", harponChance(haLvl) * 100) + "%");
            haStats.add("§7Harpons : §e" + harponCount(haLvl) + " §7(croix de 5 chacun)");
            haStats.add("§8La chance baisse, la salve grossit.");
        }
        placeEnchant(player, menu, 20, REQ_HARPON, "Pluie de Harpons", makeEnchantTile(Material.TRIDENT,
                "§5§lPluie de Harpons",
                "§7Des harpons tombent du ciel sur la mine.",
                haLvl, HARPON_MAX,
                harponCost(haLvl), haStats));

        // Explosion (slot 22).
        int exLvl = getExplosionLevel(player);
        java.util.List<String> exStats = new ArrayList<>();
        if (exLvl <= 0) {
            exStats.add("§7Chance d'explosion : §e0%");
            exStats.add("§7Zone détruite : §einactif");
        } else {
            double exRadius = explosionRadius(exLvl);
            exStats.add("§7Chance d'explosion : §e" + String.format(java.util.Locale.US, "%.2f", explosionChance(exLvl) * 100) + "%");
            exStats.add("§7Zone détruite : §e" + explosionBlocks(exLvl) + " blocs §7(rayon " + String.format(java.util.Locale.US, "%.1f", exRadius) + ")");
            exStats.add("§8La chance baisse, la sphère grossit.");
        }
        placeEnchant(player, menu, 22, REQ_EXPLOSION, "Explosion", makeEnchantTile(Material.TNT,
                "§c§lExplosion",
                "§7Casse une zone de blocs autour (rayon croissant).",
                exLvl, EXPLOSION_MAX,
                explosionCost(exLvl), exStats));

        // Fracture (slot 24).
        int frLvl = getFractureLevel(player);
        java.util.List<String> frStats = new ArrayList<>();
        if (frLvl <= 0) {
            frStats.add("§7Chance de fracture : §e0%");
            frStats.add("§7Éclairs : §einactif");
        } else {
            double frRadius = fractureRadius(frLvl);
            frStats.add("§7Chance de fracture : §e" + String.format(java.util.Locale.US, "%.2f", fractureChance(frLvl) * 100) + "%");
            frStats.add("§7Éclairs : §e" + fractureBolts(frLvl) + " §7par salve");
            frStats.add("§7Éclairs : §e" + fractureBolts(frLvl) + " §7× §e" + (fractureBlocks(frLvl) / fractureBolts(frLvl)) + " §7blocs");
            frStats.add("§7Total : §e" + fractureBlocks(frLvl) + " §7blocs par salve");
            frStats.add("§8La chance baisse, la salve grossit.");
        }
        placeEnchant(player, menu, 24, REQ_FRACTURE, "Fracture", makeEnchantTile(Material.LIGHTNING_ROD,
                "§3§lFracture",
                "§7Fait tomber des éclairs qui pulvérisent la mine.",
                frLvl, FRACTURE_MAX,
                fractureCost(frLvl), frStats));

        // Fortune des Abysses (slot 28 — placée à l'emplacement de l'ancienne Dîme).
        int fortLvl = getFortuneLevel(player);
        java.util.List<String> fortStats = new ArrayList<>();
        if (fortLvl <= 0) {
            fortStats.add("§7Clés en minant : §einactif");
        } else {
            fortStats.add("§7Clé §fCommune §7: §e" + String.format(java.util.Locale.US, "%.3f", fortuneChanceCommune(fortLvl) * 100) + "%");
            fortStats.add("§7Clé §9Rare §7: §e" + String.format(java.util.Locale.US, "%.3f", fortuneChanceRare(fortLvl) * 100) + "%");
            fortStats.add("§7Clé §6Légendaire §7: §e" + String.format(java.util.Locale.US, "%.3f", fortuneChanceLegendaire(fortLvl) * 100) + "%");
            fortStats.add("§7≈ 1 clé tous les §e" + fortuneBlocsParCle(fortLvl) + " §7blocs minés");
        }
        placeEnchant(player, menu, 28, REQ_FORTUNE, "Fortune des Abysses", makeEnchantTile(Material.TRIPWIRE_HOOK,
                "§6§lFortune des Abysses",
                "§7Chance de trouver des §eclés§7 de coffre en minant.",
                fortLvl, FORTUNE_MAX,
                fortuneCost(fortLvl), fortStats));

        // Dîme du Passeur (slot 30 — placée à l'emplacement de l'ancienne Fortune).
        int diLvl = getDimeLevel(player);
        java.util.List<String> diStats = new ArrayList<>();
        if (diLvl <= 0) {
            diStats.add("§7Paie : §einactif");
        } else {
            diStats.add("§7Chaque §e" + dimeThreshold(diLvl) + "ᵉ §7bloc payé §6x" + (int) dimeMult(diLvl));
            if (diLvl >= 20 && diLvl < DIME_MAX) {
                diStats.add("§8Niv. suivant : §6x" + (int) dimeMult(diLvl + 1));
            }
        }
        placeEnchant(player, menu, 30, REQ_DIME, "Dîme du Passeur", makeEnchantTile(Material.GOLD_INGOT,
                "§f§lDîme du Passeur",
                "§7Un bloc sur N est payé §6x10§7 (péage).",
                diLvl, DIME_MAX,
                dimeCost(diLvl), diStats));

        // Mémoire de la Roche (slot 32) — bonus d'XP de pioche.
        int meLvl = getMemoireLevel(player);
        java.util.List<String> meStats = new ArrayList<>();
        if (meLvl <= 0) {
            meStats.add("§7Bonus XP pioche : §einactif");
        } else {
            meStats.add("§7Bonus XP pioche : §a+" + String.format(java.util.Locale.US, "%.3f", memoireBonus(meLvl) * 100) + "%");
        }
        placeEnchant(player, menu, 32, REQ_MEMOIRE, "Mémoire de la Roche", makeEnchantTile(Material.SCULK_CATALYST,
                "§2§lMémoire de la Roche",
                "§7La roche t'apprend plus vite : §a+% d'XP de pioche§7.",
                meLvl, MEMOIRE_MAX,
                memoireCost(meLvl), meStats));

        // Cœur de la Tempête (slot 42 — a échangé sa place avec le Sel de Contrebande le 2026-08-21).
        int cyLvl = getCycloneLevel(player);
        java.util.List<String> cyStats = new ArrayList<>();
        if (cyLvl <= 0) {
            cyStats.add("§7Tempête : §einactif");
        } else {
            cyStats.add("§7Chance de tempête : §e" + String.format(java.util.Locale.US, "%.2f", cycloneChance(cyLvl) * 100) + "%");
            cyStats.add("§7Blocs par tempête : §e" + cycloneBlocks(cyLvl));
            cyStats.add("§7Durée : §e" + String.format(java.util.Locale.US, "%.1f", cycloneDuration(cyLvl)) + " s §7(" + cycloneBoltCount(cyLvl) + " éclairs × " + cycloneBlocksPerBolt(cyLvl) + " blocs)");
        }
        placeEnchant(player, menu, 42, REQ_CYCLONE, "Cœur de la Tempête", makeEnchantTile(Material.HEART_OF_THE_SEA,
                "§c§lCœur de la Tempête",
                "§7Déclenche §bta tempête personnelle§7 : des impacts tombent seuls.",
                cyLvl, CYCLONE_MAX,
                cycloneCost(cyLvl), cyStats));

        // Vol (slot 38 — a échangé sa place avec le Reflux le 2026-08-21 : le Vol quitte la page 2).
        // makeFlyTile gère elle-même son affichage verrouillé (niveau de pioche + Plume) : pas de
        // placeEnchant ici, sinon on aurait deux écrans de verrou superposés.
        menu.setItem(38, makeFlyTile(player));

        // Gouffre (slot 38) — vague « Abîme ».
        int goLvl = getGouffreLevel(player);
        java.util.List<String> goStats = new ArrayList<>();
        if (goLvl <= 0) {
            goStats.add("§7Chance de gouffre : §e0%");
            goStats.add("§7Puits : §einactif");
        } else {
            goStats.add("§7Chance de gouffre : §e" + String.format(java.util.Locale.US, "%.2f", gouffreChance(goLvl) * 100) + "%");
            goStats.add("§7Puits sphérique : §e" + gouffreBlocks(goLvl) + " blocs §7(rayon " + String.format(java.util.Locale.US, "%.1f", gouffreRadius(goLvl)) + ")");
        }
        placeEnchant(player, menu, 40, REQ_GOUFFRE, "Gouffre", makeEnchantTile(Material.OBSIDIAN,
                "§c§lGouffre",
                "§7Le sol s'effondre : un §5puits sphérique§7 s'ouvre sous tes pieds.",
                goLvl, GOUFFRE_MAX,
                gouffreCost(goLvl), goStats));

        // Sel de Contrebande (slot 34 — a échangé sa place avec le Cœur de la Tempête le 2026-08-21).
        int seLvl = getContrebandeLevel(player);
        java.util.List<String> seStats = new ArrayList<>();
        if (seLvl <= 0) {
            seStats.add("§7Bonus : §einactif");
        } else {
            seStats.add("§7Si sac §e≥ " + (int) (CONTREBANDE_FILL * 100) + "% §7plein :");
            seStats.add("§7Blocs valent §a+" + String.format(java.util.Locale.US, "%.3f", contrebandeBonus(seLvl) * 100) + "%");
        }
        placeEnchant(player, menu, 34, REQ_CONTREBANDE, "Sel de Contrebande", makeEnchantTile(Material.GUNPOWDER,
                "§a§lSel de Contrebande",
                "§7Sac presque plein = §a+% de valeur§7 (risque/récompense).",
                seLvl, CONTREBANDE_MAX,
                contrebandeCost(seLvl), seStats));

        // Flèche vers la PAGE 2 (enchants spéciaux : le Vol).
        menu.setItem(53, plugin.namedItem(Material.ARROW, "§b▶ Page 2",
                "§7Enchants spéciaux (Vol...)"));

        // Bouton fermer (slot 49).
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§c§lFermer");
        close.setItemMeta(cm);
        menu.setItem(49, close);

        // Ligne d'aide réservée aux OP (invisible pour les joueurs).
        ajouteIndiceOp(player, menu, OP_SLOTS_P1);
    }

    // Titre de la PAGE 2 du menu d'enchants (enchants spéciaux type Vol).
    private static final String ENCHANT_MENU_TITLE_2 = "§b✦ §3§lEnchantements §7(2) §b✦";

    // Tuile du Reflux, extraite pour être posable sur N'IMPORTE QUELLE page : depuis le 2026-08-21
    // il vit sur la page 2 (slot 10), à la place du Vol.
    private void placeReflux(Player player, Inventory menu, int slot) {
        int reLvl = getRefluxLevel(player);
        java.util.List<String> reStats = new ArrayList<>();
        if (reLvl <= 0) {
            reStats.add("§7Chance de reflux : §e0%");
            reStats.add("§7Couloir : §einactif");
        } else {
            reStats.add("§7Chance de reflux : §e" + String.format(java.util.Locale.US, "%.3f", refluxChance(reLvl) * 100) + "%");
            reStats.add("§7Couloir : §e~" + String.format(java.util.Locale.US, "%.2f", refluxLength(reLvl)) + " blocs §7derrière toi");
        }
        placeEnchant(player, menu, slot, REQ_REFLUX, "Reflux", makeEnchantTile(Material.PRISMARINE_SHARD,
                "§c§lReflux",
                "§7Ouvre un couloir §f1×2§7 §oderrière§7 toi (à l'opposé du regard).",
                reLvl, REFLUX_MAX,
                refluxCost(reLvl), reStats));
    }

    public void openEnchantMenuPage2(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, ENCHANT_MENU_TITLE_2);
        // Même fond que la page 1 : verre bleu clair (chaque enchant est entouré de bleu).
        ItemStack bg = plugin.pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        // Raccourci /mine (slot 0), identique à la page 1.
        menu.setItem(MINE_SHORTCUT_SLOT, mineShortcutTile());

        // En-tête identique à la page 1.
        ItemStack header = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName("§b§lPioche du Mineur §7» §fspéciaux");
        java.util.List<String> hl = new ArrayList<>();
        hl.add("§7Les enchants rares et uniques.");
        hm.setLore(hl);
        hm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        header.setItemMeta(hm);
        menu.setItem(4, header);

        // Même agencement aéré que la page 1 : enchants sur la rangée du haut (10,12,14,16) puis
        // saut au slot 18 et reprise au slot 20, exactement comme les enchants classiques.
        menu.setItem(10, makeFlecheTile(player)); // Pluie de Flèches (échangée avec Reflux le 2026-08-22)
        menu.setItem(12, makeHasteTile(player));  // Célérité
        placeReflux(player, menu, 14);            // Reflux (échangé avec la Pluie de Flèches le 2026-08-22)
        menu.setItem(16, makeTntTile(player));    // Pluie de TNT
        menu.setItem(20, makeAllongeTile(player)); // Allonge (après le saut de 18, comme la page 1)

        // Flèche RETOUR vers la page 1.
        menu.setItem(45, plugin.namedItem(Material.ARROW, "§e◀ Page 1",
                "§7Retour aux enchants classiques"));

        // Bouton fermer.
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§c§lFermer");
        close.setItemMeta(cm);
        menu.setItem(49, close);

        // Ligne d'aide réservée aux OP (invisible pour les joueurs).
        ajouteIndiceOp(player, menu, OP_SLOTS_P2);

        player.openInventory(menu);
    }

    // Tuile spéciale de l'enchant Vol : 2 niveaux (base + vol plus rapide), conditionné à la Plume.
    // États : verrouillé (niv pioche / Plume) / à acheter niv.1 / niv.1 acquis → propose niv.2 / max.
    private ItemStack makeFlyTile(Player player) {
        int fly     = getFlyLevel(player);
        boolean levelOk = plugin.getPickaxeLevel(player) >= REQ_FLY;
        boolean plume   = plugin.getActe() != null && plugin.getActe().hasFoundPlume(player);

        ItemStack it = new ItemStack(Material.FEATHER);
        ItemMeta m = it.getItemMeta();
        java.util.List<String> lore = new ArrayList<>();
        String[] roman = {"", "I", "II", "III"};

        // VERROUILLÉ : tuile brouillée (nom + lore masqués), on ne révèle PAS ce que fait l'enchant.
        if (fly == 0 && (!levelOk || !plume)) {
            it.setType(Material.GRAY_STAINED_GLASS_PANE);
            m.setDisplayName("§8§l🔒 §8§k" + "Vol".replaceAll(".", "?"));
            lore.add("");
            lore.add("§c🔒 Verrouillé");
            if (!levelOk) {
                lore.add("§7Débloqué au §bniveau de pioche " + REQ_FLY);
                lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
            } else {
                lore.add("§7Il te faut d'abord trouver la §e§lPlume§7.");
                lore.add("§8Parle au Contremaître, puis mine (1/1000).");
            }
            m.setLore(lore);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(m);
            return it;
        }

        // DÉBLOQUÉ : on montre la description.
        lore.add("§7Vole librement §fdans ta mine§7.");
        lore.add("§8Minage ralenti en vol · coupé à la sortie.");
        lore.add("");
        if (fly >= FLY_MAX) {
            m.setDisplayName("§b§lVol III §7» §aacquis ✔");
            lore.add("§aVol niveau §f3§a : vol le + rapide, §fcasse instantanée§a.");
            lore.add("§7Tape §f/fly §7dans ta mine pour l'activer.");
            m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else if (fly >= 1) {
            double next = (fly == 1) ? FLY_PRICE_2 : FLY_PRICE_3;
            m.setDisplayName("§b§lVol " + roman[fly] + " §7» §eaméliorer en Vol " + roman[fly + 1]);
            lore.add("§aTu possèdes le Vol §f(niv." + fly + ")§a.");
            lore.add(fly == 1 ? "§7Vol II : §fvol + rapide, casse + rapide§7." : "§7Vol III : §fvol le + rapide, casse instantanée§7.");
            lore.add("§7Prix : §6" + PrivateMines.formatNumber(next) + "$");
            lore.add("");
            lore.add("§e▶ Clique pour acheter le Vol " + roman[fly + 1]);
            m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            m.setDisplayName("§b§lVol §7» §eà acheter");
            lore.add("§7Prix : §6" + PrivateMines.formatNumber(FLY_PRICE) + "$");
            lore.add("");
            lore.add("§e▶ Clique pour acheter le Vol");
        }
        m.setLore(lore);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // Tuile de l'enchant Célérité (Haste) : 3 niveaux, effet Haste permanent dans la mine (niv.pioche 50).
    private ItemStack makeHasteTile(Player player) {
        int lvl = getHasteLevel(player);
        boolean levelOk = plugin.getPickaxeLevel(player) >= REQ_HASTE;

        ItemStack it = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta m = it.getItemMeta();
        java.util.List<String> lore = new ArrayList<>();
        String[] roman = {"", "I", "II", "III"};

        // VERROUILLÉ : tuile brouillée (nom + lore masqués), on ne révèle PAS ce que fait l'enchant.
        if (lvl == 0 && !levelOk) {
            it.setType(Material.GRAY_STAINED_GLASS_PANE);
            m.setDisplayName("§8§l🔒 §8§k" + "Célérité".replaceAll(".", "?"));
            lore.add("");
            lore.add("§c🔒 Verrouillé");
            lore.add("§7Débloqué au §bniveau de pioche " + REQ_HASTE);
            lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
            m.setLore(lore);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(m);
            return it;
        }

        lore.add("§7Un effet §eCélérité§7 (Haste) §fdans ta mine§7.");
        lore.add("§8Compense le ralentissement du minage en vol.");
        lore.add("");
        if (lvl > 0) lore.add("§7Niveau actuel : §eCélérité " + roman[Math.min(lvl, 3)]);
        if (lvl >= HASTE_MAX) {
            m.setDisplayName("§e§lCélérité III §7» §aacquis ✔");
            lore.add("§aNiveau maximum atteint.");
            m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            m.setDisplayName("§e§lCélérité §7» §e" + (lvl == 0 ? "à acheter" : "améliorer en " + roman[lvl + 1]));
            lore.add("§7Prochain : §eCélérité " + roman[lvl + 1]);
            lore.add("§7Prix : §6" + PrivateMines.formatNumber(hasteCost(lvl)) + "$");
            lore.add("");
            lore.add("§e▶ Clique pour acheter");
            if (lvl > 0) { m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true); m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); }
        }
        m.setLore(lore);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // Tuile de l'enchant Pluie de Flèches : 100 niveaux, salve de flèches qui tombent du ciel et
    // percent la mine vers le bas (niv.pioche 55). Brouillée tant que la pioche n'atteint pas 55.
    private ItemStack makeFlecheTile(Player player) {
        int lvl = getFlecheLevel(player);
        boolean levelOk = plugin.getPickaxeLevel(player) >= REQ_FLECHE;

        ItemStack it = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta m = it.getItemMeta();
        java.util.List<String> lore = new ArrayList<>();

        // VERROUILLÉ : tuile brouillée (nom + lore masqués), on ne révèle PAS ce que fait l'enchant.
        if (lvl == 0 && !levelOk) {
            it.setType(Material.GRAY_STAINED_GLASS_PANE);
            m.setDisplayName("§8§l🔒 §8§k" + "Pluie de Flèches".replaceAll(".", "?"));
            lore.add("");
            lore.add("§c🔒 Verrouillé");
            lore.add("§7Débloqué au §bniveau de pioche " + REQ_FLECHE);
            lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
            m.setLore(lore);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(m);
            return it;
        }

        // DÉBLOQUÉ : description + stats du niveau actuel.
        lore.add("§7Une §fpluie de flèches§7 tombe du ciel et");
        lore.add("§7perce la mine §fvers le bas§7 à chaque impact.");
        lore.add("");
        lore.add("§7Niveau : §a" + lvl + " §7/ §a" + FLECHE_MAX);
        if (lvl <= 0) {
            lore.add("§7Chance : §e0%");
            lore.add("§7Flèches : §einactif");
            lore.add("§7Perce : §einactif");
        } else {
            lore.add("§7Chance : §e" + String.format(java.util.Locale.US, "%.1f", flecheChance(lvl) * 100) + "%");
            lore.add("§7Flèches : §e" + flecheCount(lvl) + " §7par salve");
            lore.add("§7Perce : §e" + flechePierce(lvl) + " §7bloc(s) vers le bas");
            lore.add("§7Total : §6~" + flecheTotalBlocks(lvl) + " §7blocs / salve");
        }
        if (lvl >= FLECHE_MAX) {
            m.setDisplayName("§d§lPluie de Flèches §7» §aacquis ✔");
            lore.add("");
            lore.add("§aNiveau maximum atteint §7(§6" + flecheTotalBlocks(FLECHE_MAX) + "§7 blocs/salve).");
        } else {
            m.setDisplayName("§d§lPluie de Flèches §7» §e" + (lvl == 0 ? "à acheter" : "améliorer"));
            lore.add("");
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(flecheCost(lvl)) + "$");
            lore.add("§e▶ Clique pour " + (lvl == 0 ? "acheter" : "améliorer"));
        }
        m.setLore(lore);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // Tuile de l'enchant Pluie de TNT : 100 niveaux, une TNT tombe et explose en cassant une SPHÈRE
    // de blocs (niv.pioche 60). Chance faible et stable, c'est le RAYON qui monte. Brouillée < niv 60.
    private ItemStack makeTntTile(Player player) {
        int lvl = getTntLevel(player);
        boolean levelOk = plugin.getPickaxeLevel(player) >= REQ_TNT;

        ItemStack it = new ItemStack(Material.TNT);
        ItemMeta m = it.getItemMeta();
        java.util.List<String> lore = new ArrayList<>();

        // VERROUILLÉ : tuile brouillée (nom + lore masqués), on ne révèle PAS ce que fait l'enchant.
        if (lvl == 0 && !levelOk) {
            it.setType(Material.GRAY_STAINED_GLASS_PANE);
            m.setDisplayName("§8§l🔒 §8§k" + "Pluie de TNT".replaceAll(".", "?"));
            lore.add("");
            lore.add("§c🔒 Verrouillé");
            lore.add("§7Débloqué au §bniveau de pioche " + REQ_TNT);
            lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
            m.setLore(lore);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(m);
            return it;
        }

        // DÉBLOQUÉ : description + stats du niveau actuel.
        lore.add("§7Une §fTNT§7 tombe du ciel et §fexplose§7,");
        lore.add("§7creusant une §fsphère§7 de blocs dans la mine.");
        lore.add("");
        lore.add("§7Niveau : §a" + lvl + " §7/ §a" + TNT_MAX);
        if (lvl <= 0) {
            lore.add("§7Chance : §e0%");
            lore.add("§7Rayon : §einactif");
        } else {
            lore.add("§7Chance : §e" + String.format(java.util.Locale.US, "%.1f", tntChance(lvl) * 100) + "%");
            lore.add("§7Rayon : §e" + String.format(java.util.Locale.US, "%.1f", tntRadius(lvl)) + " §7blocs");
            lore.add("§7Explosion : §6~" + tntBlocksEstimes(lvl) + " §7blocs");
        }
        if (lvl >= TNT_MAX) {
            m.setDisplayName("§c§lPluie de TNT §7» §aacquis ✔");
            lore.add("");
            lore.add("§aNiveau maximum atteint §7(rayon §66§7 · §6~900§7 blocs).");
        } else {
            m.setDisplayName("§c§lPluie de TNT §7» §e" + (lvl == 0 ? "à acheter" : "améliorer"));
            lore.add("");
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(tntCost(lvl)) + "$");
            lore.add("§e▶ Clique pour " + (lvl == 0 ? "acheter" : "améliorer"));
        }
        m.setLore(lore);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // Tuile de l'enchant Allonge : 10 niveaux, +0,4 bloc de portée de minage chacun (actif en mine).
    // Débloqué au niveau de pioche 75. Brouillée (verrouillée) tant que le niveau n'est pas atteint.
    private ItemStack makeAllongeTile(Player player) {
        int lvl = getAllongeLevel(player);
        boolean levelOk = plugin.getPickaxeLevel(player) >= REQ_ALLONGE;

        ItemStack it = new ItemStack(Material.SPYGLASS);
        ItemMeta m = it.getItemMeta();
        java.util.List<String> lore = new ArrayList<>();

        if (lvl == 0 && !levelOk) {
            it.setType(Material.GRAY_STAINED_GLASS_PANE);
            m.setDisplayName("§8§l🔒 §8§k" + "Allonge".replaceAll(".", "?"));
            lore.add("");
            lore.add("§c🔒 Verrouillé");
            lore.add("§7Débloqué au §bniveau de pioche " + REQ_ALLONGE);
            lore.add("§8(ta pioche : niveau " + plugin.getPickaxeLevel(player) + ")");
            m.setLore(lore);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(m);
            return it;
        }

        lore.add("§7Ta pioche mine de §fplus loin§7.");
        lore.add("§8(uniquement dans ta mine)");
        lore.add("");
        lore.add("§7Niveau : §a" + lvl + " §7/ §a" + ALLONGE_MAX);
        lore.add("§7Portée bonus : §e+" + String.format(java.util.Locale.US, "%.1f", allongeBonus(lvl)) + " §7blocs");
        if (lvl >= ALLONGE_MAX) {
            m.setDisplayName("§d§lAllonge §7» §aacquis ✔");
            lore.add("");
            lore.add("§aNiveau maximum atteint §7(+4 blocs).");
        } else {
            m.setDisplayName("§d§lAllonge §7» §e" + (lvl == 0 ? "à acheter" : "améliorer"));
            lore.add("");
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(allongeCost(lvl)) + "$");
            lore.add("§e▶ Clique pour " + (lvl == 0 ? "acheter" : "améliorer"));
        }
        m.setLore(lore);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // Construit une tuile d'enchantement (icône brillante) avec niveau/coût + stats du niveau actuel.
    private ItemStack makeEnchantTile(Material icon, String name, String desc, int level, int max, double cost,
                                      java.util.List<String> stats) {
        ItemStack book = new ItemStack(icon);
        ItemMeta m = book.getItemMeta();
        m.setDisplayName(name);
        java.util.List<String> lore = new ArrayList<>();
        lore.add(desc);
        lore.add("");
        lore.add("§7Niveau : §a" + level + " §7/ §a" + max);
        if (stats != null) for (String s : stats) lore.add(s);
        if (level < max) {
            lore.add("");
            lore.add("§7Coût : §6" + PrivateMines.formatNumber(cost) + "$");
            lore.add("§e➜ Clic pour améliorer !");
        } else {
            lore.add("");
            lore.add("§a✔ Niveau maximum atteint");
        }
        m.setLore(lore);
        // Effet brillant.
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        book.setItemMeta(m);
        return book;
    }

    private void buyEfficiency(Player player) {
        int lvl = getEfficiencyLevel(player);
        if (lvl >= EFFICIENCY_MAX) {
            player.sendMessage("§eEfficacité est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = efficiencyCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        efficiencyLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aEfficacité améliorée au niveau §b" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    private void buyExplosion(Player player) {
        int lvl = getExplosionLevel(player);
        if (lvl >= EXPLOSION_MAX) {
            player.sendMessage("§eExplosion est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = explosionCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        explosionLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aExplosion améliorée au niveau §c" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void buyForage(Player player) {
        int lvl = getForageLevel(player);
        if (lvl >= FORAGE_MAX) {
            player.sendMessage("§eForage est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = forageCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        forageLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aForage amélioré au niveau §e" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
    }

    private void buyFracture(Player player) {
        int lvl = getFractureLevel(player);
        if (lvl >= FRACTURE_MAX) {
            player.sendMessage("§eFracture est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = fractureCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        fractureLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aFracture améliorée au niveau §b" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void buyVein(Player player) {
        int lvl = getVeinLevel(player);
        if (lvl >= VEIN_MAX) {
            player.sendMessage("§eVein Miner est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = veinCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        veinLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aVein Miner amélioré au niveau §a" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    private void buyColonne(Player player) {
        int lvl = getColonneLevel(player);
        if (lvl >= COLONNE_MAX) {
            player.sendMessage("§eColonne d'Écume est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = colonneCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        colonneLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aColonne d'Écume améliorée au niveau §a" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
    }

    private void buyReflux(Player player) {
        int lvl = getRefluxLevel(player);
        if (lvl >= REFLUX_MAX) {
            player.sendMessage("§eReflux est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = refluxCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        refluxLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aReflux amélioré au niveau §c" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
    }

    private void buyGouffre(Player player) {
        int lvl = getGouffreLevel(player);
        if (lvl >= GOUFFRE_MAX) {
            player.sendMessage("§eGouffre est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = gouffreCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        gouffreLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aGouffre amélioré au niveau §5" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
    }

    private void buyContrebande(Player player) {
        int lvl = getContrebandeLevel(player);
        if (lvl >= CONTREBANDE_MAX) {
            player.sendMessage("§eSel de Contrebande est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = contrebandeCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        contrebandeLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aSel de Contrebande amélioré au niveau §2" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
    }

    // Achat de l'enchant Vol : niv.1 (200K), niv.2 (500K, casse + rapide), niv.3 (1M, casse instantanée + vol + rapide).
    // Requiert le niveau de pioche 45 + la Plume trouvée.
    private void buyFly(Player player) {
        int fly = getFlyLevel(player);
        if (fly >= FLY_MAX) {
            player.sendMessage("§eTu possèdes déjà le §b§lVol III§e (max) ! Tape §f/fly §edans ta mine.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Le niveau 1 exige pioche 45 + Plume ; les niveaux suivants s'achètent après le précédent.
        if (fly == 0) {
            if (plugin.getPickaxeLevel(player) < REQ_FLY) {
                player.sendMessage("§c🔒 Le Vol se débloque au §bniveau de pioche " + REQ_FLY + "§c.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            if (plugin.getActe() == null || !plugin.getActe().hasFoundPlume(player)) {
                player.sendMessage("§cIl te faut d'abord trouver la §e§lPlume§c ! Parle au §6Contremaître§c, puis mine (1/1000).");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }
        double cost = (fly == 0) ? FLY_PRICE : (fly == 1) ? FLY_PRICE_2 : FLY_PRICE_3;
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        flyLevel.put(player.getUniqueId(), fly + 1);
        if (fly == 0) {
            player.sendMessage("§b✦ §aTu as acquis le §b§lVol §apour §6" + PrivateMines.formatNumber(cost) + "$ §a!");
            player.sendMessage("§7Tape §f/fly §7dans ta mine pour t'envoler. §8(minage ralenti en vol)");
        } else if (fly == 1) {
            player.sendMessage("§b✦ §aVol amélioré en §b§lVol II §apour §6" + PrivateMines.formatNumber(cost) + "$ §a! §7(vol + rapide, casse + rapide)");
        } else {
            player.sendMessage("§b✦ §aVol amélioré en §b§lVol III §apour §6" + PrivateMines.formatNumber(cost) + "$ §a! §7(vol le + rapide, §fcasse instantanée§7)");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);
    }

    // Achat d'un niveau de Célérité (Haste) : niveau de pioche 50 requis, prix par niveau.
    private void buyHaste(Player player) {
        int lvl = getHasteLevel(player);
        if (lvl >= HASTE_MAX) {
            player.sendMessage("§eCélérité est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (plugin.getPickaxeLevel(player) < REQ_HASTE) {
            player.sendMessage("§c🔒 Célérité se débloque au §bniveau de pioche " + REQ_HASTE + "§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = hasteCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        hasteLevel.put(player.getUniqueId(), lvl + 1);
        // Applique tout de suite l'effet si le joueur est dans sa mine.
        plugin.refreshHasteEffect(player);
        player.sendMessage("§e✦ §aCélérité améliorée au niveau §e" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ §a!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
    }

    // Achat d'un niveau de Pluie de Flèches : niveau de pioche 55 requis, 100 niveaux.
    private void buyFleche(Player player) {
        int lvl = getFlecheLevel(player);
        if (lvl >= FLECHE_MAX) {
            player.sendMessage("§ePluie de Flèches est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (plugin.getPickaxeLevel(player) < REQ_FLECHE) {
            player.sendMessage("§c🔒 Pluie de Flèches se débloque au §bniveau de pioche " + REQ_FLECHE + "§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = flecheCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        flecheLevel.put(player.getUniqueId(), lvl + 1);
        int nv = lvl + 1;
        if (nv == 1) {
            player.sendMessage("§d✦ §aTu as acquis la §d§lPluie de Flèches §apour §6" + PrivateMines.formatNumber(cost) + "$ §a!");
            player.sendMessage("§7Des flèches tomberont du ciel en minant et perceront la roche.");
        } else {
            player.sendMessage("§d✦ §aPluie de Flèches améliorée au niveau §e" + nv + " §apour §6" + PrivateMines.formatNumber(cost) + "$ §a! §7(" + flecheCount(nv) + " flèches · perce " + flechePierce(nv) + ")");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ARROW_SHOOT, 0.9f, 1.4f);
    }

    // Achat d'un niveau de Pluie de TNT : niveau de pioche 60 requis, 100 niveaux.
    private void buyTnt(Player player) {
        int lvl = getTntLevel(player);
        if (lvl >= TNT_MAX) {
            player.sendMessage("§ePluie de TNT est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (plugin.getPickaxeLevel(player) < REQ_TNT) {
            player.sendMessage("§c🔒 Pluie de TNT se débloque au §bniveau de pioche " + REQ_TNT + "§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = tntCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        tntLevel.put(player.getUniqueId(), lvl + 1);
        int nv = lvl + 1;
        if (nv == 1) {
            player.sendMessage("§c✦ §aTu as acquis la §c§lPluie de TNT §apour §6" + PrivateMines.formatNumber(cost) + "$ §a!");
            player.sendMessage("§7Des TNT tomberont du ciel en minant et exploseront la roche.");
        } else {
            player.sendMessage("§c✦ §aPluie de TNT améliorée au niveau §e" + nv + " §apour §6" + PrivateMines.formatNumber(cost) + "$ §a! §7(rayon " + String.format(java.util.Locale.US, "%.1f", tntRadius(nv)) + ")");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
    }

    // Achat d'un niveau d'Allonge : niveau de pioche 75 requis, 10 niveaux (+0,4 bloc de portée chacun).
    private void buyAllonge(Player player) {
        int lvl = getAllongeLevel(player);
        if (lvl >= ALLONGE_MAX) {
            player.sendMessage("§eAllonge est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (plugin.getPickaxeLevel(player) < REQ_ALLONGE) {
            player.sendMessage("§c🔒 Allonge se débloque au §bniveau de pioche " + REQ_ALLONGE + "§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = allongeCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        int nv = lvl + 1;
        allongeLevel.put(player.getUniqueId(), nv);
        // Applique la portée tout de suite si le joueur est dans sa mine.
        plugin.refreshReachAttribute(player);
        if (nv == 1) {
            player.sendMessage("§d✦ §aTu as acquis l'§d§lAllonge §apour §6" + PrivateMines.formatNumber(cost) + "$ §a!");
            player.sendMessage("§7Ta pioche mine de plus loin §8(uniquement dans ta mine)§7.");
        } else {
            player.sendMessage("§d✦ §aAllonge améliorée au niveau §e" + nv + " §apour §6"
                    + PrivateMines.formatNumber(cost) + "$ §a! §7(+"
                    + String.format(java.util.Locale.US, "%.1f", allongeBonus(nv)) + " blocs)");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    private void buyDime(Player player) {
        int lvl = getDimeLevel(player);
        if (lvl >= DIME_MAX) {
            player.sendMessage("§eDîme du Passeur est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = dimeCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        int nv = lvl + 1;
        dimeLevel.put(player.getUniqueId(), nv);
        plugin.applyPickaxeEnchants(player);
        String suffixe = nv > 20 ? " §7(bloc payé §6x" + (int) dimeMult(nv) + "§7)" : "";
        player.sendMessage("§aDîme du Passeur améliorée au niveau §f" + nv + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !" + suffixe);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.9f);
    }

    private void buyFortune(Player player) {
        int lvl = getFortuneLevel(player);
        if (lvl >= FORTUNE_MAX) {
            player.sendMessage("§eFortune des Abysses est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = fortuneCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        fortuneLevel.put(player.getUniqueId(), lvl + 1);
        player.sendMessage("§aFortune des Abysses améliorée au niveau §6" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void buyHarpon(Player player) {
        int lvl = getHarponLevel(player);
        if (lvl >= HARPON_MAX) {
            player.sendMessage("§ePluie de Harpons est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = harponCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        harponLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aPluie de Harpons améliorée au niveau §5" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void buyMemoire(Player player) {
        int lvl = getMemoireLevel(player);
        if (lvl >= MEMOIRE_MAX) {
            player.sendMessage("§eMémoire de la Roche est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = memoireCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        memoireLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aMémoire de la Roche améliorée au niveau §2" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void buyCyclone(Player player) {
        int lvl = getCycloneLevel(player);
        if (lvl >= CYCLONE_MAX) {
            player.sendMessage("§eCœur de la Tempête est déjà au niveau maximum !");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        double cost = cycloneCost(lvl);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(cost) + "$");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(player, cost);
        if (plugin.getQuests() != null) plugin.getQuests().onEnchantLevel(player);
        cycloneLevel.put(player.getUniqueId(), lvl + 1);
        plugin.applyPickaxeEnchants(player);
        player.sendMessage("§aCœur de la Tempête amélioré au niveau §b" + (lvl + 1) + " §apour §6" + PrivateMines.formatNumber(cost) + "$ !");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ==========================================================================================
    //  [OP] RÉGLAGE DIRECT DU NIVEAU D'UN ENCHANT  (ajouté le 2026-08-24)
    // ------------------------------------------------------------------------------------------
    //  Shift + clic droit sur un enchant (OP uniquement) -> saisie du niveau voulu dans le chat.
    //  ⚠️ Ctrl+clic droit N'EST PAS détectable : Minecraft n'envoie pas la touche Ctrl dans les
    //     menus, le client émet le même paquet qu'un clic droit normal. D'où le Shift.
    //  Le niveau est PLAFONNÉ au max de l'enchant (les formules ne sont calibrées que jusque-là).
    //  Même mécanique de saisie que le renommage d'Île (PrivateMines.onRenameChat).
    // ==========================================================================================

    /** Joueur -> clé de l'enchant dont il est en train de saisir le niveau dans le chat. */
    private final java.util.Map<java.util.UUID, String> pendingSetLevel = new java.util.HashMap<>();

    /** Slots des enchants de la PAGE 1 (mêmes valeurs que dans onEnchantMenuClick). */
    private static final int[] OP_SLOTS_P1 = { 10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34, 38, 40, 42 };
    /** Slots des enchants de la PAGE 2. */
    private static final int[] OP_SLOTS_P2 = { 10, 12, 14, 16, 20 };

    /** Clé interne de l'enchant occupant ce slot, ou null si le slot n'est pas un enchant. */
    private String enchantKeyAt(boolean page2, int slot) {
        if (page2) {
            switch (slot) {
                case 10: return "fleche";
                case 12: return "haste";
                case 14: return "reflux";
                case 16: return "tnt";
                case 20: return "allonge";
                default: return null;
            }
        }
        switch (slot) {
            case 10: return "efficacite";
            case 12: return "vein";
            case 14: return "forage";
            case 16: return "colonne";
            case 20: return "harpon";
            case 22: return "explosion";
            case 24: return "fracture";
            case 28: return "fortune";
            case 30: return "dime";
            case 32: return "memoire";
            case 34: return "contrebande";
            case 38: return "fly";
            case 40: return "gouffre";
            case 42: return "cyclone";
            default: return null;
        }
    }

    /** Les enchants de la page 2 (pour rouvrir la bonne page après la saisie). */
    private boolean estPage2(String key) {
        return key.equals("fleche") || key.equals("haste") || key.equals("reflux")
                || key.equals("tnt") || key.equals("allonge");
    }

    private String nomEnchant(String key) {
        switch (key) {
            case "efficacite":  return "Efficacité";
            case "vein":        return "Vein Miner";
            case "forage":      return "Forage";
            case "colonne":     return "Colonne d'Écume";
            case "harpon":      return "Pluie de Harpons";
            case "explosion":   return "Explosion";
            case "fracture":    return "Fracture";
            case "fortune":     return "Fortune des Abysses";
            case "dime":        return "Dîme du Passeur";
            case "memoire":     return "Mémoire de la Roche";
            case "contrebande": return "Sel de Contrebande";
            case "fly":         return "Vol";
            case "gouffre":     return "Gouffre";
            case "cyclone":     return "Cœur de la Tempête";
            case "fleche":      return "Pluie de Flèches";
            case "haste":       return "Célérité";
            case "reflux":      return "Reflux";
            case "tnt":         return "Pluie de TNT";
            case "allonge":     return "Allonge";
            default:            return key;
        }
    }

    private int maxEnchant(String key) {
        switch (key) {
            case "efficacite":  return EFFICIENCY_MAX;
            case "vein":        return VEIN_MAX;
            case "forage":      return FORAGE_MAX;
            case "colonne":     return COLONNE_MAX;
            case "harpon":      return HARPON_MAX;
            case "explosion":   return EXPLOSION_MAX;
            case "fracture":    return FRACTURE_MAX;
            case "fortune":     return FORTUNE_MAX;
            case "dime":        return DIME_MAX;
            case "memoire":     return MEMOIRE_MAX;
            case "contrebande": return CONTREBANDE_MAX;
            case "fly":         return FLY_MAX;
            case "gouffre":     return GOUFFRE_MAX;
            case "cyclone":     return CYCLONE_MAX;
            case "fleche":      return FLECHE_MAX;
            case "haste":       return HASTE_MAX;
            case "reflux":      return REFLUX_MAX;
            case "tnt":         return TNT_MAX;
            case "allonge":     return ALLONGE_MAX;
            default:            return 0;
        }
    }

    private int niveauEnchant(String key, Player p) {
        switch (key) {
            case "efficacite":  return getEfficiencyLevel(p);
            case "vein":        return getVeinLevel(p);
            case "forage":      return getForageLevel(p);
            case "colonne":     return getColonneLevel(p);
            case "harpon":      return getHarponLevel(p);
            case "explosion":   return getExplosionLevel(p);
            case "fracture":    return getFractureLevel(p);
            case "fortune":     return getFortuneLevel(p);
            case "dime":        return getDimeLevel(p);
            case "memoire":     return getMemoireLevel(p);
            case "contrebande": return getContrebandeLevel(p);
            case "fly":         return getFlyLevel(p);
            case "gouffre":     return getGouffreLevel(p);
            case "cyclone":     return getCycloneLevel(p);
            case "fleche":      return getFlecheLevel(p);
            case "haste":       return getHasteLevel(p);
            case "reflux":      return getRefluxLevel(p);
            case "tnt":         return getTntLevel(p);
            case "allonge":     return getAllongeLevel(p);
            default:            return 0;
        }
    }

    /** Écrit le niveau (déjà plafonné par l'appelant) via les setters existants. */
    private void appliqueNiveau(String key, Player p, int lvl) {
        java.util.UUID id = p.getUniqueId();
        switch (key) {
            case "efficacite":  setEfficiencyLevel(id, lvl); break;
            case "vein":        setVeinLevel(id, lvl); break;
            case "forage":      setForageLevel(id, lvl); break;
            case "colonne":     setColonneLevel(id, lvl); break;
            case "harpon":      setHarponLevel(id, lvl); break;
            case "explosion":   setExplosionLevel(id, lvl); break;
            case "fracture":    setFractureLevel(id, lvl); break;
            case "fortune":     setFortuneLevel(id, lvl); break;
            case "dime":        setDimeLevel(id, lvl); break;
            case "memoire":     setMemoireLevel(id, lvl); break;
            case "contrebande": setContrebandeLevel(id, lvl); break;
            case "fly":         setFlyLevel(id, lvl); break;
            case "gouffre":     setGouffreLevel(id, lvl); break;
            case "cyclone":     setCycloneLevel(id, lvl); break;
            case "fleche":      setFlecheLevel(id, lvl); break;
            case "haste":       setHasteLevel(id, lvl); break;
            case "reflux":      setRefluxLevel(id, lvl); break;
            case "tnt":         setTntLevel(id, lvl); break;
            case "allonge":     setAllongeLevel(id, lvl); break;
            default: break;
        }
    }

    /** Ajoute la ligne d'aide OP en bas des tuiles d'enchant. Ne fait rien pour un joueur normal. */
    private void ajouteIndiceOp(Player p, Inventory menu, int[] slots) {
        if (!p.isOp()) return;
        for (int slot : slots) {
            ItemStack it = menu.getItem(slot);
            if (it == null || it.getType() == Material.AIR) continue;
            ItemMeta m = it.getItemMeta();
            if (m == null) continue;
            java.util.List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§8[OP] Shift+clic droit : définir le niveau");
            m.setLore(lore);
            it.setItemMeta(m);
        }
    }

    /** Ouvre la saisie du niveau dans le chat pour cet enchant. */
    private void demandeNiveau(Player p, String key) {
        pendingSetLevel.put(p.getUniqueId(), key);
        p.closeInventory();
        p.sendMessage("§8§m                                        ");
        p.sendMessage("§b§l[OP] §f" + nomEnchant(key) + " §7— niveau actuel : §b" + niveauEnchant(key, p));
        p.sendMessage("§7Écris le niveau voulu dans le chat §8(0 à " + maxEnchant(key) + ")§7.");
        p.sendMessage("§7Ou écris §eannuler§7.");
        p.sendMessage("§8§m                                        ");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
    }

    /** Saisie du niveau dans le chat. Async : tout le travail repart sur le thread principal. */
    @EventHandler
    public void onSetLevelChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        final String key = pendingSetLevel.get(p.getUniqueId());
        if (key == null) return;
        event.setCancelled(true);
        pendingSetLevel.remove(p.getUniqueId());
        final String saisie = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (saisie.equalsIgnoreCase("annuler")) {
                p.sendMessage("§7Réglage annulé.");
                return;
            }
            if (!p.isOp()) return;                       // re-vérifié : l'OP a pu être retiré entre-temps
            int lvl;
            try {
                lvl = Integer.parseInt(saisie);
            } catch (NumberFormatException ex) {
                p.sendMessage("§c« " + saisie + " » n'est pas un nombre. Recommence depuis le menu.");
                return;
            }
            int max = maxEnchant(key);
            boolean plafonne = lvl > max;
            if (lvl < 0) lvl = 0;
            if (plafonne) lvl = max;
            appliqueNiveau(key, p, lvl);
            plugin.applyPickaxeEnchants(p);
            p.sendMessage("§a✔ " + nomEnchant(key) + " §aréglé au niveau §b" + lvl
                    + (plafonne ? " §7(plafonné, le max est " + max + ")" : ""));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            if (estPage2(key)) openEnchantMenuPage2(p); else openEnchantMenu(p);
        });
    }

    @EventHandler
    public void onEnchantMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();

        // ── PAGE 2 (enchants spéciaux : le Vol) ──
        if (ENCHANT_MENU_TITLE_2.equals(title)) {
            event.setCancelled(true);
            Player p2 = (Player) event.getWhoClicked();
            int s2 = event.getRawSlot();
            // [OP] Shift+clic droit -> saisie du niveau dans le chat (voir demandeNiveau).
            if (p2.isOp() && event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                String cle = enchantKeyAt(true, s2);
                if (cle != null) { demandeNiveau(p2, cle); return; }
            }
            if (s2 == 49) { p2.closeInventory(); return; }
            if (s2 == MINE_SHORTCUT_SLOT) { plugin.openMineMenu(p2); return; }  // raccourci /mine
            if (s2 == 45) { openEnchantMenu(p2); return; }   // retour page 1
            if (s2 == 10) { buyFleche(p2); openEnchantMenuPage2(p2); return; } // achat Pluie de Flèches
            if (s2 == 12) { buyHaste(p2); openEnchantMenuPage2(p2); return; } // achat Célérité
            if (s2 == 14) {                                                    // achat Reflux
                if (!tryUnlock(p2, REQ_REFLUX, "Reflux")) return;
                buyReflux(p2); openEnchantMenuPage2(p2); return;
            }
            if (s2 == 16) { buyTnt(p2); openEnchantMenuPage2(p2); return; }    // achat Pluie de TNT
            if (s2 == 20) { buyAllonge(p2); openEnchantMenuPage2(p2); return; } // achat Allonge
            return;
        }

        if (!ENCHANT_MENU_TITLE.equals(title)) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        // [OP] Shift+clic droit -> saisie du niveau dans le chat (voir demandeNiveau).
        if (player.isOp() && event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            String cle = enchantKeyAt(false, slot);
            if (cle != null) { demandeNiveau(player, cle); return; }
        }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == MINE_SHORTCUT_SLOT) { plugin.openMineMenu(player); return; }  // raccourci /mine
        if (slot == 53) { openEnchantMenuPage2(player); return; }  // aller page 2

        // Niveau total AVANT l'achat : si ça augmente, c'est qu'un enchant a bien été amélioré.
        int before = totalEnchantLevels(player);
        // Page unique : 10=Efficacité, 12=Vein, 14=Forage, 16=Colonne, 20=Harpons,
        //               22=Explosion, 24=Fracture, 28=Fortune, 30=Dîme.
        // Chaque enchant vérifie son palier de pioche : si verrouillé -> message + rien.
        if (slot == 10)      { if (!tryUnlock(player, REQ_EFFICIENCY, "Efficacité")) return; buyEfficiency(player); }
        else if (slot == 12) { if (!tryUnlock(player, REQ_VEIN, "Vein Miner")) return; buyVein(player); }
        else if (slot == 14) { if (!tryUnlock(player, REQ_FORAGE, "Forage")) return; buyForage(player); }
        else if (slot == 16) { if (!tryUnlock(player, REQ_COLONNE, "Colonne d'Écume")) return; buyColonne(player); }
        else if (slot == 20) { if (!tryUnlock(player, REQ_HARPON, "Pluie de Harpons")) return; buyHarpon(player); }
        else if (slot == 22) { if (!tryUnlock(player, REQ_EXPLOSION, "Explosion")) return; buyExplosion(player); }
        else if (slot == 24) { if (!tryUnlock(player, REQ_FRACTURE, "Fracture")) return; buyFracture(player); }
        else if (slot == 28) { if (!tryUnlock(player, REQ_FORTUNE, "Fortune des Abysses")) return; buyFortune(player); }
        else if (slot == 30) { if (!tryUnlock(player, REQ_DIME, "Dîme du Passeur")) return; buyDime(player); }
        else if (slot == 32) { if (!tryUnlock(player, REQ_MEMOIRE, "Mémoire de la Roche")) return; buyMemoire(player); }
        else if (slot == 42) { if (!tryUnlock(player, REQ_CYCLONE, "Cœur de la Tempête")) return; buyCyclone(player); }
        else if (slot == 38) { buyFly(player); }   // Vol : buyFly vérifie lui-même le niveau de pioche ET la Plume
        else if (slot == 40) { if (!tryUnlock(player, REQ_GOUFFRE, "Gouffre")) return; buyGouffre(player); }
        else if (slot == 34) { if (!tryUnlock(player, REQ_CONTREBANDE, "Sel de Contrebande")) return; buyContrebande(player); }
        else return;
        refreshEnchantMenu(player, event.getInventory());
        // Un enchant a-t-il été amélioré ? (niveau total en hausse) → parcours de découverte.
        if (totalEnchantLevels(player) > before) {
            plugin.getTutorial().onEnchantUpgrade(player);
        }
    }
}
