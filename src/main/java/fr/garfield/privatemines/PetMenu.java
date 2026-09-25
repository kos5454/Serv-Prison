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
import java.util.List;

/**
 * Menu /pets — familiers.
 *
 * <p>Écran 1 : sélecteur d'arcs (6 têtes, seul l'Arc 1 est cliquable pour l'instant).
 * <p>Écran 2 : sous-menu de l'Arc 1 = vitrine des 6 pets (1 par rareté) + zone d'achat de crates.
 *
 * <p>Achat de crate : débit de l'argent Vault (/money), tirage pondéré parmi les 6 pets de l'arc,
 * dépôt du/des pet(s) (têtes ItemStack) dans le backpack (/bp).
 * <ul>
 *   <li>1 crate (15 000) → animation « roulette » horizontale puis dépôt du gagnant.</li>
 *   <li>5 crates (70 000) / 10 crates (135 000) → pas d'animation, dépôt direct des tirages.</li>
 * </ul>
 *
 * Les têtes sont fournies par {@link PetHeadProvider} (HeadDB via réflexion, préchargées + cache).
 */
public class PetMenu implements Listener {

    private static final String MAIN_TITLE = "§8§lŒuf des Origines Bestiales";
    private static final String ARC1_TITLE = "§6§l✦ Arc I — L'Aube du Grand Appel ✦";
    private static final String ARC2_TITLE = "§3§l✦ Arc II — Le Chant des Profondeurs ✦";
    private static final String ROULETTE_TITLE = "§0§l⟳ Œuf des Origines — Éclosion…";

    // Mine qui débloque l'Arc II des familiers (code "O" = mine 15, Village des Rançons).
    private static final String ARC2_UNLOCK_MINE = "O";

    // ── Têtes HeadDB des 6 arcs (menu principal) ───────────────────────────────
    // (Noms en jeu = univers original « Abysses du Grand Appel ». Réf. One Piece en commentaire.)
    private static final int HDB_ARC1 = 16145; // L'Aube du Grand Appel   (réf. Romance Dawn)
    private static final int HDB_ARC2 = 2223;  // La Baie des Écumeurs     (réf. Ville d'Orange)
    private static final int HDB_ARC3 = 76028; // Le Hameau des Frondes    (réf. Village de Syrup)
    private static final int HDB_ARC4 = 1492;  // Le Radeau des Saveurs    (réf. Baratie)
    private static final int HDB_ARC5 = 22354; // Les Récifs du Joug       (réf. Arlong Park)
    private static final int HDB_ARC6 = 83885; // Le Port des Adieux       (réf. Loguetown)

    // ── Têtes HeadDB des 6 pets de l'Arc 1 ─────────────────────────────────────
    private static final int HDB_CRABE   = 42459; // Crabe des Vasières  (Commun)
    private static final int HDB_TORTUE  = 17929; // Tortue de Récif     (Peu commun)
    private static final int HDB_RAIE    = 40219; // Raie Fantôme        (Rare)
    private static final int HDB_SERPENT = 1750;  // Serpent de Marée    (Épique)
    private static final int HDB_BALEINE = 3924;  // Baleine de l'Appel  (Légendaire)
    private static final int HDB_AUBE    = 5973;  // Aube du Grand Appel (Mythique)

    // ── Têtes HeadDB des 6 pets de l'Arc 2 ─────────────────────────────────────
    private static final int HDB_MOUETTE   = 61833; // Mouette Grise        (Commun)
    private static final int HDB_ANGUILLE  = 37280; // Anguille Vive        (Peu commun)
    private static final int HDB_LOUTRE    = 646;   // Loutre Chapardeuse   (Rare)
    private static final int HDB_KRAKEN    = 8343;  // Kraken Juvénile      (Épique)
    private static final int HDB_LEVIATHAN = 24526; // Léviathan des Fosses (Légendaire)
    private static final int HDB_GARDIEN   = 1368;  // Gardien du Grand Appel (Mythique)

    // Tête du bouton « Équipement des familiers » (bas à gauche).
    private static final int HDB_EQUIP = 337;

    // Tous les ids à précharger au démarrage.
    public static final int[] ALL_HEAD_IDS = {
            HDB_ARC1, HDB_ARC2, HDB_ARC3, HDB_ARC4, HDB_ARC5, HDB_ARC6,
            HDB_CRABE, HDB_TORTUE, HDB_RAIE, HDB_SERPENT, HDB_BALEINE, HDB_AUBE,
            HDB_MOUETTE, HDB_ANGUILLE, HDB_LOUTRE, HDB_KRAKEN, HDB_LEVIATHAN, HDB_GARDIEN,
            HDB_EQUIP
    };

    // ── Prix des crates ────────────────────────────────────────────────────────
    private static final double PRICE_1  = 15_000;
    private static final double PRICE_5  = 70_000;   // ~5k offert
    private static final double PRICE_10 = 135_000;  // ~15k offert

