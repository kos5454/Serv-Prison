package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guide pour débutants : une BossBar en haut de l'écran qui affiche des astuces
 * tournant toutes les 15 secondes. Le joueur peut la masquer/réafficher avec /guide.
 * La préférence (masqué ou non) est persistée dans le data.yml du plugin.
 *
 * Conçu pour accueillir plus tard un système de quêtes (la barre pourra afficher
 * un objectif en cours au lieu d'une simple astuce).
 */
public class GuideManager {

    private final PrivateMines plugin;

    // BossBar active de chaque joueur connecté.
    private final Map<UUID, BossBar> bars = new HashMap<>();
    // Joueurs ayant choisi de masquer le guide (préférence persistée).
    private final Set<UUID> hidden = new HashSet<>();

    // Joueurs pour qui la barre d'astuces est temporairement masquée (ex : BossBar OneBlock
    // active sur l'Île). NON persisté : ce n'est pas une préférence, juste un état contextuel.
    private final Set<UUID> suppressed = new HashSet<>();

    // Objectif de quête affiché en priorité (pendant l'Acte I). Tant qu'un joueur a un
    // objectif ici, les astuces ne tournent PAS pour lui : la barre reste figée sur l'objectif.
    private final Map<UUID, String> questObjective = new HashMap<>();

    // Index de l'astuce affichée pour chaque joueur (pour faire tourner sans tous les synchroniser).
    private final Map<UUID, Integer> tipIndex = new HashMap<>();

    // Astuces affichées à tour de rôle (après l'Apprentissage). Faciles à compléter plus tard.
    private static final String[] TIPS = {
        "§eFais §f/mine §epour aller miner, et débloque de §6nouvelles îles §equand tu peux te les offrir !",
        "§eVends tes blocs : ton sac se vide tout seul. Surveille ton §6argent §eavec §f/money§e.",
        "§eOuvre le §f/shop §epour acheter des blocs, puis construis avec sur ton §f/ob §e(ton Île).",
        "§eRange tes blocs achetés dans ton sac avec §f/bp §e(sortable seulement sur ton Île).",
        "§eTon Île est à toi : §f/ob §epour t'y téléporter, §6construire §eet inviter des amis.",
        "§eInvite un ami sur ton Île : §f/ob friend <joueur>§e, puis il fait §f/ob accept§e.",
        "§eGrimpe dans le classement des joueurs avec §f/classements §e(alias §f/top§e).",
        "§eVois le prix exact de tes blocs (bonus compris) avec §f/blockvalue §e(alias §f/bv§e).",
        "§eOuvre §f/pets §epour obtenir des §dfamiliers §equi boostent tes gains !",
        "§eÉquipe un familier dans §f/pets §7» §f⚔ Équipement §epour activer ses bonus.",
        "§eAméliore ton §6sac §e(capacité + vente) en cliquant dessus dans la mine.",
        "§eEnrichis ta §6pioche §e: monte ses enchants pour miner plus vite et gagner plus.",
        "§eSouhaite la bienvenue aux nouveaux avec §f/bvn §epour un petit bonus !",
        "§eRevois ton objectif à tout moment avec §f/quest§e.",
        "§eMasque ou réaffiche ce guide quand tu veux avec §f/guide§e.",
        "§eSi la mine n'est pas entièrement générée, refais §f/mine §epour la régénérer.",
    };

    // Durée d'affichage d'une astuce avant rotation : 15 secondes = 300 ticks.
    private static final long ROTATE_TICKS = 15L * 20L;

    public GuideManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Marque (en mémoire) qu'un joueur a masqué/affiché le guide. Appelé au chargement des données.
    public void setHidden(UUID id, boolean isHidden) {
        if (isHidden) hidden.add(id); else hidden.remove(id);
    }

    public boolean isHidden(UUID id) {
        return hidden.contains(id);
    }

    /**
     * Masque (true) ou rétablit (false) TEMPORAIREMENT la barre d'astuces d'un joueur,
     * sans toucher à sa préférence /guide. Utilisé quand une autre BossBar (OneBlock sur
     * l'Île) doit prendre la place. En rétablissant, on réaffiche la barre d'astuces.
     */
    public void setSuppressed(Player player, boolean on) {
        UUID id = player.getUniqueId();
        if (on) {
            suppressed.add(id);
            BossBar bar = bars.get(id);
            if (bar != null) bar.setVisible(false);
        } else {
            suppressed.remove(id);
            // Réaffiche la barre d'astuces (sauf si le joueur l'a masquée via /guide).
            show(player);
        }
    }

    // Affiche la BossBar à un joueur (sauf s'il l'a masquée). Appelé à la connexion.
    public void show(Player player) {
        UUID id = player.getUniqueId();
        if (hidden.contains(id)) return;
        if (suppressed.contains(id)) return; // barre OneBlock active : on ne montre pas les astuces
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            bars.put(id, bar);
        }
        tipIndex.putIfAbsent(id, 0);
        // Si le joueur a un objectif de quête en cours (Acte I), on l'affiche en priorité.
        String obj = questObjective.get(id);
        if (obj != null) {
            bar.setColor(BarColor.BLUE);
            bar.setTitle(obj);
        } else {
            bar.setColor(BarColor.YELLOW);
            bar.setTitle(currentTip(id));
            bar.setProgress(1.0);
        }
        bar.addPlayer(player);
        bar.setVisible(true);
    }

    // Retire et oublie la BossBar d'un joueur. Appelé à la déconnexion.
    public void remove(Player player) {
        UUID id = player.getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        tipIndex.remove(id);
        suppressed.remove(id);
    }

    // Bascule l'affichage du guide pour un joueur (commande /guide). Renvoie true si désormais visible.
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (hidden.contains(id)) {
            hidden.remove(id);
            show(player);
            return true;
        } else {
            hidden.add(id);
            BossBar bar = bars.get(id);
            if (bar != null) bar.setVisible(false);
            return false;
        }
    }

    // Astuce courante d'un joueur.
    private String currentTip(UUID id) {
        int i = tipIndex.getOrDefault(id, 0) % TIPS.length;
        return TIPS[i];
    }

    // ===== Objectif de quête (Acte I) =====

    // Fige la BossBar d'un joueur sur un objectif de quête (couleur bleue). Écrase l'astuce en cours.
    // Tant qu'un objectif est actif, la rotation d'astuces le laisse tranquille.
    public void setQuestObjective(Player player, String text) {
        setQuestObjective(player, text, 1.0);
    }

    // Variante avec une progression (0.0 à 1.0) : la barre se remplit selon l'avancement de l'objectif.
    public void setQuestObjective(Player player, String text, double progress) {
        UUID id = player.getUniqueId();
        questObjective.put(id, text);
        if (hidden.contains(id)) return;
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            bars.put(id, bar);
            bar.addPlayer(player);
        }
        bar.setColor(BarColor.BLUE);
        bar.setTitle(text);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    /**
     * Réécrit UNIQUEMENT la BossBar (titre, progression, couleur) sans toucher à l'objectif
     * mémorisé. C'est ce qu'utilise la boussole de quête ({@link QuestCompass}) : elle redécore
     * la barre à chaque tick à partir du texte BRUT, qui doit donc rester intact — sinon la
     * flèche et la distance s'empileraient sur elles-mêmes à l'infini.
     */
    public void paintQuestBar(Player player, String title, double progress, BarColor color) {
        UUID id = player.getUniqueId();
        if (!questObjective.containsKey(id) || hidden.contains(id)) return;
        BossBar bar = bars.get(id);
        if (bar == null) return;
        bar.setTitle(title);
        bar.setColor(color);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    // Met à jour SEULEMENT la progression de la barre d'objectif (sans changer le texte).
    public void setQuestProgress(Player player, double progress) {
        UUID id = player.getUniqueId();
        if (!questObjective.containsKey(id) || hidden.contains(id)) return;
        BossBar bar = bars.get(id);
        if (bar != null) bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    // Un joueur a-t-il un objectif de quête en cours ?
    public boolean hasQuestObjective(UUID id) {
        return questObjective.containsKey(id);
    }

    /** Texte de l'objectif de quête courant (celui affiché dans la BossBar), ou null si aucun. */
    public String getQuestObjective(UUID id) {
        return questObjective.get(id);
    }

    // Termine l'objectif de quête : la barre repasse en mode astuces (jaune) dès la prochaine rotation.
    public void clearQuestObjective(Player player) {
        UUID id = player.getUniqueId();
        questObjective.remove(id);
        if (hidden.contains(id)) return;
        BossBar bar = bars.get(id);
        if (bar != null) {
            bar.setColor(BarColor.YELLOW);
            bar.setTitle(currentTip(id));
            bar.setProgress(1.0); // barre pleine en mode astuces
            bar.setVisible(true);
        }
    }

    // Lance la rotation des astuces : avance l'astuce de chaque joueur visible toutes les 15s.
    public void startTipTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                if (hidden.contains(id)) continue;
                if (suppressed.contains(id)) continue; // barre OneBlock active sur l'Île
                // Un objectif de quête (Acte I) est prioritaire : on ne fait pas tourner les astuces.
                if (questObjective.containsKey(id)) continue;
                BossBar bar = bars.get(id);
                if (bar == null) { show(player); continue; }
                int next = (tipIndex.getOrDefault(id, 0) + 1) % TIPS.length;
                tipIndex.put(id, next);
                bar.setTitle(currentTip(id));
                bar.setProgress(1.0);
                bar.setVisible(true);
            }
        }, ROTATE_TICKS, ROTATE_TICKS);
    }
}
