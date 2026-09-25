package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Boîte de dialogue immersive (faite maison, sans plugin externe).
 *
 * Affiche dans le chat un cadre stylé avec le nom du PNJ, puis fait défiler ses répliques
 * une par une (intervalle régulier, son discret à chaque ligne), et exécute un callback
 * une fois la dernière réplique affichée (pour faire avancer la quête).
 *
 * Un joueur ne peut pas lancer deux dialogues en même temps (anti-spam de clic droit).
 */
public class DialogueManager {

    private final PrivateMines plugin;

    // Joueurs ayant un dialogue en cours (empêche de relancer / superposer).
    private final Set<UUID> talking = new HashSet<>();

    // Facteur de vitesse du dialogue en cours par joueur (1.0 = normal ; 1.6 = 60% plus lent).
    // Permet de ralentir CERTAINS PNJ (ex : le Dernier Témoin, qui a de longues tirades) sans
    // toucher au rythme des autres.
    private final java.util.Map<UUID, Double> speed = new java.util.HashMap<>();

    // Intervalle par défaut entre deux répliques (en ticks). 20t = 1s, donc 100t = 5s.
    private static final long LINE_DELAY = 100L;

    // Bornes du délai calculé sur la longueur du texte (en ticks).
    private static final long MIN_DELAY = 50L;   // 2,5 s : plancher pour une phrase très courte / didascalie
    private static final long MAX_DELAY = 180L;  // 9 s   : plafond pour une longue tirade
    private static final double TICKS_PER_CHAR = 0.9; // ~temps de lecture par caractère visible

    /**
     * Délai adapté à la LONGUEUR de la réplique qu'on vient d'afficher : une phrase courte de 7 mots
     * n'attend que ~2,5 s, une longue tirade jusqu'à ~9 s. On compte les caractères VISIBLES (on retire
     * les codes couleur §x). Borné entre {@link #MIN_DELAY} et {@link #MAX_DELAY}.
     */
    private static long delayForLine(String line) {
        if (line == null) return LINE_DELAY;
        int visible = line.replaceAll("§.", "").trim().length();
        long d = Math.round(visible * TICKS_PER_CHAR);
        return Math.max(MIN_DELAY, Math.min(MAX_DELAY, d));
    }

    // Applique le facteur de vitesse du joueur à un délai de base (min 20t = 1s de garde-fou).
    private long scaled(UUID id, long baseTicks) {
        double f = speed.getOrDefault(id, 1.0);
        return Math.max(20L, Math.round(baseTicks * f));
    }

    public DialogueManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    public boolean isTalking(Player p) {
        return talking.contains(p.getUniqueId());
    }

    /**
     * Lance un dialogue immersif.
     *
     * @param player   le joueur
     * @param npcName  nom coloré du PNJ (ex "§bLe Veilleur")
     * @param accent   code couleur d'accent pour le cadre (ex "§b")
     * @param lines    les répliques (déjà colorées avec §)
     * @param onFinish action exécutée après la dernière réplique (peut être null)
     */
    public void startDialogue(Player player, String npcName, String accent,
                              String[] lines, Runnable onFinish) {
        startDialogue(player, npcName, accent, lines, onFinish, 1.0);
    }

    /**
     * Variante avec un facteur de vitesse : {@code speedFactor} > 1 ralentit le défilé des répliques
     * (ex : 1.6 → chaque attente est 60% plus longue), pour les PNJ aux longues tirades.
     */
    public void startDialogue(Player player, String npcName, String accent,
                              String[] lines, Runnable onFinish, double speedFactor) {
        UUID id = player.getUniqueId();
        if (talking.contains(id)) return;      // dialogue déjà en cours
        if (lines == null || lines.length == 0) {
            if (onFinish != null) onFinish.run();
            return;
        }
        talking.add(id);
        speed.put(id, speedFactor <= 0 ? 1.0 : speedFactor);

        // Cadre d'ouverture : ligne pleine + nom du PNJ.
        String bar = accent + "§m                                        ";
        player.sendMessage(bar);
        player.sendMessage("  " + npcName);
        player.sendMessage("§8§m                                        ");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);

        // Fait défiler les répliques une par une.
        scheduleLine(player, npcName, accent, lines, 0, onFinish);
    }

    // Affiche la réplique n° index, puis planifie la suivante (ou clôt le dialogue).
    private void scheduleLine(Player player, String npcName, String accent,
                              String[] lines, int index, Runnable onFinish) {
        long base = (index == 0) ? 10L : LINE_DELAY; // la 1re réplique arrive vite (le cadre vient de s'ouvrir)
        scheduleLineDelayed(player, npcName, accent, lines, index, onFinish, scaled(player.getUniqueId(), base));
    }

    private void scheduleLineDelayed(Player player, String npcName, String accent,
                              String[] lines, int index, Runnable onFinish, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID id = player.getUniqueId();
            if (!player.isOnline()) { talking.remove(id); speed.remove(id); return; }

            // Réplique : ligne vide (pour aérer la lecture) puis « > <texte> ».
            player.sendMessage("");
            player.sendMessage(accent + "» " + lines[index]);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 1.6f);

            if (index + 1 < lines.length) {
                // Le délai avant la réplique suivante dépend de la LONGUEUR de celle qu'on vient d'afficher.
                scheduleLineDelayed(player, npcName, accent, lines, index + 1, onFinish,
                        scaled(id, delayForLine(lines[index])));
            } else {
                // Dernière réplique : ferme le cadre après un délai proportionnel à sa longueur + callback.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(accent + "§m                                        ");
                    }
                    talking.remove(id);
                    speed.remove(id);
                    if (onFinish != null && player.isOnline()) onFinish.run();
                }, scaled(id, delayForLine(lines[index])));
            }
        }, delay);
    }
}
