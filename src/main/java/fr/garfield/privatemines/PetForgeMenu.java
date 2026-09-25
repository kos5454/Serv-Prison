package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Forge des Familiers — atelier de FUSION manuel.
 *
 * <p>Le joueur dépose 3 familiers dans les emplacements 29, 31, 33 puis clique l'enclume (slot 4).
 * Règle : les 3 doivent être la <b>même espèce ET le même niveau</b> → ils fusionnent en
 * <b>1 familier de niveau +1</b> (bonus boostés, cf. {@link PetEquipMenu#aggregate}).
 * Le niveau maximum dépend de la rareté ({@link PetMenu#levelCapForRank}).
 *
 * <p>Si le joueur ferme le menu sans fusionner, les pets déposés lui sont rendus (inventaire, sinon sol).
 */
public class PetForgeMenu implements Listener {

    public static final String TITLE = "§8§l⚒ Forge des Familiers";

    private static final int ANVIL_SLOT = 4;                 // enclume = bouton « Fusionner »
    private static final int[] INPUT_SLOTS = {29, 31, 33};   // 3 emplacements de dépôt (gauche/centre/droite)
    // Vitres du CHEMIN (grises au repos, vertes quand le dépôt est rempli) — positions de la maquette.
    private static final int[] GLASS_SLOTS = {2, 3, 5, 6, 11, 13, 15, 20, 22, 24};
    // Chemin de vitres qui relie chaque dépôt à l'enclume : passe au VERT quand un pet y est déposé.
    // Indices alignés sur INPUT_SLOTS : {chemin de 29}, {chemin de 31}, {chemin de 33}.
    private static final int[][] PATHS = {
            {20, 11, 2, 3},   // dépôt 29 (gauche)
            {22, 13},         // dépôt 31 (centre)
            {24, 15, 6, 5},   // dépôt 33 (droite)
    };

    private final PrivateMines plugin;
    // Fusion en cours : on ignore l'InventoryCloseEvent déclenché par le remplacement du menu.
    private final java.util.Set<UUID> fusing = new java.util.HashSet<>();

    public PetForgeMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, TITLE);
        // Fond : tous les slots en vitre bleu clair (déco/lisibilité)…
        ItemStack fond = plugin.pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) menu.setItem(i, fond);
        // …les 3 emplacements de dépôt restent vides (cliquables).
        for (int s : INPUT_SLOTS) menu.setItem(s, null);
        // Vitres grises du chemin (posées par-dessus le fond bleu).
        for (int s : GLASS_SLOTS) menu.setItem(s, plugin.pane(Material.GRAY_STAINED_GLASS_PANE));
        menu.setItem(ANVIL_SLOT, anvilButton());
        // Bas : retour + fermer.
        menu.setItem(48, plugin.namedItem(Material.ARROW, "§e◀ Retour", "§7Revenir au menu des familiers"));
        menu.setItem(50, plugin.namedItem(Material.BARRIER, "§cFermer", "§8Tes familiers déposés te seront rendus"));
        refreshHints(menu); // colore les chemins selon les dépôts (tout gris au départ)
        p.openInventory(menu);
    }

    private ItemStack anvilButton() {
        ItemStack it = new ItemStack(Material.ANVIL);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§e§l⚒ Fusionner");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Dépose §e3 familiers identiques§7 dans");
        lore.add("§7les 3 emplacements du bas (même espèce");
        lore.add("§7ET même niveau), puis clique ici.");
        lore.add("");
        lore.add("§7Résultat : §a1 familier de niveau +1§7.");
        lore.add("§8Niveau max selon la rareté du familier.");
        lore.add("");
        lore.add("§e▶ Clique pour fusionner");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private boolean isInputSlot(int raw) {
        for (int s : INPUT_SLOTS) if (s == raw) return true;
        return false;
    }

    // Colore les chemins de vitres : le chemin d'un dépôt occupé passe au VERT, les autres restent GRIS.
    // On repart d'un fond gris pour toutes les vitres, puis on verdit les chemins des dépôts remplis.
    private void refreshHints(Inventory top) {
        // 1) tout gris.
        for (int s : GLASS_SLOTS) top.setItem(s, plugin.pane(Material.GRAY_STAINED_GLASS_PANE));
        // 2) verdir le chemin de chaque dépôt occupé.
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack in = top.getItem(INPUT_SLOTS[i]);
            boolean occupied = in != null && in.getType() != Material.AIR;
            if (!occupied) continue;
            for (int s : PATHS[i]) {
                top.setItem(s, plugin.namedItem(Material.LIME_STAINED_GLASS_PANE,
                        "§a§l✔ Familier connecté", "§7Prêt pour la fusion"));
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // ── Clic dans l'inventaire PERSO du joueur (raw >= taille du menu) ─────────
        if (raw >= topSize) {
            // Seuls les familiers peuvent être déposés ; on les envoie dans le 1er slot libre.
            ItemStack clicked = event.getCurrentItem();
            if (plugin.getPetMenu().isPet(clicked)) {
                event.setCancelled(true);
                // Refus si un pet d'une AUTRE espèce (ou autre niveau) est déjà déposé :
                // on ne mélange pas des familiers non fusionnables.
                if (!matchesExisting(event.getView().getTopInventory(), clicked)) {
                    p.sendMessage("§cTu ne peux déposer que des familiers §eidentiques §c(même espèce ET même niveau) que le premier déposé.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                int free = firstFreeInput(event.getView().getTopInventory());
                if (free < 0) {
                    p.sendMessage("§cLes 3 emplacements de fusion sont déjà occupés.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                event.getView().getTopInventory().setItem(free, clicked.clone());
                event.setCurrentItem(null);
                refreshHints(event.getView().getTopInventory());
                p.updateInventory();
                p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.3f);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        // ── Clic DANS le menu (haut) ──────────────────────────────────────────────
        // Les 3 emplacements de dépôt : clic = on rend le pet au joueur (retire du slot).
        if (isInputSlot(raw)) {
            event.setCancelled(true);
            ItemStack cur = event.getView().getTopInventory().getItem(raw);
            if (cur != null && cur.getType() != Material.AIR) {
                giveBack(p, cur);
                event.getView().getTopInventory().setItem(raw, null);
                refreshHints(event.getView().getTopInventory());
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            }
            return;
        }

        // Tout le reste du menu est non cliquable.
        event.setCancelled(true);
        if (raw == 50) { p.closeInventory(); return; }               // Fermer (rend les pets via onClose)
        if (raw == 48) { returnInputs(p, event.getView().getTopInventory());
                         plugin.getPetMenu().openMain(p); return; }   // Retour
        if (raw == ANVIL_SLOT) { tryFuse(p, event.getView().getTopInventory()); return; }
    }

    // Empêche les glisser-déposer (dépôt uniquement par clic sur un pet).
    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) { event.setCancelled(true); return; }
        }
    }

    // Vrai si `candidate` peut rejoindre les dépôts : soit aucun pet n'est encore déposé,
    // soit il a la MÊME espèce ET le MÊME niveau que ceux déjà présents (sinon fusion impossible).
    private boolean matchesExisting(Inventory top, ItemStack candidate) {
        String candId = plugin.getPetMenu().petIdOf(candidate);
        int candLvl = plugin.getPetMenu().petLevelOf(candidate);
        for (int s : INPUT_SLOTS) {
            ItemStack in = top.getItem(s);
            if (in == null || in.getType() == Material.AIR) continue;
            String inId = plugin.getPetMenu().petIdOf(in);
            int inLvl = plugin.getPetMenu().petLevelOf(in);
            // Un pet déjà présent : le candidat doit lui correspondre.
            if (candId == null || !candId.equals(inId) || candLvl != inLvl) return false;
        }
        return true;
    }

    private int firstFreeInput(Inventory top) {
        for (int s : INPUT_SLOTS) {
            ItemStack it = top.getItem(s);
            if (it == null || it.getType() == Material.AIR) return s;
        }
        return -1;
    }

    // Tente la fusion des 3 pets déposés.
    private void tryFuse(Player p, Inventory top) {
        ItemStack a = top.getItem(INPUT_SLOTS[0]);
        ItemStack b = top.getItem(INPUT_SLOTS[1]);
        ItemStack c = top.getItem(INPUT_SLOTS[2]);
        if (isEmpty(a) || isEmpty(b) || isEmpty(c)) {
            p.sendMessage("§cDépose §e3 familiers §cdans les 3 emplacements avant de fusionner.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String idA = plugin.getPetMenu().petIdOf(a);
        String idB = plugin.getPetMenu().petIdOf(b);
        String idC = plugin.getPetMenu().petIdOf(c);
        int lvA = plugin.getPetMenu().petLevelOf(a);
        int lvB = plugin.getPetMenu().petLevelOf(b);
        int lvC = plugin.getPetMenu().petLevelOf(c);

        // Même espèce ?
        if (idA == null || !idA.equals(idB) || !idA.equals(idC)) {
            p.sendMessage("§cLes 3 familiers doivent être de la §emême espèce§c.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Même niveau ?
        if (lvA != lvB || lvA != lvC) {
            p.sendMessage("§cLes 3 familiers doivent être du §emême niveau§c (ici " + lvA + "/" + lvB + "/" + lvC + ").");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        // Cap de rareté ?
        int rank = plugin.getPetMenu().petRankOf(a);
        int cap = plugin.getPetMenu().levelCapForRank(rank);
        if (lvA >= cap) {
            p.sendMessage("§eCe familier est déjà au niveau maximum pour sa rareté (§6" + cap + "§e).");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Fusion ! On vide les 3 slots et on donne 1 pet de niveau +1.
        top.setItem(INPUT_SLOTS[0], null);
        top.setItem(INPUT_SLOTS[1], null);
        top.setItem(INPUT_SLOTS[2], null);

        ItemStack base = plugin.getPetMenu().templatePet(idA);
        if (base == null) base = a.clone(); // fallback : repart d'un des items déposés
        int newLevel = lvA + 1;
        ItemStack result = plugin.getPetMenu().withLevel(base, newLevel);
        // Le résultat va directement dans l'INVENTAIRE du joueur (scellé, non empilable).
        giveBack(p, result);

        PetMenu.PetDef def = PetMenu.defById(idA);
        String pname = (def != null) ? def.name : "§7Familier";
        p.sendMessage("§a§l⚒ §7Fusion réussie ! " + pname + " §7est passé au §a⭐" + newLevel
                + " §7et est arrivé dans ton §einventaire§7 !");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);

        // Rouvre l'atelier vide (on marque « fusing » pour que onClose ne rende rien : les slots sont déjà vides).
        fusing.add(p.getUniqueId());
        open(p);
        fusing.remove(p.getUniqueId());
    }

    // Rend au joueur tous les pets présents dans les 3 emplacements de dépôt (puis les vide).
    private void returnInputs(Player p, Inventory top) {
        for (int s : INPUT_SLOTS) {
            ItemStack it = top.getItem(s);
            if (it != null && it.getType() != Material.AIR) {
                giveBack(p, it);
                top.setItem(s, null);
            }
        }
    }

    // Rend un pet au joueur : inventaire si possible, sinon au sol (scellé : ne s'empile pas).
    private void giveBack(Player p, ItemStack pet) {
        java.util.Map<Integer, ItemStack> overflow = p.getInventory().addItem(plugin.getPetMenu().seal(pet));
        for (ItemStack left : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.updateInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        if (fusing.contains(p.getUniqueId())) return; // fermeture provoquée par la ré-ouverture post-fusion
        returnInputs(p, event.getView().getTopInventory());
    }

    private boolean isEmpty(ItemStack it) {
        return it == null || it.getType() == Material.AIR;
    }
}
