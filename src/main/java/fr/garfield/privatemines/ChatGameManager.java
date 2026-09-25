                                                                                                                        package fr.garfield.privatemines;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mini-jeux de chat : toutes les 15 minutes (s'il y a au moins 2 joueurs connectes), le serveur
 * pose une question dans le chat. Le gagnant empoche <b>+10 % de son argent</b>, avec un
 * <b>minimum garanti</b> pour que l'evenement veuille aussi dire quelque chose aux debutants.
 *
 * <p>Deux epreuves :
 * <ul>
 *   <li><b>Calcul mental</b> - « 8 x 7 = ? ». Le PREMIER qui ecrit la bonne reponse gagne.</li>
 *   <li><b>Nombre mystere</b> - « je pense a un nombre entre 1 et 50 ». UNE seule proposition par
 *       joueur (sinon on brute-force en spammant le chat) ; le plus proche gagne a la fin, et
 *       tomber pile termine la partie tout de suite.</li>
 * </ul>
 *
 * <p><b>Concurrence</b> : {@link AsyncPlayerChatEvent} arrive sur un thread ASYNC. On n'y touche
 * donc a aucune API Bukkit - on designe le gagnant avec un {@link AtomicBoolean#compareAndSet}
 * (departage atomique, le premier arrive gagne vraiment) puis tout le reste (economie, broadcast,
 * sons) repart sur le thread principal via le scheduler.
 *
 * <p>Commande de test : {@code /testgames [calcul|nombre|stop]} (OP).
 */
public class ChatGameManager implements Listener {

    private final PrivateMines plugin;
    private final Random rng = new Random();

    // ------------------------------------------------------------------ reglages

    /** Intervalle entre deux parties automatiques. */
    private static final long INTERVALLE_TICKS = 20L * 60 * 15;   // 15 minutes
    /** En dessous de ce nombre de joueurs connectes, aucune partie automatique (pas de solo). */
    private static final int  MIN_JOUEURS      = 2;
    /** Temps laisse pour repondre. */
    private static final int  DUREE_SECONDES   = 30;
    /** Part du solde du gagnant versee en recompense. */
    private static final int  GAIN_POURCENT    = 10;
    /**
     * Minimum garanti = la valeur de N blocs de pierre AU PRIX DE SA MEILLEURE MINE debloquee.
     * Sans ca un debutant a 0 $ gagnerait 0 $ : 10 % de rien, c'est rien. Indexe sur la mine pour
     * que le plancher suive la progression au lieu d'etre un montant fixe vite ridicule.
     */
    private static final int  PLANCHER_BLOCS   = 500;
    /**
     * Nombre mystere : tomber PILE sur la cible (du premier et unique coup) donne une cle de crate
     * en BONUS des +10 %. La plage la plus large est 4x plus dure que la plus petite (1 chance sur
     * 200 contre 1 sur 50) : elle vaut donc une cle LEGENDAIRE, les autres une cle RARE.
     */
    private static final int  PLAGE_LEGENDAIRE = 200;

    private static final String BARRE = "§8§m                                                        ";

    // ------------------------------------------------------------------ etat

    private enum Type { CALCUL, NOMBRE }

    /** Une partie en cours. Immuable sauf les propositions (remplies depuis le thread async). */
    private static class Partie {
        Type type;
        String question;      // version coloree pour le chat
        String questionBrute; // version sans couleurs pour le title
        int reponse;          // le resultat attendu / le nombre cible
        int borneMin, borneMax;
        final AtomicBoolean resolue = new AtomicBoolean(false);
        // Nombre mystere : UNE proposition par joueur, + l'ordre d'arrivee pour departager.
        final Map<UUID, Integer> propositions = new ConcurrentHashMap<>();
        final List<UUID> ordre = Collections.synchronizedList(new ArrayList<>());
        BukkitTask timeout;
    }

    /** volatile : lue depuis le thread du chat (async), ecrite depuis le thread principal. */
    private volatile Partie enCours;

    public ChatGameManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ boucle automatique

    /** Demarre la boucle : une partie toutes les 15 min, s'il y a assez de monde et rien en cours. */
    public void startAutoTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (enCours != null) return;
            if (Bukkit.getOnlinePlayers().size() < MIN_JOUEURS) return;
            lancer(rng.nextBoolean() ? Type.CALCUL : Type.NOMBRE);
        }, INTERVALLE_TICKS, INTERVALLE_TICKS);
    }

    // ------------------------------------------------------------------ lancement d'une partie

    private void lancer(Type type) {
        Partie p = new Partie();
        p.type = type;
        if (type == Type.CALCUL) genererCalcul(p);
        else genererNombre(p);
        enCours = p;
        annoncer(p);
        // Fin de partie au bout de DUREE_SECONDES si personne n'a trouve (ou, pour le nombre
        // mystere, pour designer le plus proche parmi les propositions recues).
        p.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> expirer(p), DUREE_SECONDES * 20L);
    }

    /** Un calcul faisable de tete : x (petites tables), + et - a deux chiffres. */
    private void genererCalcul(Partie p) {
        int op = rng.nextInt(3);
        int a, b;
        String signe;
        if (op == 0) {                       // multiplication : tables de 2 a 12
            a = 2 + rng.nextInt(11);
            b = 2 + rng.nextInt(11);
            signe = "×";
            p.reponse = a * b;
        } else if (op == 1) {                // addition a deux chiffres
            a = 11 + rng.nextInt(89);
            b = 11 + rng.nextInt(89);
            signe = "+";
            p.reponse = a + b;
        } else {                             // soustraction, jamais negative
            a = 30 + rng.nextInt(70);
            b = 1 + rng.nextInt(a - 1);
            signe = "−";
            p.reponse = a - b;
        }
        p.question      = "§f" + a + " §e" + signe + " §f" + b + " §e= §f?";
        p.questionBrute = a + " " + signe + " " + b + " = ?";
    }

    /** Un nombre mystere dans une plage tiree au hasard (plus la plage est large, plus c'est dur). */
    private void genererNombre(Partie p) {
        int[] plages = {50, 100, 200};
        p.borneMin = 1;
        p.borneMax = plages[rng.nextInt(plages.length)];
        p.reponse  = p.borneMin + rng.nextInt(p.borneMax);
        p.question      = "§7Un nombre entre §f" + p.borneMin + " §7et §f" + p.borneMax;
        p.questionBrute = "Un nombre entre " + p.borneMin + " et " + p.borneMax;
    }

    // ------------------------------------------------------------------ affichage

    /** L'annonce : encadre plein chat + son + title, pour que personne ne la rate en minant. */
    private void annoncer(Partie p) {
        String titre = p.type == Type.CALCUL ? "CALCUL MENTAL" : "NOMBRE MYSTÈRE";
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(BARRE);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("      §e§l⚡ JEU DU CHAT §8» §6§l" + titre);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("            §l" + p.question);
        Bukkit.broadcastMessage("");
        if (p.type == Type.CALCUL) {
            Bukkit.broadcastMessage("   §7Le §fpremier §7a ecrire la reponse dans le chat gagne.");
        } else {
            Bukkit.broadcastMessage("   §7Ecris ton nombre dans le chat — §fune seule proposition§7 !");
            Bukkit.broadcastMessage("   §7Le §fplus proche §7gagne a la fin.");
            String cle = p.borneMax >= PLAGE_LEGENDAIRE ? "§6legendaire" : "§9rare";
            Bukkit.broadcastMessage("   §e★ §7Tomber §fpile dessus §7= une cle " + cle + " §7en bonus !");
        }
        Bukkit.broadcastMessage("   §7Recompense : §a+" + GAIN_POURCENT + " % §7de ton argent §8(minimum garanti)");
        Bukkit.broadcastMessage("   §8Tu as §c" + DUREE_SECONDES + " secondes§8.");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(BARRE);
        Bukkit.broadcastMessage("");
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2.0f);
            // 2 s a l'ecran : assez pour lever les yeux du minage, assez court pour ne pas gener.
            pl.sendTitle("§e§l⚡ JEU DU CHAT", "§f" + p.questionBrute, 5, 40, 10);
        }
    }

    // ------------------------------------------------------------------ reception des reponses

    /**
     * THREAD ASYNC. On ne touche a AUCUNE API Bukkit ici : on se contente de lire la partie en
     * cours, de parser un entier, et de repasser sur le thread principal pour payer et annoncer.
     * Le message reste affiche dans le chat (voir les autres courir, ca fait partie du jeu).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Partie p = enCours;
        if (p == null) return;
        final Integer valeur = parseEntier(event.getMessage());
        if (valeur == null) return;
        final Player joueur = event.getPlayer();

        if (p.type == Type.CALCUL) {
            if (valeur != p.reponse) return;
            // compareAndSet = le departage atomique : deux bonnes reponses dans le meme tick,
            // une seule passe. Pas de double paiement possible.
            if (!p.resolue.compareAndSet(false, true)) return;
            Bukkit.getScheduler().runTask(plugin, () -> terminer(p, joueur.getUniqueId(), valeur));
            return;
        }

        // Nombre mystere : la PREMIERE proposition du joueur est la seule qui compte.
        if (p.propositions.putIfAbsent(joueur.getUniqueId(), valeur) != null) {
            joueur.sendMessage("§c✖ Tu as deja propose un nombre pour cette partie.");
            return;
        }
        p.ordre.add(joueur.getUniqueId());
        if (valeur == p.reponse) {   // pile dessus : inutile d'attendre la fin du chrono
            if (!p.resolue.compareAndSet(false, true)) return;
            Bukkit.getScheduler().runTask(plugin, () -> terminer(p, joueur.getUniqueId(), valeur));
        }
    }

    /** Le message ENTIER doit etre un nombre (« 56 »), sinon ce n'est pas une reponse. */
    private Integer parseEntier(String message) {
        String s = message.trim();
        if (s.isEmpty() || s.length() > 9) return null;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ fin de partie

    /** Fin du chrono : personne n'a trouve - pour le nombre mystere, le plus proche l'emporte. */
    private void expirer(Partie p) {
        if (enCours != p) return;                       // deja terminee par une bonne reponse
        if (!p.resolue.compareAndSet(false, true)) return;

        UUID gagnant = null;
        int meilleure = 0;
        if (p.type == Type.NOMBRE && !p.propositions.isEmpty()) {
            int meilleurEcart = Integer.MAX_VALUE;
            // On parcourt dans l'ORDRE D'ARRIVEE : a ecart egal, le plus rapide gagne.
            synchronized (p.ordre) {
                for (UUID id : p.ordre) {
                    Integer prop = p.propositions.get(id);
                    if (prop == null) continue;
                    int ecart = Math.abs(prop - p.reponse);
                    if (ecart < meilleurEcart) {
                        meilleurEcart = ecart;
                        gagnant = id;
                        meilleure = prop;
                    }
                }
            }
        }
        terminer(p, gagnant, meilleure);
    }

    /** Cloture : verse la recompense et annonce, ou annonce que personne n'a trouve. */
    private void terminer(Partie p, UUID gagnantId, int proposition) {
        if (enCours != p) return;
        enCours = null;
        if (p.timeout != null) {
            p.timeout.cancel();
            p.timeout = null;
        }

        Player gagnant = gagnantId == null ? null : Bukkit.getPlayer(gagnantId);
        if (gagnant == null) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(BARRE);
            Bukkit.broadcastMessage("   §c✖ §7Personne n'a trouve ! La reponse etait §f§l" + p.reponse + "§7.");
            Bukkit.broadcastMessage(BARRE);
            Bukkit.broadcastMessage("");
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            }
            return;
        }

        BigInteger gain = payer(gagnant);

        // Bonus « pile dessus » : sur le nombre mystere, tomber exactement sur la cible vaut une
        // cle de crate en plus de l'argent. Legendaire si la plage etait la plus large.
        CrateManager.CrateRank bonus = null;
        if (p.type == Type.NOMBRE && proposition == p.reponse) {
            CrateManager crates = plugin.getCrates();
            if (crates != null) {
                bonus = p.borneMax >= PLAGE_LEGENDAIRE ? CrateManager.LEGEND : CrateManager.RARE;
                crates.giveKey(gagnant.getUniqueId(), bonus.key, 1);
            }
        }

        String detail = p.type == Type.NOMBRE && proposition != p.reponse
                ? "§7le plus proche avec §f" + proposition + " §8(reponse : " + p.reponse + ")"
                : "§7a trouve §f§l" + p.reponse;

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(BARRE);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("      §a§l✔ §f§l" + gagnant.getName() + " §7remporte le jeu du chat !");
        Bukkit.broadcastMessage("      " + detail);
        Bukkit.broadcastMessage("      §7Gain : §6§l+" + PrivateMines.formatNumberBig(gain) + "$");
        if (bonus != null) {
            Bukkit.broadcastMessage("      §e§l★ PILE DESSUS §7: §f+ 1 "
                    + bonus.display.replaceAll("Coffre ", "Cle ") + " §7en bonus !");
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(BARRE);
        Bukkit.broadcastMessage("");
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
        gagnant.playSound(gagnant.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (bonus != null) {
            // Pile dessus : tout le serveur entend le carillon, pas seulement le gagnant.
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
            }
        }
        gagnant.sendTitle("§6§l+ " + PrivateMines.formatNumberBig(gain) + "$",
                bonus != null ? bonus.color + "§l★ + 1 " + bonus.display.replaceAll("Coffre ", "Cle ")
                              : "§7Jeu du chat remporte !", 5, 50, 10);
    }

    /**
     * Verse la recompense : +10 % du solde, ou le plancher indexe sur la meilleure mine si 10 %
     * est derisoire. Renvoie le montant verse.
     */
    private BigInteger payer(Player p) {
        EconomyManager eco = plugin.getCustomEco();
        if (eco == null) return BigInteger.ZERO;
        BigInteger solde = eco.getBalanceBig(p.getUniqueId());
        BigInteger part  = solde.multiply(BigInteger.valueOf(GAIN_POURCENT)).divide(BigInteger.valueOf(100));
        long plancher = Math.max(1L, Math.round(plugin.getStoneSellPrice(p) * PLANCHER_BLOCS));
        BigInteger gain = part.max(BigInteger.valueOf(plancher));
        eco.setBalanceBig(p.getUniqueId(), solde.add(gain));
        return gain;
    }

    // ------------------------------------------------------------------ /testgames (OP)

    /** {@code /testgames [calcul|nombre|stop]} : lance une partie tout de suite, sans attendre. */
    public boolean handleTestCommand(Player admin, String[] args) {
        if (!admin.isOp()) {
            admin.sendMessage("§cReserve aux OP.");
            return true;
        }
        String quoi = args.length >= 1 ? args[0].toLowerCase() : "";

        if (quoi.equals("stop")) {
            Partie p = enCours;
            if (p == null) {
                admin.sendMessage("§7Aucune partie en cours.");
                return true;
            }
            p.resolue.set(true);
            enCours = null;
            if (p.timeout != null) p.timeout.cancel();
            Bukkit.broadcastMessage("§8» §7Le jeu du chat a ete §cannule§7. §8(reponse : " + p.reponse + ")");
            return true;
        }

        if (enCours != null) {
            admin.sendMessage("§cUne partie est deja en cours. §7Utilise §f/testgames stop§7 pour l'annuler.");
            return true;
        }

        Type type;
        if (quoi.startsWith("calc")) type = Type.CALCUL;
        else if (quoi.startsWith("nomb")) type = Type.NOMBRE;
        else if (quoi.isEmpty()) type = rng.nextBoolean() ? Type.CALCUL : Type.NOMBRE;
        else {
            admin.sendMessage("§cUsage : §f/testgames [calcul|nombre|stop]");
            return true;
        }

        lancer(type);
        // La reponse est soufflee a l'admin en prive : c'est une commande de test.
        admin.sendMessage("§8» §7Partie lancee. §8Reponse : §f" + enCoursReponse());
        return true;
    }

    private String enCoursReponse() {
        Partie p = enCours;
        return p == null ? "—" : String.valueOf(p.reponse);
    }
}
