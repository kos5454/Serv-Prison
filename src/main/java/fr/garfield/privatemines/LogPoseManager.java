package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Boussole du Log Pose — visite guidée d'Alabasta (Arc II).
 *
 * <p>Le joueur reçoit une boussole (item COMPASS) dont l'aiguille pointe vers le lieu à
 * découvrir (lodestone tracking). En s'approchant d'un lieu, son lore se révèle (titre + son +
 * particules + chat), puis la boussole se recale vers le lieu suivant. Le dernier lieu atteint
 * clôt la visite et récompense le joueur (clé de crate).
 *
 * <p>Les lieux sont dans {@link #SPOTS} (coordonnées PROVISOIRES à ajuster une fois la map buildée).
 * La progression (index du lieu courant) est persistée dans {@code logpose.yml} par UUID.
 */
public class LogPoseManager implements Listener {

    private final PrivateMines plugin;
    private final NamespacedKey logPoseKey;   // marque l'item boussole (anti-triche)

    // Rang de la clé de crate donnée en fin de visite, et nombre.
    private static final String REWARD_KEY_RANK = "legendaire";
    private static final int    REWARD_KEY_AMOUNT = 1;

    // Distance (blocs) sous laquelle on considère le lieu « atteint ».
    private static final double ARRIVE_RADIUS = 5.0;

    /** Un lieu de la visite : nom affiché + position + lignes de lore. */
    static final class Spot {
        final String name; final int x, y, z; final String[] lore;
        Spot(String name, int x, int y, int z, String... lore) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.lore = lore;
        }

        /**
         * Version « brouillée » du nom (caractères Minecraft §k qui changent en permanence),
         * pour taquiner la PROCHAINE destination sans la révéler. On conserve les codes couleur/
         * format en tête du nom, on insère §k juste avant le texte visible, et on ferme par §r
         * pour que le brouillage ne déborde pas sur le reste de la ligne.
         */
        String hidden() {
            // Sépare les codes de format en tête (§x…) du texte visible.
            int i = 0;
            StringBuilder prefix = new StringBuilder();
            while (i + 1 < name.length() && name.charAt(i) == '§') {
                prefix.append(name, i, i + 2);
                i += 2;
            }
            return prefix + "§k" + name.substring(i) + "§r";
        }
    }

    // ⬇️ Lieux de la visite d'Alabasta. Coordonnées PROVISOIRES (près de la mine 22 à Alabasta)
    //    à remplacer par les vrais lieux une fois la map buildée. Ordre = ordre de visite.
    static final List<Spot> SPOTS = java.util.Arrays.asList(
        // Réécrit le 2026-08-23 — voir « Serv prison/DIALOGUES_PNJ.md ». Texte calé sur le BUILD
        // réel : une église à clocher qui domine la ville, pas une petite salle de grès.
        // Le maillet et le ciseau au pied du mur sont ceux du Dernier Témoin (3e lieu) : le joueur
        // trouve les outils au 1er lieu et rencontre leur propriétaire au dernier. Ne pas les
        // retirer sans retirer aussi « je grave leurs noms » de son dialogue.
        new Spot("§e§lLe Sanctuaire des Noms", -477, 71, 1015,
                "§7Le clocher domine tout le quartier. Personne ne l'entretient et pourtant l'horloge tourne toujours.",
                "§7On entend le mécanisme de l'horloge au-dessus de ta tête. C'est le seul bruit de la pièce.",
                "§7Un maillet et un ciseau sont posés au pied du mur, sous des centaines de noms gravés dans le grès."),
        // Réécrit le 2026-08-24 — voir « Serv prison/DIALOGUES_PNJ.md ».
        // ⚠️ L'ancien texte DÉFLORAIT tout l'arc : il répétait le Gardien du Seuil (« quelqu'un a
        // fermé l'eau ») et grillait la confession du Dernier Témoin (« ils se sont battus pour ce
        // qui restait au fond des gourdes »). Le lieu MONTRE, les PNJ EXPLIQUENT — ne pas remettre
        // d'explication ici. L'eau coule parce que le Témoin dit l'avoir rouverte : le joueur voit
        // le résultat au 2e lieu et n'apprend la cause qu'au 3e.
        // Les gourdes vides sont l'écho exact de sa réplique ; les retirer casse le rappel.
        new Spot("§6§lLa Fontaine Muette", -409, 71, 971,
                "§7Tu entends de l'eau bien avant de voir la fontaine. Elle coule à plein débit au milieu de la place.",
                "§7Le bruit de l'eau porte très loin parce qu'il n'y a plus rien pour le couvrir.",
                "§7Il y a des dizaines de gourdes vides dans le sable autour du bassin. Aucune n'a été ramassée."),
        // Ce 3e lieu = emplacement du PNJ conteur « Le Dernier Témoin » (déjà placé).
        // Quand on y arrive, la boussole reste ; c'est le PNJ qui, à la fin de son récit, donnera la
        // clé de crate ET retirera la boussole (voir finishVisit + le rôle temoin_final).
        // Réécrit le 2026-08-24 — voir « Serv prison/DIALOGUES_PNJ.md ».
        // ⚠️ L'ancien texte le présentait en RESCAPÉ (« il a survécu pour le dire ») alors que toute
        // la scène repose sur son aveu : c'est LUI qui a fermé l'eau. Ne pas le rendre sympathique
        // avant qu'il parle, et ne pas annoncer qu'il « connaît le fin mot ».
        // Les outils bouclent la visite : trouvés au 1er lieu (Sanctuaire), en main au dernier.
        new Spot("§c§lLe Dernier Témoin", -476, 81, 1192,
                "§7Ta boussole ne tourne plus du tout. Elle t'a amené au bout de sa route.",
                "§7Un maillet et un ciseau, les mêmes qu'au Sanctuaire des Noms, sauf qu'ici quelqu'un s'en sert encore.",
                "§7Il ne lèvera pas la tête pour toi. C'est à toi d'aller lui adresser la parole.")
    );

    // Progression : UUID -> index du prochain lieu à atteindre (0..SPOTS.size()). == size() → visite finie.
    private final java.util.Map<UUID, Integer> progress = new java.util.HashMap<>();
    private File file;
    private org.bukkit.configuration.file.FileConfiguration config;

    public LogPoseManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.logPoseKey = new NamespacedKey(plugin, "logpose");
        load();
    }

    // ===== Persistance =====

    private void load() {
        file = new File(plugin.getDataFolder(), "logpose.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            config = new org.bukkit.configuration.file.YamlConfiguration();
            return;
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection sec = config.getConfigurationSection("progress");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try { progress.put(UUID.fromString(key), sec.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    /** Le joueur a-t-il TERMINÉ la visite d'Alabasta (Boussole du Log Pose) ? = a atteint tous les lieux.
     *  Utilisé comme condition de déblocage du Prestige (fin des quêtes de l'Arc II). */
    public boolean aTermineVisite(Player p) {
        return progress.getOrDefault(p.getUniqueId(), 0) >= SPOTS.size();
    }

    public void save() {
        if (config == null) config = new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.Map.Entry<UUID, Integer> e : progress.entrySet()) {
            config.set("progress." + e.getKey(), e.getValue());
        }
        try { config.save(file); }
        catch (java.io.IOException e) { plugin.getLogger().warning("logpose.yml : " + e.getMessage()); }
    }

    // ===== Item boussole =====

    /** Vrai si l'item est une Boussole du Log Pose (par tag PDC). */
    boolean isLogPose(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS) return false;
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(logPoseKey, PersistentDataType.BYTE);
    }

    /**
     * Neutralise le « navwand » de WorldEdit sur la Log Pose : par défaut WorldEdit fait de la
     * boussole (COMPASS) un outil de navigation (clic droit = se téléporter sur le bloc visé,
     * clic gauche = traverser). Comme notre Log Pose EST une boussole, elle héritait de ce pouvoir.
     * On annule donc toute interaction quand l'item tenu est la Log Pose — l'aiguille (lodestone)
     * continue de fonctionner, mais plus de téléportation. Le navwand reste dispo sur une boussole
     * ordinaire (pour les builds). Priorité HIGHEST : on passe avant WorldEdit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLogPoseInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR
                && a != Action.LEFT_CLICK_BLOCK && a != Action.LEFT_CLICK_AIR) return;
        if (!isLogPose(event.getItem())) return;
        event.setCancelled(true);
    }

    private boolean hasLogPose(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isLogPose(it)) return true;
        return false;
    }

    /** Construit une boussole pointant vers `target` (ou sans cible si target == null). */
    private ItemStack createLogPose(Location target) {
        ItemStack it = new ItemStack(Material.COMPASS);
        CompassMeta m = (CompassMeta) it.getItemMeta();
        m.setDisplayName("§6§l✦ Log Pose");
        m.setLore(java.util.Arrays.asList(
                "§7L'aiguille pointe vers le prochain lieu",
                "§7à découvrir dans ces terres de sable.",
                "",
                "§8Suis-la, et l'histoire se dévoilera."));
        if (target != null) {
            m.setLodestone(target);
            m.setLodestoneTracked(false); // pointe vers la position même sans vrai lodestone posé
        }
        m.getPersistentDataContainer().set(logPoseKey, PersistentDataType.BYTE, (byte) 1);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    /** Met à jour (ou pose) la boussole du joueur pour qu'elle pointe vers son lieu courant. */
    private void refreshLogPose(Player p) {
        int idx = progress.getOrDefault(p.getUniqueId(), 0);
        Location target = (idx >= 0 && idx < SPOTS.size())
                ? spotLocation(p.getWorld(), SPOTS.get(idx)) : null;
        ItemStack fresh = createLogPose(target);
        // Remplace la 1re boussole trouvée dans l'inventaire (sinon on l'ajoute).
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLogPose(inv.getItem(i))) { inv.setItem(i, fresh); return; }
        }
        inv.addItem(fresh);
    }

    /**
     * Où le joueur doit aller MAINTENANT dans la visite d'Alabasta : le Gardien du Seuil tant
     * qu'il n'a pas la boussole, le lieu suivant pendant la visite, le Dernier Témoin à la fin.
     * Le texte de l'objectif étant dynamique, la boussole de quête ne peut pas le deviner :
     * c'est ce manager qui sait où en est le joueur.
     */
    public Location cibleActuelle(Player p) {
        int idx = progress.getOrDefault(p.getUniqueId(), 0);
        NpcManager npc = plugin.getNpc();
        if (!hasLogPose(p) && idx == 0) {
            return npc == null ? null : npc.locationOfRole(NpcManager.ROLE_GUIDE_BOUSSOLE);
        }
        if (idx < SPOTS.size()) return spotLocation(p.getWorld(), SPOTS.get(idx));
        return npc == null ? null : npc.locationOfRole(NpcManager.ROLE_TEMOIN_FINAL);
    }

    private Location spotLocation(World w, Spot s) {
        return new Location(w, s.x + 0.5, s.y, s.z + 0.5);
    }

    // ===== Remise (appelée par le PNJ conteur donneur) =====

    /** Donne la boussole au joueur (ou la lui rend s'il l'a perdue). Démarre la visite si besoin. */
    public void giveLogPose(Player p) {
        UUID id = p.getUniqueId();
        int idx = progress.getOrDefault(id, 0);
        if (idx >= SPOTS.size()) {
            p.sendMessage("§6✦ §7Tu as déjà parcouru toutes ces terres, voyageur.");
            return;
        }
        if (hasLogPose(p)) {
            refreshLogPose(p);
            // Destination courante pas encore atteinte → nom brouillé (§k).
            p.sendMessage("§6✦ §7Ton Log Pose se recale vers §f" + SPOTS.get(idx).hidden());
            return;
        }
        progress.putIfAbsent(id, 0);
        refreshLogPose(p);
        refreshObjective(p); // BossBar : passe de « Trouve le Gardien » à « Suis ton Log Pose (0/3) »
        save();
        p.sendTitle("§6§l✦ Log Pose", "§7Suis l'aiguille et découvre Alabasta", 10, 60, 20);
        p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1f);
        p.sendMessage("§6✦ §7On te confie un §6Log Pose§7. §cSuis son aiguille §7: elle te mènera");
        p.sendMessage("§6✦ §7de lieu en lieu, et chaque terre te livrera son histoire.");
    }

    // ===== Boucle de détection d'approche =====

    /** Démarre la tâche qui vérifie, toutes les 10 ticks, si un porteur atteint son lieu courant. */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Integer idx = progress.get(p.getUniqueId());
                if (idx == null || idx >= SPOTS.size()) continue; // pas de visite en cours
                if (!hasLogPose(p)) continue;
                Spot s = SPOTS.get(idx);
                Location target = spotLocation(p.getWorld(), s);
                if (!p.getWorld().equals(target.getWorld())) continue;
                if (p.getLocation().distanceSquared(target) <= ARRIVE_RADIUS * ARRIVE_RADIUS) {
                    reachSpot(p, idx);
                }
            }
        }, 40L, 10L);
    }

    // ===== Objectif de quête (BossBar en haut de l'écran) =====

    /**
     * Recale la BossBar « objectif » selon l'état de la visite du joueur :
     *  - pas de boussole encore reçue → « Trouve le Gardien du Seuil »
     *  - visite en cours (idx lieux découverts) → « Suis ton Log Pose (idx/total) », barre qui se remplit
     *  - tous les lieux vus → « Parle au Dernier Témoin » (barre pleine)
     * La barre est retirée (clearQuestObjective) une fois la visite clôturée par le PNJ.
     */
    public void refreshObjective(Player p) {
        GuideManager guide = plugin.getGuide();
        if (guide == null) return;
        int total = SPOTS.size();
        int idx = progress.getOrDefault(p.getUniqueId(), 0);
        if (!hasLogPose(p) && idx == 0) {
            // Arrivée dans l'Arc II mais boussole pas encore prise.
            guide.setQuestObjective(p, "§eLes Sables du Second Souffle §7» §fTrouve le §6Gardien du Seuil", 0.0);
        } else if (idx < total) {
            String next = SPOTS.get(idx).hidden();
            guide.setQuestObjective(p,
                    "§eLes Sables du Second Souffle §7» §fSuis ton §6Log Pose §7(" + idx + "/" + total + ") §8→ " + next,
                    (double) idx / total);
        } else {
            guide.setQuestObjective(p, "§eLes Sables du Second Souffle §7» §fParle au §cDernier Témoin", 1.0);
        }
    }

    /** Le joueur a atteint le lieu d'index idx : révèle le lore, avance, recale ou termine. */
    private void reachSpot(Player p, int idx) {
        Spot s = SPOTS.get(idx);
        // Effets d'arrivée.
        p.sendTitle(s.name, "§7Lieu découvert §8(" + (idx + 1) + "/" + SPOTS.size() + ")", 10, 70, 20);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.02);
        p.sendMessage("");
        p.sendMessage("§6✦ " + s.name);
        for (String line : s.lore) p.sendMessage("  §o" + line);
        p.sendMessage("");

        int next = idx + 1;
        progress.put(p.getUniqueId(), next);
        if (next < SPOTS.size()) {
            refreshLogPose(p);
            // Le nom de la PROCHAINE destination reste brouillé (§k) : mystère jusqu'à ce qu'on y arrive.
            p.sendMessage("§6✦ §7Ton Log Pose se recale vers un lieu encore sans nom… §f" + SPOTS.get(next).hidden());
            p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1.3f);
        } else {
            finishVisit(p);
        }
        refreshObjective(p); // met à jour la BossBar (progression x/total, ou « Parle au Témoin »)
        save();
    }

    /**
     * Fin de la VISITE (dernier lieu atteint) : on NE donne PAS encore la récompense et on NE retire
     * PAS la boussole. La visite mène à un PNJ conteur (le Dernier Témoin) qui racontera l'histoire ;
     * c'est LUI qui, à sa dernière réplique, appellera {@link #completeVisit(Player)} pour remettre la
     * clé de crate et faire disparaître la boussole (devenue inutile). Ici on ne fait qu'orienter.
     */
    private void finishVisit(Player p) {
        p.sendTitle("§6§l✦ Le bout de la route", "§7Va trouver le dernier témoin", 10, 80, 30);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.9f);
        p.sendMessage("§6✦ §7Ton Log Pose s'est tu : il t'a mené aussi loin qu'il le pouvait.");
        p.sendMessage("§6✦ §7Approche celui qui t'attend, et §fécoute son histoire§7.");
    }

    /**
     * Clôture réelle de la visite, déclenchée par le PNJ conteur à la fin de son récit : remet la
     * récompense (clé de crate) et retire la boussole du Log Pose de l'inventaire. Idempotent-ish :
     * si le joueur n'a plus de boussole, on ne redonne pas la clé (évite le double-cadeau).
     */
    public void completeVisit(Player p) {
        // Sécurité : ne rien donner si la visite n'est pas au dernier lieu, ou si la boussole a déjà été reprise.
        int idx = progress.getOrDefault(p.getUniqueId(), 0);
        if (idx < SPOTS.size()) return;      // visite pas terminée
        if (!hasLogPose(p)) return;          // boussole déjà reprise → récompense déjà donnée

        // Retire la boussole (elle ne sert plus).
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLogPose(inv.getItem(i))) inv.setItem(i, null);
        }

        // Récompense : clé(s) de crate.
        CrateManager crates = plugin.getCrates();
        if (crates != null) {
            crates.giveKey(p.getUniqueId(), REWARD_KEY_RANK, REWARD_KEY_AMOUNT);
            CrateManager.CrateRank r = CrateManager.rankOf(REWARD_KEY_RANK);
            String rankName = (r != null) ? r.display : "§9Coffre Rare";
            p.sendMessage("§6✦ §7Pour ta traversée, on t'offre §f" + REWARD_KEY_AMOUNT + "× "
                    + rankName + " §7(clé de crate). §8Retrouve-la dans §f/keys§8.");
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        // Visite entièrement clôturée : on retire l'objectif de la BossBar (retour aux astuces).
        GuideManager guide = plugin.getGuide();
        if (guide != null) guide.clearQuestObjective(p);
    }

    /** Vrai si le joueur a atteint le dernier lieu (visite terminée, en attente du Dernier Témoin). */
    public boolean isVisitDone(Player p) {
        return progress.getOrDefault(p.getUniqueId(), 0) >= SPOTS.size();
    }

    /** Vrai si le joueur a une visite EN COURS (commencée mais pas finie). */
    public boolean isVisitInProgress(Player p) {
        int idx = progress.getOrDefault(p.getUniqueId(), 0);
        return idx > 0 && idx < SPOTS.size();
    }

    /**
     * Vrai si la visite est ENTIÈREMENT clôturée : les 3 lieux sont vus ET le Dernier Témoin a repris
     * la boussole (completeVisit). Tant que le joueur tient encore le Log Pose (dernier lieu atteint mais
     * pas encore parlé au Témoin), renvoie false → l'objectif « Parle au Dernier Témoin » reste affiché.
     */
    public boolean isVisitFullyDone(Player p) {
        return isVisitDone(p) && !hasLogPose(p);
    }

    // ===== Utilitaires admin =====

    /** Réinitialise la visite d'un joueur (pour tester). */
    public void reset(Player p) {
        progress.put(p.getUniqueId(), 0);
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLogPose(inv.getItem(i))) inv.setItem(i, null);
        }
        save();
        p.sendMessage("§7Ta visite d'Alabasta a été réinitialisée.");
    }
}
