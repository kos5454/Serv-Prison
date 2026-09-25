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
import java.util.List;
import java.util.UUID;

/**
 * Hôtel des ventes entre joueurs (/ah).
 *
 * <p>Un joueur met en vente l'objet qu'il tient : {@code /ah sell <prix>} (le prix accepte les
 * suffixes courts : 10K, 2.5M, 100AB…). L'objet quitte son inventaire et apparaît dans le menu
 * public. Un acheteur clique dessus : l'argent est débité, l'objet lui est remis, et le vendeur
 * reçoit le prix moins la taxe.
 *
 * <p>Règles :
 * <ul>
 *   <li>{@value #MAX_VENTES} ventes simultanées par joueur ;</li>
 *   <li>durée de {@value #DUREE_JOURS_BASE} jours, prolongeable jusqu'à {@value #DUREE_JOURS_MAX}
 *       (tarif fixe : 1 000$ le 1er jour, puis ×1,5) ; à l'expiration l'objet part
 *       dans « Mes retours » ;</li>
 *   <li>taxe de {@value #TAXE_PCT}% prélevée sur le prix de vente ;</li>
 *   <li>objets de progression (sac, pioche, ordinateur quantique, objets de quête) interdits.</li>
 * </ul>
 *
 * <p>Tout est dans le menu : page publique, « Mes ventes » (annulation) et « Mes retours »
 * (récupération des objets expirés/annulés). Persistance dans {@code auctions.yml}.
 */
public class AuctionManager implements Listener {

    public static final int MAX_VENTES = 5;
    public static final int DUREE_JOURS_BASE = 2;    // durée offerte à la mise en vente
    public static final int DUREE_JOURS_MAX  = 9;    // plafond avec prolongations
    public static final int TAXE_PCT = 3;            // prélevé sur le prix quand l'objet se vend
    // Prolonger : tarif FIXE (indépendant du prix de l'objet). 1 000$ la première fois,
    // puis ×1,5 à chaque prolongation suivante (1000, 1500, 2250, 3375, 5062, 7593, 11390).
    public static final double PROLONG_BASE = 1_000.0;
    public static final double PROLONG_MULT = 1.5;
    private static final long JOUR_MS = 24 * 3600_000L;

    private static final String TITLE_MAIN    = "§8§l⚖ Hôtel des Ventes";
    private static final String TITLE_MINE    = "§8§l⚖ Mes ventes en cours";
    private static final String TITLE_RETOURS = "§8§l⚖ Mes retours";
    private static final String TITLE_HISTO   = "§8§l⚖ Mes anciennes ventes";
    private static final String TITLE_VITRINE = "§8§l★ Choisis l'objet à mettre en vitrine";

    // Nombre max d'entrées d'historique gardées par joueur (les plus vieilles sont oubliées).
    public static final int HISTO_MAX = 45;

    // ── Vitrine : la 1re ligne (9 cases) met une vente en avant pendant 12h. ──
    public static final int VITRINE_SLOTS = 9;
    public static final double VITRINE_PRIX = 1_000_000;
    public static final int VITRINE_HEURES = 12;
    private static final long VITRINE_MS = VITRINE_HEURES * 3600_000L;

    // 4 lignes d'objets (la 1re est la vitrine, la 6e la navigation).
    private static final int PER_PAGE = 36;
    private static final int LISTE_START = 9; // 1re case de la liste (après la vitrine)

    private final PrivateMines plugin;
    private java.io.File file;
    private org.bukkit.configuration.file.YamlConfiguration config;

    /** Une vente en cours. */
    public static final class Lot {
        String id;                 // identifiant unique
        UUID vendeur;
        String vendeurNom;
        ItemStack item;
        java.math.BigInteger prix;
        long expireAt;             // timestamp de fin
        int joursTotal;            // durée totale accordée (2 de base, jusqu'à 9 avec prolongations)
        boolean rendu;             // true = déjà rendu au vendeur (dans ses retours)
        // Vitrine : case occupée (-1 = aucune) et fin de la mise en avant.
        int vitrineSlot = -1;
        long vitrineFin;
    }

    /** Une entrée d'historique : une vente désormais terminée (vendue, expirée ou annulée). */
    public static final class Vente {
        ItemStack item;
        java.math.BigInteger prix;        // prix affiché de la vente
        java.math.BigInteger net;         // ce que le vendeur a réellement touché (0 si non vendue)
        java.math.BigInteger taxe;        // taxe prélevée (0 si non vendue)
        String acheteurNom;               // pseudo de l'acheteur (null si non vendue)
        long date;                        // timestamp de conclusion
        Statut statut;

        enum Statut {
            VENDUE  ("§a✔ Vendue",   org.bukkit.Material.LIME_STAINED_GLASS_PANE),
            EXPIREE ("§e⌛ Expirée",  org.bukkit.Material.YELLOW_STAINED_GLASS_PANE),
            ANNULEE ("§c✖ Annulée",  org.bukkit.Material.RED_STAINED_GLASS_PANE);
            final String libelle; final org.bukkit.Material pastille;
            Statut(String l, org.bukkit.Material m) { this.libelle = l; this.pastille = m; }
        }
    }

