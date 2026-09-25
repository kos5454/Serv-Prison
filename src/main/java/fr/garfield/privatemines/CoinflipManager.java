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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pile ou face entre joueurs (/coinflip).
 *
 * <p>Un joueur crée un pari avec une mise : {@code /coinflip <mise>} (la mise accepte les suffixes
 * courts : 10K, 2.5M, 100AB…). La mise est <b>débitée à la création</b> (réservée), et le pari
 * apparaît dans le menu public. Un autre joueur clique dessus : sa mise est débitée à son tour,
 * un tirage 50/50 désigne le gagnant, qui rafle <b>le pot entier</b> (les deux mises) — sans taxe.
 *
 * <p>Règles :
 * <ul>
 *   <li>{@value #MAX_PARIS} paris ouverts par joueur ;</li>
 *   <li>un pari en attente expire après {@value #DUREE_JOURS} jours ; la mise revient au créateur
 *       via « Mes remboursements » ;</li>
 *   <li>aucune taxe : le gagnant touche l'intégralité du pot ;</li>
 *   <li>le solde est vérifié à la création ET quand on rejoint (on ne peut pas parier à découvert).</li>
 * </ul>
 *
 * <p>Tout est dans le menu : page publique, « Mes paris » (annulation), « Mon historique »
 * (gagnés/perdus) et « Mes remboursements ». Persistance dans {@code coinflips.yml}.
 */
public class CoinflipManager implements Listener {

    public static final int MAX_PARIS = 3;
    public static final int DUREE_JOURS = 3;         // un pari en attente expire au bout de 3 jours
    private static final long JOUR_MS = 24 * 3600_000L;

    private static final String TITLE_MAIN    = "§8§l🪙 Pile ou Face";
    private static final String TITLE_MINE    = "§8§l🪙 Mes paris en cours";
    private static final String TITLE_HISTO   = "§8§l🪙 Mon historique";
    private static final String TITLE_REMB    = "§8§l🪙 Mes remboursements";

    public static final int HISTO_MAX = 45;
    private static final int PER_PAGE = 45; // 5 lignes d'items, la 6e = navigation

    private final PrivateMines plugin;
    private java.io.File file;
    private org.bukkit.configuration.file.YamlConfiguration config;

    /** Un pari en attente d'adversaire. */
    public static final class Pari {
        String id;
        UUID createur;
        String createurNom;
        BigInteger mise;   // déjà débitée du créateur
        long expireAt;
    }

    /** Une entrée d'historique : un pari terminé (gagné, perdu, expiré ou annulé). */
    public static final class Partie {
        BigInteger mise;         // la mise de CE joueur
        BigInteger gain;         // ce que le joueur a touché net (0 si perdu/expiré/annulé)
        String adversaireNom;    // pseudo de l'autre joueur (null si non joué)
        long date;
        Statut statut;

        enum Statut {
            GAGNE  ("§a✔ Gagné",   Material.LIME_STAINED_GLASS_PANE),
            PERDU  ("§c✖ Perdu",   Material.RED_STAINED_GLASS_PANE),
            EXPIRE ("§e⌛ Expiré",  Material.YELLOW_STAINED_GLASS_PANE),
            ANNULE ("§7✖ Annulé",  Material.GRAY_STAINED_GLASS_PANE);
            final String libelle; final Material pastille;
            Statut(String l, Material m) { this.libelle = l; this.pastille = m; }
        }
    }

    // Paris ouverts (clé = id).
    private final java.util.Map<String, Pari> paris = new java.util.LinkedHashMap<>();
    // Historique par joueur (le plus récent en tête, plafonné à HISTO_MAX).
    private final java.util.Map<UUID, java.util.Deque<Partie>> historique = new java.util.HashMap<>();
    // Mises à rembourser (pari expiré ou annulé), par UUID.
    private final java.util.Map<UUID, List<BigInteger>> remboursements = new java.util.HashMap<>();
    // Page courante dans le menu public.
    private final java.util.Map<UUID, Integer> page = new java.util.HashMap<>();
    private int compteur = 0;
    // Graine pseudo-aléatoire pour le tirage (pas besoin de sécurité cryptographique ici).
    private final java.util.Random rng = new java.util.Random();

    public CoinflipManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Commande /coinflip
    // ══════════════════════════════════════════════════════════════════════════

    public boolean handleCommand(Player p, String[] args) {
        if (args.length == 0) { openMain(p, 0); return true; }

        String sub = args[0].toLowerCase();
        if (sub.equals("help") || sub.equals("aide")) {
            p.sendMessage("§8§m                                        ");
            p.sendMessage("§6§l🪙 Pile ou Face");
            p.sendMessage("§f/coinflip §7— ouvre les paris en cours");
            p.sendMessage("§f/coinflip <mise> §7— crée un pari (ta mise est réservée)");
            p.sendMessage("§7Mises acceptées : §f5000§7, §f10K§7, §f2.5M§7, §f100AB§7...");
            p.sendMessage("§7Max §f" + MAX_PARIS + " paris§7 · expire en §f" + DUREE_JOURS
                    + " jours§7 · §asans taxe");
            p.sendMessage("§7Le gagnant du tirage rafle §ele pot entier§7 (les 2 mises).");
            p.sendMessage("§8§m                                        ");
            return true;
        }
        // Sinon : on tente de lire un montant → création de pari.
        creerPari(p, args[0]);
        return true;
    }

    private void creerPari(Player p, String miseSaisie) {
        if (compterParis(p.getUniqueId()) >= MAX_PARIS) {
            p.sendMessage("§cTu as déjà §e" + MAX_PARIS + " paris§c ouverts. Annules-en un dans §f/coinflip§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        BigInteger mise = EconomyManager.parseMontant(miseSaisie);
        if (mise == null || mise.signum() <= 0) {
            p.sendMessage("§cMise invalide : §f" + miseSaisie + " §7(ex. §f5000§7, §f10K§7, §f2.5M§7)");
            return;
        }
        EconomyManager eco = plugin.getCustomEco();
        BigInteger solde = eco.getBalanceBig(p.getUniqueId());
        if (solde.compareTo(mise) < 0) {
            p.sendMessage("§cIl te manque §6" + PrivateMines.formatNumberBig(mise.subtract(solde)) + "$§c pour parier ça.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Mise réservée : débitée immédiatement (comme mettre un objet en vente à l'AH).
        eco.setBalanceBig(p.getUniqueId(), solde.subtract(mise));

        Pari pari = new Pari();
        pari.id = "C" + (++compteur) + "_" + p.getUniqueId().toString().substring(0, 8);
        pari.createur = p.getUniqueId();
        pari.createurNom = p.getName();
        pari.mise = mise;
        pari.expireAt = System.currentTimeMillis() + DUREE_JOURS * JOUR_MS;
        paris.put(pari.id, pari);
        save();

        p.sendMessage("§a✔ Pari créé : §6" + PrivateMines.formatNumberBig(mise) + "$ §aréservés.");
        p.sendMessage("§7Il expire dans §f" + DUREE_JOURS + " jours §7· §asans taxe§7. Annulable dans §f/coinflip§7.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
    }

    private int compterParis(UUID id) {
        int n = 0;
        for (Pari pr : paris.values()) if (pr.createur.equals(id)) n++;
        return n;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Expiration
    // ══════════════════════════════════════════════════════════════════════════

    /** Tâche : rembourse les paris périmés (mise → « Mes remboursements » du créateur). */
    public void startExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgerExpirés, 20L * 60, 20L * 600); // toutes les 10 min
    }

    private void purgerExpirés() {
        long now = System.currentTimeMillis();
        List<String> aRetirer = new ArrayList<>();
        for (Pari pr : paris.values()) {
            if (pr.expireAt > now) continue;
            remboursements.computeIfAbsent(pr.createur, k -> new ArrayList<>()).add(pr.mise);
            ajouterHistorique(pr.createur, partieTerminee(pr.mise, Partie.Statut.EXPIRE));
            aRetirer.add(pr.id);
            Player c = Bukkit.getPlayer(pr.createur);
            if (c != null) {
                c.sendMessage("§e⌛ Ton pari de §6" + PrivateMines.formatNumberBig(pr.mise)
                        + "$ §ea expiré. Récupère ta mise dans §f/coinflip §7→ §fMes remboursements§e.");
            }
        }
        for (String id : aRetirer) paris.remove(id);
        if (!aRetirer.isEmpty()) save();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Menus
    // ══════════════════════════════════════════════════════════════════════════

    public void openMain(Player p, int pg) {
        List<Pari> actifs = new ArrayList<>(paris.values());
        int totalPages = Math.max(1, (int) Math.ceil(actifs.size() / (double) PER_PAGE));
        pg = Math.max(0, Math.min(pg, totalPages - 1));
        page.put(p.getUniqueId(), pg);

        Inventory menu = Bukkit.createInventory(null, 54, TITLE_MAIN);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= actifs.size()) break;
            menu.setItem(i, tuilePari(actifs.get(idx), p));
        }
        if (actifs.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucun pari en cours",
                    "", "§7Sois le premier : tape", "§f/coinflip <mise>"));
        }

        if (pg > 0) menu.setItem(45, plugin.namedItem(Material.SPECTRAL_ARROW, "§e‹ Page précédente"));
        if (pg < totalPages - 1) menu.setItem(53, plugin.namedItem(Material.SPECTRAL_ARROW, "§ePage suivante ›"));

        menu.setItem(47, boutonMesParis(p));
        menu.setItem(48, boutonHistorique(p));
        menu.setItem(50, boutonRemboursements(p));
        menu.setItem(49, plugin.namedItem(Material.BARRIER, "§cFermer",
                "§7Page §f" + (pg + 1) + "§7/§f" + totalPages));

        p.openInventory(menu);
    }

    private void openMesParis(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_MINE);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int slot = 0;
        for (Pari pr : paris.values()) {
            if (!pr.createur.equals(p.getUniqueId())) continue;
            if (slot >= PER_PAGE) break;
            menu.setItem(slot++, tuileMonPari(pr));
        }
        if (slot == 0) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucun pari en cours",
                    "", "§7Crée un pari avec", "§f/coinflip <mise>"));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour aux paris"));
        p.openInventory(menu);
    }

    private void openHistorique(Player p) {
        java.util.Deque<Partie> mes = historique.getOrDefault(p.getUniqueId(), new java.util.ArrayDeque<>());
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_HISTO);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        int i = 0;
        for (Partie v : mes) {
            if (i >= PER_PAGE) break;
            menu.setItem(i++, tuileHistorique(v));
        }
        if (mes.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Aucune partie passée",
                    "", "§7Tes paris terminés (gagnés, perdus,", "§7expirés, annulés) apparaîtront ici."));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour aux paris"));
        p.openInventory(menu);
    }

    private void openRemboursements(Player p) {
        List<BigInteger> mes = remboursements.getOrDefault(p.getUniqueId(), new ArrayList<>());
        Inventory menu = Bukkit.createInventory(null, 54, TITLE_REMB);
        ItemStack filler = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, filler);

        for (int i = 0; i < mes.size() && i < PER_PAGE; i++) {
            menu.setItem(i, plugin.namedItem(Material.GOLD_NUGGET,
                    "§6" + PrivateMines.formatNumberBig(mes.get(i)) + "$",
                    "", "§7Mise d'un pari expiré ou annulé.", "", "§e▶ Clique pour récupérer"));
        }
        if (mes.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Rien à récupérer",
                    "", "§7Les mises des paris expirés ou", "§7annulés atterrissent ici."));
        }
        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7◀ Retour aux paris"));
        p.openInventory(menu);
    }

    // ── Tuiles ──

    // Un pari public : la mise, le créateur, le temps restant, et l'invitation à rejoindre.
    private ItemStack tuilePari(Pari pr, Player spectateur) {
        boolean mien = pr.createur.equals(spectateur.getUniqueId());
        Material mat = mien ? Material.GOLD_BLOCK : Material.GOLD_INGOT;
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Mise : §6" + PrivateMines.formatNumberBig(pr.mise) + "$");
        lore.add("§7Créateur : §f" + pr.createurNom);
        lore.add("§7Pot à remporter : §6" + PrivateMines.formatNumberBig(pr.mise.multiply(BigInteger.TWO)) + "$");
        lore.add("§7Expire dans : §f" + tempsRestant(pr.expireAt));
        lore.add("");
        if (mien) {
            lore.add("§8C'est ton pari. §7(annule-le dans §fMes paris§7)");
        } else {
            lore.add("§a▶ Clique pour miser §6" + PrivateMines.formatNumberBig(pr.mise) + "$ §aet lancer la pièce");
            lore.add("§750 % de chances de tout rafler.");
        }
        return plugin.namedItem(mat, "§6🪙 Pari de §f" + pr.createurNom,
                lore.toArray(new String[0]));
    }

    // Mon pari en attente : bouton d'annulation.
    private ItemStack tuileMonPari(Pari pr) {
        return plugin.namedItem(Material.GOLD_BLOCK, "§6🪙 Ton pari",
                "",
                "§7Mise réservée : §6" + PrivateMines.formatNumberBig(pr.mise) + "$",
                "§7Pot à remporter : §6" + PrivateMines.formatNumberBig(pr.mise.multiply(BigInteger.TWO)) + "$",
                "§7Expire dans : §f" + tempsRestant(pr.expireAt),
                "",
                "§c▶ Clique pour annuler §7(mise remboursée)");
    }

    private ItemStack tuileHistorique(Partie v) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Résultat : " + v.statut.libelle);
        lore.add("§7Mise : §6" + PrivateMines.formatNumberBig(v.mise) + "$");
        if (v.statut == Partie.Statut.GAGNE) {
            lore.add("§7Adversaire : §f" + (v.adversaireNom != null ? v.adversaireNom : "?"));
            lore.add("§7Gain net : §a+" + PrivateMines.formatNumberBig(v.gain) + "$");
        } else if (v.statut == Partie.Statut.PERDU) {
            lore.add("§7Adversaire : §f" + (v.adversaireNom != null ? v.adversaireNom : "?"));
            lore.add("§7Perte : §c-" + PrivateMines.formatNumberBig(v.mise) + "$");
        }
        lore.add("§7" + ilYA(v.date));
        return plugin.namedItem(v.statut.pastille, v.statut.libelle, lore.toArray(new String[0]));
    }

    private ItemStack boutonMesParis(Player p) {
        return plugin.namedItem(Material.CHEST, "§6§l📤 Mes paris en cours",
                "",
                "§7Tu as §e" + compterParis(p.getUniqueId()) + "§7/§e" + MAX_PARIS + " §7paris ouverts.",
                "§7Annule un pari en attente ici §7(mise remboursée).",
                "",
                "§f§lComment parier ?",
                "§7Tape : §a/coinflip <mise>",
                "§8Ex. §7/coinflip 10K §8· §7/coinflip 2.5M",
                "§7Ta mise est §eréservée §7jusqu'à ce qu'on te rejoigne.",
                "",
                "§a✔ Sans taxe : le gagnant rafle tout.",
                "",
                "§e▶ Clique pour ouvrir");
    }

    private ItemStack boutonHistorique(Player p) {
        int n = historique.getOrDefault(p.getUniqueId(), new java.util.ArrayDeque<>()).size();
        return plugin.namedItem(Material.WRITABLE_BOOK, "§e§l📜 Mon historique",
                "", "§7Parties terminées : §e" + n,
                "§7(gagnées, perdues, expirées, annulées)", "", "§e▶ Clique pour ouvrir");
    }

    private ItemStack boutonRemboursements(Player p) {
        int n = remboursements.getOrDefault(p.getUniqueId(), new ArrayList<>()).size();
        return plugin.namedItem(Material.ENDER_CHEST, "§b§l📥 Mes remboursements",
                "", "§7Mises à récupérer : §e" + n,
                "§7(paris expirés ou annulés)", "", "§e▶ Clique pour ouvrir");
    }

    private String tempsRestant(long expireAt) {
        long ms = expireAt - System.currentTimeMillis();
        if (ms <= 0) return "expiré";
        long h = ms / 3600_000L;
        long min = (ms % 3600_000L) / 60_000L;
        if (h > 0) return h + "h" + String.format("%02d", min);
        return min + " min";
    }

    private String ilYA(long date) {
        long ms = System.currentTimeMillis() - date;
        if (ms < 60_000L) return "à l'instant";
        long min = ms / 60_000L;
        if (min < 60) return "il y a " + min + " min";
        long h = min / 60;
        if (h < 24) return "il y a " + h + "h";
        return "il y a " + (h / 24) + "j";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Clics
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onCoinflipClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!TITLE_MAIN.equals(title) && !TITLE_MINE.equals(title)
                && !TITLE_HISTO.equals(title) && !TITLE_REMB.equals(title)) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        // ── Page publique ──
        if (TITLE_MAIN.equals(title)) {
            int pg = page.getOrDefault(p.getUniqueId(), 0);
            if (slot == 49) { p.closeInventory(); return; }
            if (slot == 45) { openMain(p, pg - 1); return; }
            if (slot == 53) { openMain(p, pg + 1); return; }
            if (slot == 47) { openMesParis(p); return; }
            if (slot == 48) { openHistorique(p); return; }
            if (slot == 50) { openRemboursements(p); return; }
            if (slot >= PER_PAGE) return;
            List<Pari> actifs = new ArrayList<>(paris.values());
            int idx = pg * PER_PAGE + slot;
            if (idx >= actifs.size()) return;
            rejoindre(p, actifs.get(idx));
            return;
        }

        // ── Mes paris (annulation) ──
        if (TITLE_MINE.equals(title)) {
            if (slot == 49) { openMain(p, 0); return; }
            if (slot >= PER_PAGE) return;
            List<Pari> miens = new ArrayList<>();
            for (Pari pr : paris.values()) if (pr.createur.equals(p.getUniqueId())) miens.add(pr);
            if (slot >= miens.size()) return;
            annuler(p, miens.get(slot));
            return;
        }

        // ── Historique (lecture seule) ──
        if (TITLE_HISTO.equals(title)) {
            if (slot == 49) openMain(p, 0);
            return;
        }

        // ── Remboursements (récupération) ──
        if (TITLE_REMB.equals(title)) {
            if (slot == 49) { openMain(p, 0); return; }
            if (slot >= PER_PAGE) return;
            List<BigInteger> mes = remboursements.get(p.getUniqueId());
            if (mes == null || slot >= mes.size()) return;
            BigInteger montant = mes.remove(slot);
            if (mes.isEmpty()) remboursements.remove(p.getUniqueId());
            EconomyManager eco = plugin.getCustomEco();
            eco.setBalanceBig(p.getUniqueId(), eco.getBalanceBig(p.getUniqueId()).add(montant));
            p.sendMessage("§a✔ §6" + PrivateMines.formatNumberBig(montant) + "$ §arécupérés.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            save();
            openRemboursements(p);
        }
    }

    /** Un joueur rejoint un pari : on débite sa mise, on tire, on paie le gagnant. */
    private void rejoindre(Player joueur, Pari pari) {
        // Le pari a pu être pris/annulé/expiré entre-temps.
        if (!paris.containsKey(pari.id)) {
            joueur.sendMessage("§cCe pari n'est plus disponible.");
            openMain(joueur, page.getOrDefault(joueur.getUniqueId(), 0));
            return;
        }
        if (pari.createur.equals(joueur.getUniqueId())) {
            joueur.sendMessage("§cTu ne peux pas rejoindre ton propre pari. §7(annule-le dans §fMes paris§7)");
            return;
        }
        EconomyManager eco = plugin.getCustomEco();
        BigInteger solde = eco.getBalanceBig(joueur.getUniqueId());
        if (solde.compareTo(pari.mise) < 0) {
            joueur.sendMessage("§cIl te manque §6" + PrivateMines.formatNumberBig(pari.mise.subtract(solde)) + "$§c.");
            joueur.playSound(joueur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Débit du rejoignant (la mise du créateur est déjà réservée).
        eco.setBalanceBig(joueur.getUniqueId(), solde.subtract(pari.mise));
        paris.remove(pari.id);

        BigInteger pot = pari.mise.multiply(BigInteger.TWO);
        boolean createurGagne = rng.nextBoolean();
        UUID gagnant = createurGagne ? pari.createur : joueur.getUniqueId();
        String nomCreateur = pari.createurNom;
        String nomJoueur = joueur.getName();

        // Crédit du gagnant du pot entier (aucune taxe).
        eco.setBalanceBig(gagnant, eco.getBalanceBig(gagnant).add(pot));

        // Historique des deux camps.
        ajouterHistorique(pari.createur, createurGagne
                ? partieGagnee(pari.mise, pot, nomJoueur)
                : partieTerminee(pari.mise, Partie.Statut.PERDU, nomJoueur));
        ajouterHistorique(joueur.getUniqueId(), createurGagne
                ? partieTerminee(pari.mise, Partie.Statut.PERDU, nomCreateur)
                : partieGagnee(pari.mise, pot, nomCreateur));
        save();

        // Feedback au rejoignant.
        boolean joueurGagne = !createurGagne;
        String face = joueurGagne ? "§ePILE" : "§6FACE";
        if (joueurGagne) {
            joueur.sendMessage("§8§m                                        ");
            joueur.sendMessage("🪙 " + face + " §a— tu gagnes le pari contre §f" + nomCreateur + " §a!");
            joueur.sendMessage("§7Tu rafles §a+" + PrivateMines.formatNumberBig(pot) + "$ §7(pot entier).");
            joueur.sendMessage("§8§m                                        ");
            joueur.playSound(joueur.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        } else {
            joueur.sendMessage("§8§m                                        ");
            joueur.sendMessage("🪙 " + face + " §c— tu perds le pari contre §f" + nomCreateur + "§c.");
            joueur.sendMessage("§7Tu perds ta mise de §c-" + PrivateMines.formatNumberBig(pari.mise) + "$§7.");
            joueur.sendMessage("§8§m                                        ");
            joueur.playSound(joueur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
        }

        // Feedback au créateur s'il est en ligne.
        Player createur = Bukkit.getPlayer(pari.createur);
        if (createur != null) {
            if (createurGagne) {
                createur.sendMessage("§8§m                                        ");
                createur.sendMessage("§a🪙 §f" + nomJoueur + " §aa rejoint ton pari... et tu §agagnes §a!");
                createur.sendMessage("§7Tu rafles §a+" + PrivateMines.formatNumberBig(pot) + "$ §7(pot entier).");
                createur.sendMessage("§8§m                                        ");
                createur.playSound(createur.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            } else {
                createur.sendMessage("§8§m                                        ");
                createur.sendMessage("§c🪙 §f" + nomJoueur + " §ca rejoint ton pari... et tu §cperds§c.");
                createur.sendMessage("§7Tu perds ta mise de §c-" + PrivateMines.formatNumberBig(pari.mise) + "$§7.");
                createur.sendMessage("§8§m                                        ");
                createur.playSound(createur.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            }
        }
        openMain(joueur, page.getOrDefault(joueur.getUniqueId(), 0));
    }

    private void annuler(Player p, Pari pari) {
        if (!paris.containsKey(pari.id)) { openMesParis(p); return; }
        paris.remove(pari.id);
        // Remboursement de la mise réservée.
        remboursements.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(pari.mise);
        ajouterHistorique(p.getUniqueId(), partieTerminee(pari.mise, Partie.Statut.ANNULE));
        save();
        p.sendMessage("§e✖ Pari annulé. Récupère ta mise de §6" + PrivateMines.formatNumberBig(pari.mise)
                + "$ §edans §fMes remboursements§e.");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1f);
        openMesParis(p);
    }

    private void ajouterHistorique(UUID id, Partie v) {
        java.util.Deque<Partie> dq = historique.computeIfAbsent(id, k -> new java.util.ArrayDeque<>());
        dq.addFirst(v);
        while (dq.size() > HISTO_MAX) dq.removeLast();
    }

    private Partie partieGagnee(BigInteger mise, BigInteger pot, String adversaire) {
        Partie v = new Partie();
        v.mise = mise;
        v.gain = pot.subtract(mise); // gain NET = le pot moins ce qu'on avait misé
        v.adversaireNom = adversaire;
        v.date = System.currentTimeMillis();
        v.statut = Partie.Statut.GAGNE;
        return v;
    }

    private Partie partieTerminee(BigInteger mise, Partie.Statut statut) {
        return partieTerminee(mise, statut, null);
    }

    private Partie partieTerminee(BigInteger mise, Partie.Statut statut, String adversaire) {
        Partie v = new Partie();
        v.mise = mise;
        v.gain = BigInteger.ZERO;
        v.adversaireNom = adversaire;
        v.date = System.currentTimeMillis();
        v.statut = statut;
        return v;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Persistance (coinflips.yml)
    // ══════════════════════════════════════════════════════════════════════════

    public void load() {
        file = new java.io.File(plugin.getDataFolder(), "coinflips.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        paris.clear();
        remboursements.clear();
        historique.clear();

        org.bukkit.configuration.ConfigurationSection secParis = config.getConfigurationSection("paris");
        if (secParis != null) {
            for (String key : secParis.getKeys(false)) {
                try {
                    Pari pr = new Pari();
                    pr.id = key;
                    pr.createur = UUID.fromString(secParis.getString(key + ".createur"));
                    pr.createurNom = secParis.getString(key + ".createurNom", "?");
                    pr.mise = new BigInteger(secParis.getString(key + ".mise", "0"));
                    pr.expireAt = secParis.getLong(key + ".expireAt");
                    if (pr.mise.signum() > 0) { paris.put(key, pr); compteur++; }
                } catch (Exception ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection secRemb = config.getConfigurationSection("remboursements");
        if (secRemb != null) {
            for (String key : secRemb.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<BigInteger> montants = new ArrayList<>();
                    for (String s : secRemb.getStringList(key)) montants.add(new BigInteger(s));
                    if (!montants.isEmpty()) remboursements.put(id, montants);
                } catch (Exception ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection secHist = config.getConfigurationSection("historique");
        if (secHist != null) {
            for (String key : secHist.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    java.util.Deque<Partie> dq = new java.util.ArrayDeque<>();
                    org.bukkit.configuration.ConfigurationSection secJ = secHist.getConfigurationSection(key);
                    if (secJ == null) continue;
                    java.util.List<String> idx = new ArrayList<>(secJ.getKeys(false));
                    idx.sort(java.util.Comparator.comparingInt(Integer::parseInt));
                    for (String k : idx) {
                        Partie v = new Partie();
                        v.mise = new BigInteger(secJ.getString(k + ".mise", "0"));
                        v.gain = new BigInteger(secJ.getString(k + ".gain", "0"));
                        v.adversaireNom = secJ.getString(k + ".adversaire", null);
                        v.date = secJ.getLong(k + ".date");
                        try { v.statut = Partie.Statut.valueOf(secJ.getString(k + ".statut", "PERDU")); }
                        catch (Exception e) { v.statut = Partie.Statut.PERDU; }
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
        for (Pari pr : paris.values()) {
            String k = "paris." + pr.id;
            config.set(k + ".createur", pr.createur.toString());
            config.set(k + ".createurNom", pr.createurNom);
            config.set(k + ".mise", pr.mise.toString());
            config.set(k + ".expireAt", pr.expireAt);
        }
        for (java.util.Map.Entry<UUID, List<BigInteger>> e : remboursements.entrySet()) {
            List<String> montants = new ArrayList<>();
            for (BigInteger b : e.getValue()) montants.add(b.toString());
            config.set("remboursements." + e.getKey(), montants);
        }
        for (java.util.Map.Entry<UUID, java.util.Deque<Partie>> e : historique.entrySet()) {
            int i = 0;
            for (Partie v : e.getValue()) {
                String k = "historique." + e.getKey() + "." + i;
                config.set(k + ".mise", v.mise.toString());
                config.set(k + ".gain", v.gain.toString());
                if (v.adversaireNom != null) config.set(k + ".adversaire", v.adversaireNom);
                config.set(k + ".date", v.date);
                config.set(k + ".statut", v.statut.name());
                i++;
            }
        }
        try { config.save(file); } catch (java.io.IOException ignored) {}
    }
}
