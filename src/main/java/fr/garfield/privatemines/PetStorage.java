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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage des familiers (pets) possédés par un joueur — indépendant du /bp.
 *
 * <p>Chaque joueur possède une liste de pets (têtes ItemStack taguées {@code pet_id}).
 * Persistée dans playerdata.yml sous « <uuid>.pets.<i> ».
 *
 * <p>Le menu « Mes familiers » (ouvert via le coffre en bas à droite des écrans /pets)
 * affiche la liste, paginée, avec possibilité de cliquer un pet pour le récupérer en main.
 */
public class PetStorage implements Listener {

    public static final String TITLE = "§8§lMes Familiers";
    private static final int PAGE_SIZE = 45; // 5 rangées de pets, dernière rangée = navigation

    private final PrivateMines plugin;
    private final Map<UUID, List<ItemStack>> pets = new HashMap<>();
    // Page actuellement ouverte par joueur (pour la navigation).
    private final Map<UUID, Integer> openPage = new HashMap<>();
    // Sens de tri par joueur : true = Mythique→Commun (défaut), false = Commun→Mythique.
    private final Map<UUID, Boolean> sortDesc = new HashMap<>();
    // Ordre d'arc par joueur : false = Arc I d'abord (défaut), true = Arc II d'abord.
    private final Map<UUID, Boolean> sortArc2First = new HashMap<>();
    private static final int SORT_SLOT = 45;     // tri par rareté (bas à gauche)
    private static final int ARC_SORT_SLOT = 46;  // tri par arc (juste à droite de la rareté)
    private static final int BACK_SLOT = 48;      // retour (à gauche du bouton fermer=49)
    private static final int PREV_SLOT = 50;      // page précédente
    private static final int NEXT_SLOT = 53;      // page suivante (coin bas droit)

    public PetStorage(PrivateMines plugin) {
        this.plugin = plugin;
    }

    private List<ItemStack> list(UUID id) {
        return pets.computeIfAbsent(id, k -> new ArrayList<>());
    }

    // Ajoute un pet à la collection du joueur.
    public void addPet(Player p, ItemStack pet) {
        list(p.getUniqueId()).add(pet.clone());
        save(p);
    }

    public int count(Player p) {
        return list(p.getUniqueId()).size();
    }

    // Liste brute des pets d'un joueur (pour la sauvegarde par PrivateMines.savePlayer).
    public List<ItemStack> getPetsRaw(UUID id) {
        return list(id);
    }

