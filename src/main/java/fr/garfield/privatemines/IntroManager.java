package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * ACTE I — « L'Éveil sur la Grève des Oubliés ».
 *
 * Gère la scène d'ouverture (les ~20 premières secondes) jouée à la TOUTE PREMIÈRE
 * connexion d'un joueur :
 *   1. écran assombri très court (BLINDNESS ~1s),
 *   2. title / subtitle « L'ÉVEIL »,
 *   3. son d'ambiance de vague,
 *   4. deux lignes de lore dans le chat (espacées),
 *   5. BossBar de guidage figée sur l'objectif « Parle au Veilleur ».
 *
 * On mémorise dans le PersistentDataContainer du joueur qu'il a déjà vu l'intro,
 * pour ne JAMAIS la rejouer (sauf commande admin de test /intro).
 *
 * Le reste de l'Acte I (dialogues des PNJ, coffre partagé, retour à l'Ancre) sera
 * ajouté ensuite ; cette classe ne fait que l'ouverture.
 */
public class IntroManager {

    private final PrivateMines plugin;

    // Clé PDC : marque un joueur ayant déjà vu la cinématique d'ouverture.
    private final NamespacedKey introSeenKey;

    // Objectif affiché dans la BossBar au tout début de l'Acte I.
    public static final String OBJECTIVE_TALK_VEILLEUR =
            "§b⚓ La Grève des Oubliés §7— §fParle au Veilleur";

    public IntroManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.introSeenKey = new NamespacedKey(plugin, "intro_seen");
    }

    // Un joueur a-t-il déjà vu la cinématique d'ouverture ?
    public boolean hasSeenIntro(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.has(introSeenKey, PersistentDataType.BYTE);
    }

    // Marque le joueur comme ayant vu l'intro (persistant : survit à la reconnexion).
    private void markIntroSeen(Player player) {
        player.getPersistentDataContainer().set(introSeenKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Joue la scène d'ouverture à un joueur : écran sombre, titre, son, lore, BossBar.
     * @param player     le joueur
     * @param markAsSeen true à la 1re connexion (mémorise pour ne pas rejouer) ;
     *                   false pour un simple test admin (ne touche pas au flag).
     */
    public void playIntro(Player player, boolean markAsSeen) {
        if (markAsSeen) markIntroSeen(player);
        if (!player.isOnline()) return;

        // 1) Écran assombri très court (~1s) : effet BLINDNESS de 25 ticks.
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));

        // 3) Son d'ambiance : la vague qui se retire (joué tout de suite, discret).
        player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 0.8f, 1f);

        // 2) Title / Subtitle « L'ÉVEIL » après ~20 ticks (le temps que l'écran soit sombre).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // fadeIn=20, stay=70, fadeOut=30 ticks.
            player.sendTitle("§b§lL'ÉVEIL", "§7Tu ne te souviens de rien...", 20, 70, 30);
            player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 0.6f, 0.8f);
        }, 20L);

        // 4) Deux lignes de lore dans le chat (effet « pensée qui remonte »), espacées.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§8» §7La mer t'a tout pris. Ton nom. Ton passé. Tout.");
        }, 90L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§8» §7Tu n'as plus que le sable sous les mains. Quelqu'un marche vers toi le long de l'eau.");
        }, 150L);

        // 5) BossBar figée sur l'objectif « Parle au Veilleur » (bleue), après le fondu.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getGuide().setQuestObjective(player, OBJECTIVE_TALK_VEILLEUR);
        }, 210L);
    }
}
