package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * ACTE I — logique de progression (« L'Éveil sur la Grève des Oubliés »).
 *
 * Suit l'avancement de chaque joueur dans l'Acte I, gère l'objet-quête « Jeton de Marée »,
 * le coffre PARTAGÉ anti-vide (chaque joueur récupère SA copie, une seule fois), et le
 * déblocage final de l'aventure (/mine).
 *
 * <p>Progression stockée dans le PersistentDataContainer du joueur (clé "acte1_step") :
 * <ul>
 *   <li>0 = rien fait (doit parler au Veilleur)</li>
 *   <li>1 = a parlé au Veilleur (doit parler à l'Ancre)</li>
 *   <li>2 = a parlé à l'Ancre (doit trouver le Jeton dans le coffre)</li>
 *   <li>3 = a trouvé le Jeton (doit le rapporter à l'Ancre)</li>
 *   <li>4 = Acte I terminé (aventure débloquée)</li>
 * </ul>
 *
 * Les PNJ (NpcManager) appellent les méthodes onTalk* selon lequel a été cliqué ;
 * cette classe décide quoi afficher et fait avancer l'étape.
 */
public class ActeManager implements Listener {

    private final PrivateMines plugin;
    private final DialogueManager dialogues;

    // Clés PDC de progression / possession du Jeton.
    private final NamespacedKey stepKey;      // acte1_step : entier 0..4
    private final NamespacedKey jetonTakenKey; // jeton_pris : le joueur a déjà pris sa copie au coffre
    // Clé PDC posée sur l'ITEM Jeton de Marée (anti-triche : on identifie l'item par ce tag, pas par son nom).
    private final NamespacedKey jetonItemKey;

    // Objectifs affichés dans la BossBar à chaque étape.
    public static final String OBJ_VEILLEUR =
            "§b⚓ La Grève des Oubliés §7— §fParle au Veilleur";
    public static final String OBJ_ANCRE =
            "§b⚓ La Grève des Oubliés §7— §fParle à l'Ancre";
    public static final String OBJ_COFFRE =
            "§b⚓ La Grève des Oubliés §7— §fTrouve le Jeton de Marée §7(coffre caché)";
    public static final String OBJ_RETOUR =
            "§b⚓ La Grève des Oubliés §7— §fRapporte le Jeton à l'Ancre";

    // ===== ACTE II (sur l'île de la mine) =====
    private final NamespacedKey step2Key;       // acte2_step : entier 0..3
    private final NamespacedKey teteTakenKey;    // tete_prise : a déjà pris sa Tête de Pioche au coffre II
    private final NamespacedKey teteItemKey;     // tag sur l'item Tête de Pioche des Souvenirs
    private final NamespacedKey puitsSeenKey;    // puits_vu : a déjà découvert le secret au fond du Puits des Souvenirs

    // ===== ACTE IV (mine 15) : l'Ordinateur Quantique =====
    private final NamespacedKey pcGivenKey;      // pc_quantique_donne : a déjà reçu l'Ordinateur Quantique (anti-double)
    private final NamespacedKey pcItemKey;       // tag anti-triche posé sur l'item Ordinateur Quantique

    // ===== La Plume (niveau de pioche 45) : histoire du Contremaître + drop 1/1000 -> enchant Fly =====
    private final NamespacedKey plumeStoryKey;   // plume_histoire : a écouté l'histoire de la Plume (Contremaître)
    private final NamespacedKey plumeFoundKey;   // plume_trouvee : a trouvé la Plume en minant (débloque l'achat de Fly)
    private final NamespacedKey plumeHintKey;    // plume_indice : la cinématique du niveau 45 s'est déjà jouée (anti-double)

    // Objectifs BossBar de l'Acte II.
    public static final String OBJ2_CONTREMAITRE =
            "§6⛏ La Première Terre §7— §fParle au Contremaître";
    public static final String OBJ2_TETE =
            "§6⛏ La Première Terre §7— §fTrouve la Tête de Pioche §7(coffre caché)";
    public static final String OBJ2_FORGERON =
            "§6⛏ La Première Terre §7— §fApporte la Tête de Pioche au Forgeron";

    /** Le gestionnaire de dialogues immersifs (cadre + répliques espacées), partagé avec les autres PNJ. */
    public DialogueManager getDialogues() { return dialogues; }

    public ActeManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.dialogues = new DialogueManager(plugin);
        this.stepKey = new NamespacedKey(plugin, "acte1_step");
        this.jetonTakenKey = new NamespacedKey(plugin, "jeton_pris");
        this.jetonItemKey = new NamespacedKey(plugin, "quest_item");
        this.step2Key = new NamespacedKey(plugin, "acte2_step");
        this.teteTakenKey = new NamespacedKey(plugin, "tete_prise");
        this.teteItemKey = new NamespacedKey(plugin, "quest_item2");
        this.puitsSeenKey = new NamespacedKey(plugin, "puits_vu");
        this.pcGivenKey = new NamespacedKey(plugin, "pc_quantique_donne");
        this.pcItemKey = new NamespacedKey(plugin, "pc_quantique_item");
        this.plumeStoryKey = new NamespacedKey(plugin, "plume_histoire");
        this.plumeFoundKey = new NamespacedKey(plugin, "plume_trouvee");
        this.plumeHintKey = new NamespacedKey(plugin, "plume_indice");
    }

    // ===== La Plume (niveau 45) =====

    /** A déjà vu l'indice/cinématique du niveau 45 (va parler au Contremaître) ? */
    public boolean hasSeenPlumeHint(Player p) {
        return p.getPersistentDataContainer().has(plumeHintKey, PersistentDataType.BYTE);
    }
    public void setSeenPlumeHint(Player p) {
        p.getPersistentDataContainer().set(plumeHintKey, PersistentDataType.BYTE, (byte) 1);
    }
    /** A écouté l'histoire de la Plume auprès du Contremaître (débloque la recherche) ? */
    public boolean hasHeardPlumeStory(Player p) {
        return p.getPersistentDataContainer().has(plumeStoryKey, PersistentDataType.BYTE);
    }
    public void setHeardPlumeStory(Player p) {
        p.getPersistentDataContainer().set(plumeStoryKey, PersistentDataType.BYTE, (byte) 1);
    }
    /** A trouvé la Plume en minant (débloque l'achat de l'enchant Fly) ? */
    public boolean hasFoundPlume(Player p) {
        return p.getPersistentDataContainer().has(plumeFoundKey, PersistentDataType.BYTE);
    }
    public void setFoundPlume(Player p) {
        p.getPersistentDataContainer().set(plumeFoundKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** Cinématique/indice du niveau 45 : dit au joueur d'aller voir le Contremaître. Une seule fois. */
    public void triggerPlumeHint(Player p) {
        if (hasSeenPlumeHint(p)) return;
        setSeenPlumeHint(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendTitle("§b✦ Un murmure ancien", "§7Va parler au §6Contremaître§7...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            p.sendMessage("");
            p.sendMessage("§b✦ §fLe vent de la mine a changé. Le §6Contremaître§f a quelque chose à te dire...");
            p.sendMessage("§7Retourne le voir sur la §6Première Terre§7.");
            p.sendMessage("");
        }, 20L);
    }

    /** Le joueur trouve la Plume en minant : gros message + flag. Une seule fois. */
    public void triggerPlumeFound(Player p) {
        if (hasFoundPlume(p)) return;
        setFoundPlume(p);
        p.sendTitle("§e§lLA PLUME !", "§fTu l'as trouvée dans la roche", 10, 80, 30);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        p.sendMessage("");
        p.sendMessage("§e§l✦ TU AS TROUVÉ LA PLUME ! ✦");
        p.sendMessage("§fUne plume d'un blanc impossible, légère comme un souffle, chaude comme un souvenir.");
        p.sendMessage("§7Tu peux maintenant acheter l'enchant §b§lVol§7 dans le menu de ta pioche.");
        p.sendMessage("");
    }

    // ===== Progression (PDC) =====

    public int getStep(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        return pdc.getOrDefault(stepKey, PersistentDataType.INTEGER, 0);
    }

    private void setStep(Player p, int step) {
        p.getPersistentDataContainer().set(stepKey, PersistentDataType.INTEGER, step);
    }

    /** L'Acte I est-il terminé pour ce joueur ? (débloque /mine) */
    public boolean isActeDone(Player p) {
        return getStep(p) >= 4;
    }

    // ===== Item « Jeton de Marée » =====

    /** Crée une copie du Jeton de Marée (item-quête identifiable par tag PDC). */
    public ItemStack createJeton() {
        ItemStack jeton = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = jeton.getItemMeta();
        meta.setDisplayName("§b§lJeton de Marée");
        meta.setLore(Arrays.asList(
                "§7Une vieille pièce rongée par le sel.",
                "§7La mer l'a rejetée pour toi.",
                "",
                "§eRapporte-la à §al'Ancre§e."));
        // Tag anti-triche : c'est CE tag qui identifie l'item, pas son nom.
        meta.getPersistentDataContainer().set(jetonItemKey, PersistentDataType.STRING, "jeton_maree");
        jeton.setItemMeta(meta);
        return jeton;
    }

    /** Vrai si l'item est un vrai Jeton de Marée (vérifié par tag PDC, pas par nom). */
    public boolean isJeton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return "jeton_maree".equals(
                meta.getPersistentDataContainer().get(jetonItemKey, PersistentDataType.STRING));
    }

    /** Le joueur a-t-il un Jeton de Marée quelque part dans son inventaire ? */
    public boolean hasJetonInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isJeton(item)) return true;
        }
        return false;
    }

    /** Retire un Jeton de Marée de l'inventaire du joueur (le premier trouvé). */
    private void removeOneJeton(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isJeton(contents[i])) {
                ItemStack it = contents[i];
                if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
                else p.getInventory().setItem(i, null);
                return;
            }
        }
    }

    // ===== Dialogues des PNJ =====

    /** Le joueur fait clic droit sur LE VEILLEUR. */
    public void onTalkVeilleur(Player p) {
        int step = getStep(p);
        if (step >= 1) {
            // Déjà parlé : petite réplique de rappel selon l'avancement.
            dialogues.startDialogue(p, "§bLe Veilleur", "§b", new String[]{
                    "§fToujours là ? §aL'Ancre§f, plus loin sur la grève. Je ne fais que répéter, maintenant. §7(Regarde la BossBar en haut de l'écran pour te guider.)"
            }, null);
            return;
        }
        // Première fois : l'accueil. Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ».
        // Registre : ironie fatiguée. Le Veilleur a vu tellement de naufragés que c'est devenu une
        // formalité pour lui ; l'humour vient de ce décalage, jamais de la situation du joueur.
        // Deux lignes ont été SUPPRIMÉES et ne doivent pas revenir :
        //   • l'itinéraire pas à pas vers l'Ancre → remplacé par un renvoi à la BossBar, que la
        //     boussole de quête (QuestCompass) remplit ; on ne redonne plus le chemin à la main ;
        //   • l'Écho Premier → volontairement décalé, seul le Forgeron en parle (fin de l'Acte II).
        dialogues.startDialogue(p, "§bLe Veilleur", "§b", new String[]{
                "§fAh, t'es réveillé. Bouge pas trop vite, tu vas... voilà. C'est ça. Tout le monde vomit, c'est normal.",
                "§fBon. Ta mémoire est partie avec la mer. Elle rend les corps mais pas le reste.",
                "§fCherche pas, tu vas te faire mal à la tête. Moi ça fait vingt ans que je cherche et j'en suis qu'à trois lettres. Et encore j'suis pas sûr de l'ordre.",
                "§fAllez, debout. §aL'Ancre§f s'occupe des départs. Elle est plus loin sur la grève. Reviens me voir si tu te perds, j'ai que ça à faire. §7(Regarde la BossBar en haut de l'écran pour te guider.)"
        }, () -> {
            // Fin du dialogue : étape 1 validée.
            if (getStep(p) < 1) {
                setStep(p, 1);
                plugin.getGuide().setQuestObjective(p, OBJ_ANCRE);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            }
        });
    }

    /** Le joueur fait clic droit sur L'ANCRE. */
    public void onTalkAncre(Player p) {
        int step = getStep(p);

        if (step == 0) {
            // Pas encore parlé au Veilleur.
            dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                    "§fTu viens d'où, toi ? Non. Le §bVeilleur§f d'abord, sur le ponton. C'est la procédure.",
            }, null);
            return;
        }

        if (step == 1) {
            // A parlé au Veilleur : donne l'objectif du coffre.
            dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                    "§fLe Veilleur t'envoie ? Bon. T'assieds pas, ça va être court.",
                    "§fRègle numéro une, et il n'y en a qu'une : on ne part pas d'ici les mains vides.",
                    "§fSur la plage il y a un vieux coffre à moitié enterré. Il brille en §bbleu§f, c'est pas discret. Dedans il y a un §eJeton de Marée§f.",
                    "§fRamène-le-moi et je m'occupe du reste. J'ai trois départs avant ce soir."
            }, () -> {
                if (getStep(p) < 2) {
                    setStep(p, 2);
                    plugin.getGuide().setQuestObjective(p, OBJ_COFFRE);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                }
            });
            return;
        }

        if (step == 2) {
            // Sait qu'il doit trouver le coffre mais ne l'a pas encore.
            if (hasJetonInInventory(p)) {
                // Cas de rattrapage : il a le Jeton (ex. donné) mais l'étape n'a pas suivi -> on l'accepte.
                closeActe(p);
                return;
            }
            dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                    "§fPas de Jeton, pas de départ. C'est vers les rochers, cherche la §blueur§f."
            }, null);
            return;
        }

        if (step == 3) {
            // A trouvé le Jeton : vérifie qu'il l'a bien, puis clôt l'Acte I.
            if (!hasJetonInInventory(p)) {
                dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                        "§fTu l'as eu et tu l'as perdu. Bravo. Retourne au coffre."
                }, null);
                return;
            }
            closeActe(p);
            return;
        }

        // step >= 4 : Acte terminé, réplique libre.
        // Filet de sécurité : si un Jeton traîne encore en poche (Acte clos par une ancienne
        // version, ou item dupliqué), l'Ancre le reprend ici. Il n'a plus aucune utilité.
        if (hasJetonInInventory(p)) removeOneJeton(p);
        dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                "§fT'es encore là ? §6/mine§f. Le passage est ouvert depuis un moment."
        }, null);
    }

    /** Dialogue de clôture de l'Acte I + effets + déblocage. */
    private void closeActe(Player p) {
        // IMPORTANT : le Jeton est consommé et l'étape validée TOUT DE SUITE, pas dans le
        // callback de fin de dialogue. Sinon un joueur qui part avant la dernière réplique
        // (téléportation, déconnexion) gardait son Jeton en poche, l'Acte restant ouvert.
        removeOneJeton(p);
        setStep(p, 4);

        // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ».
        // Registre de l'Ancre : la capitainerie. Elle expédie les départs comme un bureau de port,
        // elle n'a jamais le temps, et la plage est vide. Le contraste avec le Veilleur (qui traîne)
        // est VOLONTAIRE : c'est ce qui les empêche de sonner comme la même personne.
        dialogues.startDialogue(p, "§aL'Ancre", "§a", new String[]{
                "§fBon. Tu l'as trouvé. Plus vite que la moyenne, je note.",
                "§fMarché conclu. Tape §6/mine§f, ça t'emmène à ta première terre.",
                "§fLà-bas la roche garde des souvenirs. Tu la casses, tu les récupères. C'est pas plus compliqué.",
                "§7Bon voyage. Et oublie pas pourquoi tu creuses."
        }, () -> {
            // Acte I fini → on ENCHAÎNE directement sur l'objectif de l'Acte II (« Parle au
            // Contremaître ») au lieu de repasser en astuces génériques. Sinon, en allant à la
            // mine 1, la BossBar affichait le guide au lieu du dialogue à suivre.
            restoreObjective(p);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendTitle("§6§l⚓ L'AVENTURE COMMENCE", "§7Tape §6/mine §7pour partir", 15, 70, 25);
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§6✦ §eActe I terminé §7— §fla Grève des Oubliés te laisse partir.");
            p.sendMessage("§6✦ §7Tape §6/mine §7pour rejoindre ta première terre.");
            p.sendMessage("§8§m                                        ");
        });
    }

    // ===== Coffre PARTAGÉ anti-vide =====

    /** Coordonnées du coffre-quête (lues depuis config.yml), ou null si non défini. */
    public Location getChestLocation() {
        if (!plugin.getConfig().contains("acte1.chest.world")) return null;
        World w = Bukkit.getWorld(plugin.getConfig().getString("acte1.chest.world"));
        if (w == null) return null;
        return new Location(w,
                plugin.getConfig().getInt("acte1.chest.x"),
                plugin.getConfig().getInt("acte1.chest.y"),
                plugin.getConfig().getInt("acte1.chest.z"));
    }

    /** Définit (admin) le coffre-quête à un bloc donné et sauvegarde en config. */
    public void setChestLocation(Block block) {
        plugin.getConfig().set("acte1.chest.world", block.getWorld().getName());
        plugin.getConfig().set("acte1.chest.x", block.getX());
        plugin.getConfig().set("acte1.chest.y", block.getY());
        plugin.getConfig().set("acte1.chest.z", block.getZ());
        plugin.saveConfig();
    }

    /**
     * Tâche répétée : fait briller le coffre-quête avec des particules bleues (âmes + enchant),
     * visibles seulement par les joueurs qui doivent ENCORE le trouver (étape < 3).
     * Ainsi une fois le Jeton pris, la lueur disparaît pour ce joueur : la plage redevient calme.
     */
    public void startChestParticles() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location chest = getChestLocation();
            if (chest == null || chest.getWorld() == null) return;
            Location center = chest.clone().add(0.5, 1.0, 0.5);
            for (Player p : chest.getWorld().getPlayers()) {
                // On ne montre la lueur qu'aux joueurs qui n'ont pas encore trouvé le Jeton.
                if (getStep(p) >= 3) continue;
                // Ne l'afficher qu'à portée raisonnable (évite le spam de paquets).
                if (p.getLocation().distanceSquared(center) > 60 * 60) continue;
                p.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 6, 0.25, 0.5, 0.25, 0.0);
                p.spawnParticle(Particle.ENCHANT, center.clone().add(0, 0.4, 0), 10, 0.35, 0.6, 0.35, 0.4);
            }
        }, 20L, 12L); // toutes les 0,6s environ
    }

    private boolean isQuestChest(Block block) {
        if (block == null) return false;
        Location chest = getChestLocation();
        if (chest == null) return false;
        return block.getWorld().equals(chest.getWorld())
                && block.getX() == chest.getBlockX()
                && block.getY() == chest.getBlockY()
                && block.getZ() == chest.getBlockZ();
    }

    /**
     * Intercepte l'ouverture du coffre-quête : on N'OUVRE PAS l'inventaire réel.
     * Chaque joueur reçoit SA copie du Jeton une seule fois ; le coffre ne se vide jamais.
     */
    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // L'événement se déclenche 2× par clic (main principale + main secondaire) :
        // on ne traite QUE la main principale pour éviter les doubles messages/actions.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!isQuestChest(event.getClickedBlock())) return;

        // On empêche l'ouverture normale du coffre (sinon inventaire partagé qui se vide).
        event.setCancelled(true);
        Player p = event.getPlayer();
        Location chestLoc = event.getClickedBlock().getLocation();

        // Le coffre ne s'ouvre que si le joueur a reçu l'objectif de l'Ancre (étape >= 2).
        // Tant qu'il n'a pas parlé au Veilleur PUIS à l'Ancre, le coffre reste scellé.
        if (getStep(p) < 2) {
            p.sendMessage("§7Le coffre est scellé. Va d'abord parler au §bVeilleur§7, puis à §al'Ancre§7.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }

        PersistentDataContainer pdc = p.getPersistentDataContainer();

        // Anti-duplication : on ne donne rien tant qu'il en a déjà un sur lui.
        if (hasJetonInInventory(p)) {
            p.sendMessage("§7Le coffre est vide. Tu as déjà pris ce qu'il contenait.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }
        // Acte I terminé : le Jeton n'a plus aucune utilité, le coffre se referme pour de bon.
        if (getStep(p) >= 4) {
            p.sendMessage("§7Le coffre est vide. Tu as déjà pris ce qu'il contenait.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }
        // ⚠ CORRIGÉ 2026-08-23 : on redonne SA copie même s'il l'avait déjà prise. Avant, la clé
        // « jeton_pris » bloquait le coffre à VIE : un joueur qui perdait son Jeton (mort dans
        // l'End, suppression en créatif, dépôt dans un coffre) restait coincé à l'Acte I pour
        // toujours — l'Ancre l'envoyait au coffre, et le coffre le renvoyait à l'Ancre.
        // Aucune duplication possible : les deux gardes ci-dessus couvrent les seuls cas où
        // un second Jeton aurait un sens.

        // Donne SA copie du Jeton.
        p.getInventory().addItem(createJeton());
        pdc.set(jetonTakenKey, PersistentDataType.BYTE, (byte) 1);

        // Effets « souvenir qui remonte ».
        p.playSound(chestLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 1f);
        p.playSound(chestLoc, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        World w = chestLoc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.SOUL, chestLoc.clone().add(0.5, 0.9, 0.5), 25, 0.3, 0.4, 0.3, 0.02);
        }
        p.sendTitle("§b§lJeton de Marée", "§7Tu l'as trouvé...", 10, 60, 20);

        // Fait avancer l'étape (si le joueur en était à « trouve le coffre »).
        if (getStep(p) < 3) {
            setStep(p, 3);
            plugin.getGuide().setQuestObjective(p, OBJ_RETOUR);
        }
    }

    // ==========================================================================
    // ===============================  ACTE II  ================================
    // =====================  « La Première Terre » (île mine)  =================
    // ==========================================================================

    // ----- Acte II : progression -----

    public int getStep2(Player p) {
        return p.getPersistentDataContainer().getOrDefault(step2Key, PersistentDataType.INTEGER, 0);
    }

    private void setStep2(Player p, int step) {
        p.getPersistentDataContainer().set(step2Key, PersistentDataType.INTEGER, step);
    }

    /** L'Acte II est-il terminé ? (le joueur a récupéré pioche + sac auprès des PNJ) */
    public boolean isActe2Done(Player p) {
        return getStep2(p) >= 3;
    }

    /** Le joueur possède-t-il déjà son sac + sa pioche (Acte II fini) ? Sert à décider si on les donne. */
    public boolean hasEarnedTools(Player p) {
        return isActe2Done(p);
    }

    // ----- Acte II : item « Tête de Pioche des Souvenirs » -----

    /** Crée une copie de la Tête de Pioche des Souvenirs (item-quête, tag PDC anti-triche). */
    public ItemStack createTete() {
        ItemStack tete = new ItemStack(Material.NETHERITE_SCRAP);
        ItemMeta meta = tete.getItemMeta();
        meta.setDisplayName("§6§lTête de Pioche des Souvenirs");
        meta.setLore(Arrays.asList(
                "§7Un morceau de métal ancien, lourd de mémoire.",
                "§7Entre de bonnes mains, il redeviendra une pioche.",
                "",
                "§eApporte-la au §cForgeron§e."));
        meta.getPersistentDataContainer().set(teteItemKey, PersistentDataType.STRING, "tete_pioche");
        tete.setItemMeta(meta);
        return tete;
    }

    public boolean isTete(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "tete_pioche".equals(
                item.getItemMeta().getPersistentDataContainer().get(teteItemKey, PersistentDataType.STRING));
    }

    public boolean hasTeteInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isTete(item)) return true;
        }
        return false;
    }

    private void removeOneTete(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTete(contents[i])) {
                ItemStack it = contents[i];
                if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
                else p.getInventory().setItem(i, null);
                return;
            }
        }
    }

    // ----- Acte II : dialogues des PNJ -----

    /** Clic droit sur LE CONTREMAÎTRE : accueille, donne le sac, lance la quête de la Tête de Pioche. */
    public void onTalkContremaitre(Player p) {
        int step = getStep2(p);

        if (step >= 3) {
            // Niveau de pioche 45 : l'histoire de la Plume (une seule fois débloque la recherche).
            if (plugin.getPickaxeLevel(p) >= 45 && !hasHeardPlumeStory(p)) {
                // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». 8 lignes -> 5.
                // Il raconte la légende de l'atelier : à moitié sceptique, mais c'est pour ça que
                // les vieux d'ici continuent de creuser. Même registre que le reste de l'Acte II.
                dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                        "§fApproche. Tu as cassé assez de pierre pour entendre ce qui se dit entre nous.",
                        "§fOn raconte qu'un Oublié est tombé si profond qu'il est passé au travers du fond du monde, et qu'il a trouvé une §e§lPlume§f qui flottait dans le vide.",
                        "§fElle rend léger celui qui la porte, au point qu'il peut travailler sans jamais poser les pieds.",
                        "§fElle ne se montre qu'à ceux qui cassent de la pierre sans s'arrêter, et encore, pas souvent. §7Un bloc sur mille, à peu près.",
                        "§fEt si tu la trouves, ne viens pas me la montrer. Va la donner à ta pioche."
                }, () -> {
                    if (!hasHeardPlumeStory(p)) {
                        setHeardPlumeStory(p);
                        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                        p.sendTitle("§e✦ La Plume", "§7Trouve-la en minant (1/1000)", 10, 60, 20);
                    }
                });
                return;
            }
            // Rappel s'il a entendu l'histoire mais pas encore trouvé la Plume.
            if (hasHeardPlumeStory(p) && !hasFoundPlume(p)) {
                dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                        "§fLa §e§lPlume§f se cache encore dans la roche. Mine, gamin, mine... elle finira par se montrer."
                }, null);
                return;
            }
            dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                    "§fTu creuses bien, gamin. La roche n'a qu'à bien se tenir."
            }, null);
            return;
        }

        if (step == 0) {
            // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». 10 lignes -> 6.
            // Registre commun aux PNJ de l'Acte I : de VRAIES phrases. Ni énumérations à quatre
            // virgules, ni hachis de phrases de trois mots — les deux se repèrent immédiatement.
            // Ce qui a été coupé et ne doit pas revenir :
            //   • les itinéraires pas à pas (« traverse la mine », « le bâtiment rond et bleu »)
            //     → la boussole de quête (QuestCompass) pointe le coffre puis le Forgeron ;
            //     on garde les NOMS de lieux, qui font partie du récit.
            // Ce qui a été GARDÉ sur demande : les deux lignes qui expliquent le sac, mais dites
            // comme un chef de chantier décrit un outil, pas comme une notice.
            dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                    "§fTe voilà sur la §6Première Terre§f. C'est ici que tout le monde commence et que beaucoup s'arrêtent.",
                    "§fPrends ce §6sac§f avant toute chose. C'est le seul que j'ai en trop et je le reverrai pas.",
                    "§fTout ce que tu casses tombe dedans sans que tu aies à te baisser une seule fois.",
                    "§fLe jour où il sera trop petit tu pourras l'agrandir contre de l'argent, comme tout ici.",
                    "§fTu descendras dans le §5vieux puits§f pour y prendre une §6Tête de Pioche des Souvenirs§f, et je te conseille de ne pas traîner en bas.",
                    "§fLe §cForgeron§f s'occupera du reste. C'est le seul qui sait faire ça et il le fait pas pour n'importe qui."
            }, () -> {
                if (getStep2(p) < 1) {
                    setStep2(p, 1);
                    // Donne le sac (via l'API du plugin).
                    plugin.giveBagPublic(p);
                    plugin.getGuide().setQuestObjective(p, OBJ2_TETE);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    p.sendTitle("§6Sac de Minage reçu", "§7Trouve la Tête de Pioche", 10, 60, 20);
                }
            });
            return;
        }

        // step 1 ou 2 : il a le sac mais pas encore fini.
        if (hasTeteInInventory(p)) {
            dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                    "§fAh, tu l'as. Alors ne reste pas là, le §cForgeron§f t'attend."
            }, null);
        } else {
            dialogues.startDialogue(p, "§6Le Contremaître", "§6", new String[]{
                    "§fLe coffre est au fond du §5vieux puits§f et il y sera encore demain si tu continues à traîner ici."
            }, null);
        }
    }

    /** Clic droit sur LE FORGERON : échange la Tête de Pioche contre la vraie pioche, clôt l'Acte II. */
    public void onTalkForgeron(Player p) {
        int step = getStep2(p);

        if (step >= 3) {
            dialogues.startDialogue(p, "§cLe Forgeron", "§c", new String[]{
                    "§fElle a l'air d'aller. Reviens me voir quand elle commencera à te parler."
            }, null);
            return;
        }

        if (step == 0) {
            dialogues.startDialogue(p, "§cLe Forgeron", "§c", new String[]{
                    "§fReviens quand tu auras quelque chose à me donner. Le §6Contremaître§f t'expliquera quoi."
            }, null);
            return;
        }

        if (!hasTeteInInventory(p)) {
            dialogues.startDialogue(p, "§cLe Forgeron", "§c", new String[]{
                    "§fPas de Tête, pas de pioche. Elle est au fond du §5puits§f et elle §bbrille§f, tu ne peux pas la manquer."
            }, null);
            return;
        }

        // Il a la Tête : on forge la pioche.
        // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». 10 lignes -> 5.
        // Le Forgeron est l'artisan : il parle du métal, il remarque ce que les autres ne voient
        // pas, et il en sait plus qu'il ne veut en dire.
        // ⚠ La ligne 4 est la SEULE introduction de l'Écho Premier avant l'Arc II : elle a été
        // retirée du Veilleur (choix du user), donc c'est ici que le joueur apprend le but du jeu.
        // Supprimée au passage : la didascalie « Le métal chauffe, chante, prend forme » — rythme
        // ternaire en rafale, exactement le tic que le user a signalé.
        dialogues.startDialogue(p, "§cLe Forgeron", "§c", new String[]{
                "§fPose ça sur l'enclume et recule. Ça va être chaud et je n'ai pas envie de te soigner en plus.",
                "§fTiens. Ta pioche. Elle tiendra plus longtemps que tes bras, je te le garantis.",
                "§fIl y a quelque chose dans ce métal que je n'arrive pas à lire. Ça fait quarante ans que je forge et c'est la première fois.",
                "§fIl paraît qu'il existe quelque chose au bout des mers, l'§6Écho Premier§f. Si quelqu'un doit comprendre ce métal, ce sera celui qui l'atteindra.",
                "§7Une dernière chose. Le §5portail§7 qui luit au fond du puits, ne t'en approche pas encore. Il s'ouvrira quand tu seras prêt, pas avant."
        }, () -> {
            removeOneTete(p);
            setStep2(p, 3);
            plugin.givePickaxePublic(p);
            plugin.getGuide().clearQuestObjective(p);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendTitle("§6§l⛏ TA PREMIÈRE PIOCHE", "§7La Première Terre t'accueille", 15, 70, 25);
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§6✦ §eActe II terminé §7— §fte voilà vrai mineur.");
            p.sendMessage("§8§m                                        ");
            // Enchaîne sur le parcours de découverte guidé (Acte III).
            plugin.getTutorial().start(p);
        });
    }

    // ----- Acte II : coffre de la Tête de Pioche -----

    public Location getChest2Location() {
        if (!plugin.getConfig().contains("acte2.chest.world")) return null;
        World w = Bukkit.getWorld(plugin.getConfig().getString("acte2.chest.world"));
        if (w == null) return null;
        return new Location(w,
                plugin.getConfig().getInt("acte2.chest.x"),
                plugin.getConfig().getInt("acte2.chest.y"),
                plugin.getConfig().getInt("acte2.chest.z"));
    }

    public void setChest2Location(Block block) {
        plugin.getConfig().set("acte2.chest.world", block.getWorld().getName());
        plugin.getConfig().set("acte2.chest.x", block.getX());
        plugin.getConfig().set("acte2.chest.y", block.getY());
        plugin.getConfig().set("acte2.chest.z", block.getZ());
        plugin.saveConfig();
    }

    private boolean isQuestChest2(Block block) {
        if (block == null) return false;
        Location chest = getChest2Location();
        if (chest == null) return false;
        return block.getWorld().equals(chest.getWorld())
                && block.getX() == chest.getBlockX()
                && block.getY() == chest.getBlockY()
                && block.getZ() == chest.getBlockZ();
    }

    /** Lueur bleue permanente sur le coffre II, visible seulement par ceux qui cherchent la Tête (step2 == 1). */
    public void startChest2Particles() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location chest = getChest2Location();
            if (chest == null || chest.getWorld() == null) return;
            Location center = chest.clone().add(0.5, 1.0, 0.5);
            for (Player p : chest.getWorld().getPlayers()) {
                if (getStep2(p) != 1) continue; // seulement pendant la recherche
                if (p.getLocation().distanceSquared(center) > 60 * 60) continue;
                p.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 6, 0.25, 0.5, 0.25, 0.0);
                p.spawnParticle(Particle.ENCHANT, center.clone().add(0, 0.4, 0), 10, 0.35, 0.6, 0.35, 0.4);
            }
        }, 20L, 12L);
    }

    // ----- Acte II : le SECRET DU MÉTAL (murmure du Puits des Souvenirs) -----
    //
    // Historique : ce secret se déclenchait à la proximité d'un « fond du puits » défini par
    // /pnj setpuits. Cette position n'a jamais été renseignée en config, donc le murmure ne
    // s'est jamais joué pour personne. Il part désormais automatiquement 4 s après la prise
    // de la Tête de Pioche au coffre de l'Acte II (voir takeTete), une seule fois par joueur.

    /**
     * Lore de la Renaissance (Prestige) — un court texte par jalon. Voir
     * « Serv prison/PRESTIGE_RENAISSANCE.md » §2. Appelé après chaque renaissance réussie.
     */
    public void raconterRenaissance(Player p, int prestige) {
        String texte;
        switch (prestige) {
            // Réécrits le 2026-08-24 — voir « Serv prison/DIALOGUES_PNJ.md ».
            // ⚠️ Les mots « Abysses » et « abîme » ont été RETIRÉS (règle : plus jamais ce registre).
            // Le vocabulaire du Prestige est : le Portail, le Puits des Souvenirs, un René, un Oublié.
            case 1:
                texte = "§5§oTu es ressorti du Portail sans aucun souvenir du chemin. Ta pioche est neuve et la roche ne te reconnaît plus. On appelle ça être un §6René§5§o.";
                break;
            case 2:
            case 3:
                texte = "§5§oIl y a des marques dans la pierre du couloir. D'autres sont passés ici, et pas une ou deux fois.";
                break;
            case 5:
                texte = "§5§oUn §7Oublié §5§ote regarde passer sans reculer. Il a compris que tu étais descendu autant de fois que lui.";
                break;
            case 10:
                texte = "§6§oDix fois. À force de tout oublier, tu ne sais plus très bien ce que tu étais venu chercher ici.";
                break;
            default:
                texte = null;
        }
        if (texte == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.sendMessage("§8§m                                        ");
            p.sendMessage(texte);
            p.sendMessage("§8§m                                        ");
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
        }, 90L); // ~4,5 s après, le temps que le title de renaissance s'estompe.
    }

    /**
     * Révèle le secret du métal (une seule fois par joueur, clé PDC puits_vu).
     * Déclenché 4 s après la prise de la Tête de Pioche au coffre de l'Acte II.
     * La garde « déjà vu » est ICI pour que tout appelant soit protégé.
     */
    private void revealPuitsSecret(Player p) {
        if (p.getPersistentDataContainer().has(puitsSeenKey, PersistentDataType.BYTE)) return;
        p.getPersistentDataContainer().set(puitsSeenKey, PersistentDataType.BYTE, (byte) 1);
        // Ambiance sonore grave.
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 0.7f);
        // Titre + lore secret (indice sur le secret de la pioche).
        // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». 6 lignes -> 4.
        // Supprimées : « la roche saigne des souvenirs » et « un murmure monte du fond, sans bouche
        // pour le porter » — les deux images citées en exemple dans le diagnostic d'écriture.
        // L'Écho Premier n'est plus mentionné ici : le Forgeron reste sa seule introduction.
        p.sendTitle("§5§lLE PUITS DES SOUVENIRS", "§7Il y a une voix, ici", 15, 70, 25);
        p.sendMessage("§8§m                                        ");
        p.sendMessage("§5✦ §oTu es descendu là où plus personne ne descend.");
        p.sendMessage("§7§oQuelque chose parle, et ça vient du métal que tu tiens.");
        p.sendMessage("§d\"§oCe n'est pas un morceau de fer que tu as ramassé. C'est quelque chose qui a faim.");
        p.sendMessage("§d§oElle se réveillera à mesure que tu creuseras. §fOuvre-la d'un clic droit§d§o, tu verras ce qu'elle sait faire.\"");
        p.sendMessage("§8§m                                        ");
    }

    /**
     * ACTE II : coffre de la Tête de Pioche des Souvenirs.
     * Même principe anti-vide que l'Acte I : chaque joueur reçoit SA copie une seule fois.
     */
    @EventHandler
    public void onChest2Interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!isQuestChest2(event.getClickedBlock())) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        Location chestLoc = event.getClickedBlock().getLocation();

        // Scellé tant que le joueur n'a pas reçu la quête du Contremaître (step2 >= 1).
        if (getStep2(p) < 1) {
            p.sendMessage("§7Le coffre est scellé. Va d'abord parler au §6Contremaître§7.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }

        PersistentDataContainer pdc = p.getPersistentDataContainer();

        // Même correction que le coffre de l'Acte I (voir onChestInteract) : sans elle, perdre
        // la Tête de Pioche signifiait ne JAMAIS obtenir sa pioche, donc ne jamais finir l'Acte II.
        if (hasTeteInInventory(p)) {
            p.sendMessage("§7Le coffre est vide. Tu as déjà pris ce qu'il contenait.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }
        // Acte II terminé (la pioche est forgée) : la Tête ne sert plus à rien.
        if (getStep2(p) >= 3) {
            p.sendMessage("§7Le coffre est vide. Tu as déjà pris ce qu'il contenait.");
            p.playSound(chestLoc, Sound.BLOCK_CHEST_LOCKED, 0.7f, 1f);
            return;
        }

        p.getInventory().addItem(createTete());
        pdc.set(teteTakenKey, PersistentDataType.BYTE, (byte) 1);

        p.playSound(chestLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 1f);
        p.playSound(chestLoc, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        World w = chestLoc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.SOUL, chestLoc.clone().add(0.5, 0.9, 0.5), 25, 0.3, 0.4, 0.3, 0.02);
        }
        p.sendTitle("§6§lTête de Pioche", "§7Tu l'as trouvée...", 10, 60, 20);

        if (getStep2(p) < 2) {
            setStep2(p, 2);
            plugin.getGuide().setQuestObjective(p, OBJ2_FORGERON);
        }

        // Le secret du métal se révèle 4 s après la prise de la Tête — le temps que le title
        // « Tête de Pioche » s'efface. Une seule fois par joueur (clé PDC puits_vu).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) revealPuitsSecret(p);
        }, 80L);
    }

    /**
     * Réinitialise SEULEMENT l'Acte II (outil de test) : étape 0, retire la Tête de Pioche,
     * réautorise la prise au coffre II, ET retire le sac + la pioche (on repart mains vides).
     * Remet l'objectif BossBar au début de l'Acte II (parler au Contremaître).
     * Ne touche PAS à l'Acte I (le joueur reste "aventure débloquée").
     */
    public void resetActe2(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        setStep2(p, 0);
        pdc.remove(teteTakenKey);
        pdc.remove(puitsSeenKey); // ré-autorise la découverte du secret du puits
        while (hasTeteInInventory(p)) removeOneTete(p);
        // On retire les outils pour vraiment refaire l'Acte II depuis le début.
        plugin.stripToolsPublic(p);
        // Acte III (parcours de découverte) suit l'Acte II : on le remet à zéro aussi.
        plugin.getTutorial().reset(p);
        // Réaffiche le bon objectif (Contremaître si Acte I fini, sinon rien de spécial).
        if (isActeDone(p)) plugin.getGuide().setQuestObjective(p, OBJ2_CONTREMAITRE);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.2f);
    }

    /**
     * Réinitialise SEULEMENT l'Acte I (outil de test) : étape 0, retire le Jeton,
     * réautorise la prise au coffre I. NOTE : comme l'Acte II suit l'Acte I,
     * on remet aussi l'Acte II à zéro (sinon état incohérent).
     */
    public void resetActe1(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        // ----- Acte I -----
        setStep(p, 0);
        pdc.remove(jetonTakenKey);
        while (hasJetonInInventory(p)) removeOneJeton(p);
        // ----- Acte II (forcément remis à zéro puisqu'il suit l'Acte I) -----
        setStep2(p, 0);
        pdc.remove(teteTakenKey);
        pdc.remove(puitsSeenKey);
        while (hasTeteInInventory(p)) removeOneTete(p);
        plugin.stripToolsPublic(p);
        // Acte III (parcours de découverte) remis à zéro aussi.
        plugin.getTutorial().reset(p);
        // Remet l'objectif du tout début.
        plugin.getGuide().setQuestObjective(p, OBJ_VEILLEUR);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.2f);
    }

    /** Réinitialise TOUTE la progression (Acte I + Acte II). */
    public void resetQuest(Player p) {
        resetActe1(p); // resetActe1 remet déjà l'Acte II à zéro
    }

    /** Réaffiche le bon objectif de BossBar à la connexion selon l'étape en cours. */
    public void restoreObjective(Player p) {
        // Acte I d'abord (tant qu'il n'est pas fini).
        if (!isActeDone(p)) {
            switch (getStep(p)) {
                case 0: plugin.getGuide().setQuestObjective(p, OBJ_VEILLEUR); break;
                case 1: plugin.getGuide().setQuestObjective(p, OBJ_ANCRE); break;
                case 2: plugin.getGuide().setQuestObjective(p, OBJ_COFFRE); break;
                case 3: plugin.getGuide().setQuestObjective(p, OBJ_RETOUR); break;
                default: break;
            }
            return;
        }
        // Acte I fini : on affiche l'objectif de l'Acte II tant qu'il n'est pas terminé.
        switch (getStep2(p)) {
            case 0: plugin.getGuide().setQuestObjective(p, OBJ2_CONTREMAITRE); break;
            case 1: plugin.getGuide().setQuestObjective(p, OBJ2_TETE); break;
            case 2: plugin.getGuide().setQuestObjective(p, OBJ2_FORGERON); break;
            default: /* 3+ : Acte II fini, aventure libre */ break;
        }
    }

    // ==========================================================================
    // ===============================  ACTE IV  ================================
    // ============  « L'Ordinateur Quantique » (niveau de pioche 50)  ==========
    // ==========================================================================
    //
    // Quand le joueur atteint le niveau de pioche 50, une cinématique automatique
    // se joue et lui remet l'ORDINATEUR QUANTIQUE : un appareil capable de lire
    // les armures des anciens mineurs (des Oubliés qui ne sont pas remontés) et
    // d'en extraire des Fragments de Souvenir.

    /** Crée une copie de l'Ordinateur Quantique (item identifiable par tag PDC anti-triche). */
    public ItemStack createOrdinateurQuantique() {
        ItemStack pc = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = pc.getItemMeta();
        meta.setDisplayName("§b§lOrdinateur Quantique");
        meta.setLore(Arrays.asList(
                "§7Un appareil froid qui pulse d'une lueur bleue.",
                "§7Il capte les §bsouvenirs figés§7 dans le métal",
                "§7des anciens mineurs qui ne sont jamais remontés.",
                "",
                "§7Récupère leurs §farmures§7 et recycle-les ici",
                "§7pour en extraire des §dFragments de Souvenir§7.",
                "",
                "§e§oClic droit pour ouvrir le recyclage"));
        meta.getPersistentDataContainer().set(pcItemKey, PersistentDataType.STRING, "pc_quantique");
        pc.setItemMeta(meta);
        return pc;
    }

    /** Vrai si l'item est un vrai Ordinateur Quantique (vérifié par tag PDC, pas par nom). */
    public boolean isOrdinateurQuantique(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "pc_quantique".equals(
                item.getItemMeta().getPersistentDataContainer().get(pcItemKey, PersistentDataType.STRING));
    }

    /** Le joueur a-t-il déjà reçu son Ordinateur Quantique (une fois pour toutes) ? */
    public boolean hasReceivedPc(Player p) {
        return p.getPersistentDataContainer().has(pcGivenKey, PersistentDataType.BYTE);
    }

    /**
     * Déclenche la cinématique de l'Acte IV et remet l'Ordinateur Quantique.
     * Appelé quand le joueur atteint le niveau de pioche 50. Ne fait rien si déjà reçu.
     */
    public void triggerOrdinateurQuantique(Player p) {
        if (hasReceivedPc(p)) return;
        p.getPersistentDataContainer().set(pcGivenKey, PersistentDataType.BYTE, (byte) 1);

        // Ambiance : on laisse une seconde au joueur (téléport dans la mine) avant la scène.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 0.7f);
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
            p.sendTitle("§b§l⛬ SIGNAL CAPTÉ", "§7Quelque chose répond dans la roche...", 15, 60, 20);

            // Le dialogue narratif (voix off), enchaîné après le titre.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». 6 lignes -> 4.
                // Ce n'est PAS un personnage : pas d'humour ici, ce sont les mêmes règles d'écriture
                // (vraies phrases, aucune métaphore empilée) au service d'une voix qui dérange.
                // ⚠ Les 2 lignes de mécanique ont été SUPPRIMÉES : le lore de l'item Ordinateur
                // Quantique (createOrdinateurQuantique) dit déjà tout, et mieux — le joueur le lit
                // trois secondes plus tard. Ne pas les réintroduire.
                dialogues.startDialogue(p, "§b⛬ Voix du Métal", "§b", new String[]{
                        "§fPersonne n'était descendu aussi loin depuis très longtemps.",
                        "§fTu n'es pas seul en bas. Les autres y sont restés, avec leur matériel et tout ce qu'ils étaient.",
                        "§fTu croiseras leurs §farmures§f en creusant. Elles ont gardé un peu de ceux qui les portaient.",
                        "§fPrends ceci. Tu comprendras en le regardant."
                }, () -> {
                    if (!p.isOnline()) return;
                    // Place l'Ordinateur Quantique au slot 8 (verrouillé), en mine uniquement.
                    plugin.giveOrdiPublic(p);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
                    p.spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
                    p.sendTitle("§b§l⛬ ORDINATEUR QUANTIQUE", "§7Reçu — recycle les armures des Oubliés", 15, 70, 25);
                    p.sendMessage("§8§m                                        ");
                    p.sendMessage("§b✦ §fTu as reçu l'§bOrdinateur Quantique§f.");
                    p.sendMessage("§7✦ Récupère les §farmures§7 des anciens mineurs et recycle-les");
                    p.sendMessage("§7   pour obtenir des §dFragments de Souvenir§7.");
                    p.sendMessage("§8§m                                        ");
                });
            }, 60L); // ~3s après le titre
        }, 40L); // ~2s après l'arrivée dans la mine
    }

    // ===================================================================================
    //  MENU DE RECYCLAGE de l'Ordinateur Quantique (Acte IV — brique 1)
    //  Le joueur dépose les armures des anciens mineurs (Oubliés) et les recycle en
    //  Fragments de Souvenir. Rendement par matière (une pièce d'armure = X Fragments).
    // ===================================================================================

    static final String RECYCLER_TITLE = "§b§l⛬ Ordinateur Quantique";
    // Grille 6 lignes (54). La zone de dépôt des armures est au CENTRE (2 rangées de 5),
    // entourée de vitres bleues. La ligne du haut est une ligne de vitres bleues, sauf le
    // slot 4 qui garde la boussole d'info (intouchable). Bas = solde / bouton / collection.
    private static final int[] DEPOT_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int INFO_SLOT = 4;         // boussole « Recyclage des Souvenirs » (décor, intouchable)
    private static final int RECYCLE_BTN_SLOT = 49; // bouton « Recycler »
    private static final int SOLDE_SLOT = 45;       // affichage du solde de Fragments
    private static final int COLLECTION_SLOT = 53;  // compteur d'armures recyclées
    private static final int FORGE_BTN_SLOT = 47;   // bouton « ⚒️ Forge » (ouvre le menu de forge)
    private static final int TRI_BTN_SLOT = 51;     // hopper « ♻ Tri auto » (colonne 7, dernière ligne)

    /** Vrai si le slot du haut est une case de dépôt d'armure (les seules manipulables). */
    private static boolean estDepot(int slot) {
        for (int s : DEPOT_SLOTS) if (s == slot) return true;
        return false;
    }

    /**
     * Rendement en Fragments de Souvenir d'UNE pièce d'armure = ratio de la PIÈCE × facteur de MATIÈRE.
     * Ratios pièce (nb de matériaux) : bottes 4 · casque 5 · jambières 8 · plastron 13.
     * Facteurs matière : cuir 1 · mailles 3 · fer 6 · or 10 · diamant 24 · netherite 60.
     * Renvoie 0 si l'item n'est pas une armure recyclable.
     */
    static int fragmentsPour(ItemStack it) {
        if (it == null) return 0;
        int ratio = ratioPiece(it.getType());
        int facteur = facteurMatiere(it.getType());
        if (ratio == 0 || facteur == 0) return 0;
        return ratio * facteur * Math.max(1, it.getAmount());
    }

    /** Ratio d'une pièce selon son emplacement (0 si ce n'est pas une armure). */
    private static int ratioPiece(Material mat) {
        String n = mat.name();
        if (n.endsWith("_BOOTS")) return 4;
        if (n.endsWith("_HELMET") || mat == Material.TURTLE_HELMET) return 5;
        if (n.endsWith("_LEGGINGS")) return 8;
        if (n.endsWith("_CHESTPLATE")) return 13;
        return 0;
    }

    /** Facteur multiplicateur selon la matière (0 si non reconnue). */
    private static int facteurMatiere(Material mat) {
        String n = mat.name();
        if (n.startsWith("LEATHER") || mat == Material.TURTLE_HELMET) return 1;
        if (n.startsWith("CHAINMAIL")) return 3;
        if (n.startsWith("IRON")) return 6;
        if (n.startsWith("GOLDEN")) return 10;
        if (n.startsWith("DIAMOND")) return 24;
        if (n.startsWith("NETHERITE")) return 60;
        return 0;
    }

    /**
     * Clic droit avec l'Ordinateur Quantique : ouvre le menu de recyclage des armures.
     */
    @EventHandler
    public void onOrdinateurUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!isOrdinateurQuantique(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 1.4f);
        openRecycler(p);
    }

    /** Ouvre (ou rafraîchit) le menu de recyclage de l'Ordinateur Quantique. */
    public void openRecycler(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, RECYCLER_TITLE);
        decorateRecycler(inv, p);
        p.openInventory(inv);
    }

    /** (Re)pose le décor du menu (tout sauf les armures déjà déposées par le joueur). */
    private void decorateRecycler(Inventory inv, Player p) {
        // Fond en vitres bleues partout SAUF : les cases de dépôt (centre), la boussole (4)
        // et les boutons du bas (solde 45 / recycler 49 / collection 53).
        ItemStack glass = pane(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (estDepot(i)) continue;                 // cases de dépôt au centre : laissées vides
            if (i == INFO_SLOT) continue;              // boussole conservée à sa place
            if (i == SOLDE_SLOT || i == RECYCLE_BTN_SLOT || i == COLLECTION_SLOT
                    || i == FORGE_BTN_SLOT || i == TRI_BTN_SLOT) continue;  // boutons
            inv.setItem(i, glass);
        }
        // Boussole d'info (intouchable) : 1 ligne + invite à ouvrir la page détaillée (livre).
        ItemStack info = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§b§l⛬ Recyclage des Souvenirs");
        im.setLore(Arrays.asList(
                "§7Dépose les armures des Oubliés, puis §aRecycle§7.",
                "",
                "§e§oCliquez pour ouvrir le Codex §7(chances & paliers)"));
        info.setItemMeta(im);
        inv.setItem(INFO_SLOT, info);

        // Bouton Recycler (émeraude).
        ItemStack btn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta bm = btn.getItemMeta();
        bm.setDisplayName("§a§l✔ Recycler");
        bm.setLore(Arrays.asList(
                "§7Transforme toutes les armures déposées",
                "§7en §dFragments de Souvenir§7.",
                "",
                "§8Les objets non-armure te seront rendus."));
        btn.setItemMeta(bm);
        inv.setItem(RECYCLE_BTN_SLOT, btn);

        // Bouton Forge (enclume) : retravailler les bonus d'une armure contre des Fragments.
        ItemStack forge = new ItemStack(Material.ANVIL);
        ItemMeta fm = forge.getItemMeta();
        fm.setDisplayName("§6§l⚒ La Forge des Oubliés");
        fm.setLore(Arrays.asList(
                "§7Dépense des §dFragments§7 pour retravailler",
                "§7les bonus d'une armure d'Oublié :",
                "§8• §eReroll §7d'un bonus au hasard",
                "§8• §aAméliorer §7la valeur d'un bonus",
                "§8• §bAjouter §7un bonus (slot libre)",
                "",
                "§e§oCliquez pour ouvrir la Forge"));
        forge.setItemMeta(fm);
        inv.setItem(FORGE_BTN_SLOT, forge);

        // Hopper « Tri auto » : recycle automatiquement au drop les matières cochées.
        inv.setItem(TRI_BTN_SLOT, triItem(p));

        // Solde de Fragments.
        inv.setItem(SOLDE_SLOT, soldeItem(p));
        // Compteur de collection (armures recyclées).
        inv.setItem(COLLECTION_SLOT, collectionItem(p));
    }

    private ItemStack soldeItem(Player p) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§d§lFragments de Souvenir");
        m.setLore(Arrays.asList(
                "§7Ton solde : §d" + PrivateMines.formatNumberBig(
                        plugin.getFragments().getFragments(p)) + " ✦",
                "",
                "§8Monnaie des Oubliés — bientôt dépensable",
                "§8pour améliorer les armures et le lore."));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack collectionItem(Player p) {
        int nb = plugin.getFragments().getArmuresRecyclees(p);
        // Prochaine matière à débloquer (ou toutes débloquées).
        ArmorManager.Matiere prochaine = null;
        for (ArmorManager.Matiere m : ArmorManager.Matiere.values()) {
            if (!m.estDebloquee(nb)) { prochaine = m; break; }
        }
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§6§lCollection des Oubliés");
        m.setLore(Arrays.asList(
                "§7Armures recyclées : §e" + nb,
                "§7Paliers franchis : §a" + ArmorManager.nbPaliersBoost(nb) + " §8(cuir §c↓§8, hautes §a↑)",
                prochaine != null
                        ? "§7Prochaine matière : " + prochaine.couleur + prochaine.nom + " §7(" + prochaine.seuil + ")"
                        : "§aToutes les matières débloquées ✔",
                "",
                "§8Recycle pour débloquer de meilleures matières",
                "§8et augmenter ton taux de drop."));
        it.setItemMeta(m);
        return it;
    }

    /** Icône hopper du menu de recyclage : rappelle quelles matières sont en tri auto. */
    private ItemStack triItem(Player p) {
        int nb = 0;
        StringBuilder actives = new StringBuilder();
        for (ArmorManager.Matiere m : ArmorManager.Matiere.values()) {
            if (plugin.getFragments().isAutoRecycle(p.getUniqueId(), m.name())) {
                nb++;
                if (actives.length() > 0) actives.append("§7, ");
                actives.append(m.couleur).append(m.nom);
            }
        }
        ItemStack it = new ItemStack(Material.HOPPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§d§l♻ Tri automatique");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Recycle §dautomatiquement§7 en Fragments");
        lore.add("§7au drop §8(sans passer par ton inventaire).");
        lore.add("");
        lore.add("§7Une armure est recyclée si §aSA MATIÈRE");
        lore.add("§aest cochée§7, §lOU §r§7si son nb de bonus");
        lore.add("§7est §e≤ au seuil §8(les 2 sont indépendants).");
        lore.add("");
        lore.add(nb == 0 ? "§8Aucune matière en tri auto." : "§7Matières §8(" + nb + ") : " + actives);
        int seuilAff = plugin.getFragments().getAutoRecycleSeuil(p.getUniqueId());
        lore.add(seuilAff <= 0 ? "§7Seuil bonus : §7désactivé (0)"
                : "§7Seuil bonus : §e≤ " + seuilAff + " bonus");
        lore.add("");
        lore.add("§e§oCliquez pour choisir les matières");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack pane(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    /** Clics dans le menu de recyclage. */
    @EventHandler
    public void onRecyclerClick(InventoryClickEvent event) {
        if (!RECYCLER_TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean inTop = raw < top.getSize();

        // Dans le menu (haut) : seules les cases de dépôt sont manipulables ; tout le reste est du décor.
        if (inTop) {
            if (raw == RECYCLE_BTN_SLOT) {
                event.setCancelled(true);
                doRecycle(p, top);
                return;
            }
            if (raw == INFO_SLOT) {
                event.setCancelled(true);
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.2f);
                ouvrirLivreInfos(p);
                return;
            }
            if (raw == FORGE_BTN_SLOT) {
                event.setCancelled(true);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                openForge(p);
                return;
            }
            if (raw == TRI_BTN_SLOT) {
                event.setCancelled(true);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                openTri(p);
                return;
            }
            if (!estDepot(raw)) {
                // Vitres / solde / collection / boussole : intouchables (corrige le bug de récupération).
                event.setCancelled(true);
            }
            // (dans une case de dépôt : on laisse le joueur poser/reprendre librement)
            return;
        }
        // Shift-clic depuis l'inventaire du joueur : Bukkit répartit dans les cases libres du haut. OK.
    }

    // ===================================================================================
    //  TRI AUTOMATIQUE (menu du hopper) — recycle au drop les matières cochées
    //  Une case à cocher par matière (6). Clic = active/désactive. Persistant (fragments.yml).
    // ===================================================================================

    static final String TRI_TITLE = "§d§l♻ Tri automatique";
    private static final int[] TRI_SLOTS = {19, 20, 21, 23, 24, 25}; // une case par matière
    private static final int TRI_SEUIL_SLOT = 40; // bouton « seuil de bonus max à recycler » (1..5)
    private static final int TRI_BACK_SLOT = 49;

    /** Ouvre (ou rafraîchit) le menu de tri auto. */
    public void openTri(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TRI_TITLE);
        decorateTri(inv, p);
        p.openInventory(inv);
    }

    private void decorateTri(Inventory inv, Player p) {
        ItemStack bord = pane(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        ItemStack fond = pane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            int col = i % 9, row = i / 9;
            boolean contour = (row == 0 || row == 5 || col == 0 || col == 8);
            inv.setItem(i, contour ? bord : fond);
        }
        // En-tête d'aide.
        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§d§l♻ Tri automatique");
        im.setLore(Arrays.asList(
                "§7Coche les matières à §drecycler automatiquement§7.",
                "§7Quand une armure de ce type drope en minant,",
                "§7elle devient direct des §dFragments§7 (jamais dans l'inventaire).",
                "",
                "§8Vert = activé · Rouge = désactivé."));
        info.setItemMeta(im);
        inv.setItem(4, info);

        ArmorManager.Matiere[] mats = ArmorManager.Matiere.values();
        for (int i = 0; i < mats.length && i < TRI_SLOTS.length; i++) {
            ArmorManager.Matiere m = mats[i];
            boolean on = plugin.getFragments().isAutoRecycle(p.getUniqueId(), m.name());
            Material icone = on ? Material.LIME_DYE : Material.GRAY_DYE;
            ItemStack it = new ItemStack(icone);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(m.couleur + "§l" + m.nom + (on ? " §a✔" : " §c✖"));
            meta.setLore(Arrays.asList(
                    on ? "§aRecyclage auto ACTIVÉ" : "§7Recyclage auto désactivé",
                    "",
                    "§e§oClic pour " + (on ? "§cdésactiver" : "§aactiver")));
            it.setItemMeta(meta);
            inv.setItem(TRI_SLOTS[i], it);
        }

        // Bouton SEUIL : filtre INDÉPENDANT des matières — recycle toute armure ≤ N bonus, quelle que
        // soit sa matière (même non cochée). Une matière cochée recycle de son côté, peu importe les bonus.
        int seuil = plugin.getFragments().getAutoRecycleSeuil(p.getUniqueId());
        ItemStack seuilItem = new ItemStack(Material.COMPARATOR);
        seuilItem.setAmount(Math.max(1, seuil));
        ItemMeta sm = seuilItem.getItemMeta();
        if (seuil <= 0) {
            sm.setDisplayName("§b§l⚙ Seuil : §7désactivé (0)");
            sm.setLore(Arrays.asList(
                    "§7Le seuil ne recycle §caucune§7 armure.",
                    "§7Seules les §ematières cochées§7 sont recyclées.",
                    "",
                    "§8Le seuil recycle TOUTE armure ayant",
                    "§8≤ N bonus, quelle que soit sa matière.",
                    "",
                    "§a▶ Clic DROIT §7: monter le seuil (max §e6§7)",
                    "§c▶ Clic GAUCHE §7: baisser (min §e0§7)"));
        } else {
            sm.setDisplayName("§b§l⚙ Seuil : recycle jusqu'à §e" + seuil + " bonus");
            sm.setLore(Arrays.asList(
                    "§7Recycle auto §lTOUTE§r§7 armure ayant",
                    "§eau plus " + seuil + " bonus§7, §lpeu importe la matière§7.",
                    "§7Au-dessus (§e" + (seuil + 1) + "+§7), l'armure est §agardée§7.",
                    "",
                    "§8Indépendant des matières cochées :",
                    "§8une matière cochée est recyclée quoi qu'il arrive.",
                    "",
                    "§a▶ Clic DROIT §7: monter le seuil (max §e6§7)",
                    "§c▶ Clic GAUCHE §7: baisser (min §e0§7)"));
        }
        seuilItem.setItemMeta(sm);
        inv.setItem(TRI_SEUIL_SLOT, seuilItem);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§e◀ Retour au recyclage");
        back.setItemMeta(bm);
        inv.setItem(TRI_BACK_SLOT, back);
    }

    /** Clics dans le menu de tri auto. */
    @EventHandler
    public void onTriClick(InventoryClickEvent event) {
        if (!TRI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw >= top.getSize()) return;

        if (raw == TRI_BACK_SLOT) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
            openRecycler(p);
            return;
        }
        if (raw == TRI_SEUIL_SLOT) {
            // Clic droit = +1 (max 6, netherite) · clic gauche = -1 (min 0, désactivé).
            int delta = event.isRightClick() ? +1 : -1;
            int nouveau = plugin.getFragments().changeAutoRecycleSeuil(p.getUniqueId(), delta);
            // Son plus aigu quand on monte, plus grave quand on baisse.
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, delta > 0 ? 1.4f : 0.7f);
            decorateTri(top, p);
            return;
        }
        for (int i = 0; i < TRI_SLOTS.length; i++) {
            if (TRI_SLOTS[i] != raw) continue;
            ArmorManager.Matiere m = ArmorManager.Matiere.values()[i];
            boolean now = plugin.getFragments().toggleAutoRecycle(p.getUniqueId(), m.name());
            p.playSound(p.getLocation(), now ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.8f, now ? 1.4f : 0.8f);
            decorateTri(top, p);
            return;
        }
    }

    // ===================================================================================
    //  PAGE DÉTAILLÉE (menu GUI « grand livre », sans resource pack)
    //  Interface paginée façon page de codex : fond parchemin en vitres teintées,
    //  icônes réelles colorées, boutons Précédent/Suivant. 3 pages.
    // ===================================================================================

    // Le titre encode la page ouverte (1..3) pour que le clic sache où on est.
    private static final String INFO_TITLE_BASE = "§3§l✦ Codex de l'Ordinateur";
    private static final int INFO_PAGES = 3;
    private static final int INFO_PREV_SLOT = 45;  // ◀ page précédente
    private static final int INFO_BACK_SLOT = 49;  // retour au recyclage
    private static final int INFO_NEXT_SLOT = 53;  // page suivante ▶

    private static String infoTitle(int page) {
        String sous;
        switch (page) {
            case 1:  sous = "Trouver des armures"; break;
            case 2:  sous = "Recyclage & Fragments"; break;
            default: sous = "Collection des Oubliés"; break;
        }
        return INFO_TITLE_BASE + " §8» §b" + sous + " §7(" + page + "/" + INFO_PAGES + ")";
    }

    /** Retrouve le numéro de page (1..3) à partir du titre de la vue, ou 0 si ce n'est pas le codex. */
    private static int infoPageFromTitle(String title) {
        if (title == null || !title.startsWith(INFO_TITLE_BASE)) return 0;
        for (int pg = 1; pg <= INFO_PAGES; pg++) if (title.equals(infoTitle(pg))) return pg;
        return 0;
    }

    /**
     * Ouvre la page détaillée du recyclage (menu GUI paginé) : chances de trouver des armures
     * en minant + paliers d'amélioration, rendement du recyclage, et collection/rangs rares.
     * Les % sont des valeurs PRÉVUES (le drop en minant sera branché dessus plus tard).
     */
    public void ouvrirLivreInfos(Player p) {
        ouvrirCodex(p, 1);
    }

    public void ouvrirCodex(Player p, int page) {
        if (page < 1) page = 1;
        if (page > INFO_PAGES) page = INFO_PAGES;
        Inventory inv = Bukkit.createInventory(null, 54, infoTitle(page));

        // Fond « parchemin » : contour cyan, intérieur gris clair.
        ItemStack bord = pane(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack fond = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            int col = i % 9, row = i / 9;
            boolean contour = (row == 0 || row == 5 || col == 0 || col == 8);
            inv.setItem(i, contour ? bord : fond);
        }

        switch (page) {
            case 1:  remplirPageChances(inv, p); break;
            case 2:  remplirPageRendement(inv, p); break;
            default: remplirPageCollection(inv, p); break;
        }

        // Navigation.
        if (page > 1) inv.setItem(INFO_PREV_SLOT, navItem(Material.ARROW, "§a◀ Page précédente", "§7Page " + (page - 1)));
        inv.setItem(INFO_BACK_SLOT, navItem(Material.BARRIER, "§c✖ Retour au recyclage", "§7Revenir à l'Ordinateur"));
        if (page < INFO_PAGES) inv.setItem(INFO_NEXT_SLOT, navItem(Material.ARROW, "§aPage suivante ▶", "§7Page " + (page + 1)));

        p.openInventory(inv);
    }

    // ── Page 1 : progression des matières (débloquées en recyclant) + boost de drop ─────
    private void remplirPageChances(Inventory inv, Player p) {
        int recyclees = plugin.getFragments().getArmuresRecyclees(p);

        inv.setItem(4, decoItem(Material.IRON_ORE, "§b§l⚒ Matières débloquées",
                "§7En minant, tu ne peux dropper que les §fmatières",
                "§fdéjà débloquées§7. Recycle des armures pour",
                "§7débloquer les matières supérieures.",
                "",
                "§8Chaque tranche de §f" + ArmorManager.PAS_PALIER + " armures §8fait §aévoluer",
                "§8le drop : le §fcuir baisse§8, les §fmatières hautes montent§8."));

        // Les 6 matières, dans l'ordre, un item par matière (état débloqué/verrouillé + taux effectif).
        int[] slots = {19, 20, 21, 23, 24, 25};
        ArmorManager.Matiere[] mats = ArmorManager.Matiere.values();
        for (int i = 0; i < mats.length && i < slots.length; i++) {
            ArmorManager.Matiere m = mats[i];
            boolean ok = m.estDebloquee(recyclees);
            Material icone = plastronDe(m);
            double tauxEff = ArmorManager.dropPercentEffectif(icone, recyclees);
            double hausse = ArmorManager.haussePalier(m); // >0 = monte, <0 = baisse (additif par palier)
            int pct = (int) Math.round(hausse * 100);
            String sens = hausse >= 0
                    ? "§a↑ +" + pct + "%/palier"
                    : "§c↓ " + pct + "%/palier";
            String etat;
            if (ok) etat = "§2✔ Débloquée §8» drop §a" + String.format(java.util.Locale.US, "%.3f", tauxEff) + "%";
            else    etat = "§cVerrouillée §8» §7" + m.seuil + " armures (§e" + recyclees + "§7)";
            // Boost total cumulé + armures avant le prochain palier (seulement si débloquée).
            double boost = ArmorManager.boostDropMatiere(m, recyclees);
            int avant = ArmorManager.armuresAvantProchainPalier(m, recyclees);
            String ligneBoost = ok
                    ? "§7Boost actuel : §b×" + String.format(java.util.Locale.US, "%.2f", boost)
                            + " §8· prochain dans §e" + avant + " §8armures"
                    : "§8Boost débloqué une fois la matière obtenue";
            inv.setItem(slots[i], decoItem(ok ? icone : Material.GRAY_DYE,
                    (ok ? "§a§l" : "§8") + m.couleur + m.nom,
                    "§7Débloque à : §e" + m.seuil + " armures recyclées",
                    "§7Taux de base : §7" + String.format(java.util.Locale.US, "%.2f", ArmorManager.dropPercent(icone)) + "%",
                    "§7Évolution : " + sens,
                    ligneBoost,
                    "",
                    etat));
        }

        // Progression du joueur : compteur + paliers + prochain déblocage.
        inv.setItem(40, decoItem(Material.CLOCK, "§e§lTa progression",
                "§7Armures recyclées : §e" + recyclees,
                "§7Paliers franchis : §a" + ArmorManager.nbPaliersBoost(recyclees),
                "§7Prochain palier dans : §e" + (ArmorManager.PAS_PALIER - (recyclees % ArmorManager.PAS_PALIER)) + " armures",
                "§8À chaque palier : cuir §c↓§8, matières hautes §a↑",
                prochainMatiereTexte(recyclees)));
    }

    private Material plastronDe(ArmorManager.Matiere m) {
        try { return Material.valueOf(m.prefix + "_CHESTPLATE"); }
        catch (IllegalArgumentException e) { return Material.LEATHER_CHESTPLATE; }
    }

    /** Texte « prochaine matière à débloquer » pour la page 1. */
    private String prochainMatiereTexte(int recyclees) {
        for (ArmorManager.Matiere m : ArmorManager.Matiere.values()) {
            if (!m.estDebloquee(recyclees)) {
                return "§7Prochaine matière : " + m.couleur + m.nom + " §7(" + m.seuil + " armures)";
            }
        }
        return "§7Toutes les matières débloquées §a✔";
    }

    // ── Page 2 : rendement du recyclage (Fragments par matière) ─────────────────────────
    private void remplirPageRendement(Inventory inv, Player p) {
        int recyclees = plugin.getFragments().getArmuresRecyclees(p);
        inv.setItem(4, decoItem(Material.RECOVERY_COMPASS, "§d§l✦ Recyclage & Drop",
                "§7En minant, tu peux déterrer l'armure d'un Oublié.",
                "§7Recycle-la pour des §5Fragments de Souvenir§7.",
                "",
                "§8Chaque §fchiffre §8= §echance de drop §8/ §5rendement recyclage§8.",
                "§8Tu peux dropper §fn'importe quelle pièce §8: casque,",
                "§8plastron, jambières ou bottes §8(le plastron n'illustre)."));

        // Une icône par matière : rendement recyclage (fourchette) + drop effectif selon déblocage.
        inv.setItem(19, matiereItem(Material.LEATHER_CHESTPLATE,   "§fCuir",      recyclees));
        inv.setItem(20, matiereItem(Material.CHAINMAIL_CHESTPLATE, "§7Mailles",   recyclees));
        inv.setItem(21, matiereItem(Material.IRON_CHESTPLATE,      "§fFer",       recyclees));
        inv.setItem(23, matiereItem(Material.GOLDEN_CHESTPLATE,    "§6Or",        recyclees));
        inv.setItem(24, matiereItem(Material.DIAMOND_CHESTPLATE,   "§bDiamant",   recyclees));
        inv.setItem(25, matiereItem(Material.NETHERITE_CHESTPLATE, "§8Netherite", recyclees));

        inv.setItem(40, decoItem(Material.AMETHYST_SHARD, "§d§lFragments de Souvenir",
                "§7La monnaie des Oubliés.",
                "",
                "§8Bientôt dépensable pour améliorer les armures,",
                "§8révéler leur histoire et débloquer des rangs rares.",
                "",
                "§8§o(Chances de drop prévues ; le drop en minant arrive bientôt.)"));
    }

    // ── Page 3 : collection & progression vers les matières rares ───────────────────────
    private void remplirPageCollection(Inventory inv, Player p) {
        int recyclees = plugin.getFragments().getArmuresRecyclees(p);
        inv.setItem(4, decoItem(Material.BOOK, "§6§l✦ Collection des Oubliés",
                "§7Chaque armure retrouvée porte l'histoire",
                "§7d'un mineur qui n'est jamais remonté."));

        // Cible = prochaine matière à débloquer (ou netherite comme sommet).
        ArmorManager.Matiere prochaine = null;
        for (ArmorManager.Matiere m : ArmorManager.Matiere.values()) {
            if (!m.estDebloquee(recyclees)) { prochaine = m; break; }
        }
        int cible = prochaine != null ? prochaine.seuil : ArmorManager.Matiere.NETHERITE.seuil;
        inv.setItem(22, decoItem(Material.WRITABLE_BOOK, "§6§lTa collection",
                "§7Armures recyclées : §e" + recyclees,
                prochaine != null
                        ? "§7Prochaine matière : " + prochaine.couleur + prochaine.nom + " §7(" + cible + ")"
                        : "§aToutes les matières débloquées ✔",
                "",
                barreProgression(recyclees, Math.max(1, cible))));

        inv.setItem(31, decoItem(Material.NETHERITE_HELMET, "§5§l✦ Vers les matières rares",
                "§7Recycle pour débloquer §fMailles§7, §fFer§7, §6Or§7,",
                "§bDiamant §7puis §8Netherite§7 — et booster ton taux de drop.",
                "",
                "§8Chaque §f" + ArmorManager.PAS_PALIER + " armures §8» §a×1.15 §8de drop.",
                "§8§oLe recyclage n'est que le début du voyage."));
    }

    // ── petits helpers d'affichage ──────────────────────────────────────────────────────

    private ItemStack decoItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private ItemStack navItem(Material mat, String name, String sub) {
        return decoItem(mat, name, sub);
    }

    private ItemStack matiereItem(Material chestplate, String nom, int recyclees) {
        ArmorManager.Matiere mat = ArmorManager.matiereDe(chestplate);
        boolean debloquee = mat != null && mat.estDebloquee(recyclees);
        double dropEff = ArmorManager.dropPercentEffectif(chestplate, recyclees);
        double dropBase = ArmorManager.dropPercent(chestplate);
        // Fourchette de rendement : bottes (min) → plastron (max) pour cette matière.
        int min = fragmentsPour(new ItemStack(bootsDe(chestplate)));
        int max = fragmentsPour(new ItemStack(chestplate));
        String ligneDrop = debloquee
                ? "§7Drop actuel : §a" + String.format(java.util.Locale.US, "%.2f", dropEff) + "% "
                        + "§8(base " + String.format(java.util.Locale.US, "%.2f", dropBase) + "%)"
                : "§cVerrouillée §8» §7débloque à §e" + (mat != null ? mat.seuil : 0) + " armures";
        return decoItem(debloquee ? chestplate : Material.GRAY_DYE, (debloquee ? "" : "§8") + nom,
                ligneDrop,
                "§7Recyclage : §d" + min + " à " + max + " ✦",
                "§8   bottes " + min + " · casque " + fragmentsPour(new ItemStack(casqueDe(chestplate)))
                        + " · jambières " + fragmentsPour(new ItemStack(jambieresDe(chestplate)))
                        + " · plastron " + max,
                "",
                "§8Toutes les pièces (casque, plastron,",
                "§8jambières, bottes) peuvent tomber.");
    }

    // Variantes de pièce d'une même matière (à partir du plastron), pour afficher la fourchette.
    private static Material bootsDe(Material chestplate) {
        return Material.valueOf(chestplate.name().replace("_CHESTPLATE", "_BOOTS"));
    }
    private static Material casqueDe(Material chestplate) {
        return Material.valueOf(chestplate.name().replace("_CHESTPLATE", "_HELMET"));
    }
    private static Material jambieresDe(Material chestplate) {
        return Material.valueOf(chestplate.name().replace("_CHESTPLATE", "_LEGGINGS"));
    }

    /** Petite barre de progression texte (10 crans). */
    private String barreProgression(int val, int max) {
        int crans = Math.max(0, Math.min(10, (int) Math.round(10.0 * val / max)));
        StringBuilder b = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) b.append(i < crans ? "§a█" : "§7█");
        b.append("§8] §e").append(Math.min(100, (int) Math.round(100.0 * val / max))).append("%");
        return b.toString();
    }

    /** Clics dans le Codex (navigation entre pages + retour au recyclage). */
    @EventHandler
    public void onCodexClick(InventoryClickEvent event) {
        int page = infoPageFromTitle(event.getView().getTitle());
        if (page == 0) return;
        event.setCancelled(true); // menu purement informatif : rien n'est manipulable
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        Player p = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        if (raw == INFO_PREV_SLOT && page > 1) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
            ouvrirCodex(p, page - 1);
        } else if (raw == INFO_NEXT_SLOT && page < INFO_PAGES) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.3f);
            ouvrirCodex(p, page + 1);
        } else if (raw == INFO_BACK_SLOT) {
            p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 1.4f);
            openRecycler(p);
        }
    }

    /** Recycle toutes les armures déposées dans la zone du haut → Fragments de Souvenir. */
    private void doRecycle(Player p, Inventory top) {
        int totalFragments = 0;
        int nbArmures = 0;
        for (int slot : DEPOT_SLOTS) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType() == Material.AIR) continue;
            int gain = fragmentsPour(it);
            if (gain > 0) {
                totalFragments += gain;
                nbArmures += it.getAmount();
                top.setItem(slot, null); // consommé
            }
            // les non-armures restent en place (rendues à la fermeture / reprises par le joueur)
        }
        if (totalFragments <= 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§cAucune armure à recycler dans la rangée du haut."));
            return;
        }
        // Bonus « +% gain de recyclage » des armures d'Oublié portées.
        double recyPct = plugin.getArmorBonus(p).recyclePct;
        if (recyPct != 0) totalFragments = (int) Math.round(totalFragments * (1.0 + recyPct / 100.0));

        plugin.getFragments().addFragments(p, totalFragments);
        plugin.getFragments().addArmuresRecyclees(p.getUniqueId(), nbArmures);

        // Effets + retours.
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        p.spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        p.sendMessage("§b⛬ §fL'Ordinateur absorbe §e" + nbArmures + " armure(s)§f et en libère §d"
                + totalFragments + " Fragments de Souvenir§f.");
        p.sendMessage("§7   Solde : §d" + PrivateMines.formatNumberBig(
                plugin.getFragments().getFragments(p)) + " ✦");

        // Rafraîchit l'affichage (solde + collection).
        top.setItem(SOLDE_SLOT, soldeItem(p));
        top.setItem(COLLECTION_SLOT, collectionItem(p));
    }

    /** À la fermeture : on rend au joueur tout ce qui reste dans la zone de dépôt (armures non recyclées, objets divers). */
    @EventHandler
    public void onRecyclerClose(InventoryCloseEvent event) {
        if (!RECYCLER_TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getPlayer();
        Inventory top = event.getInventory();
        for (int slot : DEPOT_SLOTS) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType() == Material.AIR) continue;
            java.util.Map<Integer, ItemStack> reste = p.getInventory().addItem(it);
            // Si l'inventaire est plein, on jette au sol pour ne rien perdre.
            for (ItemStack drop : reste.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
            top.setItem(slot, null);
        }
    }

    // ===================================================================================
    //  LA FORGE DES OUBLIÉS (Acte IV — brique 2)
    //  Le joueur dépose UNE armure d'Oublié et dépense des Fragments de Souvenir pour :
    //   • Reroll  d'un bonus (change le type + re-tire la valeur)
    //   • Améliorer un bonus (+20 % de la valeur pleine, plafond ×2 la valeur max de rareté)
    //   • Ajouter un bonus dans un slot libre (selon la rareté)
    //  Coûts = base × facteur de rareté (voir ArmorManager). Écrit le PDC + le lore.
    // ===================================================================================

    static final String FORGE_TITLE = "§6§l⚒ La Forge des Oubliés";
    private static final int FORGE_DEPOT_SLOT   = 22;        // case où déposer l'armure à forger
    private static final int FORGE_REROLL_SLOT  = 29;        // reroll d'UN bonus AU HASARD
    private static final int FORGE_AMELIO_SLOT  = 31;        // améliorer UN bonus AU HASARD
    private static final int FORGE_REMOVE_SLOT  = 38;        // retirer UN bonus AU HASARD
    private static final int FORGE_ADD_SLOT     = 40;        // ajouter un bonus
    private static final int FORGE_SOLDE_SLOT   = 45;        // solde de Fragments
    private static final int FORGE_BACK_SLOT    = 49;        // retour au recyclage
    private static final int FORGE_INFO_SLOT    = 4;         // rappel d'aide (décor)

    /** Compteur d'opérations de forge par joueur : sert de graine pseudo-aléatoire (Math.random indispo). */
    private final java.util.Map<java.util.UUID, Long> forgeSeed = new java.util.HashMap<>();

    private long nextForgeSeed(Player p) {
        long s = forgeSeed.getOrDefault(p.getUniqueId(), (long) p.getUniqueId().hashCode());
        s = s * 6364136223846793005L + 1442695040888963407L;
        forgeSeed.put(p.getUniqueId(), s);
        return s;
    }

    /** Ouvre la Forge (menu vide, le joueur y dépose une armure). */
    public void openForge(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, FORGE_TITLE);
        decorateForge(inv, p, null);
        p.openInventory(inv);
    }

    /** (Re)pose le décor de la Forge en fonction de l'armure actuellement déposée (peut être null). */
    private void decorateForge(Inventory inv, Player p, ItemStack armure) {
        ItemStack glass = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack orange = pane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            int col = i % 9, row = i / 9;
            boolean contour = (row == 0 || row == 5 || col == 0 || col == 8);
            inv.setItem(i, contour ? orange : glass);
        }
        // La case de dépôt reste manipulable : on la vide du décor.
        inv.setItem(FORGE_DEPOT_SLOT, armure); // null = vide (le joueur y pose son armure)

        // Aide (décor).
        ItemStack info = new ItemStack(Material.ANVIL);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§l⚒ La Forge des Oubliés");
        im.setLore(Arrays.asList(
                "§7Dépose une §earmure d'Oublié§7 au centre,",
                "§7puis choisis une action ci-dessous.",
                "",
                "§8Les coûts montent avec la rareté de l'armure."));
        info.setItemMeta(im);
        inv.setItem(FORGE_INFO_SLOT, info);

        boolean estArmure = plugin.getArmor().isArmureOubli(armure);
        java.util.List<ArmorManager.BonusInstance> bonuses =
                estArmure ? plugin.getArmor().getBonuses(armure) : java.util.Collections.emptyList();

        // Récap des bonus actuels (pour l'affichage des lores) + combien sont encore améliorables.
        java.util.List<String> recapBonus = new java.util.ArrayList<>();
        int nbAmeliorables = 0;
        for (int i = 0; i < bonuses.size(); i++) {
            ArmorManager.BonusInstance bi = bonuses.get(i);
            boolean max = plugin.getArmor().estAuPlafond(armure, i);
            recapBonus.add("§8• " + bi.type.couleur + "+" + fmtForge(bi.valeur) + "% §7" + bi.type.label
                    + (max ? " §8(max)" : ""));
            if (!max) nbAmeliorables++;
        }

        // ── Bouton REROLL (UN bonus AU HASARD) ────────────────────────────────
        int coutReroll = estArmure ? plugin.getArmor().coutReroll(armure) : 0;
        ItemStack rr = new ItemStack(estArmure && !bonuses.isEmpty() ? Material.SUNFLOWER : Material.GRAY_DYE);
        ItemMeta rm = rr.getItemMeta();
        rm.setDisplayName("§e§l⟳ Reroll aléatoire");
        java.util.List<String> rlore = new java.util.ArrayList<>();
        if (!estArmure) {
            rlore.add("§7Dépose d'abord une armure d'Oublié.");
        } else if (bonuses.isEmpty()) {
            rlore.add("§cCette armure n'a aucun bonus à rerollen.");
        } else {
            rlore.add("§7Remplace §eun bonus tiré au hasard§7 par");
            rlore.add("§7un autre effet (nouvelle valeur).");
            rlore.add("§8§oTu ne choisis pas lequel.");
            rlore.add("");
            rlore.addAll(recapBonus);
            rlore.add("");
            rlore.add("§8Coût : §d" + coutReroll + " ✦");
            rlore.add("§e§oClic pour rerollen");
        }
        rm.setLore(rlore);
        rr.setItemMeta(rm);
        inv.setItem(FORGE_REROLL_SLOT, rr);

        // ── Bouton AMÉLIORER (UN bonus AU HASARD parmi les non-plafonnés) ─────
        int coutAmelio = estArmure ? plugin.getArmor().coutAmeliorer(armure) : 0;
        boolean peutAmeliorer = estArmure && nbAmeliorables > 0;
        ItemStack am = new ItemStack(peutAmeliorer ? Material.LIME_DYE
                : (estArmure && !bonuses.isEmpty() ? Material.BARRIER : Material.GRAY_DYE));
        ItemMeta am2 = am.getItemMeta();
        am2.setDisplayName("§a§l⬆ Améliorer aléatoire");
        java.util.List<String> alore = new java.util.ArrayList<>();
        if (!estArmure) {
            alore.add("§7Dépose d'abord une armure d'Oublié.");
        } else if (bonuses.isEmpty()) {
            alore.add("§cCette armure n'a aucun bonus à améliorer.");
        } else if (nbAmeliorables == 0) {
            alore.add("§c✖ Tous les bonus sont déjà au maximum.");
            alore.add("");
            alore.addAll(recapBonus);
        } else {
            alore.add("§7Augmente §aun bonus tiré au hasard§7");
            alore.add("§7parmi ceux pas encore au max.");
            alore.add("§8§oTu ne choisis pas lequel.");
            alore.add("");
            alore.addAll(recapBonus);
            alore.add("");
            alore.add("§8Coût : §d" + coutAmelio + " ✦");
            alore.add("§a§oClic pour améliorer");
        }
        am2.setLore(alore);
        am.setItemMeta(am2);
        inv.setItem(FORGE_AMELIO_SLOT, am);

        // ── Bouton RETIRER (UN bonus AU HASARD) ──────────────────────────────
        // Garde-fou : impossible si l'armure n'a qu'un seul bonus (on garde toujours 1 effet).
        int coutRetirer = estArmure ? plugin.getArmor().coutRetirer(armure) : 0;
        boolean peutRetirer = estArmure && bonuses.size() >= 2;
        ItemStack rem = new ItemStack(peutRetirer ? Material.REDSTONE
                : (estArmure && !bonuses.isEmpty() ? Material.BARRIER : Material.GRAY_DYE));
        ItemMeta rem2 = rem.getItemMeta();
        rem2.setDisplayName("§c§l✖ Retirer aléatoire");
        java.util.List<String> rlore2 = new java.util.ArrayList<>();
        if (!estArmure) {
            rlore2.add("§7Dépose d'abord une armure d'Oublié.");
        } else if (bonuses.isEmpty()) {
            rlore2.add("§cCette armure n'a aucun bonus à retirer.");
        } else if (bonuses.size() == 1) {
            rlore2.add("§c✖ Impossible : il ne reste qu'un bonus.");
            rlore2.add("§8Une armure garde toujours au moins 1 effet.");
            rlore2.add("");
            rlore2.addAll(recapBonus);
        } else {
            rlore2.add("§7Enlève §cun bonus tiré au hasard§7 de");
            rlore2.add("§7l'armure §8(libère un slot).");
            rlore2.add("§8§oTu ne choisis pas lequel.");
            rlore2.add("");
            rlore2.addAll(recapBonus);
            rlore2.add("");
            rlore2.add("§8Coût : §d" + coutRetirer + " ✦");
            rlore2.add("§c§oClic pour retirer");
        }
        rem2.setLore(rlore2);
        rem.setItemMeta(rem2);
        inv.setItem(FORGE_REMOVE_SLOT, rem);

        // Bouton Ajouter un bonus.
        int libres = estArmure ? plugin.getArmor().slotsLibres(armure) : 0;
        int coutAjout = estArmure ? plugin.getArmor().coutAjouter(armure) : 0;
        ItemStack add = new ItemStack(libres > 0 ? Material.LAPIS_LAZULI : Material.GRAY_DYE);
        ItemMeta ad = add.getItemMeta();
        ad.setDisplayName("§b§l✚ Ajouter un bonus");
        if (!estArmure) {
            ad.setLore(Arrays.asList("§7Dépose d'abord une armure d'Oublié."));
        } else if (libres <= 0) {
            ad.setLore(Arrays.asList(
                    "§7Slots utilisés : §e" + bonuses.size() + "§7/§e"
                            + plugin.getArmor().getRarete(armure).slots,
                    "",
                    "§c✖ Aucun slot libre (armure pleine)."));
        } else {
            ad.setLore(Arrays.asList(
                    "§7Slots libres : §a" + libres,
                    "",
                    "§7Ajoute un nouveau bonus dans un slot libre.",
                    "§8Coût : §d" + coutAjout + " ✦",
                    "",
                    "§b§oClic pour ajouter"));
        }
        add.setItemMeta(ad);
        inv.setItem(FORGE_ADD_SLOT, add);

        // Solde + retour.
        inv.setItem(FORGE_SOLDE_SLOT, soldeItem(p));
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bkm = back.getItemMeta();
        bkm.setDisplayName("§e◀ Retour au recyclage");
        back.setItemMeta(bkm);
        inv.setItem(FORGE_BACK_SLOT, back);
    }

    private static String fmtForge(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** Clics dans la Forge. */
    @EventHandler
    public void onForgeClick(InventoryClickEvent event) {
        if (!FORGE_TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean inTop = raw < top.getSize();

        if (inTop) {
            if (raw == FORGE_DEPOT_SLOT) {
                // On laisse le joueur poser/reprendre l'armure, puis on rafraîchit au tick suivant.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack cur = top.getItem(FORGE_DEPOT_SLOT);
                    decorateForge(top, p, cur);
                });
                return;
            }
            event.setCancelled(true);
            if (raw == FORGE_BACK_SLOT) {
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.0f);
                // Rendre l'armure déposée avant de repartir.
                rendreArmureForge(p, top);
                openRecycler(p);
                return;
            }
            if (raw == FORGE_REROLL_SLOT) { forgeAction(p, top, "REROLL"); return; }
            if (raw == FORGE_AMELIO_SLOT) { forgeAction(p, top, "AMELIO"); return; }
            if (raw == FORGE_REMOVE_SLOT) { forgeAction(p, top, "REMOVE"); return; }
            if (raw == FORGE_ADD_SLOT)    { forgeAction(p, top, "ADD"); return; }
            return;
        }
        // Shift-clic depuis l'inventaire du joueur vers la Forge : n'autoriser QUE le dépôt d'une armure.
        if (event.isShiftClick()) {
            ItemStack moved = event.getCurrentItem();
            if (!plugin.getArmor().isArmureOubli(moved) || top.getItem(FORGE_DEPOT_SLOT) != null) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            // Dépose UNE pièce dans la case, gère le reste.
            ItemStack one = moved.clone(); one.setAmount(1);
            top.setItem(FORGE_DEPOT_SLOT, one);
            if (moved.getAmount() > 1) { moved.setAmount(moved.getAmount() - 1); }
            else { event.setCurrentItem(null); }
            decorateForge(top, p, one);
        }
    }

    /** Exécute une action de forge (REROLL / AMELIO / ADD) sur l'armure déposée. */
    private void forgeAction(Player p, Inventory top, String action) {
        ItemStack armure = top.getItem(FORGE_DEPOT_SLOT);
        if (!plugin.getArmor().isArmureOubli(armure)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            forgeActionBar(p, "§cDépose d'abord une armure d'Oublié au centre.");
            return;
        }
        ArmorManager am = plugin.getArmor();
        int cout;
        switch (action) {
            case "REROLL": cout = am.coutReroll(armure);   break;
            case "AMELIO": cout = am.coutAmeliorer(armure); break;
            case "REMOVE": cout = am.coutRetirer(armure);   break;
            default:       cout = am.coutAjouter(armure);   break;
        }
        // Solde suffisant ?
        java.math.BigInteger coutBI = java.math.BigInteger.valueOf(cout);
        if (!plugin.getFragments().has(p.getUniqueId(), coutBI)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            forgeActionBar(p, "§cPas assez de Fragments §7(§d" + cout + " ✦§7 requis).");
            return;
        }

        boolean ok;
        String feedback;
        long seed = nextForgeSeed(p);
        switch (action) {
            case "REROLL":
                ok = am.rerollAleatoire(armure, seed);
                feedback = ok ? "§e⟳ Un bonus a été rerollé au hasard§7 !"
                              : "§cCette armure n'a aucun bonus à rerollen.";
                break;
            case "AMELIO":
                ok = am.ameliorerAleatoire(armure, seed);
                feedback = ok ? "§a⬆ Un bonus a été amélioré au hasard§7 !"
                              : "§cTous les bonus sont déjà au maximum.";
                break;
            case "REMOVE":
                ok = am.retirerBonusAleatoire(armure, seed);
                feedback = ok ? "§c✖ Un bonus a été retiré au hasard§7 !"
                              : "§cImpossible : l'armure doit garder au moins 1 bonus.";
                break;
            default: // ADD
                ok = am.ajouterBonus(armure, seed);
                feedback = ok ? "§b✚ Nouveau bonus ajouté§7 !" : "§cAucun slot libre (ou plus de bonus disponible).";
                break;
        }
        if (!ok) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            forgeActionBar(p, feedback);
            decorateForge(top, p, armure);
            return;
        }
        // Débit (atomique) + effets.
        if (!plugin.getFragments().withdraw(p.getUniqueId(), coutBI)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            forgeActionBar(p, "§cPas assez de Fragments §7(§d" + cout + " ✦§7 requis).");
            decorateForge(top, p, armure);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        p.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.02);
        forgeActionBar(p, feedback + " §8(−" + cout + " ✦)");
        top.setItem(FORGE_DEPOT_SLOT, armure);
        decorateForge(top, p, armure);
    }

    private void forgeActionBar(Player p, String msg) {
        p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(msg));
    }

    /** Rend au joueur l'armure déposée dans la Forge (inventaire ou sol si plein). */
    private void rendreArmureForge(Player p, Inventory top) {
        ItemStack it = top.getItem(FORGE_DEPOT_SLOT);
        if (it == null || it.getType() == Material.AIR) return;
        java.util.Map<Integer, ItemStack> reste = p.getInventory().addItem(it);
        for (ItemStack drop : reste.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
        top.setItem(FORGE_DEPOT_SLOT, null);
    }

    /** À la fermeture de la Forge : on rend l'armure déposée. */
    @EventHandler
    public void onForgeClose(InventoryCloseEvent event) {
        if (!FORGE_TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getPlayer();
        rendreArmureForge(p, event.getInventory());
    }

    /** Reset de l'Acte IV (outil de test) : oublie l'item reçu et le retire de l'inventaire. */
    public void resetActe4(Player p) {
        p.getPersistentDataContainer().remove(pcGivenKey);
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isOrdinateurQuantique(contents[i])) p.getInventory().setItem(i, null);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.2f);
    }
}
