package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;

/**
 * LA BOUSSOLE DE QUÊTE — le guidage vers les PNJ, dans la BossBar.
 *
 * <p>Problème d'origine : un nouveau joueur lit « Parle au Veilleur » et n'a aucune idée d'où
 * aller. La barre d'XP était écartée (elle affiche le niveau de pioche) et un hologramme visible
 * à 200 blocs est impossible ; la BossBar est donc le seul support qui reste.</p>
 *
 * <p>À chaque tick, pour les seuls joueurs ayant un objectif de quête ET une cible connue, on
 * réécrit le titre de la BossBar : <b>le texte de l'objectif est conservé</b> (le joueur doit
 * toujours savoir ce qu'il a à faire) et on lui ajoute une <b>flèche 8 directions</b> relative à
 * son regard, la <b>distance en mètres</b>, une <b>couleur rouge → verte</b> et une <b>barre qui
 * se remplit</b> à mesure qu'il approche.</p>
 *
 * <p>⚠ La décoration n'est écrite QUE dans la BossBar, via
 * {@link GuideManager#paintQuestBar}. L'objectif mémorisé reste le texte brut : sinon, à chaque
 * tick, la flèche et la distance s'empileraient sur elles-mêmes.</p>
 */
public class QuestCompass {

    private final PrivateMines plugin;

    public QuestCompass(PrivateMines plugin) {
        this.plugin = plugin;
    }

    /** Distance à laquelle la barre est vide. En deçà, elle se remplit jusqu'à l'arrivée. */
    private static final double PORTEE = 300.0;
    /** En deçà de cette distance, on annonce l'arrivée au lieu d'afficher une direction. */
    private static final double ARRIVEE = 5.0;

    // Flèches par secteur de 45°, dans l'ordre : devant, devant-droite, droite, derrière-droite,
    // derrière, derrière-gauche, gauche, devant-gauche.
    private static final String[] FLECHES = { "⬆", "↗", "➡", "↘", "⬇", "↙", "⬅", "↖" };

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    private void tick() {
        GuideManager guide = plugin.getGuide();
        if (guide == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Filtre le plus large en premier : sans objectif, on ne calcule rien du tout.
            // En pratique seuls les joueurs en pleine quête coûtent quelque chose.
            String objectif = guide.getQuestObjective(p.getUniqueId());
            if (objectif == null) continue;
            Location cible = cible(p, objectif);
            if (cible == null || cible.getWorld() == null) continue;
            if (!cible.getWorld().equals(p.getWorld())) continue;

            double dist = p.getLocation().distance(cible);
            double progression = Math.max(0.0, Math.min(1.0, 1.0 - dist / PORTEE));

            String suffixe;
            BarColor couleur;
            if (dist <= ARRIVEE) {
                suffixe = "  §a§l✔ Tu y es !";
                couleur = BarColor.GREEN;
                progression = 1.0;
            } else {
                couleur = dist > 150 ? BarColor.RED : dist > 50 ? BarColor.YELLOW : BarColor.GREEN;
                suffixe = "  §f§l" + fleche(p, cible) + " §7" + Math.round(dist) + " m";
            }
            guide.paintQuestBar(p, objectif + suffixe, progression, couleur);
        }
    }

    /**
     * La flèche à afficher : direction de la cible RELATIVE au regard du joueur.
     * ⬆ = tout droit, ➡ = à ta droite, ⬇ = derrière toi.
     */
    private String fleche(Player p, Location cible) {
        double dx = cible.getX() - p.getLocation().getX();
        double dz = cible.getZ() - p.getLocation().getZ();
        // Même convention que le yaw de Minecraft (cf. NpcManager.tickLook).
        double azimut = Math.toDegrees(Math.atan2(-dx, dz));
        // Écart ramené dans [-180, 180] : négatif = à gauche, positif = à droite.
        double ecart = ((azimut - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        int secteur = ((int) Math.round(ecart / 45.0) + 8) % 8;
        return FLECHES[secteur];
    }

    /**
     * Où doit aller le joueur, d'après l'objectif affiché.
     *
     * <p>Les objectifs des Actes I et II sont des constantes : on les compare directement.
     * Ceux de l'Arc II sont construits dynamiquement (le lieu suivant change), donc c'est
     * {@link LogPoseManager} qui sait où en est le joueur.</p>
     *
     * <p>Renvoie {@code null} si l'objectif n'a pas de cible physique : la BossBar reste
     * simplement telle quelle, sans boussole.</p>
     */
    private Location cible(Player p, String objectif) {
        ActeManager acte = plugin.getActe();
        NpcManager npc = plugin.getNpc();
        if (npc == null) return null;

        // ── Acte I : la Grève des Oubliés.
        if (ActeManager.OBJ_VEILLEUR.equals(objectif)) return npc.locationOfRole(NpcManager.ROLE_VEILLEUR);
        if (ActeManager.OBJ_ANCRE.equals(objectif))    return npc.locationOfRole(NpcManager.ROLE_ANCRE);
        if (ActeManager.OBJ_RETOUR.equals(objectif))   return npc.locationOfRole(NpcManager.ROLE_ANCRE);
        if (ActeManager.OBJ_COFFRE.equals(objectif))   return acte == null ? null : acte.getChestLocation();

        // ── Acte II : la Première Terre.
        if (ActeManager.OBJ2_CONTREMAITRE.equals(objectif)) return npc.locationOfRole(NpcManager.ROLE_CONTREMAITRE);
        if (ActeManager.OBJ2_FORGERON.equals(objectif))     return npc.locationOfRole(NpcManager.ROLE_FORGERON);
        if (ActeManager.OBJ2_TETE.equals(objectif))         return acte == null ? null : acte.getChest2Location();

        // ── Arc II : la visite d'Alabasta (texte dynamique → on demande au LogPose).
        if (plugin.getLogPose() != null && objectif.contains("Second Souffle")) {
            return plugin.getLogPose().cibleActuelle(p);
        }
        return null;
    }
}