    // Crates de l'Arc II (bien plus chères : débloquées à la mine 15).
    private static final double PRICE2_1  = 500_000;
    private static final double PRICE2_5  = 2_000_000;   // 500k offert
    private static final double PRICE2_10 = 3_850_000;   // 1,15M offert

    // Placement des 6 têtes d'arcs (écran 1) : 2 rangées de 3, centrées.
    private static final int[] ARC_SLOTS = {20, 22, 24, 29, 31, 33};

    // Placement des 6 pets dans le sous-menu (écran 2) :
    // rangée haute Commun/Peu commun/Rare = 2,4,6 ; rangée basse Épique/Lég/Mythique = 20,22,24.
    private static final int[] PET_SLOTS = {2, 4, 6, 20, 22, 24};

    // Boutons d'achat sur les lignes du bas.
    private static final int BUY_1_SLOT  = 38;
    private static final int BUY_5_SLOT  = 40;
    private static final int BUY_10_SLOT = 42;
    private static final int BACK_SLOT   = 49;
    private static final int PETS_SLOT   = 53; // coffre « Mes familiers » (bas à droite)
    private static final int EQUIP_SLOT  = 45; // tête « Équipement des familiers » (bas à gauche)
    private static final int FORGE_SLOT  = 47; // enclume « Forge des familiers » (bas)

    // Slots de la « piste » de roulette (ligne du milieu de l'inventaire d'éclosion).
    private static final int[] ROULETTE_TRACK = {19, 20, 21, 22, 23, 24, 25};
    private static final int ROULETTE_MARKER = 22; // case centrale = résultat

    private final PrivateMines plugin;
    // Tag NBT qui marque un item comme « pet » (identifiant de l'espèce).
    private final org.bukkit.NamespacedKey petKey;
    // Tag NBT du rang de rareté (0 = Commun … 5 = Mythique) pour le tri du menu « Mes Familiers ».
    private final org.bukkit.NamespacedKey petRankKey;

    // Définition d'un pet de l'Arc 1 (ordre = Commun → Mythique).
    // Les bonus machine-lisibles (pourcentages, base = niveau 1) alimentent PetBonusManager.
    static final class PetDef {
        final int headId; final String name; final String rarity; final String dropRate;
        final double weight; final String[] bonuses; final String id;
        // Bonus effectifs au niveau 1 (0 = pas de bonus sur cet axe).
        final double blockValuePct;   // valeur des blocs +X%
        final double capacityPct;     // capacité du sac +X%
        final double sellMoneyPct;    // argent à la vente +X%
        final double doubleBlockPct;  // chance de double bloc X%
        final double miningSpeedPct;  // vitesse de minage +X%
        PetDef(String id, int headId, String name, String rarity, String dropRate, double weight, String[] bonuses,
               double blockValuePct, double capacityPct, double sellMoneyPct, double doubleBlockPct, double miningSpeedPct) {
            this.id = id; this.headId = headId; this.name = name; this.rarity = rarity;
            this.dropRate = dropRate; this.weight = weight; this.bonuses = bonuses;
            this.blockValuePct = blockValuePct; this.capacityPct = capacityPct; this.sellMoneyPct = sellMoneyPct;
            this.doubleBlockPct = doubleBlockPct; this.miningSpeedPct = miningSpeedPct;
        }
    }

    // Les 6 pets de l'Arc 1 avec leurs poids de tirage (55/25/12/5/2,5/0,5 %) et bonus effectifs.
    static final PetDef[] ARC1_PETS = {
            new PetDef("arc1_crabe", HDB_CRABE, "§f§lCrabe des Vasières", "§f⚪ Commun", "55%", 55.0,
                    new String[]{"§7Valeur des blocs §a+2%"},
                    2, 0, 0, 0, 0),
            new PetDef("arc1_tortue", HDB_TORTUE, "§a§lTortue de Récif", "§a🟢 Peu commun", "25%", 25.0,
                    new String[]{"§7Capacité du sac §a+5%"},
                    0, 5, 0, 0, 0),
            new PetDef("arc1_raie", HDB_RAIE, "§9§lRaie Fantôme", "§9🔵 Rare", "12%", 12.0,
                    new String[]{"§7Chance de double bloc §a3%"},
                    0, 0, 0, 3, 0),
            new PetDef("arc1_serpent", HDB_SERPENT, "§5§lSerpent de Marée", "§5🟣 Épique", "5%", 5.0,
                    new String[]{"§7Argent à la vente §a+8%", "§7Vitesse de minage §a+5%"},
                    0, 0, 8, 0, 5),
            new PetDef("arc1_baleine", HDB_BALEINE, "§6§lBaleine de l'Appel", "§6🟠 Légendaire", "2,5%", 2.5,
                    new String[]{"§7Argent à la vente §a+12%", "§7Capacité du sac §a+12%"},
                    0, 12, 12, 0, 0),
            new PetDef("arc1_aube", HDB_AUBE, "§c§lAube du Grand Appel", "§c🔴 Mythique", "0,5%", 0.5,
                    new String[]{"§7Valeur des blocs §a+15%", "§7Chance de double bloc §a10%"},
                    15, 0, 0, 10, 0)
    };

