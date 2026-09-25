package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ANNUAIRE DES COMMANDES (/commande) — la liste de TOUTES les commandes du plugin.
 *
 * <p>Uniquement les nôtres : rien d'Essentials, de LuckPerms ou de WorldEdit. La liste est
 * <b>alphabétique</b> et paginée, et les commandes réservées aux admins ne sont affichées
 * qu'aux OP — un joueur normal voit 26 entrées au lieu de 48. Les 8 commandes /reset* en
 * font partie : elles sont réservées aux OP depuis le 2026-08-23 (voir CommandManager).</p>
 *
 * <p>Le menu est <b>passif</b> : cliquer ne lance rien, c'est un aide-mémoire à lire.</p>
 *
 * <p>⚠ Pour ajouter une commande à l'annuaire : une ligne dans {@link #TABLE}, rien d'autre.
 * La pagination et le filtrage OP suivent tout seuls. Pense à garder la table dans l'ordre
 * alphabétique (elle n'est pas triée à l'exécution, pour que l'ordre reste sous ton contrôle).</p>
 */
public class CommandMenu implements Listener {

    private final PrivateMines plugin;

    public CommandMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    static final String TITRE = "§8§lCommandes";
    /** Titre de la page des sous-commandes (« §8§lCommande §7/mine »). */
    static final String TITRE_SOUS = "§8§lCommande";

    /** Une entrée de l'annuaire : la commande, son icône, si elle est réservée aux OP, sa description. */
    private static final class Cmd {
        final String nom; final Material icone; final boolean op; final String[] desc;
        Cmd(String nom, Material icone, boolean op, String... desc) {
            this.nom = nom; this.icone = icone; this.op = op; this.desc = desc;
        }
    }

    private static Cmd c(String nom, Material icone, String... desc)   { return new Cmd(nom, icone, false, desc); }
    private static Cmd op(String nom, Material icone, String... desc)  { return new Cmd(nom, icone, true,  desc); }

    // ⬇️⬇️⬇️  L'UNIQUE endroit à éditer pour ajouter/modifier une commande de l'annuaire.  ⬇️⬇️⬇️
    private static final Cmd[] TABLE = {
        c("/ah", Material.ENDER_CHEST,
                "L'hôtel des ventes entre joueurs :", "mets tes objets en vente et achète", "ceux des autres."),
        c("/banque", Material.IRON_INGOT,
                "La banque de ton île : dépose du fer", "et de l'or pour faire monter ton île", "au classement des îles."),
        c("/blockvalue", Material.GOLD_NUGGET,
                "Le prix de vente de chaque bloc,", "tes bonus compris.", "§8Alias : /bv"),
        c("/bp", Material.SHULKER_BOX,
                "Ton backpack : les blocs que tu as", "achetés au shop."),
        c("/bvn", Material.CAKE,
                "Souhaite la bienvenue aux nouveaux", "joueurs. Te rapporte +2 % de ton solde."),
        c("/classements", Material.ITEM_FRAME,
                "Les classements du serveur : argent,", "progression, blocs minés, enchants,", "îles et gains par seconde."),
        c("/coinflip", Material.SUNFLOWER,
                "Pile ou face entre joueurs : tu mises,", "quelqu'un rejoint, le gagnant rafle tout."),
        c("/collections", Material.FILLED_MAP,
                "Tes trouvailles rares des mines :", "les items-clés de chaque arc et la Plume."),
        c("/commande", Material.WRITABLE_BOOK,
                "Ce menu : toutes les commandes", "du serveur et ce qu'elles font."),
        op("/crate", Material.TRAPPED_CHEST,
                "Gère les crates : pose, retire, liste,", "donne des clés (/crate givekey) et", "teste les lots."),
        c("/daily", Material.CLOCK,
                "Ta série de récompenses quotidiennes.", "Normalement réclamée auprès du PNJ", "« Récompenses quotidiennes »."),
        c("/end", Material.DRAGON_HEAD,
                "T'emmène dans l'End. Le Dragon du Vide", "y apparaît chaque jour à 20 h.", "§c⚔ PvP et perte de stuff sur place."),
        c("/fly", Material.FEATHER,
                "Active ou coupe le Vol.", "Uniquement dans ta mine, et seulement", "si tu as l'enchant Vol."),
        op("/fracturelevel", Material.NETHERRACK,
                "Fixe le niveau de l'enchant Fracture", "d'un joueur. Outil de test."),
        c("/guide", Material.OAK_SIGN,
                "Affiche ou masque la BossBar", "d'astuces en haut de l'écran."),
        op("/givemoney", Material.GOLD_BLOCK,
                "Donne de l'argent à un joueur."),
        op("/holo", Material.GLOWSTONE,
                "Gère les hologrammes : pose, texte,", "position au bloc près, orientation."),
        op("/intro", Material.END_CRYSTAL,
                "Rejoue la cinématique d'ouverture", "de l'Acte I. Outil de test."),
        c("/jobs", Material.IRON_PICKAXE,
                "Les métiers. Pour l'instant le Mineur :", "des paliers de blocs minés à réclamer.", "§8Alias : /metier"),
        c("/keys", Material.TRIPWIRE_HOOK,
                "Combien de clés de crate tu possèdes,", "rang par rang."),
        op("/logpose", Material.COMPASS,
                "Te redonne la Boussole du Log Pose", "(visite d'Alabasta). /logpose reset", "remet la visite à zéro."),
        c("/mine", Material.STONE,
                "Le menu des mines : voyage entre elles", "et débloque les suivantes.", "§8Une page par arc."),
        c("/money", Material.GOLD_INGOT,
                "Ton solde.", "§8/money set <joueur> <montant> est OP."),
        op("/mur", Material.BARRIER,
                "Murs invisibles : sélection au bâton,", "repousse les non-OP. Sert à fermer", "une zone sans la murer."),
        c("/ob", Material.GRASS_BLOCK,
                "Ton île : téléportation, réglages, visites,", "amis, et le Bloc du Grand Appel.", "§8Alias : /is, /parcelle"),
        op("/particules", Material.BLAZE_POWDER,
                "Pose et gère les particules", "décoratives du décor."),
        c("/pets", Material.BONE,
                "Tes familiers : équipe-les, fusionne-les", "à la forge, range-les dans le coffre."),
        c("/phases", Material.SEA_LANTERN,
                "Les 12 phases du Bloc du Grand Appel :", "leurs blocs et leurs paliers."),
        op("/pickaxelevel", Material.DIAMOND_PICKAXE,
                "Fixe le niveau de pioche d'un joueur.", "Outil de test."),
        op("/pnj", Material.PLAYER_HEAD,
                "Gère les PNJ : pose, nom, couleur, skin,", "et surtout leur RÔLE (Contremaître,", "Forgeron, Récompenses quotidiennes…)."),
        c("/prestige", Material.NETHER_STAR,
                "La Renaissance : repars de zéro contre", "un bonus de vente définitif et cumulatif."),
        c("/quest", Material.WRITTEN_BOOK,
                "Le hub des quêtes : quotidiennes,", "hebdomadaire, et ta quête d'histoire.", "§8Alias : /quete"),
        op("/resetarmures", Material.LEATHER_CHESTPLATE,
                "Retire toutes tes armures d'Oublié.", "§c⚠ Irréversible."),
        op("/resetbv", Material.PAPER,
                "Remet à zéro les statistiques", "de ton menu /blockvalue."),
        op("/resetenchants", Material.ENCHANTED_BOOK,
                "Remet tous les enchants de ta pioche", "au niveau 0.", "§c⚠ Irréversible, l'argent n'est pas rendu."),
        op("/resetfragment", Material.AMETHYST_SHARD,
                "Remet ton solde de Fragments", "de Souvenir à zéro.", "§c⚠ Irréversible."),
        op("/resetlevelpioche", Material.EXPERIENCE_BOTTLE,
                "Remet ton niveau de pioche à 1.", "§c⚠ Irréversible."),
        op("/resetmines", Material.STONE_PICKAXE,
                "Reverrouille tes mines débloquées.", "§c⚠ Irréversible, l'argent n'est pas rendu."),
        op("/resetordi", Material.REDSTONE,
                "Remet l'Ordinateur Quantique à zéro :", "compteur de recyclage et tri auto.", "§7Tes Fragments sont conservés."),
        op("/resetsac", Material.CHEST,
                "Remet ton sac au niveau 1 et le vide.", "§c⚠ Irréversible."),
        op("/setspawn", Material.RED_BED,
                "Définit le spawn du serveur", "à ta position actuelle."),
        c("/shop", Material.EMERALD,
                "Le shop : achète des blocs", "et des consommables."),
        c("/spawn", Material.COMPASS,
                "Te ramène au spawn du serveur."),
        op("/spawner", Material.SPAWNER,
                "Outils liés aux spawners d'île,", "dont le Pic du Démonteur."),
        c("/stats", Material.BOOK,
                "Tous tes bonus en pourcentage,", "familiers et armures cumulés."),
        op("/testgames", Material.OAK_BUTTON,
                "Force une partie des mini-jeux", "de chat. Outil de test."),
        c("/warp", Material.ENDER_PEARL,
                "Le menu des warps : téléportation", "rapide vers les lieux du serveur."),
        op("/zone", Material.STRUCTURE_VOID,
                "Gère les zones protégées :", "délimite où les joueurs ne peuvent", "ni casser ni poser."),
    };

    // ── Mise en page : 4 rangées utiles de 7 cases = 28 commandes par page. ──
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_PREV = 48, SLOT_CLOSE = 49, SLOT_NEXT = 50;
    private static final int SLOT_RETOUR = 48;   // page des sous-commandes : retour à l'annuaire

    /** Les commandes visibles par ce joueur : tout pour un OP, sans les commandes admin sinon. */
    private List<Cmd> visibles(Player p) {
        List<Cmd> out = new ArrayList<>();
        for (Cmd cmd : TABLE) {
            if (cmd.op && !p.isOp()) continue;
            out.add(cmd);
        }
        return out;
    }

    public void open(Player p) { open(p, 0); }

    public void open(Player p, int page) {
        List<Cmd> liste = visibles(p);
        int pages = Math.max(1, (int) Math.ceil(liste.size() / (double) SLOTS.length));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        Inventory menu = Bukkit.createInventory(null, 54, TITRE + " §7(" + (page + 1) + "/" + pages + ")");
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        menu.setItem(4, plugin.namedItem(Material.WRITABLE_BOOK, "§e§lToutes les commandes",
                "", "§7Les " + liste.size() + " commandes du serveur,",
                "§7par ordre alphabétique.",
                p.isOp() ? "§8Les commandes admin te sont visibles." : "§8Seules les commandes joueur sont listées.",
                "", "§7Les commandes marquées §e▸ §7ont des",
                "§7sous-commandes : clique pour les voir.",
                "§8Cliquer ne lance jamais la commande."));

        int debut = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && debut + i < liste.size(); i++) {
            Cmd cmd = liste.get(debut + i);
            List<String> lore = new ArrayList<>();
            lore.add("");
            for (String l : cmd.desc) lore.add("§7" + l);
            if (cmd.op) {
                lore.add("");
                lore.add("§c🔧 Réservée aux admins");
            }
            // Une commande qui a des sous-commandes VISIBLES par ce joueur devient cliquable.
            boolean aDesSous = sousVisibles(p, cmd.nom.substring(1)) != null;
            if (aDesSous) {
                lore.add("");
                lore.add("§e▸ Clique pour voir ses sous-commandes");
            }
            menu.setItem(SLOTS[i], plugin.namedItem(cmd.icone,
                    (cmd.op ? "§c" : "§e") + cmd.nom + (aDesSous ? " §e▸" : ""),
                    lore.toArray(new String[0])));
        }

        if (page > 0) {
            menu.setItem(SLOT_PREV, plugin.namedItem(Material.ARROW, "§7← Page précédente",
                    "§8Page " + page + "/" + pages));
        }
        if (page < pages - 1) {
            menu.setItem(SLOT_NEXT, plugin.namedItem(Material.ARROW, "§7Page suivante →",
                    "§8Page " + (page + 2) + "/" + pages));
        }
        menu.setItem(SLOT_CLOSE, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    //  LES SOUS-COMMANDES  (ajouté le 2026-09-02)
    // ═══════════════════════════════════════════════════════════════════════════════════
    //
    // Cliquer une commande de l'annuaire ouvre SA page : la liste de ses sous-commandes, une par
    // case, avec ce qu'elle fait. Les commandes sans sous-commande ne réagissent pas au clic —
    // l'annuaire reste un aide-mémoire, jamais un lanceur.
    //
    // ⚠ Pour ajouter une sous-commande : une ligne dans SOUS, rien d'autre. La page, la pagination
    // et le filtrage OP suivent tout seuls. La clé est le nom de la commande SANS le slash.
    //
    // Source des listes : le tab-complete de CommandManager.onTabComplete (la référence), complété
    // par les handlers pour celles qui n'y figurent pas — /mine set et /mine armortest notamment,
    // qui existent dans le code mais ne sont proposées par aucun tab-complete.

    /** Une sous-commande : sa syntaxe, si elle est réservée aux OP, et ce qu'elle fait. */
    private static final class Sub {
        final String syntaxe; final boolean op; final String[] desc;
        Sub(String syntaxe, boolean op, String... desc) {
            this.syntaxe = syntaxe; this.op = op; this.desc = desc;
        }
    }

    private static Sub s(String syntaxe, String... desc)  { return new Sub(syntaxe, false, desc); }
    private static Sub so(String syntaxe, String... desc) { return new Sub(syntaxe, true,  desc); }

    private static final java.util.Map<String, Sub[]> SOUS = new java.util.HashMap<>();
    static {
        SOUS.put("mine", new Sub[] {
            s("/mine", "Ouvre le menu des mines.", "Une page par arc."),
            so("/mine set <n>", "Débloque EXACTEMENT les mines 1 à n.", "Remplace ta progression, ne l'ajoute pas."),
            so("/mine armortest <matière> <pièce>", "Génère une armure d'Oublié à bonus.", "§8Ex : /mine armortest diamant plastron"),
        });
        SOUS.put("money", new Sub[] {
            s("/money", "Affiche ton solde.", "§8Alias : /bal, /balance, /argent"),
            so("/money set <joueur> <montant>", "Fixe le solde d'un joueur.", "Remplace la somme, ne l'ajoute pas."),
        });
        SOUS.put("ah", new Sub[] {
            s("/ah", "Ouvre l'hôtel des ventes."),
            s("/ah sell <prix>", "Met en vente l'objet que tu tiens.", "5 ventes maximum, 2 jours, taxe 3 %."),
            s("/ah help", "Rappelle les règles de l'hôtel des ventes."),
        });
        SOUS.put("coinflip", new Sub[] {
            s("/coinflip", "Ouvre la liste des paris en cours."),
            s("/coinflip <mise>", "Crée un pari. Ta mise est réservée", "jusqu'à ce que quelqu'un rejoigne.", "§8Ex : /coinflip 100K"),
            s("/coinflip help", "Rappelle les règles du pile ou face."),
        });
        SOUS.put("end", new Sub[] {
            s("/end", "T'emmène à la salle d'attente de l'End.", "§c⚔ PvP et perte de stuff sur place."),
            so("/end forcedragon", "Fait apparaître le Dragon du Vide", "immédiatement, sans attendre 20 h."),
        });
        SOUS.put("daily", new Sub[] {
            s("/daily", "Ta série de récompenses quotidiennes."),
            so("/daily set <1-7> [joueur]", "Force le jour de la série."),
            so("/daily reset [joueur]", "Remet la série au jour 1."),
        });
        SOUS.put("zone", new Sub[] {
            so("/zone pos1", "Marque le 1er coin de la zone", "à ta position."),
            so("/zone pos2", "Marque le 2e coin."),
            so("/zone create <nom>", "Crée la zone entre les deux coins."),
            so("/zone list", "Liste toutes les zones protégées."),
            so("/zone info", "Décrit la zone où tu te trouves."),
            so("/zone preset <nom>", "Applique un jeu de règles tout prêt", "à une zone existante."),
            so("/zone delete <nom>", "Supprime une zone.", "§c⚠ Irréversible."),
        });
        SOUS.put("mur", new Sub[] {
            so("/mur wand", "Te donne le bâton de sélection."),
            so("/mur create <nom>", "Crée un mur invisible", "entre les deux points sélectionnés."),
            so("/mur list", "Liste tous les murs."),
            so("/mur toggle <nom>", "Active ou désactive un mur", "sans le supprimer."),
            so("/mur tp <nom>", "Te téléporte à un mur."),
            so("/mur settings", "Réglages généraux des murs."),
            so("/mur remove <nom>", "Supprime un mur.", "§c⚠ Irréversible."),
        });
        SOUS.put("crate", new Sub[] {
            so("/crate sethere <rang>", "Pose une crate à ta position.", "§8Rangs : commune, rare, legendaire"),
            so("/crate givekey <joueur> <rang> [n]", "Donne des clés à un joueur."),
            so("/crate list", "Liste les crates posées."),
            so("/crate settings", "Réglages des crates."),
            so("/crate boost <mult> [min] [joueur]", "Lance un boost de vente temporaire.", "§8Multiplicateurs : 1.5, 2.5, 5"),
            so("/crate testblock", "Teste les blocs de crate posables", "(command block, bedrock)."),
            so("/crate remove", "Retire la crate que tu regardes."),
            so("/crate clearall", "Retire TOUTES les crates.", "§c⚠ Irréversible."),
        });
        SOUS.put("pnj", new Sub[] {
            so("/pnj place <nom>", "Pose un PNJ à ta position."),
            so("/pnj setting", "Ouvre les réglages du PNJ visé :", "nom, couleur, skin et surtout son RÔLE."),
            so("/pnj list", "Liste tous les PNJ."),
            so("/pnj tp <nom>", "Te téléporte à un PNJ."),
            so("/pnj info", "Décrit le PNJ que tu regardes."),
            so("/pnj skin <pseudo>", "Change le skin d'un PNJ."),
            so("/pnj setchest", "Définit le coffre partagé de l'Acte I."),
            so("/pnj setchest2", "Définit le coffre de l'Acte II", "(la Tête de Pioche)."),
            so("/pnj setpuits", "Définit l'emplacement du Puits", "des Souvenirs."),
            so("/pnj reload", "Recharge npcs.yml après édition", "des dialogues."),
            so("/pnj resetquest [joueur]", "Remet la quête d'histoire à zéro."),
            so("/pnj remove", "Supprime le PNJ visé.", "§c⚠ Irréversible."),
        });
        SOUS.put("holo", new Sub[] {
            so("/holo create <nom>", "Crée un hologramme à ta position."),
            so("/holo edit <nom>", "Ouvre le menu d'édition :", "texte, position au bloc près, orientation."),
            so("/holo set end", "Pose l'hologramme pré-rempli de l'End", "avec le minuteur du Dragon."),
            so("/holo addline <nom> <texte>", "Ajoute une ligne."),
            so("/holo setline <nom> <n> <texte>", "Remplace la ligne n."),
            so("/holo removeline <nom> <n>", "Retire la ligne n."),
            so("/holo movehere <nom>", "Déplace l'hologramme à ta position."),
            so("/holo list", "Liste tous les hologrammes."),
            so("/holo setting", "Réglages généraux des hologrammes."),
            so("/holo delete <nom>", "Supprime un hologramme.", "§c⚠ Irréversible."),
        });
        SOUS.put("ob", new Sub[] {
            s("/ob", "Te téléporte sur ton île.", "§8Alias : /is, /oneblock"),
            s("/ob setting", "Les réglages de ton île :", "nom, météo et heure."),
            s("/ob visit <joueur>", "Visite l'île d'un autre joueur."),
            s("/ob friend <joueur>", "Invite un joueur sur ton île."),
            s("/ob accept", "Accepte une invitation reçue."),
            s("/ob unfriend <joueur>", "Retire un ami de ton île."),
            s("/ob top", "Le classement des îles", "(dépôts à la Banque d'Île)."),
            s("/ob info", "L'état de ton île et de ton OneBlock."),
            s("/ob help", "Rappelle les commandes d'île."),
            so("/ob setphase <1-12> [joueur]", "Force la phase du Bloc du Grand Appel."),
            so("/ob setblocs <n> [joueur]", "Fixe le compteur de blocs cassés", "de la phase en cours."),
            so("/ob next", "Passe à la phase suivante."),
            so("/ob reset", "Remet l'île à zéro.", "§c⚠ Irréversible."),
            so("/ob admin", "Outils d'administration des îles."),
            so("/ob forcemob", "Force l'apparition d'un mob du OneBlock."),
            so("/ob forcechest", "Force l'apparition d'un coffre."),
            so("/ob forcebutin", "Force l'apparition d'un butin."),
            so("/ob forcemerchant", "Force l'apparition du marchand."),
        });
        SOUS.put("spawner", new Sub[] {
            so("/spawner tool [joueur]", "Donne le Pic du Démonteur", "(netherite, 5 usages)."),
            so("/spawner cleanup", "Nettoie les spawners orphelins."),
        });
        SOUS.put("logpose", new Sub[] {
            so("/logpose", "Te redonne la Boussole du Log Pose", "(la visite d'Alabasta)."),
            so("/logpose reset", "Remet la visite à zéro", "pour la refaire depuis le début."),
        });
        SOUS.put("pickaxelevel", new Sub[] {
            so("/pickaxelevel set <niveau> [joueur]", "Fixe le niveau de pioche.", "§8Alias : /piochelevel"),
        });
        SOUS.put("fracturelevel", new Sub[] {
            so("/fracturelevel set <niveau> [joueur]", "Fixe le niveau de l'enchant Fracture."),
        });
        SOUS.put("prestige", new Sub[] {
            s("/prestige", "Ton rang de Renaissance", "et ton bonus de vente.", "§8Alias : /renaissance"),
            so("/prestige set <rang> [joueur]", "Fixe le rang de Renaissance."),
        });
        SOUS.put("testgames", new Sub[] {
            so("/testgames calcul", "Force un mini-jeu de calcul mental."),
            so("/testgames nombre", "Force un mini-jeu du nombre mystère."),
            so("/testgames stop", "Coupe le mini-jeu en cours."),
        });
    }

    /** Les sous-commandes visibles par ce joueur (les OP voient tout). null si la page est vide. */
    private List<Sub> sousVisibles(Player p, String commande) {
        Sub[] all = SOUS.get(commande);
        if (all == null) return null;
        List<Sub> out = new ArrayList<>();
        for (Sub s : all) {
            if (s.op && !p.isOp()) continue;
            out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    /** Le nom de commande (sans slash) porté par la case cliquée, ou null. */
    private String commandeAuSlot(Player p, int page, int slot) {
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) { idx = i; break; }
        if (idx < 0) return null;
        List<Cmd> liste = visibles(p);
        int pos = page * SLOTS.length + idx;
        if (pos < 0 || pos >= liste.size()) return null;
        return liste.get(pos).nom.substring(1);   // « /mine » -> « mine »
    }

    /** La page des sous-commandes d'UNE commande. */
    public void openSous(Player p, String commande) {
        List<Sub> liste = sousVisibles(p, commande);
        if (liste == null) return;

        Inventory menu = Bukkit.createInventory(null, 54, TITRE_SOUS + " §7/" + commande);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        menu.setItem(4, plugin.namedItem(Material.WRITABLE_BOOK, "§e§l/" + commande,
                "", "§7Les " + liste.size() + " façons d'utiliser", "§7cette commande.",
                "", "§8Ce menu ne sert qu'à lire."));

        for (int i = 0; i < SLOTS.length && i < liste.size(); i++) {
            Sub s = liste.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("");
            for (String l : s.desc) lore.add("§7" + l);
            if (s.op) {
                lore.add("");
                lore.add("§c🔧 Réservée aux admins");
            }
            menu.setItem(SLOTS[i], plugin.namedItem(s.op ? Material.COMMAND_BLOCK : Material.PAPER,
                    (s.op ? "§c" : "§e") + s.syntaxe, lore.toArray(new String[0])));
        }

        menu.setItem(SLOT_RETOUR, plugin.namedItem(Material.ARROW, "§7← Retour à l'annuaire",
                "§8Toutes les commandes du serveur"));
        menu.setItem(SLOT_CLOSE, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    /** Numéro de page lu dans le titre (« …§7(2/3) » → 1, en index 0). */
    private int pageDuTitre(String titre) {
        int o = titre.lastIndexOf('(');
        int slash = titre.indexOf('/', o);
        if (o < 0 || slash < 0) return 0;
        try {
            return Integer.parseInt(titre.substring(o + 1, slash)) - 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Le glisser-déposer est un événement À PART (InventoryDragEvent) : sans ça, on peut étaler
     * un item sur les cases du menu, et donc en sortir le contenu. Menu passif = rien ne bouge.
     */
    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        String titre = event.getView().getTitle();
        if (titre.startsWith(TITRE) || titre.startsWith(TITRE_SOUS + " §7/")) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String titre = event.getView().getTitle();
        // ⚠ Les DEUX titres doivent être reconnus ici : « §8§lCommandes §7(1/2) » pour l'annuaire
        // et « §8§lCommande §7/mine » pour la page des sous-commandes. TITRE porte un « s » final,
        // donc le titre du sous-menu ne commence PAS par TITRE — le tester séparément, sinon on
        // sort du handler sans annuler l'événement et les items du menu deviennent récupérables.
        boolean estSous = titre.startsWith(TITRE_SOUS + " §7/");
        if (!titre.startsWith(TITRE) && !estSous) return;
        // Menu passif : on annule TOUT (clic dans le menu, shift-clic depuis l'inventaire du bas,
        // touches 1-9, double-clic…) avant de décider quoi que ce soit.
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // ── Page des sous-commandes : seulement retour et fermer. ──
        if (estSous) {
            if (slot == SLOT_RETOUR) { open(p, 0); return; }
            if (slot == SLOT_CLOSE)  { p.closeInventory(); }
            return;
        }

        int page = pageDuTitre(titre);
        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }
        if (slot == SLOT_PREV)  { open(p, page - 1); return; }
        if (slot == SLOT_NEXT)  { open(p, page + 1); return; }
        // Clic sur une commande : sa page de sous-commandes, si elle en a.
        String cible = commandeAuSlot(p, page, slot);
        if (cible != null) openSous(p, cible);
    }
}
