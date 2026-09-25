package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * ACTE III — « L'Apprentissage » : un parcours de quêtes de découverte qui démarre
 * automatiquement à la fin de l'Acte II (pioche forgée).
 *
 * <p>Chaque étape se valide par une VRAIE action du joueur. Les étapes à compteur
 * (miner, améliorer le sac, améliorer les enchants) remplissent la BossBar au fur
 * et à mesure (ex : 25/50 blocs → barre à moitié). À chaque étape terminée, le joueur
 * reçoit une récompense en argent et l'objectif avance.
 *
 * <p>Progression stockée dans le PDC du joueur (clé "acte3_step") :
 * <ul>
 *   <li>1 = miner 50 blocs</li>
 *   <li>2 = laisser le sac vendre (gagner de l'argent)</li>
 *   <li>3 = acheter 5 améliorations de sac</li>
 *   <li>4 = acheter 5 améliorations d'enchant sur la pioche</li>
 *   <li>5 = ouvrir /pets</li>
 *   <li>6 = acheter un familier (une crate)</li>
 *   <li>7 = équiper un familier</li>
 *   <li>8 = aller sur sa parcelle</li>
 *   <li>9 = ouvrir /shop</li>
 *   <li>10 = ouvrir /classements</li>
 *   <li>11 = débloquer / rejoindre une nouvelle mine</li>
 *   <li>12 = parcours terminé</li>
 * </ul>
 */
public class TutorialManager implements Listener {

    private final PrivateMines plugin;
    private final org.bukkit.NamespacedKey stepKey;    // acte3_step : entier 1..12
    private final org.bukkit.NamespacedKey minedKey;    // acte3_mined : blocs minés (étape 1)
    private final org.bukkit.NamespacedKey soldKey;     // acte3_sold : blocs vendus (étape 2)
    private final org.bukkit.NamespacedKey sacKey;      // acte3_sac : améliorations de sac (étape 3)
    private final org.bukkit.NamespacedKey enchKey;     // acte3_ench : améliorations d'enchant (étape 4)

    private static final int LAST_STEP = 12; // parcours fini quand step >= 12

    // Objectifs chiffrés.
    private static final int MINE_GOAL = 50;
    private static final int SELL_GOAL = 25;
    private static final int SAC_GOAL  = 5;
    private static final int ENCH_GOAL = 5;

    // Récompense (en $) par étape validée (index = numéro d'étape terminée).
    // NB : l'étape « ouvrir /pets » (5) donne de quoi s'offrir une 1re crate (15 000$).
    private static final double[] REWARDS = {
            0,        // 0 (inutilisé)
            50,      // 1 miner 50 blocs
            50,      // 2 gagner de l'argent
            200,      // 3 améliorer le sac 5×
            200,      // 4 améliorer les enchants 5×
            14500,    // 5 ouvrir /pets → finance la crate de l'étape 6
            200,      // 6 acheter une crate
            200,      // 7 équiper un familier
            50,      // 8 parcelle
            50,      // 9 shop
            50,      // 10 classements
            200      // 11 nouvelle mine (finale)
    };

    // ── Objectifs BossBar (couleur bleue via GuideManager) ──────────────────────
    private static String objMiner(int n)  { return "§d✦ L'Apprentissage §7— §fMine des blocs §7(§e" + n + "§7/§e" + MINE_GOAL + "§7)"; }
    private static String objVendre(int n) { return "§d✦ L'Apprentissage §7— §fLaisse ton sac §evendre §ftes blocs §7(§e" + n + "§7/§e" + SELL_GOAL + "§7)"; }
    private static String objSac(int n)    { return "§d✦ L'Apprentissage §7— §fAméliore ton §6Sac §7(§e" + n + "§7/§e" + SAC_GOAL + "§7)"; }
    private static String objEnch(int n)   { return "§d✦ L'Apprentissage §7— §fAméliore ta §bpioche §7(enchants §e" + n + "§7/§e" + ENCH_GOAL + "§7)"; }
    public static final String OBJ_PETS =
            "§d✦ L'Apprentissage §7— §fOuvre §e/pets §f(tes familiers)";
    public static final String OBJ_ACHETER =
            "§d✦ L'Apprentissage §7— §fDans §e/pets §7→ §fArc I, §eachète une §ecrate";
    public static final String OBJ_EQUIP =
            "§d✦ L'Apprentissage §7— §fRefais §e/pets §7→ coffre en bas à droite §7» §féquipe ton familier";
    public static final String OBJ_PARCELLE =
            "§d✦ L'Apprentissage §7— §fVa sur ton §e/ob §7(ton Île) et ouvre ton sac §e/bp";
    public static final String OBJ_SHOP =
            "§d✦ L'Apprentissage §7— §fOuvre la boutique §e/shop";
    public static final String OBJ_CLASSEMENTS =
            "§d✦ L'Apprentissage §7— §fRegarde les §e/classements";
    public static final String OBJ_MINE =
            "§d✦ L'Apprentissage §7— §fOuvre §e/mine §fet retourne dans ta mine";

    public TutorialManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.stepKey = new org.bukkit.NamespacedKey(plugin, "acte3_step");
        this.minedKey = new org.bukkit.NamespacedKey(plugin, "acte3_mined");
        this.soldKey = new org.bukkit.NamespacedKey(plugin, "acte3_sold");
        this.sacKey = new org.bukkit.NamespacedKey(plugin, "acte3_sac");
        this.enchKey = new org.bukkit.NamespacedKey(plugin, "acte3_ench");
    }

    // ── Progression ─────────────────────────────────────────────────────────────

    public int getStep(Player p) {
        return p.getPersistentDataContainer().getOrDefault(stepKey, PersistentDataType.INTEGER, 0);
    }

    private void setStep(Player p, int step) {
        p.getPersistentDataContainer().set(stepKey, PersistentDataType.INTEGER, step);
    }

    private int counter(Player p, org.bukkit.NamespacedKey key) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    private void setCounter(Player p, org.bukkit.NamespacedKey key, int v) {
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, v);
    }

    public boolean isDone(Player p) {
        return getStep(p) >= LAST_STEP;
    }

    /** Démarre le parcours (appelé à la fin de l'Acte II). Ne fait rien si déjà lancé/fini. */
    public void start(Player p) {
        if (getStep(p) != 0) return;
        setStep(p, 1);
        setCounter(p, minedKey, 0);
        setCounter(p, soldKey, 0);
        setCounter(p, sacKey, 0);
        setCounter(p, enchKey, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§d✦ §5§lL'Apprentissage commence §7— §fApprends à survivre dans les Abysses.");
            p.sendMessage("§7Suis la barre en haut de l'écran : chaque étape te rapporte de l'§6argent§7.");
            p.sendMessage("§8§m                                        ");
            plugin.getGuide().setQuestObjective(p, objMiner(0), 0.0);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.1f);
        }, 60L);
    }

    // ── Récompense + passage à l'étape suivante ──────────────────────────────────

    // nextObjective peut être null (étape finale gérée à part) ; nextProgress = remplissage initial.
    private void reward(Player p, int completedStep, String flavor, String nextObjective, double nextProgress) {
        double amount = (completedStep < REWARDS.length) ? REWARDS[completedStep] : 0;
        if (amount > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(p, amount);
        }
        setStep(p, completedStep + 1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        p.sendMessage("§a✔ §f" + flavor + " §7(+§6" + (int) amount + "$§7)");
        if (nextObjective != null) {
            plugin.getGuide().setQuestObjective(p, nextObjective, nextProgress);
        }
    }

    private void reward(Player p, int completedStep, String flavor, String nextObjective) {
        reward(p, completedStep, flavor, nextObjective, 1.0);
    }

    // ── Hooks appelés depuis le gameplay existant ────────────────────────────────

    /** Bloc miné (depuis creditMineBlock). Étape 1 — barre remplie selon la progression. */
    public void onBlockMined(Player p) {
        if (getStep(p) != 1) return;
        int mined = counter(p, minedKey) + 1;
        setCounter(p, minedKey, mined);
        if (mined >= MINE_GOAL) {
            reward(p, 1, "Premiers coups de pioche ! La roche se souvient de toi.", objVendre(0), 0.0);
        } else {
            plugin.getGuide().setQuestObjective(p, objMiner(mined), (double) mined / MINE_GOAL);
        }
    }

    /** Le sac vend des blocs. Étape 2 — vendre 25 blocs cumulés (barre remplie selon la progression). */
    public void onMoneyEarned(Player p, int blocksSold) {
        if (getStep(p) != 2) return;
        int sold = counter(p, soldKey) + blocksSold;
        setCounter(p, soldKey, sold);
        if (sold >= SELL_GOAL) {
            reward(p, 2, "Ton sac vend tout seul : l'argent tombe pendant que tu creuses.",
                    objSac(0), 0.0);
        } else {
            plugin.getGuide().setQuestObjective(p, objVendre(sold), (double) sold / SELL_GOAL);
        }
    }

    /** Le joueur améliore son sac (capacité / vente / bonus d'argent). Étape 3 — 5 fois. */
    public void onSacUpgrade(Player p) {
        if (getStep(p) != 3) return;
        int n = counter(p, sacKey) + 1;
        setCounter(p, sacKey, n);
        if (n >= SAC_GOAL) {
            reward(p, 3, "Sac bien rodé ! Plus de capacité, plus de ventes, plus d'argent.",
                    objEnch(0), 0.0);
        } else {
            plugin.getGuide().setQuestObjective(p, objSac(n), (double) n / SAC_GOAL);
        }
    }

    /** Le joueur améliore un enchant de sa pioche. Étape 4 — 5 fois. */
    public void onEnchantUpgrade(Player p) {
        if (getStep(p) != 4) return;
        int n = counter(p, enchKey) + 1;
        setCounter(p, enchKey, n);
        if (n >= ENCH_GOAL) {
            reward(p, 4, "Ta pioche s'éveille ! Ses enchants la rendent redoutable.", OBJ_PETS);
        } else {
            plugin.getGuide().setQuestObjective(p, objEnch(n), (double) n / ENCH_GOAL);
        }
    }

    /** Le joueur achète une crate de familiers. Étape 6. */
    public void onCrateBought(Player p) {
        if (getStep(p) != 6) return;
        reward(p, 6, "Crate ouverte ! Ton familier est rangé dans §e/pets §7» §fle coffre en bas à droite (« Mes Familiers »). Va l'y récupérer pour l'équiper.", OBJ_EQUIP);
    }

    /** Le joueur équipe un familier. Étape 7. */
    public void onPetEquipped(Player p) {
        if (getStep(p) != 7) return;
        reward(p, 7, "Un familier à tes côtés : ses bonus sont désormais actifs !", OBJ_PARCELLE);
    }

    /** Le joueur entre sur sa parcelle. Étape 8. */
    public void onEnterParcelle(Player p) {
        if (getStep(p) != 8) return;
        reward(p, 8, "Ta parcelle : ton refuge et ton coffre-fort.", OBJ_SHOP);
    }

    /** Le joueur débloque OU rejoint une nouvelle mine. Étape 11 (finale). */
    public void onMineReached(Player p) {
        if (getStep(p) != 11) return;
        double amount = REWARDS[11];
        if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(p, amount);
        setStep(p, LAST_STEP);
        plugin.getGuide().clearQuestObjective(p);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendTitle("§d§l✦ APPRENTISSAGE TERMINÉ", "§7Te voilà prêt pour le grand large", 15, 80, 25);
        p.sendMessage("§8§m                                        ");
        p.sendMessage("§d✦ §5§lL'Apprentissage est terminé §7(+§6" + (int) amount + "$§7) !");
        p.sendMessage("§fTu connais maintenant l'essentiel : miner, vendre, t'améliorer,");
        p.sendMessage("§ftes familiers, ta parcelle, et le voyage entre les îles.");
        p.sendMessage("§7Le reste, tu le découvriras en chemin. §fL'Écho Premier t'attend. §b🌊");
        p.sendMessage("§8§m                                        ");
    }

    // ── Détection des ouvertures de menus (étapes 5, 9, 10) ──────────────────────

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        int step = getStep(p);
        if (step == 0 || step >= LAST_STEP) return;
        String title = event.getView().getTitle();
        if (title == null) return;

        // Étape 5 : ouvrir le menu des familiers (/pets → « Œuf des Origines Bestiales »).
        if (step == 5 && title.contains("Œuf des Origines")) {
            reward(p, 5, "Les familiers des Abysses : des compagnons qui te renforcent.", OBJ_ACHETER);
            return;
        }
        // Étape 9 : ouvrir la boutique (/shop → titre « §6§lShop »).
        if (step == 9 && title.contains("Shop")) {
            reward(p, 9, "La boutique : achète des blocs quand tu en as besoin.", OBJ_CLASSEMENTS);
            return;
        }
        // Étape 10 : voir les classements. NOTE : /classements TÉLÉPORTE désormais vers la zone
        // buildée (plus de GUI) → la validation se fait via onClassementsOpened() depuis la commande.
    }

    /** Le joueur consulte les classements (commande /classements). Valide l'étape 10. */
    public void onClassementsOpened(Player p) {
        if (getStep(p) != 10) return;
        reward(p, 10, "Les classements : ta renommée grandit à chaque exploit.", OBJ_MINE);
    }

    /** Réaffiche le bon objectif de BossBar (texte + progression) à la connexion. */
    public void restoreObjective(Player p) {
        switch (getStep(p)) {
            case 1: {
                int m = counter(p, minedKey);
                plugin.getGuide().setQuestObjective(p, objMiner(m), (double) m / MINE_GOAL);
                break;
            }
            case 2: {
                int s = counter(p, soldKey);
                plugin.getGuide().setQuestObjective(p, objVendre(s), (double) s / SELL_GOAL);
                break;
            }
            case 3: {
                int n = counter(p, sacKey);
                plugin.getGuide().setQuestObjective(p, objSac(n), (double) n / SAC_GOAL);
                break;
            }
            case 4: {
                int n = counter(p, enchKey);
                plugin.getGuide().setQuestObjective(p, objEnch(n), (double) n / ENCH_GOAL);
                break;
            }
            case 5: plugin.getGuide().setQuestObjective(p, OBJ_PETS); break;
            case 6: plugin.getGuide().setQuestObjective(p, OBJ_ACHETER); break;
            case 7: plugin.getGuide().setQuestObjective(p, OBJ_EQUIP); break;
            case 8: plugin.getGuide().setQuestObjective(p, OBJ_PARCELLE); break;
            case 9: plugin.getGuide().setQuestObjective(p, OBJ_SHOP); break;
            case 10: plugin.getGuide().setQuestObjective(p, OBJ_CLASSEMENTS); break;
            case 11: plugin.getGuide().setQuestObjective(p, OBJ_MINE); break;
            default: break; // 0 (pas commencé) ou 12 (fini) : rien
        }
    }

    /** Réinitialise le parcours (outil de test). */
    public void reset(Player p) {
        setStep(p, 0);
        setCounter(p, minedKey, 0);
        setCounter(p, soldKey, 0);
        setCounter(p, sacKey, 0);
        setCounter(p, enchKey, 0);
    }
}