    // Les 6 pets de l'Arc 2 (mêmes poids de tirage) — stats DOUBLÉES par rapport aux
    // valeurs de design, donc systématiquement au-dessus de leur équivalent Arc I.
    static final PetDef[] ARC2_PETS = {
            new PetDef("arc2_mouette", HDB_MOUETTE, "§f§lMouette Grise", "§f⚪ Commun", "55%", 55.0,
                    new String[]{"§7Argent à la vente §a+4%"},
                    0, 0, 4, 0, 0),
            new PetDef("arc2_anguille", HDB_ANGUILLE, "§a§lAnguille Vive", "§a🟢 Peu commun", "25%", 25.0,
                    new String[]{"§7Vitesse de minage §a+8%"},
                    0, 0, 0, 0, 8),
            new PetDef("arc2_loutre", HDB_LOUTRE, "§9§lLoutre Chapardeuse", "§9🔵 Rare", "12%", 12.0,
                    new String[]{"§7Argent à la vente §a+15%", "§7Capacité du sac §a+30%"},
                    0, 30, 15, 0, 0),
            new PetDef("arc2_kraken", HDB_KRAKEN, "§5§lKraken Juvénile", "§5🟣 Épique", "5%", 5.0,
                    new String[]{"§7Chance de double bloc §a20%", "§7Capacité du sac §a+30%"},
                    0, 30, 0, 20, 0),
            new PetDef("arc2_leviathan", HDB_LEVIATHAN, "§6§lLéviathan des Fosses", "§6🟠 Légendaire", "2,5%", 2.5,
                    new String[]{"§7Chance de double bloc §a35%", "§7Valeur des blocs §a+20%"},
                    20, 0, 0, 35, 0),
            new PetDef("arc2_gardien", HDB_GARDIEN, "§c§lGardien du Grand Appel", "§c🔴 Mythique", "0,5%", 0.5,
                    new String[]{"§7Argent à la vente §a+35%", "§7Chance de double bloc §a25%"},
                    0, 0, 35, 25, 0)
    };

    // Retrouve la définition d'un pet par son identifiant NBT (pet_id). null si inconnu.
    static PetDef defById(String id) {
        if (id == null) return null;
        for (PetDef d : ARC1_PETS) if (d.id.equals(id)) return d;
        for (PetDef d : ARC2_PETS) if (d.id.equals(id)) return d;
        return null;
    }

    // Numéro d'arc d'un pet (1 ou 2) — sert au lore et au rang de rareté.
    static int arcOf(PetDef def) {
        for (PetDef d : ARC2_PETS) if (d == def) return 2;
        return 1;
    }
    // Table des pets de l'arc d'un pet donné (pour retrouver son rang).
    private static PetDef[] petsOfArc(int arc) {
        return arc == 2 ? ARC2_PETS : ARC1_PETS;
    }

    public PetMenu(PrivateMines plugin) {
        this.plugin = plugin;
        this.petKey = new org.bukkit.NamespacedKey(plugin, "pet_id");
        this.petRankKey = new org.bukkit.NamespacedKey(plugin, "pet_rank");
    }

    // Clé NBT du rang de rareté, exposée à PetStorage pour trier.
    public org.bukkit.NamespacedKey getPetRankKey() { return petRankKey; }
    // Clé NBT de l'identifiant d'espèce, exposée à PetEquipMenu pour retrouver les bonus.
    public org.bukkit.NamespacedKey getPetIdKey() { return petKey; }

