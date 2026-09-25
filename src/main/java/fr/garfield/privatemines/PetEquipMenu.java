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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Équipement des familiers : jusqu'à 3 emplacements par joueur.
 * Pour l'instant SEUL le 1er emplacement est déblocable ; les 2 autres sont grisés (verrouillés).
 *
 * <p>Poser un pet dans un emplacement applique réellement ses bonus (via {@link #aggregate}) ;
 * le retirer les enlève. Les pets équipés sont persistés dans playerdata.yml sous « <uuid>.equip.<i> ».
 *
 * <p>Niveaux : chaque pet porte un tag NBT {@code pet_level} (défaut 1). Les bonus effectifs
 * montent avec le niveau via {@link #levelMult}. Le gain d'XP en minant sera branché plus tard ;
 * ici la structure est prête (niveau lu depuis le tag, défaut 1).
 */
public class PetEquipMenu implements Listener {

    public static final String TITLE = "§8§lÉquipement des Familiers";
    public static final int SLOTS = 3;
    // Nombre d'emplacements débloqués de DÉPART (le 1er). Les suivants s'achètent.
    private static final int BASE_UNLOCKED_SLOTS = 1;
    // Prix de déblocage du 2e emplacement (le 3e reste verrouillé « plus tard »).
    private static final double SLOT2_PRICE = 150_000;

    // Emplacements dans le GUI (centrés).
    private static final int[] GUI_SLOTS = {20, 22, 24};

    private final PrivateMines plugin;
    private final org.bukkit.NamespacedKey levelKey;
    // Pets équipés par joueur (taille SLOTS, null = vide).
    private final Map<UUID, ItemStack[]> equipped = new HashMap<>();
    // Nombre d'emplacements débloqués PAR JOUEUR (défaut 1). Persisté dans playerdata.
    private final Map<UUID, Integer> unlockedSlots = new HashMap<>();

    // Nombre d'emplacements débloqués pour ce joueur (au moins 1).
    private int unlockedFor(UUID id) {
        return Math.max(BASE_UNLOCKED_SLOTS, unlockedSlots.getOrDefault(id, BASE_UNLOCKED_SLOTS));
    }

    public PetEquipMenu(PrivateMines plugin) {
        this.plugin = plugin;
        this.levelKey = new org.bukkit.NamespacedKey(plugin, "pet_level");
    }

    public org.bukkit.NamespacedKey getLevelKey() { return levelKey; }

    private ItemStack[] slots(UUID id) {
        return equipped.computeIfAbsent(id, k -> new ItemStack[SLOTS]);
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    public void load(UUID id, org.bukkit.configuration.file.FileConfiguration data) {
        ItemStack[] arr = new ItemStack[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            arr[i] = data.getItemStack(id + ".equip." + i);
        }
        equipped.put(id, arr);
        unlockedSlots.put(id, data.getInt(id + ".petSlots", BASE_UNLOCKED_SLOTS));
    }

    public void save(Player p) {
        org.bukkit.configuration.file.FileConfiguration data = plugin.getDataConfig();
        ItemStack[] arr = slots(p.getUniqueId());
        for (int i = 0; i < SLOTS; i++) {
            data.set(p.getUniqueId() + ".equip." + i,
                    (arr[i] != null && arr[i].getType() != Material.AIR) ? arr[i] : null);
        }
        data.set(p.getUniqueId() + ".petSlots", unlockedFor(p.getUniqueId()));
        plugin.saveDataConfig("l'équipement pets de " + p.getName());
    }

    // Pose la clé equip.* dans dataConfig sans écrire le disque (appelé par savePlayer).
    public void writeInto(org.bukkit.configuration.file.FileConfiguration data, UUID id) {
        ItemStack[] arr = slots(id);
        for (int i = 0; i < SLOTS; i++) {
            data.set(id + ".equip." + i,
                    (arr[i] != null && arr[i].getType() != Material.AIR) ? arr[i] : null);
        }
        data.set(id + ".petSlots", unlockedFor(id));
    }

    // ── Menu ─────────────────────────────────────────────────────────────────────

    public void open(Player p) {
        Inventory menu = Bukkit.createInventory(null, 54, TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        ItemStack[] arr = slots(p.getUniqueId());
        int unlocked = unlockedFor(p.getUniqueId());
        for (int i = 0; i < SLOTS; i++) {
            if (i < unlocked) {
                if (arr[i] != null && arr[i].getType() != Material.AIR) {
                    // Pet équipé : on l'affiche avec un rappel « clique pour retirer ».
                    menu.setItem(GUI_SLOTS[i], withHint(arr[i].clone(),
                            "§e▶ Clique pour retirer ce familier"));
                } else {
                    menu.setItem(GUI_SLOTS[i], plugin.namedItem(Material.LIME_STAINED_GLASS_PANE,
                            "§a§lEmplacement libre",
                            "§7Clique un familier ici pour l'équiper"));
                }
            } else if (i == 1) {
                // 2e emplacement : déblocable contre 150K en cliquant dessus.
                menu.setItem(GUI_SLOTS[i], plugin.namedItem(Material.YELLOW_STAINED_GLASS_PANE,
                        "§6§l🔓 Débloquer cet emplacement",
                        "§7Prix : §6" + PrivateMines.formatNumber(SLOT2_PRICE) + "$",
                        "",
                        "§e▶ Clique pour débloquer"));
            } else {
                menu.setItem(GUI_SLOTS[i], plugin.namedItem(Material.RED_STAINED_GLASS_PANE,
                        "§c§l🔒 Emplacement verrouillé",
                        "§8Se débloquera plus tard"));
            }
        }

        // Récap des bonus actifs.
        menu.setItem(49, bonusRecap(p));
        // Bouton pour aller choisir un familier à équiper.
        menu.setItem(48, plugin.namedItem(Material.CHEST, "§6§l📦 Mes Familiers",
                "§7Ouvre ta collection pour équiper un familier"));
        // Bouton RETOUR vers le menu principal /pets (au lieu de tout fermer).
        menu.setItem(46, plugin.namedItem(Material.ARROW, "§e◀ Retour",
                "§7Revenir au menu des familiers"));
        menu.setItem(50, plugin.namedItem(Material.BARRIER, "§cFermer", null));

        p.openInventory(menu);
    }

    private ItemStack withHint(ItemStack it, String hint) {
        ItemMeta m = it.getItemMeta();
        List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(hint);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack bonusRecap(Player p) {
        Bonus b = aggregate(p);
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§b§l✦ Bonus actifs");
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (b.isEmpty()) {
            lore.add("§8Aucun familier équipé.");
        } else {
            if (b.blockValuePct != 0) lore.add("§7Valeur des blocs §a+" + fmt(b.blockValuePct) + "%");
            if (b.capacityPct != 0)   lore.add("§7Capacité du sac §a+" + fmt(b.capacityPct) + "%");
            if (b.sellMoneyPct != 0)  lore.add("§7Argent à la vente §a+" + fmt(b.sellMoneyPct) + "%");
            if (b.doubleBlockPct != 0) lore.add("§7Chance de double bloc §a" + fmt(b.doubleBlockPct) + "%");
            if (b.miningSpeedPct != 0) lore.add("§7Vitesse de minage §a+" + fmt(b.miningSpeedPct) + "%");
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player p = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 50) { p.closeInventory(); return; }
        if (slot == 46) { plugin.getPetMenu().openMain(p); return; } // retour menu principal
        if (slot == 48) { plugin.getPetStorage().open(p); return; }

        // Clic sur un item de l'INVENTAIRE DU JOUEUR (slots >= taille du menu) : si c'est un pet,
        // on l'équipe dans le 1er emplacement libre et on le retire de l'inventaire.
        if (slot >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (!isPet(clicked)) {
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            if (freeUnlockedSlots(p) <= 0) {
                p.sendMessage("§cAucun emplacement de familier libre. Retire d'abord un familier équipé.");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            // On équipe une copie, puis on retire l'item cliqué de l'inventaire.
            equip(p, clicked.clone());
            event.getClickedInventory().setItem(event.getSlot(), null);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.3f);
            open(p);
            return;
        }

        int unlocked = unlockedFor(p.getUniqueId());

        // Clic sur le 2e emplacement encore VERROUILLÉ → proposer de le débloquer contre 150K.
        if (slot == GUI_SLOTS[1] && unlocked < 2) {
            tryUnlockSlot2(p);
            return;
        }

        // Clic sur un emplacement d'équipement débloqué qui contient un pet → on le retire.
        for (int i = 0; i < unlocked; i++) {
            if (slot == GUI_SLOTS[i]) {
                ItemStack[] arr = slots(p.getUniqueId());
                if (arr[i] != null && arr[i].getType() != Material.AIR) {
                    unequip(p, i);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 0.8f, 1.2f);
                    open(p);
                }
                return;
            }
        }
    }

    // Débloque le 2e emplacement de familier contre SLOT2_PRICE (150K) si le joueur a les fonds.
    private void tryUnlockSlot2(Player p) {
        if (unlockedFor(p.getUniqueId()) >= 2) return; // déjà débloqué
        net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
        if (economy == null) return;
        if (economy.getBalance(p) < SLOT2_PRICE) {
            p.sendMessage("§cPas assez d'argent ! Il te faut §6" + PrivateMines.formatNumber(SLOT2_PRICE) + "$ §cpour débloquer ce 2e emplacement.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economy.withdrawPlayer(p, SLOT2_PRICE);
        unlockedSlots.put(p.getUniqueId(), 2);
        save(p);
        p.sendMessage("§a✔ 2e emplacement de familier débloqué pour §6" + PrivateMines.formatNumber(SLOT2_PRICE) + "$ §a!");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        open(p);
    }

    // Un ItemStack est un familier s'il porte le tag NBT pet_id (posé par PetMenu).
    private boolean isPet(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        String id = it.getItemMeta().getPersistentDataContainer()
                .get(plugin.getPetMenu().getPetIdKey(), PersistentDataType.STRING);
        return id != null && PetMenu.defById(id) != null;
    }

    // ── Équiper / retirer ──────────────────────────────────────────────────────

    // Équipe le pet donné (ItemStack tagué pet_id) dans le 1er emplacement libre débloqué.
    // Renvoie true si équipé, false si aucun emplacement libre.
    public boolean equip(Player p, ItemStack pet) {
        ItemStack[] arr = slots(p.getUniqueId());
        for (int i = 0; i < unlockedFor(p.getUniqueId()); i++) {
            if (arr[i] == null || arr[i].getType() == Material.AIR) {
                arr[i] = pet.clone();
                save(p);
                return true;
            }
        }
        return false;
    }

    // Retire le pet de l'emplacement i et le remet dans la collection.
    public void unequip(Player p, int i) {
        ItemStack[] arr = slots(p.getUniqueId());
        if (arr[i] == null || arr[i].getType() == Material.AIR) return;
        ItemStack pet = arr[i];
        arr[i] = null;
        save(p);
        plugin.getPetStorage().addPet(p, pet);
    }

    // Nombre d'emplacements libres et débloqués.
    public int freeUnlockedSlots(Player p) {
        ItemStack[] arr = slots(p.getUniqueId());
        int free = 0;
        for (int i = 0; i < unlockedFor(p.getUniqueId()); i++) {
            if (arr[i] == null || arr[i].getType() == Material.AIR) free++;
        }
        return free;
    }

    // ── Calcul des bonus effectifs ───────────────────────────────────────────────

    // Multiplicateur de bonus selon le niveau du pet (niv.1 = ×1 ; +5% du bonus de base par niveau).
    private double levelMult(ItemStack pet) {
        int lvl = 1;
        if (pet.hasItemMeta()) {
            Integer v = pet.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
            if (v != null) lvl = Math.max(1, v);
        }
        return levelMultForLevel(lvl);
    }

    // Multiplicateur de bonus pour un niveau donné (source unique de vérité).
    // +40% du bonus de base par niveau au-dessus de 1 (niv.2 = ×1,4 ; niv.5 = ×2,6).
    public double levelMultForLevel(int level) {
        return 1.0 + (Math.max(1, level) - 1) * 0.40;
    }

    // Agrège les bonus de tous les pets équipés (avec leur niveau).
    public Bonus aggregate(Player p) {
        Bonus b = new Bonus();
        ItemStack[] arr = slots(p.getUniqueId());
        for (int i = 0; i < unlockedFor(p.getUniqueId()); i++) {
            ItemStack pet = arr[i];
            if (pet == null || pet.getType() == Material.AIR || !pet.hasItemMeta()) continue;
            String id = pet.getItemMeta().getPersistentDataContainer()
                    .get(plugin.getPetMenu().getPetIdKey(), PersistentDataType.STRING);
            PetMenu.PetDef def = PetMenu.defById(id);
            if (def == null) continue;
            double mult = levelMult(pet);
            b.blockValuePct  += def.blockValuePct  * mult;
            b.capacityPct    += def.capacityPct    * mult;
            b.sellMoneyPct   += def.sellMoneyPct   * mult;
            b.doubleBlockPct += def.doubleBlockPct * mult;
            b.miningSpeedPct += def.miningSpeedPct * mult;
        }
        return b;
    }

    // Conteneur simple des bonus agrégés (en pourcentages).
    public static final class Bonus {
        public double blockValuePct, capacityPct, sellMoneyPct, doubleBlockPct, miningSpeedPct;
        public boolean isEmpty() {
            return blockValuePct == 0 && capacityPct == 0 && sellMoneyPct == 0
                    && doubleBlockPct == 0 && miningSpeedPct == 0;
        }
    }
}
