package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RÉCOMPENSES QUOTIDIENNES — la boucle de connexion journalière.
 *
 * <p>Cycle de <b>7 jours</b> qui repart à J1 une fois terminé. La série est <b>remise à J1</b>
 * si le joueur saute un jour : c'est la peur de perdre sa série, pas le cadeau, qui crée
 * l'habitude de se connecter. Le jour change à <b>minuit, heure du serveur</b>.</p>
 *
 * <p>Structure de la semaine — alternée et croissante, chaque jour vaut plus que le précédent :
 * <pre>
 *   J1 💰 argent   J2 🔑 clé   J3 💰 argent   J4 ✦ fragments   J5 💰 argent   J6 🔑 clé   J7 🐾 gros lot
 * </pre>
 * L'argent est un <b>pourcentage du prix de la PROCHAINE mine à débloquer</b> (5 / 9 / 13 %), donc
 * il suit tout seul la progression du joueur sans plafond ni plancher (choix assumé : à la mine 1
 * les premiers jours valent quelques dizaines de dollars, mais les mines du début coûtent si peu
 * que ça se rattrape en une heure).</p>
 *
 * <p>Le <b>contenu</b> des cases non-monétaires dépend du PALIER du joueur (4 paliers calés sur la
 * mine débloquée). C'est ce qui évite de donner une clé légendaire à un débutant tout en gardant
 * le système intéressant à la mine 25. Le changement de palier est <b>immédiat</b> : acheter une
 * mine améliore les récompenses dès le lendemain.</p>
 *
 * <p>Accès : un PNJ de rôle {@link NpcManager#ROLE_QUOTIDIEN} (« Récompenses quotidiennes »),
 * posé avec {@code /pnj}. Il scintille tant que le joueur n'a pas réclamé.</p>
 */
public class DailyManager implements Listener {

    private final PrivateMines plugin;

    public DailyManager(PrivateMines plugin) {
        this.plugin = plugin;
        startParticleTask();
    }

    static final String MENU_TITLE = "§a§lRécompenses quotidiennes";

    /** Longueur du cycle. Au-delà du J7 on repart à J1. */
    public static final int CYCLE = 7;

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LES RÉCOMPENSES
    // ═══════════════════════════════════════════════════════════════════════════════════════

    // Type de chaque jour du cycle (index 0 = J1).
    private static final int T_ARGENT = 0, T_CLE = 1, T_FRAGMENTS = 2, T_GROS_LOT = 3;
    private static final int[] TYPE_DU_JOUR = { T_ARGENT, T_CLE, T_ARGENT, T_FRAGMENTS, T_ARGENT, T_CLE, T_GROS_LOT };

    // % du prix de la PROCHAINE mine, par jour (0 = ce jour ne donne pas d'argent).
    // Volontairement PAS d'argent au J7 : son intérêt est le pet/armure + la clé, pas la somme.
    private static final double[] PCT_ARGENT = { 0.05, 0, 0.09, 0, 0.13, 0, 0 };

    // Les 4 paliers, calés sur le rang de la meilleure mine débloquée.
    // P1 = mines 1-7 · P2 = mines 8-14 · P3 = mines 15-21 (set de fer) · P4 = mines 22+ (Arc II).
    private static final int[] PALIER_MINE_MAX = { 7, 14, 21, Integer.MAX_VALUE };
    private static final String[] PALIER_NOM = { "§fMoussaillon", "§aÉcumeur", "§9Corsaire", "§5Capitaine" };

    // J2 — la première clé de la semaine, par palier.
    private static final String[] J2_CLE_RANG = { "commune", "commune", "commune", "rare" };
    private static final int[]    J2_CLE_NB   = { 1, 2, 3, 1 };

    // J4 — Fragments de Souvenir, par palier (repère : un set de fer recyclé = 180 ✦).
    private static final int[] J4_FRAGMENTS = { 100, 150, 200, 300 };

    // J6 — la seconde clé, toujours meilleure que celle du J2.
    private static final String[] J6_CLE_RANG = { "commune", "rare", "rare", "rare" };
    private static final int[]    J6_CLE_NB   = { 2, 1, 2, 3 };

    // J7 — le gros lot : une clé + un pet (ou une armure d'Oublié à partir du palier 2).
    private static final String[] J7_CLE_RANG = { "rare", "rare", "legendaire", "legendaire" };
    private static final int[]    J7_CLE_NB   = { 1, 2, 1, 2 };
    // Plage de rareté du pet/armure (index de ArmorManager.Rarete : 0 = Commune … 5 = Mythique).
    private static final int[] J7_RARETE_MIN = { 0, 1, 2, 3 };
    private static final int[] J7_RARETE_MAX = { 1, 2, 3, 4 };

    /** Palier (0..3) du joueur, d'après la mine la plus haute qu'il a débloquée. */
    public int palierDe(Player p) {
        int rang = plugin.getMineRank(p);
        for (int i = 0; i < PALIER_MINE_MAX.length; i++) {
            if (rang <= PALIER_MINE_MAX[i]) return i;
        }
        return PALIER_MINE_MAX.length - 1;
    }

    /** Prix de la PROCHAINE mine à débloquer (celui de la dernière si le joueur les a toutes). */
    public double prixProchaineMine(Player p) {
        int rang = plugin.getMineRank(p);                       // 1..N
        int idx = Math.min(rang, PrivateMines.MINES.size() - 1); // le rang pointe déjà sur la SUIVANTE
        return PrivateMines.MINES.get(idx).cost;
    }

    /** Montant en $ du jour {@code jour} (1..7) pour ce joueur — 0 si ce jour ne donne pas d'argent. */
    public double montantArgent(Player p, int jour) {
        double pct = PCT_ARGENT[jour - 1];
        if (pct <= 0) return 0;
        return Math.floor(prixProchaineMine(p) * pct);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  ÉTAT DE LA SÉRIE (persisté dans playerdata, comme les métiers)
    // ═══════════════════════════════════════════════════════════════════════════════════════

    // <uuid>.dailyLastDay  = jour (epoch day) de la dernière récompense réclamée, -1 si jamais.
    // <uuid>.dailyStreak   = numéro du jour du cycle (1..7) de cette dernière récompense.

    private long lastDay(UUID id)  { return plugin.getDataConfig().getLong(id + ".dailyLastDay", -1L); }
    private int  streak(UUID id)   { return plugin.getDataConfig().getInt(id + ".dailyStreak", 0); }

    /** Aujourd'hui, en jours depuis l'epoch, à l'heure du serveur (le cycle tourne à minuit). */
    private static long aujourdhui() { return LocalDate.now().toEpochDay(); }

    /** Le joueur a-t-il une récompense à réclamer maintenant ? */
    public boolean peutReclamer(Player p) {
        return lastDay(p.getUniqueId()) != aujourdhui();
    }

    /**
     * Le jour du cycle que le joueur réclamerait s'il cliquait maintenant (1..7).
     * Série continuée si la dernière réclamation date d'hier, sinon retour à J1.
     */
    public int jourAReclamer(Player p) {
        UUID id = p.getUniqueId();
        long last = lastDay(id);
        long today = aujourdhui();
        if (last == today) return streak(id);          // déjà réclamé aujourd'hui : on montre le jour pris
        if (last == today - 1) {                        // série continue
            int s = streak(id);
            return s >= CYCLE ? 1 : s + 1;              // le J7 fait repartir le cycle
        }
        return 1;                                       // jamais joué, ou un jour sauté → la série casse
    }

    /** Outil admin : remet la série à zéro (le joueur repart à J1 dès aujourd'hui). */
    public void reset(UUID id) {
        plugin.getDataConfig().set(id + ".dailyLastDay", null);
        plugin.getDataConfig().set(id + ".dailyStreak", null);
    }

    /** Outil admin : force la série sur un jour donné, comme si la veille avait été réclamée. */
    public void setStreak(UUID id, int jour) {
        int j = Math.max(1, Math.min(CYCLE, jour));
        plugin.getDataConfig().set(id + ".dailyLastDay", aujourdhui() - 1);
        plugin.getDataConfig().set(id + ".dailyStreak", j - 1 <= 0 ? CYCLE : j - 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LE MENU
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static final int[] SLOTS_JOURS = { 10, 11, 12, 13, 14, 15, 16 };

    public void openMenu(Player p) {
        Inventory menu = Bukkit.createInventory(null, 45, MENU_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) menu.setItem(i, bg);

        int palier = palierDe(p);
        int jourCible = jourAReclamer(p);
        boolean dispo = peutReclamer(p);

        // ── Bandeau du haut : où en est le joueur.
        List<String> info = new ArrayList<>();
        info.add("");
        info.add("§7Palier §f" + (palier + 1) + "§7/§f4 §8— " + PALIER_NOM[palier]);
        info.add("§8Monte de mine pour de meilleures récompenses.");
        info.add("");
        if (dispo) {
            info.add("§a✔ Une récompense t'attend : §fJour " + jourCible);
        } else {
            info.add("§7Récompense du jour §fdéjà réclamée§7.");
            info.add("§8Reviens demain — la série casse si tu sautes un jour.");
        }
        menu.setItem(4, plugin.namedItem(Material.CLOCK, "§6§lTa série", info.toArray(new String[0])));

        // ── Les 7 jours.
        for (int j = 1; j <= CYCLE; j++) {
            menu.setItem(SLOTS_JOURS[j - 1], tuileJour(p, j, palier, jourCible, dispo));
        }

        menu.setItem(40, plugin.namedItem(Material.BARRIER, "§cFermer", (String) null));
        p.openInventory(menu);
    }

    /** Un jour du cycle : son contenu, son état (pris / à prendre / verrouillé) et l'aperçu du palier au-dessus. */
    private ItemStack tuileJour(Player p, int jour, int palier, int jourCible, boolean dispo) {
        // État : les jours AVANT le jour courant du cycle sont déjà pris.
        boolean pris = jour < jourCible || (!dispo && jour == jourCible);
        boolean aPrendre = dispo && jour == jourCible;

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String l : descriptionRecompense(p, jour, palier)) lore.add("§7▸ " + l);

        // Aperçu grisé du palier au-dessus : la carotte qui pousse à avancer dans les mines.
        if (palier < PALIER_NOM.length - 1) {
            String[] sup = descriptionRecompense(p, jour, palier + 1);
            lore.add("");
            lore.add("§8Au palier suivant (" + PALIER_NOM[palier + 1] + "§8) :");
            for (String l : sup) lore.add("§8  " + org.bukkit.ChatColor.stripColor(l));
        }

        lore.add("");
        if (aPrendre)   lore.add("§e▶ Clique pour réclamer");
        else if (pris)  lore.add("§a✔ Déjà réclamé");
        else            lore.add("§8Verrouillé — reviens dans " + (jour - jourCible) + " jour(s)");

        String nom = (aPrendre ? "§e§l" : pris ? "§a" : "§8") + "Jour " + jour + (jour == CYCLE ? " §6★" : "");
        ItemStack it = plugin.namedItem(iconeJour(jour, pris, aPrendre), nom, lore.toArray(new String[0]));
        if (aPrendre) {
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                it.setItemMeta(m);
            }
        }
        return it;
    }

    private Material iconeJour(int jour, boolean pris, boolean aPrendre) {
        if (pris) return Material.LIME_STAINED_GLASS_PANE;
        if (!aPrendre && jour != CYCLE) return Material.GRAY_STAINED_GLASS_PANE;
        switch (TYPE_DU_JOUR[jour - 1]) {
            case T_ARGENT:    return Material.GOLD_INGOT;
            case T_CLE:       return Material.TRIPWIRE_HOOK;
            case T_FRAGMENTS: return Material.AMETHYST_SHARD;
            default:          return Material.NETHER_STAR;
        }
    }

    /** Le contenu d'un jour, en texte, pour un palier donné (sert au menu ET à l'aperçu grisé). */
    private String[] descriptionRecompense(Player p, int jour, int palier) {
        int i = jour - 1;
        switch (TYPE_DU_JOUR[i]) {
            case T_ARGENT:
                return new String[] { "§6" + PrivateMines.formatNumber(montantArgent(p, jour)) + " $" };
            case T_CLE: {
                String rang = (jour == 2 ? J2_CLE_RANG : J6_CLE_RANG)[palier];
                int nb = (jour == 2 ? J2_CLE_NB : J6_CLE_NB)[palier];
                return new String[] { nomCle(rang) + " §7×" + nb };
            }
            case T_FRAGMENTS:
                return new String[] { "§d" + J4_FRAGMENTS[palier] + " ✦ §7Fragments de Souvenir" };
            default: {
                String cle = nomCle(J7_CLE_RANG[palier]) + " §7×" + J7_CLE_NB[palier];
                String lot = palier == 0
                        ? "§bUn familier §7(" + plageRarete(palier) + "§7)"
                        : "§bUn familier ou une armure §7(" + plageRarete(palier) + "§7)";
                return new String[] { cle, lot };
            }
        }
    }

    private String plageRarete(int palier) {
        ArmorManager.Rarete[] all = ArmorManager.Rarete.values();
        return all[J7_RARETE_MIN[palier]].display + " §7→ " + all[J7_RARETE_MAX[palier]].display;
    }

    private String nomCle(String rang) {
        if ("legendaire".equals(rang)) return "§6Clé Légendaire";
        if ("rare".equals(rang))       return "§9Clé Rare";
        return "§fClé Commune";
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  LA RÉCLAMATION
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private void reclamer(Player p) {
        if (!peutReclamer(p)) {
            p.sendMessage("§cTu as déjà réclamé ta récompense aujourd'hui. Reviens demain !");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }
        int jour = jourAReclamer(p);
        int palier = palierDe(p);
        UUID id = p.getUniqueId();

        // On enregistre AVANT de donner : si un don échoue, le joueur ne peut pas re-cliquer en boucle.
        plugin.getDataConfig().set(id + ".dailyLastDay", aujourdhui());
        plugin.getDataConfig().set(id + ".dailyStreak", jour);

        p.sendMessage("");
        p.sendMessage("§6§l✦ Récompense quotidienne §8— §eJour " + jour + "§8/§e" + CYCLE);

        switch (TYPE_DU_JOUR[jour - 1]) {
            case T_ARGENT: {
                double montant = montantArgent(p, jour);
                if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(p, montant);
                p.sendMessage("  §8» §6" + PrivateMines.formatNumber(montant) + " $");
                break;
            }
            case T_CLE: {
                String rang = (jour == 2 ? J2_CLE_RANG : J6_CLE_RANG)[palier];
                int nb = (jour == 2 ? J2_CLE_NB : J6_CLE_NB)[palier];
                if (plugin.getCrates() != null) plugin.getCrates().giveKey(id, rang, nb);
                p.sendMessage("  §8» " + nomCle(rang) + " §7×" + nb);
                break;
            }
            case T_FRAGMENTS: {
                int n = J4_FRAGMENTS[palier];
                if (plugin.getFragments() != null) plugin.getFragments().addFragments(p, n);
                p.sendMessage("  §8» §d" + n + " ✦ §7Fragments de Souvenir");
                break;
            }
            default: {
                // Le J7 : la clé du palier + un pet (ou une armure dès le palier 2).
                String rang = J7_CLE_RANG[palier];
                int nb = J7_CLE_NB[palier];
                if (plugin.getCrates() != null) plugin.getCrates().giveKey(id, rang, nb);
                p.sendMessage("  §8» " + nomCle(rang) + " §7×" + nb);
                donnerGrosLot(p, palier);
                break;
            }
        }

        // Fin de cycle : on annonce que la semaine repart, sinon le joueur croit avoir tout fini.
        if (jour == CYCLE) {
            p.sendMessage("§7Cycle terminé — ta série repart au §fJour 1 §7demain.");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        }
        p.sendMessage("");

        openMenu(p);   // on rouvre pour que le joueur voie sa case passer au vert
    }

    /** Le lot du J7 : un familier, ou (palier 2+) une armure d'Oublié, dans la plage de rareté du palier. */
    private void donnerGrosLot(Player p, int palier) {
        int min = J7_RARETE_MIN[palier], max = J7_RARETE_MAX[palier];
        java.util.Random rng = new java.util.Random();
        // Palier 1 : toujours un pet (un débutant n'a pas encore l'Ordinateur pour exploiter une armure).
        boolean armure = palier > 0 && rng.nextBoolean();
        if (armure && plugin.getArmor() != null) {
            ArmorManager.Rarete[] all = ArmorManager.Rarete.values();
            int lo = Math.max(0, Math.min(min, all.length - 1));
            int hi = Math.max(lo, Math.min(max, all.length - 1));
            ArmorManager.Rarete rarete = all[lo + rng.nextInt(hi - lo + 1)];
            ItemStack it = plugin.getArmor().genererArmurePourRarete(rarete, rng);
            if (it != null) {
                for (ItemStack reste : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), reste);
                }
                p.sendMessage("  §8» " + rarete.display + " §8armure d'Oublié");
                return;
            }
        }
        if (plugin.getPetMenu() != null) {
            plugin.getPetMenu().givePetInRarityRange(p, min, max);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  RAPPELS : message au login + particules au-dessus du PNJ
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player p = event.getPlayer();
        // À la TOUTE première connexion, on laisse 15 min : l'Acte I et la découverte passent avant.
        long delai = p.hasPlayedBefore() ? 60L : 20L * 60 * 15;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !peutReclamer(p)) return;
            p.sendMessage("");
            p.sendMessage("§6§l✦ §eTa récompense quotidienne t'attend §8(Jour "
                    + jourAReclamer(p) + "§8/§e" + CYCLE + "§8)");
            p.sendMessage("§7Va voir le PNJ §a« Récompenses quotidiennes » §7au spawn.");
            p.sendMessage("");
        }, delai);
    }

    /** Fait scintiller les PNJ « Récompenses quotidiennes » pour les joueurs qui n'ont pas réclamé. */
    private void startParticleTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            NpcManager npc = plugin.getNpc();
            if (npc == null) return;
            List<org.bukkit.Location> spots = new ArrayList<>();
            for (String id : npc.getIds()) {
                if (!NpcManager.ROLE_QUOTIDIEN.equals(npc.getCfg().getRole(id))) continue;
                org.bukkit.Location loc = npc.getNpcLocation(id);
                if (loc != null) spots.add(loc);
            }
            if (spots.isEmpty()) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!peutReclamer(p)) continue;
                for (org.bukkit.Location loc : spots) {
                    if (loc.getWorld() == null || !loc.getWorld().equals(p.getWorld())) continue;
                    if (loc.distanceSquared(p.getLocation()) > 32 * 32) continue;
                    // Particules envoyées AU JOUEUR seulement : celui qui a déjà réclamé ne voit rien.
                    p.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 2.4, 0), 6, 0.3, 0.3, 0.3, 0);
                    p.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2.6, 0), 2, 0.15, 0.15, 0.15, 0.01);
                }
            }
        }, 40L, 20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  CLICS
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == 40) { p.closeInventory(); return; }
        for (int j = 1; j <= CYCLE; j++) {
            if (SLOTS_JOURS[j - 1] != slot) continue;
            if (peutReclamer(p) && j == jourAReclamer(p)) {
                reclamer(p);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            }
            return;
        }
    }
}