    // Vrai si l'item est un FAMILIER (porte le tag NBT pet_id). Sert à n'accepter que les pets
    // dans le coffre « Mes Familiers ».
    public boolean isPet(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    // ── Helpers de lecture d'un item pet (utilisés par la Forge) ─────────────────
    public String petIdOf(ItemStack it) {
        if (!isPet(it)) return null;
        return it.getItemMeta().getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
    }
    public int petRankOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return -1;
        Integer r = it.getItemMeta().getPersistentDataContainer().get(petRankKey, PersistentDataType.INTEGER);
        return r == null ? -1 : r;
    }
    public int petLevelOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 1;
        Integer l = it.getItemMeta().getPersistentDataContainer()
                .get(plugin.getPetEquip().getLevelKey(), PersistentDataType.INTEGER);
        return l == null ? 1 : Math.max(1, l);
    }
    // Écrit un nouveau niveau sur une COPIE de l'item (renvoie la copie modifiée),
    // et met à jour le nom affiché avec le badge de niveau « ⭐N » (N≥2).
    public ItemStack withLevel(ItemStack it, int level) {
        ItemStack copy = it.clone();
        int lvl = Math.max(1, level);
        ItemMeta m = copy.getItemMeta();
        m.getPersistentDataContainer().set(plugin.getPetEquip().getLevelKey(), PersistentDataType.INTEGER, lvl);
        try { m.setMaxStackSize(1); } catch (Throwable ignored) {}
        // Nom de base (depuis la définition si connue, sinon le nom actuel nettoyé du badge).
        PetDef d = defById(petIdOf(copy));
        String baseName = (d != null) ? d.name : stripLevelBadge(m.hasDisplayName() ? m.getDisplayName() : "§7Familier");
        m.setDisplayName(lvl >= 2 ? baseName + " §e⭐" + lvl : baseName);
        // Lore recalculé au nouveau niveau (les bonus affichés suivent le niveau).
        if (d != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§8" + d.rarity);
            lore.add("");
            lore.add("§7§oFamilier de l'Arc " + (arcOf(d) == 2 ? "II" : "I"));
            lore.add("");
            lore.add("§7§oBonus §7(niv. " + lvl + ") §7:");
            lore.addAll(bonusLines(d, lvl));
            m.setLore(lore);
        }
        copy.setItemMeta(m);
        copy.setAmount(1);
        return copy;
    }

    // Retire un éventuel badge « ⭐N » (et l'espace/couleur qui précède) d'un nom de pet.
    private static String stripLevelBadge(String name) {
        if (name == null) return "§7Familier";
        int idx = name.indexOf("⭐");
        if (idx < 0) return name;
        // On coupe juste avant le badge et on retire les codes couleur/espaces résiduels en fin.
        String cut = name.substring(0, idx);
        return cut.replaceAll("(?:§.)*\\s*$", "");
    }

    // « Scelle » un item familier pour qu'il ne s'empile jamais (max stack = 1, amount = 1).
    // À appeler avant de rendre un pet au joueur (main/inventaire) — utile pour les pets anciens
    // créés avant l'ajout du maxStackSize.
    public ItemStack seal(ItemStack pet) {
        if (pet == null) return null;
        ItemStack copy = pet.clone();
        if (copy.hasItemMeta()) {
            ItemMeta m = copy.getItemMeta();
            try { m.setMaxStackSize(1); } catch (Throwable ignored) {}
            copy.setItemMeta(m);
        }
        copy.setAmount(1);
        return copy;
    }
    // Niveau maximum d'un pet selon sa rareté (rang 0=Commun … 5=Mythique).
    private static final int[] LEVEL_CAP_BY_RANK = {10, 15, 20, 30, 40, 50};
    public int levelCapForRank(int rank) {
        if (rank < 0 || rank >= LEVEL_CAP_BY_RANK.length) return 10;
        return LEVEL_CAP_BY_RANK[rank];
    }
    // Construit un exemplaire NEUF (niveau 1) d'une espèce par son id — pour la Forge.
    // Renvoie null si l'espèce est inconnue.
    public ItemStack templatePet(String petId) {
        PetDef d = defById(petId);
        if (d == null) return null;
        return buildPetItem(d);
    }

    // Nom lisible d'un pet (via sa définition), fallback sur le displayName de l'item.
    public String petDisplayName(ItemStack it) {
        PetDef d = defById(petIdOf(it));
        if (d != null) return d.name;
        return (it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName())
                ? it.getItemMeta().getDisplayName() : "§7Familier";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ÉCRAN 1 — Sélecteur d'arcs
    // ═══════════════════════════════════════════════════════════════════════════

    public void openMain(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, MAIN_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        boolean arc2Ouvert = arc2Unlocked(player);

        menu.setItem(ARC_SLOTS[0], arcTile(HDB_ARC1, "§a§lArc I §7— §fL'Aube du Grand Appel",
                "Mine 1", true, "L'aube du voyage. L'appel du large résonne.",
                cratePriceLore(PRICE_1, PRICE_5, PRICE_10)));
        menu.setItem(ARC_SLOTS[1], arcTile(HDB_ARC2,
                (arc2Ouvert ? "§b§lArc II §7— §fLe Chant des Profondeurs" : "§7§lArc II §8— §7Le Chant des Profondeurs"),
                "Mine 15", arc2Ouvert,
                arc2Ouvert ? "Plus bas, l'eau chante. Les créatures y sont immenses."
                           : "Se débloque à la §fmine 15§7.",
                cratePriceLore(PRICE2_1, PRICE2_5, PRICE2_10)));
        menu.setItem(ARC_SLOTS[2], arcTile(HDB_ARC3, "§7§lArc III §8— §7Le Hameau des Frondes",
                "À venir", false, "Se débloquera en progressant dans l'aventure."));
        menu.setItem(ARC_SLOTS[3], arcTile(HDB_ARC4, "§7§lArc IV §8— §7Le Radeau des Saveurs",
                "À venir", false, "Se débloquera en progressant dans l'aventure."));
        menu.setItem(ARC_SLOTS[4], arcTile(HDB_ARC5, "§7§lArc V §8— §7Les Récifs du Joug",
                "À venir", false, "Se débloquera en progressant dans l'aventure."));
        menu.setItem(ARC_SLOTS[5], arcTile(HDB_ARC6, "§7§lArc VI §8— §7Le Port des Adieux",
                "À venir", false, "Se débloquera en progressant dans l'aventure."));

        menu.setItem(BACK_SLOT, plugin.namedItem(Material.BARRIER, "§cFermer", null));
        menu.setItem(PETS_SLOT, myPetsTile(player));
        menu.setItem(EQUIP_SLOT, equipTile());
        menu.setItem(FORGE_SLOT, forgeTile());
        player.openInventory(menu);
    }

    // L'Arc II des familiers s'ouvre quand le joueur a débloqué la mine 15.
    private boolean arc2Unlocked(Player player) {
        return plugin.hasUnlockedMine(player, ARC2_UNLOCK_MINE);
    }

    // L'enclume « Forge des Familiers » (améliore un pet par fusion de doublons).
    private ItemStack forgeTile() {
        ItemStack it = new ItemStack(Material.ANVIL);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§e§l⚒ Forge des Familiers");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Fusionne §e3 familiers identiques§7 pour");
        lore.add("§7monter un familier d'un §aniveau§7 et");
        lore.add("§7booster ses bonus.");
        lore.add("");
        lore.add("§e▶ Clique pour ouvrir");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // Le coffre « Mes familiers » (bas à droite des deux écrans).
    private ItemStack myPetsTile(Player player) {
        ItemStack it = new ItemStack(Material.CHEST);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§6§l📦 Mes Familiers");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Tu possèdes §e" + plugin.getPetStorage().count(player) + " §7familier(s).");
        lore.add("");
        lore.add("§e▶ Clique pour les voir");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // La tête « Équipement des familiers » (bas à gauche des deux écrans).
    private ItemStack equipTile() {
        ItemStack head = plugin.getPetHeads().getHead(HDB_EQUIP);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName("§b§l⚔ Équipement des Familiers");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Équipe tes familiers pour activer");
        lore.add("§7leurs bonus en jeu.");
        lore.add("");
        lore.add("§e▶ Clique pour ouvrir");
        m.setLore(lore);
        head.setItemMeta(m);
        return head;
    }

    private ItemStack arcTile(int headId, String name, String mines, boolean unlocked, String flavor) {
        return arcTile(headId, name, mines, unlocked, flavor, null);
    }

    // Version avec bloc « Prix des crates » optionnel (Arc I & II) inséré avant la ligne d'état.
    private ItemStack arcTile(int headId, String name, String mines, boolean unlocked, String flavor,
                              List<String> priceLore) {
        ItemStack head = plugin.getPetHeads().getHead(headId);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("§8" + mines);
        lore.add("");
        lore.add("§7" + flavor);
        if (priceLore != null) {
            lore.add("");
            lore.addAll(priceLore);
        }
        lore.add("");
        if (unlocked) {
            lore.add("§a✔ Débloqué");
            lore.add("§e▶ Clique pour voir les familiers de cet arc");
        } else {
            lore.add("§c🔒 Verrouillé");
        }
        m.setLore(lore);
        head.setItemMeta(m);
        return head;
    }

    // Ligne de lore « Prix d'une crate » affichée sur la tuile d'un arc.
    private List<String> cratePriceLore(double p1, double p5, double p10) {
        List<String> l = new ArrayList<>();
        l.add("§7Crate : §6" + formatPrice(p1) + "$ §8/ unité");
        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ÉCRAN 2 — Sous-menu de l'Arc 1 (vitrine des 6 pets + achat de crates)
    // ═══════════════════════════════════════════════════════════════════════════

    public void openArc1(Player player) { openArc(player, 1); }

    public void openArc2(Player player) {
        if (!arc2Unlocked(player)) {
            player.sendMessage("§c🔒 L'Arc II des familiers se débloque à la §fmine 15§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        openArc(player, 2);
    }

    // Vitrine d'un arc (6 pets + zone d'achat). Le même écran sert aux deux arcs :
    // seuls le titre, la table de pets, la couleur du séparateur et les prix changent.
    private void openArc(Player player, int arc) {
        PetDef[] pets = petsOfArc(arc);
        Inventory menu = Bukkit.createInventory(null, 54, arc == 2 ? ARC2_TITLE : ARC1_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);

        // Vitrine des 6 pets (nouvel agencement : 11,13,15 en haut / 20,22,24 en dessous).
        for (int i = 0; i < pets.length; i++) {
            menu.setItem(PET_SLOTS[i], petTile(pets[i]));
        }

        // Séparateur visuel avant la zone d'achat.
        ItemStack sep = plugin.pane(arc == 2 ? Material.BLUE_STAINED_GLASS_PANE : Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 27; i < 36; i++) menu.setItem(i, sep);

        // Zone d'achat de crates.
        double p1 = arc == 2 ? PRICE2_1 : PRICE_1;
        double p5 = arc == 2 ? PRICE2_5 : PRICE_5;
        double p10 = arc == 2 ? PRICE2_10 : PRICE_10;
        menu.setItem(BUY_1_SLOT, buyTile(Material.CHEST, "§e§l⟳ 1 Crate", p1,
                "§7Ouvre §e1 crate§7 avec animation.", "§7Tu gagnes §e1 familier§7 → §b/bp"));
        menu.setItem(BUY_5_SLOT, buyTile(Material.ENDER_CHEST, "§a§l✦ 5 Crates", p5,
                "§7Ouvre §a5 crates §7d'un coup.", "§75 familiers déposés dans §b/bp", "§8(pas d'animation)"));
        menu.setItem(BUY_10_SLOT, buyTile(Material.SHULKER_BOX, "§6§l✦ 10 Crates", p10,
                "§7Ouvre §610 crates §7d'un coup.", "§710 familiers déposés dans §b/bp", "§8(pas d'animation)"));

        menu.setItem(BACK_SLOT, plugin.namedItem(Material.ARROW, "§7◀ Retour aux arcs", null));
        menu.setItem(PETS_SLOT, myPetsTile(player));
        menu.setItem(EQUIP_SLOT, equipTile());
        player.openInventory(menu);
    }

    private ItemStack petTile(PetDef def) {
        ItemStack head = plugin.getPetHeads().getHead(def.headId);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(def.name);
        List<String> lore = new ArrayList<>();
        lore.add("§8" + def.rarity);
        lore.add("");
        lore.add("§7Taux en crate : §e" + def.dropRate);
        lore.add("");
        lore.add("§7§oBonus procurés :");
        for (String b : def.bonuses) lore.add("  §8• " + b);
        m.setLore(lore);
        head.setItemMeta(m);
        return head;
    }

    private ItemStack buyTile(Material mat, String name, double price, String... desc) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String d : desc) lore.add(d);
        lore.add("");
        lore.add("§7Prix : §6" + formatPrice(price) + "$");
        lore.add("");
        lore.add("§e▶ Clique pour acheter");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tirage & achats
    // ═══════════════════════════════════════════════════════════════════════════

    // Tire un pet de l'arc demandé selon les poids. Utilise le RNG partagé du serveur.
    private PetDef rollPet(int arc) {
        PetDef[] pets = petsOfArc(arc);
        double total = 0;
        for (PetDef p : pets) total += p.weight;
        double r = java.util.concurrent.ThreadLocalRandom.current().nextDouble(total);
        double acc = 0;
        for (PetDef p : pets) {
            acc += p.weight;
            if (r < acc) return p;
        }
        return pets[0]; // filet de sécurité
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Don d'un pet via CRATE (rareté dans une plage donnée). Utilisé par CrateManager.
    //  minRank/maxRank = index de rareté (0 = Commun … 5 = Mythique), tirage PONDÉRÉ
    //  par le weight des pets dont le rang est dans [minRank, maxRank].
    //  L'arc suit la progression : Arc II possible seulement si le joueur l'a débloqué.
    // ═══════════════════════════════════════════════════════════════════════════
    public void givePetInRarityRange(Player player, int minRank, int maxRank) {
        // Choix de l'arc selon la progression (Arc II à partir de la mine 15).
        int arc = 1;
        if (arc2Unlocked(player)) {
            arc = java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 2 : 1;
        }
        PetDef[] pets = petsOfArc(arc);
        // Filtre les pets dont le rang (index dans la table) est dans la plage voulue.
        java.util.List<PetDef> pool = new java.util.ArrayList<>();
        for (int i = 0; i < pets.length; i++) {
            if (i >= minRank && i <= maxRank) pool.add(pets[i]);
        }
        if (pool.isEmpty()) pool.add(pets[Math.min(maxRank, pets.length - 1)]); // filet
        // Tirage pondéré par weight dans la plage.
        double total = 0;
        for (PetDef p : pool) total += p.weight;
        double r = java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.max(0.0001, total));
        double acc = 0;
        PetDef won = pool.get(pool.size() - 1);
        for (PetDef p : pool) { acc += p.weight; if (r < acc) { won = p; break; } }

        ItemStack petItem = buildPetItem(won);
        // Le coffre Familiers n'a pas de limite (liste paginée) : on ajoute toujours directement.
        plugin.getPetStorage().addPet(player, petItem);
        player.sendMessage("  §8» " + won.name + " §8(" + won.rarity + "§8)");
    }

    // Génère les lignes de bonus d'un pet À SON NIVEAU (valeurs recalculées avec le multiplicateur).
    // Ainsi le lore d'un pet ⭐2 affiche bien le bonus réel (ex. +2,8%) et non le bonus de base.
    List<String> bonusLines(PetDef def, int level) {
        double mult = plugin.getPetEquip().levelMultForLevel(level);
        List<String> out = new ArrayList<>();
        if (def.blockValuePct  != 0) out.add("  §8• §7Valeur des blocs §a+" + pct(def.blockValuePct  * mult));
        if (def.capacityPct    != 0) out.add("  §8• §7Capacité du sac §a+" + pct(def.capacityPct    * mult));
        if (def.sellMoneyPct   != 0) out.add("  §8• §7Argent à la vente §a+" + pct(def.sellMoneyPct   * mult));
        if (def.doubleBlockPct != 0) out.add("  §8• §7Chance de double bloc §a" + pct(def.doubleBlockPct * mult));
        if (def.miningSpeedPct != 0) out.add("  §8• §7Vitesse de minage §a+" + pct(def.miningSpeedPct * mult));
        if (out.isEmpty()) out.add("  §8• §7Aucun bonus");
        return out;
    }
    // Formate un pourcentage : entier sans décimale, sinon 1 décimale, + le signe %.
    private static String pct(double v) {
        if (v == Math.floor(v)) return ((long) v) + "%";
        return String.format(java.util.Locale.US, "%.1f", v) + "%";
    }

    // Construit l'ItemStack « pet » à déposer dans le /bp (tête + nom + lore + tag NBT).
    private ItemStack buildPetItem(PetDef def) {
        ItemStack head = plugin.getPetHeads().getHead(def.headId);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(def.name);
        List<String> lore = new ArrayList<>();
        int arc = arcOf(def);
        PetDef[] pets = petsOfArc(arc);
        lore.add("§8" + def.rarity);
        lore.add("");
        lore.add("§7§oFamilier de l'Arc " + (arc == 2 ? "II" : "I"));
        lore.add("");
        lore.add("§7§oBonus §7(niv. 1) §7:");
        lore.addAll(bonusLines(def, 1));
        m.setLore(lore);
        m.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, def.id);
        // Rang de rareté = index dans la table de son arc (0 = Commun … 5 = Mythique).
        int rank = 0;
        for (int i = 0; i < pets.length; i++) if (pets[i] == def) { rank = i; break; }
        m.getPersistentDataContainer().set(petRankKey, PersistentDataType.INTEGER, rank);
        // Niveau initial = 1 (montée d'XP branchée dans une brique ultérieure).
        m.getPersistentDataContainer().set(plugin.getPetEquip().getLevelKey(), PersistentDataType.INTEGER, 1);
        // Les familiers ne s'empilent jamais (1 par slot) : évite d'empiler 64 pets en un stack.
        try { m.setMaxStackSize(1); } catch (Throwable ignored) { /* API < 1.20.5 */ }
        head.setItemMeta(m);
        return head;
    }

    // Achat de N crates de l'arc donné. count == 1 → animation ; sinon dépôt direct.
    private void buyCrates(Player player, int arc, int count, double price) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cÉconomie indisponible.");
            return;
        }
        // Garde-fou : on ne peut pas acheter des crates d'un arc verrouillé.
        if (arc == 2 && !arc2Unlocked(player)) {
            player.sendMessage("§c🔒 L'Arc II des familiers se débloque à la §fmine 15§c.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        double bal = plugin.getEconomy().getBalance(player);
        if (bal < price) {
            player.sendMessage("§cIl te manque §6" + formatPrice(price - bal) + "$ §cpour cet achat.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, price);
        // Parcours de découverte (Acte III) : étape « achète un familier ».
        plugin.getTutorial().onCrateBought(player);

        if (count == 1) {
            PetDef won = rollPet(arc);
            startRoulette(player, arc, won);
        } else {
            List<PetDef> won = new ArrayList<>();
            for (int i = 0; i < count; i++) won.add(rollPet(arc));
            for (PetDef d : won) plugin.getPetStorage().addPet(player, buildPetItem(d));
            player.closeInventory();
            player.sendMessage("§a§l✦ §e" + count + " familiers §aajoutés à §6Mes Familiers §7:");
            for (PetDef d : won) player.sendMessage("  §8• " + d.name);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Roulette (défilement horizontal, achat de 1 crate)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startRoulette(Player player, int arc, PetDef winner) {
        Inventory roul = Bukkit.createInventory(null, 45, ROULETTE_TITLE);
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 45; i++) roul.setItem(i, bg);
        // Flèches indicatrices au-dessus/en-dessous du marqueur central.
        roul.setItem(ROULETTE_MARKER - 9, plugin.namedItem(Material.HOPPER_MINECART, "§e▼", null));
        roul.setItem(ROULETTE_MARKER + 9, plugin.namedItem(Material.HOPPER_MINECART, "§e▲", null));
        player.openInventory(roul);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);

        // Le défilement : à chaque tick on décale les têtes ; on ralentit progressivement ;
        // on s'arrête quand le gagnant tombe pile sur le marqueur central.
        new RouletteTask(player, roul, arc, winner).runTaskTimer(plugin, 3L, 1L);
    }

    private final class RouletteTask extends org.bukkit.scheduler.BukkitRunnable {
        private final Player player;
        private final Inventory inv;
        private final PetDef[] pool;
        private final PetDef winner;
        private int ticks = 0;
        private int stepDelay = 1;   // nb de ticks entre deux décalages (augmente = ralentit)
        private int sinceStep = 0;
        private int scrolled = 0;
        private int stopAt = -1;     // nb de décalages après lequel on s'arrête
        private final java.util.Random rng = new java.util.Random(System.nanoTime());

        RouletteTask(Player player, Inventory inv, int arc, PetDef winner) {
            this.player = player; this.inv = inv; this.winner = winner;
            this.pool = petsOfArc(arc);
            // On choisit une distance de défilement pseudo-aléatoire pour l'effet.
            this.stopAt = 30 + rng.nextInt(10);
        }

        @Override
        public void run() {
            // Si le joueur a fermé/changé de fenêtre, on annule proprement (pet non perdu : on le donne).
            if (!ROULETTE_TITLE.equals(player.getOpenInventory().getTitle())) {
                deliver();
                cancel();
                return;
            }
            ticks++;
            sinceStep++;
            if (sinceStep < stepDelay) return;
            sinceStep = 0;

            scrolled++;
            // Décalage : on remplit la piste de têtes aléatoires, sauf à l'approche de l'arrêt
            // où on aligne le gagnant sur le marqueur central.
            boolean finishing = scrolled >= stopAt;
            for (int slot : ROULETTE_TRACK) {
                PetDef d = pool[rng.nextInt(pool.length)];
                inv.setItem(slot, spinTile(d));
            }
            if (finishing) {
                inv.setItem(ROULETTE_MARKER, spinTile(winner));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1f);

            // Ralentissement progressif à l'approche de l'arrêt.
            if (scrolled > stopAt - 12) stepDelay = 2;
            if (scrolled > stopAt - 7)  stepDelay = 3;
            if (scrolled > stopAt - 3)  stepDelay = 5;

            if (scrolled >= stopAt) {
                inv.setItem(ROULETTE_MARKER, spinTile(winner));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                deliver();
                // On laisse le résultat affiché quelques ticks puis on ferme.
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override public void run() {
                        if (ROULETTE_TITLE.equals(player.getOpenInventory().getTitle())) player.closeInventory();
                    }
                }.runTaskLater(plugin, 45L);
                cancel();
            }
        }

        private boolean delivered = false;
        private void deliver() {
            if (delivered) return;
            delivered = true;
            plugin.getPetStorage().addPet(player, buildPetItem(winner));
            player.sendMessage("§a§l✦ §7Tu as obtenu : " + winner.name + " §7! §8→ §6Mes Familiers");
        }
    }

    // Tuile affichée pendant le défilement (tête + nom seul, lore vidé pour masquer l'affichage HeadDB).
    private ItemStack spinTile(PetDef def) {
        ItemStack head = plugin.getPetHeads().getHead(def.headId);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(def.name);
        m.setLore(new ArrayList<>()); // enlève le lore HeadDB (ID/tags/instructions)
        head.setItemMeta(m);
        return head;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Gestion des clics
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPetMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        Player p = (Player) event.getWhoClicked();

        // ── Menu principal (sélecteur d'arcs) ──
        if (MAIN_TITLE.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == BACK_SLOT) { p.closeInventory(); return; }
            if (slot == PETS_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                plugin.getPetStorage().open(p);
                return;
            }
            if (slot == EQUIP_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                plugin.getPetEquip().open(p);
                return;
            }
            if (slot == FORGE_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                plugin.getPetForge().open(p);
                return;
            }
            if (slot == ARC_SLOTS[0]) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                openArc1(p);
            } else if (slot == ARC_SLOTS[1]) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                openArc2(p);
            }
            return;
        }

        // ── Sous-menu d'un arc (I ou II) ──
        if (ARC1_TITLE.equals(title) || ARC2_TITLE.equals(title)) {
            event.setCancelled(true);
            int arc = ARC2_TITLE.equals(title) ? 2 : 1;
            int slot = event.getRawSlot();
            if (slot >= event.getInventory().getSize()) return; // clic dans l'inventaire perso
            if (slot == BACK_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 0.9f);
                openMain(p);
            } else if (slot == PETS_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                plugin.getPetStorage().open(p);
            } else if (slot == EQUIP_SLOT) {
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                plugin.getPetEquip().open(p);
            } else if (slot == BUY_1_SLOT) {
                buyCrates(p, arc, 1, arc == 2 ? PRICE2_1 : PRICE_1);
            } else if (slot == BUY_5_SLOT) {
                buyCrates(p, arc, 5, arc == 2 ? PRICE2_5 : PRICE_5);
            } else if (slot == BUY_10_SLOT) {
                buyCrates(p, arc, 10, arc == 2 ? PRICE2_10 : PRICE_10);
            }
            return;
        }

        // ── Écran roulette : on bloque toute interaction ──
        if (ROULETTE_TITLE.equals(title)) {
            event.setCancelled(true);
        }
    }

    // Format simple d'un prix (15000 → « 15 000 »).
    private String formatPrice(double v) {
        return String.format(java.util.Locale.FRANCE, "%,.0f", v).replace(' ', ' ');
    }
}