    // ── Support Forge (fusion) ───────────────────────────────────────────────────
    // Compte combien d'exemplaires d'une espèce (pet_id) à un niveau donné le joueur possède
    // dans sa collection (n'inclut PAS les pets équipés).
    public int countSpeciesAtLevel(UUID id, String petId, int level) {
        if (petId == null) return 0;
        org.bukkit.NamespacedKey idKey = plugin.getPetMenu().getPetIdKey();
        org.bukkit.NamespacedKey lvlKey = plugin.getPetEquip().getLevelKey();
        int n = 0;
        for (ItemStack it : list(id)) {
            if (it == null || !it.hasItemMeta()) continue;
            org.bukkit.persistence.PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
            String s = pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (!petId.equals(s)) continue;
            Integer lv = pdc.get(lvlKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            int l = (lv == null) ? 1 : Math.max(1, lv);
            if (l == level) n++;
        }
        return n;
    }

    // Retire jusqu'à `count` exemplaires d'une espèce à un niveau donné de la collection.
    // Renvoie le nombre réellement retiré. Ne sauvegarde PAS (l'appelant sauvegarde une fois).
    public int consumeSpeciesAtLevel(UUID id, String petId, int level, int count) {
        if (petId == null || count <= 0) return 0;
        org.bukkit.NamespacedKey idKey = plugin.getPetMenu().getPetIdKey();
        org.bukkit.NamespacedKey lvlKey = plugin.getPetEquip().getLevelKey();
        List<ItemStack> l = list(id);
        int removed = 0;
        java.util.Iterator<ItemStack> it = l.iterator();
        while (it.hasNext() && removed < count) {
            ItemStack pet = it.next();
            if (pet == null || !pet.hasItemMeta()) continue;
            org.bukkit.persistence.PersistentDataContainer pdc = pet.getItemMeta().getPersistentDataContainer();
            String s = pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (!petId.equals(s)) continue;
            Integer lv = pdc.get(lvlKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            int lev = (lv == null) ? 1 : Math.max(1, lv);
            if (lev != level) continue;
            it.remove();
            removed++;
        }
        return removed;
    }

    // Sauvegarde publique (utilisée par la Forge après une fusion).
    public void saveNow(Player p) { save(p); }

    // Trie la liste en place : D'ABORD par arc (selon arc2First), PUIS par rareté (selon desc).
    // Les deux critères se combinent — ex. Arc II d'abord, chaque arc trié Mythique→Commun.
    // Les items sans rang (anciens/inconnus) finissent toujours en bas de leur groupe.
    private void sortPets(List<ItemStack> l, boolean desc, boolean arc2First) {
        org.bukkit.NamespacedKey rankKey = plugin.getPetMenu().getPetRankKey();
        l.sort((a, b) -> {
            // 1) Critère ARC (primaire).
            int aa = arcOf(a), ab = arcOf(b);
            if (aa != ab) {
                // arc2First : l'arc le PLUS HAUT passe en premier ; sinon le plus bas d'abord.
                return arc2First ? Integer.compare(ab, aa) : Integer.compare(aa, ab);
            }
            // 2) Critère RARETÉ (secondaire), au sein d'un même arc.
            int ra = rankOf(a, rankKey), rb = rankOf(b, rankKey);
            if (ra < 0 && rb >= 0) return 1;
            if (rb < 0 && ra >= 0) return -1;
            return desc ? Integer.compare(rb, ra) : Integer.compare(ra, rb);
        });
    }

    // Arc d'un pet stocké (1 ou 2), déduit de son pet_id ; 99 si inconnu (relégué en fin).
    private int arcOf(ItemStack it) {
        String petId = plugin.getPetMenu().petIdOf(it);
        PetMenu.PetDef def = PetMenu.defById(petId);
        if (def == null) return 99;
        return PetMenu.arcOf(def);
    }

    // Bouton de tri, affiche le sens courant et l'action au clic.
    private ItemStack sortButton(boolean desc) {
        ItemStack it = new ItemStack(Material.HOPPER);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.setDisplayName("§b§l⇅ Trier par rareté");
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (desc) {
            lore.add("§7Ordre actuel : §c🔴 Mythique §7→ §f⚪ Commun");
            lore.add("§e▶ Clique pour inverser (Commun → Mythique)");
        } else {
            lore.add("§7Ordre actuel : §f⚪ Commun §7→ §c🔴 Mythique");
            lore.add("§e▶ Clique pour inverser (Mythique → Commun)");
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Bouton de tri par ARC : affiche l'ordre courant et l'action au clic.
    private ItemStack arcSortButton(boolean arc2First) {
        ItemStack it = new ItemStack(Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.setDisplayName("§d§l⌖ Trier par arc");
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (arc2First) {
            lore.add("§7Ordre actuel : §bArc II §7d'abord");
            lore.add("§e▶ Clique pour mettre §aArc I §een premier");
        } else {
            lore.add("§7Ordre actuel : §aArc I §7d'abord");
            lore.add("§e▶ Clique pour mettre §bArc II §een premier");
        }
        lore.add("");
        lore.add("§8Se combine avec le tri par rareté.");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Copie d'affichage d'un pet avec le rappel des contrôles (ne modifie pas l'item stocké).
    private ItemStack withControls(ItemStack src) {
        ItemStack it = src.clone();
        if (!it.hasItemMeta()) return it;
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§e▶ Clic droit §7: équiper");
        lore.add("§e▶ Clic gauche §7: récupérer en main");
        lore.add("§c▶ Shift + clic droit §7: supprimer définitivement");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Nom d'affichage d'un familier (pour les messages), ou son type si pas de nom.
    private String petNom(ItemStack pet) {
        if (pet != null && pet.hasItemMeta() && pet.getItemMeta().hasDisplayName()) {
            return pet.getItemMeta().getDisplayName();
        }
        return "§7familier";
    }

    private int rankOf(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) return -1;
        Integer r = it.getItemMeta().getPersistentDataContainer()
                .get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
        return r == null ? -1 : r;
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    public void load(UUID id, org.bukkit.configuration.file.FileConfiguration data) {
        List<ItemStack> l = new ArrayList<>();
        if (data.contains(id + ".pets")) {
            List<?> raw = data.getList(id + ".pets");
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof ItemStack) l.add((ItemStack) o);
                }
            }
        }
        pets.put(id, l);
    }

    public void save(Player p) {
        org.bukkit.configuration.file.FileConfiguration data = plugin.getDataConfig();
        data.set(p.getUniqueId() + ".pets", new ArrayList<>(list(p.getUniqueId())));
        plugin.saveDataConfig("les familiers de " + p.getName());
    }

    // ── Menu « Mes familiers » ───────────────────────────────────────────────────

    public void open(Player p) {
        open(p, 0);
    }

    public void open(Player p, int page) {
        List<ItemStack> l = list(p.getUniqueId());
        boolean desc = sortDesc.getOrDefault(p.getUniqueId(), true);
        boolean arc2First = sortArc2First.getOrDefault(p.getUniqueId(), false);
        sortPets(l, desc, arc2First); // tri combiné arc + rareté (affichage ET index de clic cohérents)
        int maxPage = Math.max(0, (l.size() - 1) / PAGE_SIZE);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;
        openPage.put(p.getUniqueId(), page);

        Inventory menu = Bukkit.createInventory(null, 54, TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 45; i < 54; i++) menu.setItem(i, bg);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= l.size()) break;
            menu.setItem(i, withControls(l.get(idx)));
        }

        if (l.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_DYE,
                    "§7Aucun familier pour l'instant",
                    "§8Ouvre des crates dans /pets pour en obtenir"));
        }

        // Navigation des pages (coins bas droit).
        if (page > 0) {
            menu.setItem(PREV_SLOT, plugin.namedItem(Material.ARROW, "§e◀ Page précédente", "§8Page " + page));
        }
        if (page < maxPage) {
            menu.setItem(NEXT_SLOT, plugin.namedItem(Material.ARROW, "§ePage suivante ▶", "§8Page " + (page + 2)));
        }
        // Retour (à gauche) puis Fermer (au centre) : retour = 48, fermer = 49.
        menu.setItem(BACK_SLOT, plugin.namedItem(Material.ARROW, "§e◀ Retour",
                "§7Revenir au menu des familiers"));
        menu.setItem(49, plugin.namedItem(Material.BARRIER, "§cFermer",
                "§8" + l.size() + " familier(s) possédé(s)"));

        // Tri par rareté (45) puis tri par arc juste à droite (46).
        menu.setItem(SORT_SLOT, sortButton(desc));
        menu.setItem(ARC_SORT_SLOT, arcSortButton(arc2First));

        p.openInventory(menu);
    }

    // Glisser-déposer dans le menu : on bloque toujours (le dépôt se fait par clic, pets uniquement).
    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        int topSize = event.getView().getTopInventory().getSize();
        // Si au moins un slot visé est dans le menu (haut), on annule pour éviter d'y déposer n'importe quoi.
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) { event.setCancelled(true); return; }
        }
    }

    // Priorité HIGHEST : ce handler a le dernier mot (les protections pioche/sac de PrivateMines
    // s'exécutent avant ; ici on gère nous-mêmes le coffre des familiers sans clignotement).
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        int page = openPage.getOrDefault(p.getUniqueId(), 0);
        // Taille du menu (haut de l'inventaire) : au-delà, c'est l'inventaire PERSO du joueur.
        int topSize = event.getView().getTopInventory().getSize();

        // ── Clic dans l'inventaire PERSO du joueur (rawSlot >= taille du menu) ─────────
        // On n'accepte QUE les familiers : cliquer/shift-cliquer un pet -> déposé dans la collection.
        // Tout autre item est refusé (le clic est annulé, rien ne bouge).
        if (slot >= topSize) {
            ItemStack clicked = event.getCurrentItem();
            if (plugin.getPetMenu().isPet(clicked)) {
                // Dépôt du familier dans la collection (clic gauche/droit/shift, peu importe).
                event.setCancelled(true);
                addPet(p, clicked);
                event.setCurrentItem(null); // retire l'item de l'inventaire du joueur
                p.updateInventory();        // resync immédiate -> pas de clignotement
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.2f);
                p.sendMessage("§a§l✦ §7Familier rangé dans §6Mes Familiers§7.");
                open(p, page);
            } else {
                // Item non-pet : on bloque (seuls les pets entrent dans le coffre).
                event.setCancelled(true);
            }
            return;
        }

        // ── À partir d'ici : clic DANS le menu (slots du haut) ────────────────────────
        event.setCancelled(true);

        // Boutons de navigation.
        if (slot == 49) { p.closeInventory(); return; }
        if (slot == BACK_SLOT) { plugin.getPetMenu().openMain(p); return; } // retour menu principal
        if (slot == PREV_SLOT) { open(p, page - 1); return; }
        if (slot == NEXT_SLOT) { open(p, page + 1); return; }
        // Bouton de tri par rareté : bascule le sens et rouvre à la page 0.
        if (slot == SORT_SLOT) {
            boolean cur = sortDesc.getOrDefault(p.getUniqueId(), true);
            sortDesc.put(p.getUniqueId(), !cur);
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            open(p, 0);
            return;
        }
        // Bouton de tri par arc : bascule Arc I / Arc II d'abord (se combine avec la rareté).
        if (slot == ARC_SORT_SLOT) {
            boolean cur = sortArc2First.getOrDefault(p.getUniqueId(), false);
            sortArc2First.put(p.getUniqueId(), !cur);
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
            open(p, 0);
            return;
        }

        // Clic sur un pet (slots 0-44).
        //  • Clic DROIT   → équiper le familier (si un emplacement est libre).
        //  • Clic GAUCHE  → le récupérer en main.
        if (slot >= 0 && slot < PAGE_SIZE) {
            List<ItemStack> l = list(p.getUniqueId());
            int idx = page * PAGE_SIZE + slot;

            // Cas « redéposer » : le joueur tient un item sur le curseur et clique une case.
            // On n'accepte QUE les familiers ; ils rejoignent la collection.
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (plugin.getPetMenu().isPet(cursor)) {
                    addPet(p, cursor);
                    event.setCursor(null); // l'item quitte le curseur -> rangé
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.2f);
                    open(p, page);
                } else {
                    p.sendMessage("§cSeuls les familiers peuvent être rangés ici.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return;
            }

            if (idx >= l.size()) return;
            ItemStack pet = l.get(idx);
            if (pet == null || pet.getType() == Material.AIR) return;

            // On teste le ClickType EXACT (plus fiable que isRightClick/isShiftClick avec shift).
            org.bukkit.event.inventory.ClickType clic = event.getClick();

            // Shift + clic DROIT → suppression DÉFINITIVE du familier (pas de confirmation).
            if (clic == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                l.remove(idx);
                save(p);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.9f, 0.8f);
                p.sendMessage("§c🗑 §7Familier §f" + petNom(pet) + " §csupprimé définitivement.");
                open(p, page);
                return;
            }

            // Clic DROIT (simple) → équiper. Tout le reste (gauche, shift+gauche) → récupérer en main.
            if (clic == org.bukkit.event.inventory.ClickType.RIGHT) {
                // Équiper (clic droit).
                if (plugin.getPetEquip().freeUnlockedSlots(p) <= 0) {
                    p.sendMessage("§cAucun emplacement d'équipement libre. §7(clic gauche = récupérer en main)");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                l.remove(idx);
                save(p);
                plugin.getPetEquip().equip(p, pet);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.3f);
                p.sendMessage("§a§l✦ §7Familier équipé : ses bonus sont maintenant actifs !");
                plugin.getTutorial().onPetEquipped(p); // parcours de découverte : étape « équipe un familier »
                plugin.getPetEquip().open(p); // on bascule sur le menu d'équipement pour voir le résultat
            } else {
                // Récupérer en main (clic gauche ; scellé : ne s'empile pas dans l'inventaire).
                l.remove(idx);
                save(p);
                Map<Integer, ItemStack> overflow = p.getInventory().addItem(plugin.getPetMenu().seal(pet));
                if (!overflow.isEmpty()) {
                    for (ItemStack left : overflow.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    }
                }
                p.updateInventory(); // resync -> pas de clignotement
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                open(p, page);
            }
        }
    }
}