    // Ventes actives (clé = id du lot).
    private final java.util.Map<String, Lot> lots = new java.util.LinkedHashMap<>();
    // Historique des ventes terminées, par UUID (le plus récent en tête, plafonné à HISTO_MAX).
    private final java.util.Map<UUID, java.util.Deque<Vente>> historique = new java.util.HashMap<>();
    // Objets à rendre à un joueur (expirés ou annulés), par UUID.
    private final java.util.Map<UUID, List<ItemStack>> retours = new java.util.HashMap<>();
    // Page courante de chaque joueur dans le menu public.
    private final java.util.Map<UUID, Integer> page = new java.util.HashMap<>();
    // Case de vitrine que le joueur est en train de remplir (écran de choix).
    private final java.util.Map<UUID, Integer> vitrineChoix = new java.util.HashMap<>();
    // Compteur pour générer des ids uniques sans dépendre de l'horloge seule.
    private int compteur = 0;

    public AuctionManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Règles de vendabilité
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Un objet de progression ne doit jamais circuler entre joueurs : il est unique,
     * lié au parcours de celui qui le possède, et le vendre casserait sa progression
     * (ou permettrait d'en dupliquer les effets).
     */
    public boolean estInterdit(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return true;
        if (plugin.isBag(it)) return true;       // sac de minage
        if (plugin.isPickaxe(it)) return true;   // pioche du joueur
        if (plugin.isOrdi(it)) return true;      // ordinateur quantique
        ActeManager acte = plugin.getActe();
        if (acte != null && (acte.isJeton(it) || acte.isTete(it))) return true; // objets de quête
        return false;
    }

    /**
     * Prérequis de progression pour ACHETER un objet, ou null si l'acheteur y a droit.
     *
     * <p>Sans ça, un joueur de la mine 3 pourrait acheter un pet de l'Arc II (réservé à la
     * mine 15) ou une armure en Netherite dont il n'a pas encore débloqué la matière : il
     * court-circuiterait toute la progression prévue.
     */
    private String prerequisManquant(Player acheteur, ItemStack it) {
        // ── Pets : un pet d'arc II demande la mine qui ouvre cet arc. ──
        PetMenu pets = plugin.getPetMenu();
        if (pets != null && pets.isPet(it)) {
            PetMenu.PetDef def = PetMenu.defById(pets.petIdOf(it));
            if (def != null && PetMenu.arcOf(def) == 2 && !plugin.hasUnlockedMine(acheteur, "O")) {
                return "§c🔒 Ce familier appartient à l'§fArc II§c : il te faut la §fmine 15§c.";
            }
        }
        // ── Armures d'Oubliés : la matière doit être débloquée dans la Collection. ──
        ArmorManager armor = plugin.getArmor();
        if (armor != null && armor.isArmureOubli(it)) {
            ArmorManager.Matiere m = ArmorManager.matiereDe(it.getType());
            if (m != null && plugin.getFragments() != null) {
                int recyclees = plugin.getFragments().getArmuresRecyclees(acheteur);
                if (!m.estDebloquee(recyclees)) {
                    return "§c🔒 Tu n'as pas encore débloqué la matière §f" + m.nom
                            + "§c dans la Collection des Oubliés.";
                }
            }
        }
        return null;
    }

    /** Message expliquant pourquoi l'objet en main ne peut pas être vendu (null si OK). */
    private String raisonInterdit(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return "§cTu dois tenir l'objet à vendre dans ta main.";
        if (plugin.isBag(it))     return "§cTon §esac§c ne peut pas être vendu.";
        if (plugin.isPickaxe(it)) return "§cTa §epioche§c ne peut pas être vendue.";
        if (plugin.isOrdi(it))    return "§cL'§eordinateur quantique§c ne peut pas être vendu.";
        ActeManager acte = plugin.getActe();
        if (acte != null && (acte.isJeton(it) || acte.isTete(it)))
            return "§cLes §eobjets de quête§c ne peuvent pas être vendus.";
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Commande /ah
    // ══════════════════════════════════════════════════════════════════════════

    public boolean handleCommand(Player p, String[] args) {
        if (args.length == 0) { openMain(p, 0); return true; }

        String sub = args[0].toLowerCase();
        if (sub.equals("sell") || sub.equals("vendre")) {
            if (args.length < 2) {
                p.sendMessage("§7Usage : §f/ah sell <prix> §7(ex. §f/ah sell 10K§7, §f/ah sell 2.5M§7)");
                return true;
            }
            mettreEnVente(p, args[1]);
            return true;
        }
        if (sub.equals("help") || sub.equals("aide")) {
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§6§l⚖ Hôtel des Ventes");
            p.sendMessage("§f/ah §7— ouvre l'hôtel des ventes");
            p.sendMessage("§f/ah sell <prix> §7— vend l'objet dans ta main");
            p.sendMessage("§7Prix acceptés : §f5000§7, §f10K§7, §f2.5M§7, §f100AB§7...");
            p.sendMessage("§7Max §f" + MAX_VENTES + " ventes§7 · expire en §f" + DUREE_JOURS_BASE
                    + " jours§7 · taxe §f" + TAXE_PCT + "%");
            p.sendMessage("§7Prolongeable jusqu'à §f" + DUREE_JOURS_MAX + " jours §7(dès §f"
                    + PrivateMines.formatNumber(PROLONG_BASE) + "$§7/jour) dans §fMes ventes§7.");
            p.sendMessage("§8§m                                        ");
            return true;
        }
        openMain(p, 0);
        return true;
    }

    private void mettreEnVente(Player p, String prixSaisi) {
        ItemStack main = p.getInventory().getItemInMainHand();
        String refus = raisonInterdit(main);
        if (refus != null) {
            p.sendMessage(refus);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (compterVentes(p.getUniqueId()) >= MAX_VENTES) {
            p.sendMessage("§cTu as déjà §e" + MAX_VENTES + " ventes§c en cours. Annules-en une dans §f/ah§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        java.math.BigInteger prix = EconomyManager.parseMontant(prixSaisi);
        if (prix == null || prix.signum() <= 0) {
            p.sendMessage("§cPrix invalide : §f" + prixSaisi + " §7(ex. §f5000§7, §f10K§7, §f2.5M§7)");
            return;
        }

        // L'objet quitte la main : il n'existe plus que dans le lot (pas de duplication possible).
        ItemStack vendu = main.clone();
        p.getInventory().setItemInMainHand(null);
        p.updateInventory();

        Lot lot = new Lot();
        lot.id = "L" + (++compteur) + "_" + p.getUniqueId().toString().substring(0, 8);
        lot.vendeur = p.getUniqueId();
        lot.vendeurNom = p.getName();
        lot.item = vendu;
        lot.prix = prix;
        lot.joursTotal = DUREE_JOURS_BASE;
        lot.expireAt = System.currentTimeMillis() + DUREE_JOURS_BASE * JOUR_MS;
        lots.put(lot.id, lot);
        save();

        p.sendMessage("§a✔ §f" + nomItem(vendu) + " §7×" + vendu.getAmount()
                + " §amis en vente pour §6" + PrivateMines.formatNumberBig(prix) + "$§a.");
        p.sendMessage("§7Il expire dans §f" + DUREE_JOURS_BASE + " jours §7· taxe à la vente : §f" + TAXE_PCT + "%");
        p.sendMessage("§7Tu peux prolonger jusqu'à §f" + DUREE_JOURS_MAX + " jours §7dans §f/ah §7→ §fMes ventes§7.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
    }

    private int compterVentes(UUID id) {
        int n = 0;
        for (Lot l : lots.values()) if (!l.rendu && l.vendeur.equals(id)) n++;
        return n;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Expiration
    // ══════════════════════════════════════════════════════════════════════════

    /** Tâche horaire : bascule les lots périmés dans les retours de leur vendeur. */
    public void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgerExpirés, 20L * 60, 20L * 600); // toutes les 10 min
    }

    private void purgerExpirés() {
        long now = System.currentTimeMillis();
        List<String> aRetirer = new ArrayList<>();
        for (Lot l : lots.values()) {
            if (l.rendu || l.expireAt > now) continue;
            retours.computeIfAbsent(l.vendeur, k -> new ArrayList<>()).add(l.item);
            ajouterHistorique(l.vendeur, venteTerminee(l, Vente.Statut.EXPIREE));
            aRetirer.add(l.id);
            Player vendeur = Bukkit.getPlayer(l.vendeur);
            if (vendeur != null) {
                vendeur.sendMessage("§e⌛ Ta vente §f" + nomItem(l.item)
                        + " §ea expiré. Récupère-la dans §f/ah §7→ §fMes retours§e.");
            }
        }
        for (String id : aRetirer) lots.remove(id);
        if (!aRetirer.isEmpty()) save();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Menus
    // ══════════════════════════════════════════════════════════════════════════

    public void openMain(Player p, int pg) {
        List<Lot> actifs = lotsActifs();
        int totalPages = Math.max(1, (int) Math.ceil(actifs.size() / (double) PER_PAGE));
        pg = Math.max(0, Math.min(pg, totalPages - 1));
        page.put(p.getUniqueId(), pg);

        Inventory menu = Bukkit.createInventory(null, 54, TITLE_MAIN);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        // 1re ligne = vitrine : soit l'objet mis en avant, soit une case à acheter.
        for (int s = 0; s < VITRINE_SLOTS; s++) {
            Lot enVitrine = lotEnVitrine(s);
            menu.setItem(s, enVitrine != null ? tuileVitrine(enVitrine, p) : caseVitrineLibre());
        }

        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= actifs.size()) break;
            menu.setItem(LISTE_START + i, tuileLot(actifs.get(idx), false, p));
        }

        if (pg > 0) menu.setItem(45, plugin.namedItem(Material.SPECTRAL_ARROW, "§e‹ Page précédente"));
        if (pg < totalPages - 1) menu.setItem(53, plugin.namedItem(Material.SPECTRAL_ARROW, "§ePage suivante ›"));

        // Boutons d'accès aux sous-menus (pas de commande à retenir).
        menu.setItem(47, boutonMesVentes(p));
        menu.setItem(48, boutonHistorique(p));
        menu.setItem(50, boutonMesRetours(p));
        menu.setItem(49, plugin.namedItem(Material.BARRIER, "§cFermer",
                "§7Page §f" + (pg + 1) + "§7/§f" + totalPages));

        p.openInventory(menu);
    }

    private void openMesVentes(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_MINE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int slot = 0;
        for (Lot l : lotsActifs()) {
            if (!l.vendeur.equals(p.getUniqueId())) continue;
            if (slot >= PER_PAGE) break;
            menu.setItem(slot++, tuileLot(l, true, p));
        }
        if (slot == 0) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucune vente en cours",
                    "", "§7Tiens un objet et tape", "§f/ah sell <prix>"));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour à l'hôtel"));
        p.openInventory(menu);
    }

    private void openRetours(Player p) {
        List<ItemStack> mes = retours.getOrDefault(p.getUniqueId(), new ArrayList<>());
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_RETOURS);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        for (int i = 0; i < mes.size() && i < PER_PAGE; i++) {
            ItemStack it = mes.get(i).clone();
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                List<String> lore = m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("§e▶ Clique pour récupérer");
                m.setLore(lore);
                it.setItemMeta(m);
            }
            menu.setItem(i, it);
        }
        if (mes.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucun objet à récupérer",
                    "", "§7Les ventes expirées ou annulées", "§7atterrissent ici."));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour à l'hôtel"));
        p.openInventory(menu);
    }

    private void openHistorique(Player p) {
        java.util.Deque<Vente> mes = historique.getOrDefault(p.getUniqueId(), new java.util.ArrayDeque<>());
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_HISTO);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int i = 0;
        for (Vente v : mes) {
            if (i >= PER_PAGE) break;
            menu.setItem(i++, tuileHistorique(v));
        }
        if (mes.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucune vente passée",
                    "", "§7L'historique de tes ventes conclues", "§7(vendues, expirées, annulées) apparaît ici."));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour à l'hôtel"));
        p.openInventory(menu);
    }

    // Tuile lecture seule d'une vente passée : l'objet + un récap complet (statut, prix, taxe, net, acheteur, date).
    private ItemStack tuileHistorique(Vente v) {
        ItemStack it = v.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Statut : " + v.statut.libelle);
            lore.add("§7Prix affiché : §6" + PrivateMines.formatNumberBig(v.prix) + "$");
            if (v.statut == Vente.Statut.VENDUE) {
                lore.add("§7Acheteur : §f" + (v.acheteurNom != null ? v.acheteurNom : "?"));
                lore.add("§7Taxe (" + TAXE_PCT + "%) : §c-" + PrivateMines.formatNumberBig(v.taxe) + "$");
                lore.add("§7Net reçu : §a" + PrivateMines.formatNumberBig(v.net) + "$");
            }
            lore.add("§7" + (v.statut == Vente.Statut.VENDUE ? "Vendu" : "Terminé") + " : §f" + ilYA(v.date));
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    // « il y a Xj / Xh / Xmin » depuis un timestamp passé.
    private String ilYA(long date) {
        long ms = System.currentTimeMillis() - date;
        if (ms < 60_000L) return "à l'instant";
        long min = ms / 60_000L;
        if (min < 60) return "il y a " + min + " min";
        long h = min / 60;
        if (h < 24) return "il y a " + h + "h";
        long j = h / 24;
        return "il y a " + j + "j";
    }

    private ItemStack boutonHistorique(Player p) {
        int n = historique.getOrDefault(p.getUniqueId(), new java.util.ArrayDeque<>()).size();
        return plugin.namedItem(Material.WRITABLE_BOOK, "§e§l📜 Mes anciennes ventes",
                "", "§7Ventes conclues : §e" + n,
                "§7(vendues, expirées, annulées)", "", "§e▶ Clique pour ouvrir");
    }

    private ItemStack boutonMesVentes(Player p) {
        return plugin.namedItem(Material.CHEST, "§6§l📤 Mes ventes en cours",
                "",
                "§7Tu as §e" + compterVentes(p.getUniqueId()) + "§7/§e" + MAX_VENTES + " §7ventes.",
                "§7Annule ou prolonge tes ventes ici.",
                "",
                "§f§lComment vendre ?",
                "§7Tiens l'objet en main et tape :",
                "§a/ah sell <prix>",
                "§8Ex. §7/ah sell 10K §8· §7/ah sell 2.5M",
                "",
                "§c⚠ Taxe de " + TAXE_PCT + "% à la vente",
                "§7Quand ton objet se vend, tu perds §c" + TAXE_PCT + "% §7du prix.",
                "§8Ex. vendu 10 000$ → tu reçois 9 700$",
                "",
                "§e▶ Clique pour ouvrir");
    }

    private ItemStack boutonMesRetours(Player p) {
        int n = retours.getOrDefault(p.getUniqueId(), new ArrayList<>()).size();
        return plugin.namedItem(Material.ENDER_CHEST, "§b§l📥 Mes retours",
                "", "§7Objets à récupérer : §e" + n,
                "§7(ventes expirées ou annulées)", "", "§e▶ Clique pour ouvrir");
    }

    // Tuile d'un lot : l'objet, son prix, son vendeur et le temps restant.
    // spectateur = le joueur qui regarde la tuile (pour afficher ses prérequis manquants).
    private ItemStack tuileLot(Lot l, boolean vueVendeur, Player spectateur) {
        ItemStack it = l.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Prix : §6" + PrivateMines.formatNumberBig(l.prix) + "$");
            lore.add("§7Vendeur : §f" + l.vendeurNom);
            lore.add("§7Expire dans : §f" + tempsRestant(l.expireAt));
            lore.add("");
            if (vueVendeur) {
                lore.add("§7Durée : §f" + l.joursTotal + "§7/§f" + DUREE_JOURS_MAX + " jours");
                if (l.joursTotal < DUREE_JOURS_MAX) {
                    lore.add("§e▶ Clic DROIT §7: +1 jour pour §6"
                            + PrivateMines.formatNumberBig(coutProlongation(l)) + "$");
                } else {
                    lore.add("§8Durée maximale atteinte.");
                }
                lore.add("§c▶ Clic GAUCHE §7: annuler la vente");
            } else {
                // Prérequis non atteint : on l'affiche AVANT que le joueur clique.
                String bloque = spectateur != null ? prerequisManquant(spectateur, l.item) : null;
                if (bloque != null) {
                    lore.add(bloque);
                    lore.add("§8Tu ne peux pas encore acheter cet objet.");
                } else {
                    lore.add("§a▶ Clique pour acheter");
                }
            }
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Vitrine (1re ligne)
    // ══════════════════════════════════════════════════════════════════════════

    /** Le lot occupant cette case de vitrine, ou null si elle est libre/expirée. */
    private Lot lotEnVitrine(int slot) {
        long now = System.currentTimeMillis();
        for (Lot l : lots.values()) {
            if (l.rendu || l.vitrineSlot != slot) continue;
            if (l.vitrineFin <= now) { l.vitrineSlot = -1; continue; } // mise en avant terminée
            return l;
        }
        return null;
    }

    // Une case de vitrine libre : vitre grise cliquable.
    private ItemStack caseVitrineLibre() {
        return plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7§l★ Emplacement libre",
                "",
                "§7Mets une de tes ventes §een avant",
                "§7ici pendant §f" + VITRINE_HEURES + " heures§7.",
                "",
                "§7Prix : §6" + PrivateMines.formatNumber(VITRINE_PRIX) + "$",
                "§8Non prolongeable · l'objet reste aussi",
                "§8dans la liste normale.",
                "",
                "§e▶ Clique pour acheter l'emplacement");
    }

    // L'objet mis en avant : même tuile qu'un lot normal, avec un bandeau vitrine.
    private ItemStack tuileVitrine(Lot l, Player spectateur) {
        ItemStack it = l.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (m.hasDisplayName()) m.setDisplayName("§6★ " + m.getDisplayName());
            else m.setDisplayName("§6★ " + nomItem(l.item));
            List<String> lore = m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§6§l★ MIS EN AVANT");
            lore.add("§7Prix : §6" + PrivateMines.formatNumberBig(l.prix) + "$");
            lore.add("§7Vendeur : §f" + l.vendeurNom);
            lore.add("§7En vitrine encore : §f" + tempsRestant(l.vitrineFin));
            lore.add("");
            String bloque = spectateur != null ? prerequisManquant(spectateur, l.item) : null;
            if (bloque != null) {
                lore.add(bloque);
                lore.add("§8Tu ne peux pas encore acheter cet objet.");
            } else {
                lore.add("§a▶ Clique pour acheter");
            }
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    // Ouvre le choix « quelle vente mettre dans cette case ? » après paiement.
    private void openChoixVitrine(Player p, int slot) {
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_VITRINE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int n = 0;
        for (Lot l : lotsActifs()) {
            if (!l.vendeur.equals(p.getUniqueId())) continue;
            if (l.vitrineSlot >= 0) continue; // déjà en vitrine
            if (n >= 45) break;
            ItemStack it = l.item.clone();
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                List<String> lore = m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("§7Prix : §6" + PrivateMines.formatNumberBig(l.prix) + "$");
                lore.add("");
                lore.add("§e▶ Clique pour le mettre en vitrine");
                lore.add("§8Coût : " + PrivateMines.formatNumber(VITRINE_PRIX) + "$ · "
                        + VITRINE_HEURES + "h");
                m.setLore(lore);
                it.setItemMeta(m);
            }
            menu.setItem(n, it);
            n++;
        }
        if (n == 0) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucune vente disponible",
                    "", "§7Mets d'abord un objet en vente", "§7avec §f/ah sell <prix>§7."));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour à l'hôtel"));
        vitrineChoix.put(p.getUniqueId(), slot);
        p.openInventory(menu);
    }

    // Paye l'emplacement et y place la vente choisie.
    private void placerEnVitrine(Player p, Lot lot, int slot) {
        // La case a pu être prise pendant que le joueur choisissait.
        if (lotEnVitrine(slot) != null) {
            p.sendMessage("§cCet emplacement vient d'être pris par quelqu'un d'autre.");
            openMain(p, page.getOrDefault(p.getUniqueId(), 0));
            return;
        }
        if (!lots.containsKey(lot.id) || lot.vitrineSlot >= 0) {
            p.sendMessage("§cCette vente n'est plus disponible.");
            openMain(p, page.getOrDefault(p.getUniqueId(), 0));
            return;
        }
        EconomyManager eco = plugin.getCustomEco();
        java.math.BigInteger prix = java.math.BigInteger.valueOf((long) VITRINE_PRIX);
        java.math.BigInteger solde = eco.getBalanceBig(p.getUniqueId());
        if (solde.compareTo(prix) < 0) {
            p.sendMessage("§cIl te manque §6" + PrivateMines.formatNumberBig(prix.subtract(solde)) + "$§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        eco.setBalanceBig(p.getUniqueId(), solde.subtract(prix));
        lot.vitrineSlot = slot;
        lot.vitrineFin = System.currentTimeMillis() + VITRINE_MS;
        save();
        p.sendMessage("§6★ §f" + nomItem(lot.item) + " §6est en vitrine pour §f"
                + VITRINE_HEURES + " heures §6(§f-" + PrivateMines.formatNumber(VITRINE_PRIX) + "$§6).");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        openMain(p, 0);
    }

    private String tempsRestant(long expireAt) {
        long ms = expireAt - System.currentTimeMillis();
        if (ms <= 0) return "expiré";
        long h = ms / 3600_000L;
        long min = (ms % 3600_000L) / 60_000L;
        if (h > 0) return h + "h" + String.format("%02d", min);
        return min + " min";
    }

    private List<Lot> lotsActifs() {
        List<Lot> out = new ArrayList<>();
        for (Lot l : lots.values()) if (!l.rendu) out.add(l);
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Clics
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!TITLE_MAIN.equals(title) && !TITLE_MINE.equals(title)
                && !TITLE_RETOURS.equals(title) && !TITLE_HISTO.equals(title)
                && !TITLE_VITRINE.equals(title)) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        // ── Hôtel public ──
        if (TITLE_MAIN.equals(title)) {
            int pg = page.getOrDefault(p.getUniqueId(), 0);
            if (slot == 49) { p.closeInventory(); return; }
            if (slot == 45) { openMain(p, pg - 1); return; }
            if (slot == 53) { openMain(p, pg + 1); return; }
            if (slot == 47) { openMesVentes(p); return; }
            if (slot == 48) { openHistorique(p); return; }
            if (slot == 50) { openRetours(p); return; }

            // 1re ligne = vitrine : acheter l'objet mis en avant, ou acheter la case si libre.
            if (slot < VITRINE_SLOTS) {
                Lot enVitrine = lotEnVitrine(slot);
                if (enVitrine != null) acheter(p, enVitrine);
                else openChoixVitrine(p, slot);
                return;
            }

            int rang = slot - LISTE_START;
            if (rang < 0 || rang >= PER_PAGE) return;
            List<Lot> actifs = lotsActifs();
            int idx = pg * PER_PAGE + rang;
            if (idx >= actifs.size()) return;
            acheter(p, actifs.get(idx));
            return;
        }

        // ── Choix de l'objet à mettre en vitrine ──
        if (TITLE_VITRINE.equals(title)) {
            if (slot == 49) { vitrineChoix.remove(p.getUniqueId()); openMain(p, 0); return; }
            if (slot >= 45) return;
            Integer cible = vitrineChoix.get(p.getUniqueId());
            if (cible == null) { openMain(p, 0); return; }
            // On reconstruit la même liste que celle affichée pour retrouver le lot cliqué.
            List<Lot> dispo = new ArrayList<>();
            for (Lot l : lotsActifs()) {
                if (l.vendeur.equals(p.getUniqueId()) && l.vitrineSlot < 0) dispo.add(l);
            }
            if (slot >= dispo.size()) return;
            vitrineChoix.remove(p.getUniqueId());
            placerEnVitrine(p, dispo.get(slot), cible);
            return;
        }

        // ── Mes ventes (annulation) ──
        if (TITLE_MINE.equals(title)) {
            if (slot == 49) { openMain(p, 0); return; }
            if (slot >= PER_PAGE) return;
            List<Lot> miens = new ArrayList<>();
            for (Lot l : lotsActifs()) if (l.vendeur.equals(p.getUniqueId())) miens.add(l);
            if (slot >= miens.size()) return;
            // Clic droit = prolonger (payant) ; clic gauche = annuler.
            if (event.isRightClick()) prolonger(p, miens.get(slot));
            else annuler(p, miens.get(slot));
            return;
        }

        // ── Mes anciennes ventes (lecture seule) ──
        if (TITLE_HISTO.equals(title)) {
            if (slot == 49) { openMain(p, 0); return; }
            return;
        }

        // ── Mes retours (récupération) ──
        if (TITLE_RETOURS.equals(title)) {
            if (slot == 49) { openMain(p, 0); return; }
            if (slot >= PER_PAGE) return;
            List<ItemStack> mes = retours.get(p.getUniqueId());
            if (mes == null || slot >= mes.size()) return;
            ItemStack it = mes.get(slot);
            // On ne retire de la liste QUE si l'objet a pu être remis (inventaire plein = on garde).
            if (p.getInventory().firstEmpty() == -1) {
                p.sendMessage("§cTon inventaire est plein.");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            mes.remove(slot);
            if (mes.isEmpty()) retours.remove(p.getUniqueId());
            p.getInventory().addItem(it);
            p.updateInventory();
            p.sendMessage("§a✔ §f" + nomItem(it) + " §arécupéré.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            save();
            openRetours(p);
        }
    }

    private void acheter(Player acheteur, Lot lot) {
        // Le lot a pu être acheté entre-temps par quelqu'un d'autre.
        if (!lots.containsKey(lot.id) || lot.rendu) {
            acheteur.sendMessage("§cCette vente n'est plus disponible.");
            openMain(acheteur, page.getOrDefault(acheteur.getUniqueId(), 0));
            return;
        }
        if (lot.vendeur.equals(acheteur.getUniqueId())) {
            acheteur.sendMessage("§cTu ne peux pas acheter ta propre vente. §7(clique dans §fMes ventes§7 pour l'annuler)");
            return;
        }
        if (acheteur.getInventory().firstEmpty() == -1) {
            acheteur.sendMessage("§cTon inventaire est plein.");
            acheteur.playSound(acheteur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Progression : on ne peut pas acheter un objet qu'on n'a pas encore débloqué.
        String bloque = prerequisManquant(acheteur, lot.item);
        if (bloque != null) {
            acheteur.sendMessage(bloque);
            acheteur.playSound(acheteur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        EconomyManager eco = plugin.getCustomEco();
        java.math.BigInteger solde = eco.getBalanceBig(acheteur.getUniqueId());
        if (solde.compareTo(lot.prix) < 0) {
            java.math.BigInteger manque = lot.prix.subtract(solde);
            acheteur.sendMessage("§cIl te manque §6" + PrivateMines.formatNumberBig(manque) + "$§c.");
            acheteur.playSound(acheteur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Débit acheteur, crédit vendeur (moins la taxe).
        eco.setBalanceBig(acheteur.getUniqueId(), solde.subtract(lot.prix));
        java.math.BigInteger taxe = lot.prix.multiply(java.math.BigInteger.valueOf(TAXE_PCT))
                .divide(java.math.BigInteger.valueOf(100));
        java.math.BigInteger net = lot.prix.subtract(taxe);
        eco.setBalanceBig(lot.vendeur, eco.getBalanceBig(lot.vendeur).add(net));

        lots.remove(lot.id);
        ajouterHistorique(lot.vendeur, venteVendue(lot, acheteur.getName(), taxe, net));
        acheteur.getInventory().addItem(lot.item);
        acheteur.updateInventory();
        save();

        acheteur.sendMessage("§a✔ Tu as acheté §f" + nomItem(lot.item) + " §7×" + lot.item.getAmount()
                + " §apour §6" + PrivateMines.formatNumberBig(lot.prix) + "$§a.");
        acheteur.playSound(acheteur.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);

        Player vendeur = Bukkit.getPlayer(lot.vendeur);
        if (vendeur != null) {
            vendeur.sendMessage("§a💰 §f" + acheteur.getName() + " §aa acheté ton §f" + nomItem(lot.item)
                    + " §apour §6" + PrivateMines.formatNumberBig(lot.prix) + "$§a.");
            vendeur.sendMessage("§7Tu reçois §6" + PrivateMines.formatNumberBig(net)
                    + "$ §7(taxe " + TAXE_PCT + "% : §c-" + PrivateMines.formatNumberBig(taxe) + "$§7)");
            vendeur.playSound(vendeur.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        }
        openMain(acheteur, page.getOrDefault(acheteur.getUniqueId(), 0));
    }

    /**
     * Coût de la PROCHAINE prolongation : tarif fixe, indépendant du prix de l'objet.
     * 1 000$ pour le 1er jour ajouté, puis ×1,5 à chaque fois — le nombre de prolongations
     * déjà payées se déduit de la durée actuelle (2 jours = aucune payée).
     */
    private java.math.BigInteger coutProlongation(Lot l) {
        int dejaFaites = Math.max(0, l.joursTotal - DUREE_JOURS_BASE);
        double cout = PROLONG_BASE * Math.pow(PROLONG_MULT, dejaFaites);
        return java.math.BigInteger.valueOf((long) Math.floor(cout));
    }

    // Ajoute 1 jour à une vente (payant), dans la limite de DUREE_JOURS_MAX.
    private void prolonger(Player p, Lot lot) {
        if (!lots.containsKey(lot.id)) { openMesVentes(p); return; }
        if (lot.joursTotal >= DUREE_JOURS_MAX) {
            p.sendMessage("§cCette vente est déjà à sa durée maximale (§f" + DUREE_JOURS_MAX + " jours§c).");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        EconomyManager eco = plugin.getCustomEco();
        java.math.BigInteger cout = coutProlongation(lot);
        java.math.BigInteger solde = eco.getBalanceBig(p.getUniqueId());
        if (solde.compareTo(cout) < 0) {
            p.sendMessage("§cIl te manque §6" + PrivateMines.formatNumberBig(cout.subtract(solde)) + "$§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        eco.setBalanceBig(p.getUniqueId(), solde.subtract(cout));
        lot.joursTotal++;
        lot.expireAt += JOUR_MS;
        save();
        p.sendMessage("§a✔ Vente prolongée d'§f1 jour §apour §6"
                + PrivateMines.formatNumberBig(cout) + "$ §7(" + lot.joursTotal + "/"
                + DUREE_JOURS_MAX + " jours)");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        openMesVentes(p);
    }

    private void annuler(Player p, Lot lot) {
        if (!lots.containsKey(lot.id)) { openMesVentes(p); return; }
        lots.remove(lot.id);
        retours.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(lot.item);
        ajouterHistorique(p.getUniqueId(), venteTerminee(lot, Vente.Statut.ANNULEE));
        save();
        p.sendMessage("§e✖ Vente annulée. Récupère §f" + nomItem(lot.item) + " §edans §fMes retours§e.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1f);
        openMesVentes(p);
    }

    /** Ajoute une entrée en tête de l'historique du vendeur, en respectant le plafond HISTO_MAX. */
    private void ajouterHistorique(UUID vendeur, Vente v) {
        java.util.Deque<Vente> dq = historique.computeIfAbsent(vendeur, k -> new java.util.ArrayDeque<>());
        dq.addFirst(v);
        while (dq.size() > HISTO_MAX) dq.removeLast();
    }

    private Vente venteVendue(Lot lot, String acheteurNom, java.math.BigInteger taxe, java.math.BigInteger net) {
        Vente v = new Vente();
        v.item = lot.item.clone();
        v.prix = lot.prix;
        v.taxe = taxe;
        v.net = net;
        v.acheteurNom = acheteurNom;
        v.date = System.currentTimeMillis();
        v.statut = Vente.Statut.VENDUE;
        return v;
    }

    private Vente venteTerminee(Lot lot, Vente.Statut statut) {
        Vente v = new Vente();
        v.item = lot.item.clone();
        v.prix = lot.prix;
        v.taxe = java.math.BigInteger.ZERO;
        v.net = java.math.BigInteger.ZERO;
        v.acheteurNom = null;
        v.date = System.currentTimeMillis();
        v.statut = statut;
        return v;
    }

    private String nomItem(ItemStack it) {
        if (it == null) return "objet";
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return it.getItemMeta().getDisplayName();
        return "§f" + it.getType().name().toLowerCase().replace('_', ' ');
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Persistance (auctions.yml)
    // ══════════════════════════════════════════════════════════════════════════

    public void load() {
        file = new java.io.File(plugin.getDataFolder(), "auctions.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        lots.clear();
        retours.clear();
        historique.clear();
        org.bukkit.configuration.ConfigurationSection secLots = config.getConfigurationSection("lots");
        if (secLots != null) {
            for (String key : secLots.getKeys(false)) {
                try {
                    Lot l = new Lot();
                    l.id = key;
                    l.vendeur = UUID.fromString(secLots.getString(key + ".vendeur"));
                    l.vendeurNom = secLots.getString(key + ".vendeurNom", "?");
                    l.item = secLots.getItemStack(key + ".item");
                    l.prix = new java.math.BigInteger(secLots.getString(key + ".prix", "0"));
                    l.expireAt = secLots.getLong(key + ".expireAt");
                    l.joursTotal = secLots.getInt(key + ".joursTotal", DUREE_JOURS_BASE);
                    l.vitrineSlot = secLots.getInt(key + ".vitrineSlot", -1);
                    l.vitrineFin = secLots.getLong(key + ".vitrineFin", 0L);
                    if (l.item != null) { lots.put(key, l); compteur++; }
                } catch (Exception ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection secRet = config.getConfigurationSection("retours");
        if (secRet != null) {
            for (String key : secRet.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<?> raw = secRet.getList(key);
                    List<ItemStack> items = new ArrayList<>();
                    if (raw != null) for (Object o : raw) if (o instanceof ItemStack) items.add((ItemStack) o);
                    if (!items.isEmpty()) retours.put(id, items);
                } catch (Exception ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection secHist = config.getConfigurationSection("historique");
        if (secHist != null) {
            for (String key : secHist.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    java.util.Deque<Vente> dq = new java.util.ArrayDeque<>();
                    org.bukkit.configuration.ConfigurationSection secJ = secHist.getConfigurationSection(key);
                    if (secJ == null) continue;
                    // Les clés sont numériques (0, 1, 2…) ordonnées du plus récent au plus ancien.
                    java.util.List<String> idx = new ArrayList<>(secJ.getKeys(false));
                    idx.sort(java.util.Comparator.comparingInt(Integer::parseInt));
                    for (String k : idx) {
                        Vente v = new Vente();
                        v.item = secJ.getItemStack(k + ".item");
                        if (v.item == null) continue;
                        v.prix = new java.math.BigInteger(secJ.getString(k + ".prix", "0"));
                        v.net  = new java.math.BigInteger(secJ.getString(k + ".net", "0"));
                        v.taxe = new java.math.BigInteger(secJ.getString(k + ".taxe", "0"));
                        v.acheteurNom = secJ.getString(k + ".acheteur", null);
                        v.date = secJ.getLong(k + ".date");
                        try { v.statut = Vente.Statut.valueOf(secJ.getString(k + ".statut", "VENDUE")); }
                        catch (Exception e) { v.statut = Vente.Statut.VENDUE; }
                        dq.addLast(v);
                        if (dq.size() >= HISTO_MAX) break;
                    }
                    if (!dq.isEmpty()) historique.put(id, dq);
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        if (config == null) return;
        config = new org.bukkit.configuration.file.YamlConfiguration();
        for (Lot l : lots.values()) {
            String k = "lots." + l.id;
            config.set(k + ".vendeur", l.vendeur.toString());
            config.set(k + ".vendeurNom", l.vendeurNom);
            config.set(k + ".item", l.item);
            config.set(k + ".prix", l.prix.toString());
            config.set(k + ".expireAt", l.expireAt);
            config.set(k + ".joursTotal", l.joursTotal);
            config.set(k + ".vitrineSlot", l.vitrineSlot);
            config.set(k + ".vitrineFin", l.vitrineFin);
        }
        for (java.util.Map.Entry<UUID, List<ItemStack>> e : retours.entrySet()) {
            config.set("retours." + e.getKey(), e.getValue());
        }
        for (java.util.Map.Entry<UUID, java.util.Deque<Vente>> e : historique.entrySet()) {
            int i = 0; // 0 = plus récent (ordre de la Deque)
            for (Vente v : e.getValue()) {
                String k = "historique." + e.getKey() + "." + i;
                config.set(k + ".item", v.item);
                config.set(k + ".prix", v.prix.toString());
                config.set(k + ".net", v.net.toString());
                config.set(k + ".taxe", v.taxe.toString());
                if (v.acheteurNom != null) config.set(k + ".acheteur", v.acheteurNom);
                config.set(k + ".date", v.date);
                config.set(k + ".statut", v.statut.name());
                i++;
            }
        }
        try { config.save(file); } catch (java.io.IOException ignored) {}
    }
}
