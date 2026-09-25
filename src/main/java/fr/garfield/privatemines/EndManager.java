package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'End : event quotidien du Dragon du Vide (/warp → End).
 *
 * <p>Chaque jour à {@value #HEURE_SPAWN}h (heure réelle du serveur), un dragon <b>×5 vie</b> apparaît
 * sur l'île centrale de l'End. Les joueurs y accèdent par le menu {@code /warp} → salle d'attente
 * (coords {@link #SALLE}). Le <b>PvP</b> et la <b>perte de stuff</b> y sont actifs (déjà le cas hors
 * parcelle, cf {@link ParcelleListener}).
 *
 * <p>Règles :
 * <ul>
 *   <li>spawn auto à 20h ; si un ancien dragon traîne, il est supprimé et remplacé (jamais 2) ;</li>
 *   <li>les dégâts de chaque joueur sont suivis ; à la mort, le loot va au <b>Top 3</b>
 *       (50/30/20 %, renormalisé s'il y a moins de 3 contributeurs — jamais gaspillé) ;</li>
 *   <li>loot = argent + clés de crate + œuf/XP vanilla (le butin d'objets custom viendra plus tard) ;</li>
 *   <li>les blocs posés par les joueurs dans l'End sont effacés au dragon suivant.</li>
 * </ul>
 *
 * <p>Persistance dans {@code end.yml} (blocs posés à nettoyer, survit à un redémarrage).
 */
public class EndManager implements Listener {

    public static final String END_WORLD = "world_the_end";
    public static final int HEURE_SPAWN = 20; // 20h heure réelle

    // Salle d'attente (point de TP du /warp → End et /end).
    public static final int SALLE_X = -325, SALLE_Y = 225, SALLE_Z = -366;
    // Zone SAFE (pas de PvP) : sphère de rayon 30 blocs autour de la salle d'attente.
    public static final double SAFE_RAYON = 30.0;
    private static final double SAFE_RAYON2 = SAFE_RAYON * SAFE_RAYON; // au carré (évite sqrt)
    // Île centrale de l'End : le portail de sortie / spawn du dragon.
    public static final int ILE_X = 0, ILE_Y = 65, ILE_Z = 0;

    // Dragon ×5 vie (vanilla = 200).
    public static final double DRAGON_HP = 1000.0;

    // Loot Top 3 (parts avant renormalisation).
    private static final double[] PARTS = {0.50, 0.30, 0.20};
    // Récompenses TOTALES (réparties selon les parts). À caler après tests.
    public static final BigInteger LOOT_ARGENT_TOTAL = BigInteger.valueOf(500_000);
    public static final int LOOT_CLES_TOTAL = 6;               // 6 clés réparties (ex. 3/2/1)
    public static final String LOOT_CLE_RANG = "rare";         // rang de crate distribué

    private final PrivateMines plugin;
    private java.io.File file;
    private org.bukkit.configuration.file.YamlConfiguration config;

    // Dragon de l'event courant (null si aucun en cours).
    private UUID dragonCourant = null;
    private BossBar bossBar = null;
    // Dégâts cumulés par joueur sur le dragon courant (remis à zéro à chaque spawn).
    private final Map<UUID, Double> degats = new LinkedHashMap<>();
    // Blocs posés par les joueurs dans l'End (à effacer au prochain dragon). "x,y,z".
    private final java.util.Set<String> blocsPoses = new java.util.LinkedHashSet<>();
    // Aléatoire pour l'IA du dragon (choix de phase d'attaque). Pas de besoin cryptographique.
    private final java.util.Random rng = new java.util.Random();
    // Tag de combat (UUID → timestamp de fin) : anti déco-combat & anti fuite par commande.
    private final Map<UUID, Long> combatTag = new java.util.HashMap<>();
    // Durée du tag de combat, rechargée à chaque coup PvP.
    private static final long COMBAT_MS = 15_000L;
    // Commandes de FUITE interdites tant qu'on est en combat (téléportations).
    private static final java.util.Set<String> CMDS_FUITE = new java.util.HashSet<>(java.util.Arrays.asList(
            "spawn", "warp", "warps", "end", "is", "island", "mine", "mines", "parcelle", "ob", "oneblock",
            "tpa", "tpaccept", "tpahere", "tp", "home", "homes", "back", "rtp", "wild", "hub", "lobby"));

    public EndManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Accès (appelé par WarpMenu)
    // ══════════════════════════════════════════════════════════════════════════

    /** TP le joueur dans la salle d'attente de l'End + rappel des règles. */
    public void teleporterSalle(Player p) {
        World end = Bukkit.getWorld(END_WORLD);
        if (end == null) {
            p.sendMessage("§cL'End n'est pas disponible pour le moment.");
            return;
        }
        Location salle = new Location(end, SALLE_X + 0.5, SALLE_Y, SALLE_Z + 0.5, 0f, 0f);
        p.teleport(salle);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        p.sendMessage("§8§m                                        ");
        p.sendMessage("§5§l🐉 L'END — L'Antre du Vide");
        p.sendMessage("§7Le §5Dragon du Vide §7apparaît §fchaque jour à " + HEURE_SPAWN + "h§7.");
        p.sendMessage("§c⚠ Ici tu §lPERDS TON STUFF §c§len mourant§c.");
        p.sendMessage("§a✔ Cette salle est une §lzone sûre §a: pas de PvP ici.");
        p.sendMessage("§c⚔ Mais dès que tu sors combattre, le §lPvP est ACTIVÉ§c.");
        p.sendMessage("§7Le loot du dragon va au §fTop 3 §7des dégâts (50/30/20 %).");
        p.sendMessage("§8§m                                        ");
    }

    // Rayon de dispersion du PNJ « Passeur du Vide » (rôle end) autour du centre 0,*,0.
    public static final double ARENE_RAYON = 75.0;

    /**
     * TP le joueur dans l'ARÈNE de l'End (combat du dragon), à une position aléatoire dans un
     * rayon de {@value #ARENE_RAYON} blocs autour du centre (0, *, 0). Utilisé par le PNJ « end ».
     */
    public void teleporterArene(Player p) {
        World end = Bukkit.getWorld(END_WORLD);
        if (end == null) {
            p.sendMessage("§cL'End n'est pas disponible pour le moment.");
            return;
        }
        // Pas de dragon en cours → l'accès à l'arène est fermé (on n'y va que pour le combat).
        if (!dragonEnVie()) {
            p.sendMessage("§5§l🐉 §7Le Vide est calme... §fLe Dragon du Vide n'est pas là.");
            p.sendMessage("§7Reviens quand il §5rugira §7— chaque jour à §f" + HEURE_SPAWN + "h§7.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Point aléatoire dans le disque de rayon ARENE_RAYON autour de (0,0) en X/Z.
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rng.nextDouble()) * ARENE_RAYON; // sqrt → répartition uniforme sur le disque
        double x = Math.cos(angle) * dist;
        double z = Math.sin(angle) * dist;
        // Hauteur : on part haut et on laisse tomber sur le 1er bloc solide (sinon vide de l'End).
        Location cible = new Location(end, x, ILE_Y + 10, z);
        cible = solAuDessous(end, cible);
        p.teleport(cible);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        p.sendMessage("§5§l🐉 §7Tu es projeté dans l'§5arène du Vide§7. §c⚔ PvP + perte de stuff !");
    }

    // Descend depuis loc jusqu'au 1er bloc solide (pour ne pas TP dans le vide de l'End).
    private Location solAuDessous(World end, Location loc) {
        int x = loc.getBlockX(), z = loc.getBlockZ();
        for (int y = Math.min(255, ILE_Y + 20); y > 0; y--) {
            org.bukkit.block.Block b = end.getBlockAt(x, y, z);
            if (b.getType().isSolid()) {
                return new Location(end, x + 0.5, y + 1, z + 0.5);
            }
        }
        // Rien trouvé sous ce point : on retombe sur l'île centrale (toujours solide).
        return new Location(end, ILE_X + 0.5, ILE_Y, ILE_Z + 0.5);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Planification 20h
    // ══════════════════════════════════════════════════════════════════════════

    /** Planifie le spawn quotidien à 20h : 1er délai jusqu'au prochain 20h, puis toutes les 24h. */
    public void startDailyTask() {
        configurerMonde();
        long delaiTicks = ticksJusquA(HEURE_SPAWN);
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnDragonEvent,
                delaiTicks, 20L * 3600 * 24); // répète toutes les 24h
        plugin.getLogger().info("Dragon de l'End : prochain spawn dans " + (delaiTicks / 20 / 60) + " min.");
    }

    /**
     * Force les règles du monde de l'End : perte de stuff garantie (keepInventory = false),
     * quelle que soit la gamerule enregistrée dans level.dat. Le monde peut ne pas être encore
     * chargé au démarrage → on réessaie après un court délai.
     */
    private void configurerMonde() {
        Runnable r = () -> {
            World end = Bukkit.getWorld(END_WORLD);
            if (end == null) return;
            end.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false); // le joueur DROP son stuff à la mort
            plugin.getLogger().info("End : keepInventory forcé à false (perte de stuff active).");
        };
        r.run();
        Bukkit.getScheduler().runTaskLater(plugin, r, 200L); // re-tente à +10 s si le monde n'était pas prêt
    }

    /** Nombre de ticks depuis maintenant jusqu'au prochain passage à l'heure donnée (heure locale serveur). */
    private long ticksJusquA(int heureCible) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar cible = (java.util.Calendar) now.clone();
        cible.set(java.util.Calendar.HOUR_OF_DAY, heureCible);
        cible.set(java.util.Calendar.MINUTE, 0);
        cible.set(java.util.Calendar.SECOND, 0);
        cible.set(java.util.Calendar.MILLISECOND, 0);
        if (!cible.after(now)) cible.add(java.util.Calendar.DAY_OF_MONTH, 1); // déjà passé aujourd'hui → demain
        long deltaMs = cible.getTimeInMillis() - now.getTimeInMillis();
        return Math.max(20L, deltaMs / 50L); // 50 ms par tick
    }

    /** Millisecondes d'ici au prochain passage à l'heure donnée (heure locale serveur). */
    private long msJusquA(int heureCible) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar cible = (java.util.Calendar) now.clone();
        cible.set(java.util.Calendar.HOUR_OF_DAY, heureCible);
        cible.set(java.util.Calendar.MINUTE, 0);
        cible.set(java.util.Calendar.SECOND, 0);
        cible.set(java.util.Calendar.MILLISECOND, 0);
        if (!cible.after(now)) cible.add(java.util.Calendar.DAY_OF_MONTH, 1);
        return cible.getTimeInMillis() - now.getTimeInMillis();
    }

    /** Vrai si le dragon de l'event est actuellement en vie. */
    public boolean dragonEnVie() { return dragonCourant != null; }

    /**
     * Ligne à afficher dans l'hologramme de l'End : si le dragon est vivant → « LE DRAGON EST LÀ ! » ;
     * sinon → décompte (Xh Ymin Zs) avant le prochain spawn de 20h. Se recalcule à chaque appel,
     * donc dès que le dragon meurt, la ligne rebascule automatiquement sur le décompte.
     */
    public String texteTimerDragon() {
        if (dragonEnVie()) return "§a§l⚔ LE DRAGON EST LÀ ! §7Rejoins le combat !";
        long ms = msJusquA(HEURE_SPAWN);
        long s = ms / 1000;
        long h = s / 3600, min = (s % 3600) / 60, sec = s % 60;
        String reste;
        if (h > 0) reste = h + "h " + min + "min " + sec + "s";
        else if (min > 0) reste = min + "min " + sec + "s";
        else reste = sec + "s";
        return "§d🐉 Prochain Dragon dans : §f" + reste;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Spawn du dragon
    // ══════════════════════════════════════════════════════════════════════════

    /** Fait apparaître le dragon de l'event (supprime l'ancien s'il traîne, reset dégâts + blocs). */
    public void spawnDragonEvent() {
        World end = Bukkit.getWorld(END_WORLD);
        if (end == null) { plugin.getLogger().warning("Dragon de l'End : monde " + END_WORLD + " introuvable."); return; }

        // 1) Supprime TOUT dragon ET TOUT cristal existant (sinon ils s'accumulent à chaque
        //    spawn : Minecraft régénère les cristaux des piliers pour chaque nouveau dragon).
        //    Résultat : jamais 2 dragons, et un seul jeu de cristaux à la fois.
        for (Entity e : end.getEntities()) {
            if (e instanceof EnderDragon || e.getType() == org.bukkit.entity.EntityType.END_CRYSTAL) {
                e.remove();
            }
        }
        retirerBossBar();

        // 2) Nettoie les blocs posés par les joueurs lors de l'event précédent
        //    + referme le portail de sortie (et son œuf) généré à la mort du dragon précédent.
        nettoyerBlocsPoses(end);
        nettoyerPortailSortie(end);

        // 3) Reset du suivi des dégâts.
        degats.clear();
        dragonCourant = null;

        // 4) Charge le chunk de l'île puis fait apparaître le dragon ×5 vie.
        Location spawn = new Location(end, ILE_X + 0.5, ILE_Y, ILE_Z + 0.5);
        end.getChunkAt(spawn).load();
        EnderDragon dragon = (EnderDragon) end.spawnEntity(spawn, org.bukkit.entity.EntityType.ENDER_DRAGON);
        try {
            if (dragon.getAttribute(Attribute.MAX_HEALTH) != null) {
                dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(DRAGON_HP);
            }
            dragon.setHealth(DRAGON_HP);
        } catch (Throwable ignored) {}
        dragon.setCustomName("§5§lDragon du Vide");
        dragon.setCustomNameVisible(false);
        dragonCourant = dragon.getUniqueId();

        // 5) Barre de boss : on réutilise la barre NATIVE du dragon (renommée), plutôt qu'une
        //    barre custom — ainsi il n'y a jamais qu'UNE seule barre, qui suit la vie du dragon
        //    et disparaît toute seule à sa mort. Évite tout empilement de barres.
        bossBar = null;
        try {
            org.bukkit.boss.DragonBattle battle = dragon.getDragonBattle();
            if (battle != null && battle.getBossBar() != null) {
                bossBar = battle.getBossBar();
                bossBar.setTitle("§5§l🐉 Dragon du Vide");
                bossBar.setColor(BarColor.PURPLE);
                bossBar.setProgress(1.0); // nouveau dragon plein → barre à 100 % (sinon reste sur l'ancien état)
            }
        } catch (Throwable ignored) {}

        // 6) IA agressive : un dragon spawné manuellement reste bloqué en vol circulaire
        //    (CIRCLING) car il n'a pas de « dragon battle » qui cycle ses phases. On le
        //    pousse donc régulièrement à attaquer les joueurs présents.
        demarrerIaAgressive();
        demarrerSuiviBarre();

        // 7) Annonce serveur.
        Bukkit.broadcastMessage("§8§m                                        ");
        Bukkit.broadcastMessage("§5§l🐉 LE DRAGON DU VIDE VIENT D'APPARAÎTRE !");
        Bukkit.broadcastMessage("§7Rejoins le combat : §f/warp §7→ §5End§7. §c⚔ PvP + perte de stuff.");
        Bukkit.broadcastMessage("§7Le §fTop 3 §7des dégâts se partage le butin.");
        Bukkit.broadcastMessage("§8§m                                        ");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        save();
    }

    // Tâche rapide (0,5 s) : garde la barre de boss synchronisée sur la VRAIE vie du dragon.
    // La barre native peut rester « collée » à l'ancien état après un /end forcedragon ;
    // on la recalcule donc en continu (vie / vie max). S'arrête à la mort du dragon.
    private void demarrerSuiviBarre() {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (dragonCourant == null || bossBar == null) { task.cancel(); return; }
            World end = Bukkit.getWorld(END_WORLD);
            if (end == null) { task.cancel(); return; }
            EnderDragon dragon = null;
            for (Entity e : end.getEntities()) {
                if (e instanceof EnderDragon && e.getUniqueId().equals(dragonCourant)) { dragon = (EnderDragon) e; break; }
            }
            if (dragon == null || dragon.isDead()) { task.cancel(); return; }
            double max = dragon.getAttribute(Attribute.MAX_HEALTH) != null
                    ? dragon.getAttribute(Attribute.MAX_HEALTH).getValue() : DRAGON_HP;
            if (max > 0) bossBar.setProgress(Math.max(0, Math.min(1, dragon.getHealth() / max)));
        }, 10L, 10L); // toutes les 0,5 s
    }

    // Tâche qui rend le dragon agressif : alterne charge des joueurs et souffle, plutôt que
    // de le laisser tourner à l'infini au-dessus du portail. S'arrête quand le dragon meurt.
    private void demarrerIaAgressive() {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            // Le dragon n'existe plus (mort/supprimé) → on arrête la tâche.
            if (dragonCourant == null) { task.cancel(); return; }
            World end = Bukkit.getWorld(END_WORLD);
            if (end == null) { task.cancel(); return; }
            EnderDragon dragon = null;
            for (Entity e : end.getEntities()) {
                if (e instanceof EnderDragon && e.getUniqueId().equals(dragonCourant)) { dragon = (EnderDragon) e; break; }
            }
            if (dragon == null || dragon.isDead()) { task.cancel(); return; }

            // Y a-t-il des joueurs à combattre dans l'End ?
            java.util.List<Player> joueurs = end.getPlayers();
            if (joueurs.isEmpty()) return; // personne : on laisse le dragon planer tranquille

            // On alterne : soit il charge un joueur, soit il crache son souffle.
            EnderDragon.Phase phase = dragon.getPhase();
            if (phase == EnderDragon.Phase.CIRCLING || phase == EnderDragon.Phase.HOVER) {
                // 60 % charge un joueur, 40 % souffle.
                if (rng.nextInt(100) < 60) dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
                else dragon.setPhase(EnderDragon.Phase.STRAFING);
            }
        }, 100L, 120L); // 1re passe à 5 s, puis toutes les 6 s
    }

    private void nettoyerBlocsPoses(World end) {
        int n = 0;
        for (String s : new ArrayList<>(blocsPoses)) {
            try {
                String[] parts = s.split(",");
                int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
                end.getBlockAt(x, y, z).setType(Material.AIR, false);
                n++;
            } catch (Exception ignored) {}
        }
        blocsPoses.clear();
        if (n > 0) plugin.getLogger().info("Dragon de l'End : " + n + " blocs posés effacés.");
    }

    /**
     * Supprime l'ŒUF de dragon vanilla que Minecraft pose au sommet du portail de sortie à la
     * mort du dragon. Seul notre loot (1/10 au Top 1) doit donner des œufs. On balaie la colonne
     * au-dessus du centre (0,*,0), là où l'œuf se pose (sur la bedrock du portail).
     */
    private void supprimerOeufVanilla() {
        World end = Bukkit.getWorld(END_WORLD);
        if (end == null) return;
        // L'œuf se trouve pile au-dessus du portail, sur l'axe (0,*,0). On balaie largement en Y.
        for (int y = 60; y <= 100; y++) {
            org.bukkit.block.Block b = end.getBlockAt(ILE_X, y, ILE_Z);
            if (b.getType() == Material.DRAGON_EGG) {
                b.setType(Material.AIR, false);
                plugin.getLogger().info("End : œuf de dragon vanilla supprimé (loot géré par le plugin).");
            }
        }
    }

    /**
     * Referme le portail de sortie du Vide (généré par MC à la mort du dragon) : on retire les blocs
     * END_PORTAL et l'éventuel œuf/bedrock ajoutés, dans une petite zone autour du centre (0,*,0).
     * Appelé au spawn du dragon suivant, en même temps que le nettoyage des blocs posés — cohérent
     * avec le « reset de l'Île » côté joueur. On NE touche PAS à la bedrock native de l'île.
     */
    private void nettoyerPortailSortie(World end) {
        int n = 0;
        // Base du portail : petite zone 5×5 autour du centre, sur quelques niveaux Y.
        for (int x = ILE_X - 3; x <= ILE_X + 3; x++) {
            for (int z = ILE_Z - 3; z <= ILE_Z + 3; z++) {
                for (int y = ILE_Y - 5; y <= ILE_Y + 5; y++) {
                    org.bukkit.block.Block b = end.getBlockAt(x, y, z);
                    if (b.getType() == Material.END_PORTAL) { b.setType(Material.AIR, false); n++; }
                }
            }
        }
        // Œuf éventuellement encore présent au sommet.
        supprimerOeufVanilla();
        if (n > 0) plugin.getLogger().info("Dragon de l'End : portail de sortie refermé (" + n + " blocs).");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Suivi des dégâts
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (dragonCourant == null) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!event.getEntity().getUniqueId().equals(dragonCourant)) return;

        // Attaquant réel : direct, ou le tireur si c'est un projectile (flèche…).
        Entity att = event.getDamager();
        if (att instanceof Projectile proj && proj.getShooter() instanceof Entity src) att = src;
        if (!(att instanceof Player)) return;
        Player joueur = (Player) att;

        double d = event.getFinalDamage();
        degats.merge(joueur.getUniqueId(), d, Double::sum);
        // La barre de boss (native du dragon) se met à jour toute seule.
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Mort du dragon → loot Top 3
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (dragonCourant == null) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!event.getEntity().getUniqueId().equals(dragonCourant)) return;

        dragonCourant = null;
        retirerBossBar();

        // Minecraft pose un ŒUF vanilla sur la bedrock au sommet du portail de sortie à la mort du
        // 1er dragon. On le SUPPRIME (le seul œuf du jeu vient de notre loot 1/10 au Top 1). Le
        // portail lui-même reste (un joueur qui le prend est renvoyé au monde principal → spawn) ;
        // il sera refermé au prochain spawn de dragon (nettoyerPortailSortie).
        Bukkit.getScheduler().runTaskLater(plugin, this::supprimerOeufVanilla, 20L); // +1 s

        // Classement des contributeurs par dégâts décroissants.
        List<Map.Entry<UUID, Double>> classement = new ArrayList<>(degats.entrySet());
        classement.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (classement.isEmpty()) {
            Bukkit.broadcastMessage("§5§l🐉 Le Dragon du Vide est tombé... mais personne ne l'a frappé. Aucun butin distribué.");
            save();
            return;
        }

        // On garde au plus 3 gagnants, et on renormalise les parts s'il y en a moins de 3.
        int n = Math.min(3, classement.size());
        double sommeParts = 0;
        for (int i = 0; i < n; i++) sommeParts += PARTS[i];

        Bukkit.broadcastMessage("§8§m                                        ");
        Bukkit.broadcastMessage("§5§l🐉 LE DRAGON DU VIDE EST VAINCU !");

        for (int i = 0; i < n; i++) {
            UUID id = classement.get(i).getKey();
            double part = PARTS[i] / sommeParts; // renormalisé
            distribuerLoot(id, part, i, classement.get(i).getValue());
        }

        // Œuf de dragon : DROP RARE (1 chance sur 10) réservé au Top 1 dégâts. L'XP vanilla reste.
        if (rng.nextInt(10) == 0) {
            Player premier = Bukkit.getPlayer(classement.get(0).getKey());
            if (premier != null) {
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste =
                        premier.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));
                for (org.bukkit.inventory.ItemStack rem : reste.values())
                    premier.getWorld().dropItemNaturally(premier.getLocation(), rem);
                premier.sendMessage("§d🥚 §lCHANCE ! §r§dTu remportes l'§5Œuf de Dragon §d(1/10, meilleur dégâts) !");
                premier.playSound(premier.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Bukkit.broadcastMessage("§d🥚 §f" + premier.getName() + " §7a décroché l'§5Œuf de Dragon §7(1/10) !");
            }
        }

        Bukkit.broadcastMessage("§8§m                                        ");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1f);

        // Compte à rebours de 30 s puis tous les joueurs de l'End sont renvoyés au spawn.
        demarrerFinEvent();
        save();
    }

    /**
     * Après la mort du dragon : décompte de 30 s (titres à 30/10/5/3/2/1), puis téléporte tous
     * les joueurs encore présents dans l'End au spawn du serveur. Laisse le temps de ramasser le loot.
     */
    private void demarrerFinEvent() {
        Bukkit.broadcastMessage("§7Retour au spawn dans §f30 secondes§7... ramassez votre butin !");
        // Compteur en secondes, décrémenté chaque seconde (20 ticks).
        final int[] restant = {30};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            World end = Bukkit.getWorld(END_WORLD);
            // Plus personne dans l'End OU un nouveau dragon a été relancé entre-temps → on annule.
            if (end == null || end.getPlayers().isEmpty() || dragonCourant != null) { task.cancel(); return; }

            restant[0]--;
            int s = restant[0];
            // Titre de décompte aux paliers utiles.
            if (s == 20 || s == 10 || s == 5 || s == 3 || s == 2 || s == 1) {
                for (Player p : end.getPlayers()) {
                    p.sendTitle("§5§l🐉 Vide instable", "§7Retour au spawn dans §f" + s + "s", 0, 25, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, s <= 3 ? 1.6f : 1f);
                }
            }
            if (s <= 0) {
                task.cancel();
                Location spawn = plugin.getSpawnLocation();
                for (Player p : new ArrayList<>(end.getPlayers())) {
                    p.setWorldBorder(null);
                    plugin.getPlayerZoneMap().put(p.getUniqueId(), "spawn");
                    if (spawn != null) p.teleport(spawn);
                    p.sendTitle("§5L'Antre du Vide", "§7L'event est terminé.", 0, 40, 10);
                    p.sendMessage("§7L'Antre du Vide se referme. §fRendez-vous au prochain Dragon (20h) !");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }
            }
        }, 20L, 20L); // démarre à +1 s, puis chaque seconde
    }

    /** Verse à un joueur sa part du loot (argent + clés), avec un message. rang = 0/1/2 (podium). */
    private void distribuerLoot(UUID id, double part, int rang, double sesDegats) {
        // Argent.
        BigInteger argent = new java.math.BigDecimal(LOOT_ARGENT_TOTAL)
                .multiply(java.math.BigDecimal.valueOf(part))
                .toBigInteger();
        EconomyManager eco = plugin.getCustomEco();
        if (eco != null && argent.signum() > 0) {
            eco.setBalanceBig(id, eco.getBalanceBig(id).add(argent));
        }
        // Clés (arrondi au plus proche, min 1 pour un gagnant du podium).
        int cles = (int) Math.round(LOOT_CLES_TOTAL * part);
        if (cles < 1) cles = 1;
        CrateManager crate = plugin.getCrates();
        if (crate != null && cles > 0) crate.giveKey(id, LOOT_CLE_RANG, cles);

        String[] medailles = {"§6🥇 1er", "§7🥈 2e", "§c🥉 3e"};
        String medaille = rang < 3 ? medailles[rang] : "§7" + (rang + 1) + "e";
        Player p = Bukkit.getPlayer(id);
        String nom = p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
        Bukkit.broadcastMessage("  " + medaille + " §f" + nom + " §7— §6"
                + PrivateMines.formatNumberBig(argent) + "$ §7+ §b" + cles + " clé" + (cles > 1 ? "s" : "")
                + " §8(" + Math.round(part * 100) + "% du butin)");
        if (p != null) {
            p.sendMessage("§a✔ Loot du Dragon : §6" + PrivateMines.formatNumberBig(argent) + "$ §a+ §b"
                    + cles + " clé" + (cles > 1 ? "s " + LOOT_CLE_RANG : " " + LOOT_CLE_RANG) + "§a.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Blocs posés dans l'End (à effacer au dragon suivant)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onBlockPlaceEnd(BlockPlaceEvent event) {
        if (!event.getBlock().getWorld().getName().equals(END_WORLD)) return;
        // Les OP (admin qui build) posent librement, sans enregistrement ni protection.
        if (event.getPlayer().isOp()) return;
        org.bukkit.block.Block b = event.getBlock();
        // Zone safe (salle d'attente) : aucune pose par un joueur.
        if (estDansZoneSafe(b.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Tu ne peux rien poser dans la salle d'attente.");
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        blocsPoses.add(b.getX() + "," + b.getY() + "," + b.getZ());
    }

    /**
     * Cassage dans l'End : un joueur normal ne peut casser QUE des blocs posés par des joueurs
     * (les siens ou ceux des autres, tous présents dans {@link #blocsPoses}) — jamais le terrain
     * vanilla (obsidienne des piliers, endstone…). Les OP cassent librement (build de l'arène).
     */
    @EventHandler
    public void onBlockBreakEnd(BlockBreakEvent event) {
        if (!event.getBlock().getWorld().getName().equals(END_WORLD)) return;
        Player p = event.getPlayer();
        if (p.isOp()) return; // admin : casse tout
        org.bukkit.block.Block b = event.getBlock();
        // Zone safe (salle d'attente) : aucun cassage par un joueur.
        if (estDansZoneSafe(b.getLocation())) {
            event.setCancelled(true);
            p.sendMessage("§c✖ Tu ne peux rien casser dans la salle d'attente.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String cle = b.getX() + "," + b.getY() + "," + b.getZ();
        if (blocsPoses.contains(cle)) {
            // Bloc posé par un joueur → cassage autorisé, on le retire de la liste à nettoyer.
            blocsPoses.remove(cle);
        } else {
            // Terrain vanilla → interdit.
            event.setCancelled(true);
            p.sendMessage("§c✖ Tu ne peux casser que les blocs posés par les joueurs dans l'End.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Zone SAFE : pas de PvP dans la salle d'attente
    // ══════════════════════════════════════════════════════════════════════════

    /** Vrai si la position est dans la sphère safe (rayon 30) autour de la salle d'attente. */
    public boolean estDansZoneSafe(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(END_WORLD)) return false;
        double dx = loc.getX() - (SALLE_X + 0.5);
        double dy = loc.getY() - SALLE_Y;
        double dz = loc.getZ() - (SALLE_Z + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= SAFE_RAYON2;
    }

    // PvP interdit dans la zone safe de la salle d'attente : on annule si la VICTIME y est.
    // Ailleurs dans l'End, le PvP reste autorisé (aucune parcelle → ParcelleListener ne bloque pas).
    @EventHandler
    public void onSafeZonePvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victime)) return;
        if (!victime.getWorld().getName().equals(END_WORLD)) return;
        // Attaquant réel : direct, ou le tireur si c'est un projectile.
        Entity att = event.getDamager();
        if (att instanceof Projectile proj && proj.getShooter() instanceof Entity src) att = src;
        if (!(att instanceof Player attaquant)) return; // seul le vrai PvP joueur-vs-joueur est concerné
        if (attaquant.equals(victime)) return;
        if (estDansZoneSafe(victime.getLocation())) {
            event.setCancelled(true);
            return;
        }
        // Vrai coup PvP hors zone safe → les DEUX sont marqués « en combat » (15 s, rechargé à chaque coup).
        taggerCombat(attaquant);
        taggerCombat(victime);
    }

    // Aucun dégât (chute, mobs, feu, etc.) subi par un JOUEUR dans la zone safe.
    @EventHandler
    public void onSafeZoneDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (estDansZoneSafe(event.getEntity().getLocation())) event.setCancelled(true);
    }

    // Aucune explosion (cristaux, creepers, TNT...) ne casse de blocs dans la zone safe.
    @EventHandler
    public void onSafeZoneExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName().equals(END_WORLD)) return;
        event.blockList().removeIf(b -> estDansZoneSafe(b.getLocation()));
    }

    // Pas de propagation de feu dans la zone safe (ignition + combustion de blocs).
    @EventHandler
    public void onSafeZoneIgnite(BlockIgniteEvent event) {
        if (estDansZoneSafe(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onSafeZoneBurn(BlockBurnEvent event) {
        if (estDansZoneSafe(event.getBlock().getLocation())) event.setCancelled(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tag de combat : anti déco-combat & anti fuite par commande
    // ══════════════════════════════════════════════════════════════════════════

    // Marque un joueur « en combat » : lui pose (ou prolonge) son tag de COMBAT_MS.
    private void taggerCombat(Player p) {
        boolean nouveau = !enCombat(p.getUniqueId());
        combatTag.put(p.getUniqueId(), System.currentTimeMillis() + COMBAT_MS);
        if (nouveau) {
            p.sendMessage("§c⚔ Tu es §len combat §c! Ne te déconnecte pas et ne fuis pas §7(" + (COMBAT_MS / 1000) + "s)§c.");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.4f, 1.6f);
        }
    }

    /** Vrai si le joueur est actuellement tag « en combat ». */
    public boolean enCombat(UUID id) {
        Long fin = combatTag.get(id);
        if (fin == null) return false;
        if (System.currentTimeMillis() > fin) { combatTag.remove(id); return false; }
        return true;
    }

    // Bloque les commandes de FUITE (téléportation) tant qu'on est en combat.
    @EventHandler
    public void onCombatCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!enCombat(p.getUniqueId())) return;
        // 1er mot de la commande, sans le "/".
        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (CMDS_FUITE.contains(cmd)) {
            event.setCancelled(true);
            long reste = (combatTag.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000 + 1;
            p.sendMessage("§c⚔ Impossible de fuir en combat ! §7Attends §f" + reste + "s§7.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    // Déco EN COMBAT = mort : on drop son inventaire là où il était, on le vide, on retire son tag.
    @EventHandler
    public void onCombatQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (!enCombat(p.getUniqueId())) return;
        combatTag.remove(p.getUniqueId());
        Location loc = p.getLocation();
        // Drop tout l'inventaire + l'armure au sol (le combat-logger « meurt »).
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) loc.getWorld().dropItemNaturally(loc, it);
        }
        p.getInventory().clear();
        p.setHealth(0.0); // il réapparaîtra mort au spawn à la reconnexion
        Bukkit.broadcastMessage("§c⚔ §f" + p.getName() + " §cs'est déconnecté en plein combat dans l'End... et en meurt !");
    }

    // La barre de boss est celle NATIVE du dragon : elle disparaît d'elle-même quand le dragon
    // meurt ou est supprimé. On lâche juste notre référence (pas de removeAll : le dragon la possède).
    private void retirerBossBar() {
        bossBar = null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Commande OP de test : /end forcedragon
    // ══════════════════════════════════════════════════════════════════════════

    /** Force le spawn du dragon maintenant (test OP). */
    public void forceDragon() { spawnDragonEvent(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Persistance (end.yml) — uniquement les blocs posés à nettoyer.
    // ══════════════════════════════════════════════════════════════════════════

    public void load() {
        file = new java.io.File(plugin.getDataFolder(), "end.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        blocsPoses.clear();
        blocsPoses.addAll(config.getStringList("blocsPoses"));
    }

    public void save() {
        if (config == null) return;
        config.set("blocsPoses", new ArrayList<>(blocsPoses));
        try { config.save(file); } catch (java.io.IOException ignored) {}
    }
}
