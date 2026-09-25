package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menus /pnj setting : liste des PNJ + édition d'un PNJ (rôle, nom, couleur, skin, tp, suppr).
 * Tout est visuel et cliquable ; les changements passent par NpcManager (qui sauve dans npcs.yml).
 */
public class NpcSettingMenu implements Listener {

    private final PrivateMines plugin;

    private static final String LIST_TITLE  = "§8PNJ » Liste";
    private static final String EDIT_PREFIX = "§8PNJ » ";       // suivi de l'ID
    private static final String COLOR_PREFIX = "§8Couleur » ";  // suivi de l'ID
    private static final String ROLE_PREFIX  = "§8Rôle » ";     // suivi de l'ID
    private static final String SKIN_PREFIX  = "§8Skin » ";     // suivi de l'ID

    // Quel PNJ chaque joueur édite (pour retrouver l'ID dans les sous-menus).
    private final Map<UUID, String> editing = new HashMap<>();

    // Palette de couleurs proposée (code -> libellé + laine).
    private static final String[][] COLORS = {
            {"0","Noir","BLACK_WOOL"}, {"1","Bleu foncé","BLUE_WOOL"},
            {"2","Vert foncé","GREEN_WOOL"}, {"3","Cyan","CYAN_WOOL"},
            {"4","Rouge foncé","RED_WOOL"}, {"5","Violet","PURPLE_WOOL"},
            {"6","Or","ORANGE_WOOL"}, {"7","Gris clair","LIGHT_GRAY_WOOL"},
            {"8","Gris","GRAY_WOOL"}, {"9","Bleu","BLUE_WOOL"},
            {"a","Vert","LIME_WOOL"}, {"b","Cyan clair","LIGHT_BLUE_WOOL"},
            {"c","Rouge","RED_WOOL"}, {"d","Rose","PINK_WOOL"},
            {"e","Jaune","YELLOW_WOOL"}, {"f","Blanc","WHITE_WOOL"},
    };

    public NpcSettingMenu(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // ===== Menu LISTE =====

    public void openList(Player player) {
        java.util.Set<String> ids = plugin.getNpc().getIds();
        int rows = Math.max(1, (int) Math.ceil(ids.size() / 9.0));
        Inventory menu = Bukkit.createInventory(null, rows * 9, LIST_TITLE);

        for (String id : ids) {
            NpcConfig c = plugin.getNpc().getCfg();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m = head.getItemMeta();
            m.setDisplayName(c.getDisplayName(id));
            List<String> lore = new ArrayList<>();
            lore.add("§7ID : §f" + id);
            lore.add("§7Rôle : §f" + roleLabel(c.getRole(id)));
            lore.add("§7Position : §f" + (int) c.getX(id) + ", " + (int) c.getY(id) + ", " + (int) c.getZ(id));
            lore.add("");
            lore.add("§eClic pour éditer");
            m.setLore(lore);
            head.setItemMeta(m);
            menu.addItem(head);
        }
        if (ids.isEmpty()) {
            menu.setItem(4, named(Material.BARRIER, "§cAucun PNJ",
                    "§7Fais §f/pnj place §7pour en créer un."));
        }
        player.openInventory(menu);
    }

    // ===== Menu ÉDITION d'un PNJ =====

    public void openEdit(Player player, String id) {
        if (!plugin.getNpc().getCfg().has(id)) { openList(player); return; }
        editing.put(player.getUniqueId(), id);
        NpcConfig c = plugin.getNpc().getCfg();

        Inventory menu = Bukkit.createInventory(null, 27, EDIT_PREFIX + id);
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) menu.setItem(i, filler);

        // Aperçu (tête).
        menu.setItem(4, named(Material.PLAYER_HEAD, c.getDisplayName(id),
                "§7ID : §f" + id));

        // Rôle.
        menu.setItem(10, named(Material.PAPER, "§e🎭 Rôle : §f" + roleLabel(c.getRole(id)),
                "§7Détermine le dialogue.", "§eClic pour changer"));
        // Nom.
        menu.setItem(12, named(Material.NAME_TAG, "§e✏ Nom",
                "§7Actuel : " + c.getDisplayName(id), "§eClic : taper le nom au chat"));
        // Couleur.
        menu.setItem(14, named(Material.LIME_DYE, "§e🎨 Couleur du nom",
                "§7Actuelle : §" + c.getColor(id) + "exemple", "§eClic pour choisir"));
        // Skin.
        menu.setItem(16, named(Material.LEATHER_CHESTPLATE, "§b🖼 Skin",
                "§7Choisis un skin enregistré", "§7dans la bibliothèque.", "§eClic pour choisir"));

        // Téléporter.
        menu.setItem(21, named(Material.ENDER_PEARL, "§a🚶 Me téléporter au PNJ", null));
        // Supprimer.
        menu.setItem(23, named(Material.BARRIER, "§c🗑 Supprimer ce PNJ", "§7Définitif."));
        // Retour.
        menu.setItem(26, named(Material.ARROW, "§7« Retour à la liste", null));

        player.openInventory(menu);
    }

    // ===== Sous-menu RÔLE (paginé par chapitre) =====

    // Un rôle proposé dans le menu : sa clé (NpcManager.ROLE_*), son item d'affichage.
    private static final class RoleDef {
        final String role; final Material icon; final String name; final String[] lore;
        RoleDef(String role, Material icon, String name, String... lore) {
            this.role = role; this.icon = icon; this.name = name; this.lore = lore;
        }
    }

    // Une PAGE = un chapitre du jeu, avec son titre et ses rôles. Ordre = ordre de progression.
    private static final class RolePage {
        final String title; final RoleDef[] roles;
        RolePage(String title, RoleDef... roles) { this.title = title; this.roles = roles; }
    }

    // Les chapitres, dans l'ordre. « Aucun rôle » est ajouté d'office sur chaque page (slot dédié).
    private static final RolePage[] ROLE_PAGES = {
        new RolePage("§bChapitre I — Le Spawn",
            new RoleDef(NpcManager.ROLE_VEILLEUR, Material.PRISMARINE_CRYSTALS, "§bLe Veilleur",
                    "§7Dialogue d'accueil (étape 1 de l'Acte I)."),
            new RoleDef(NpcManager.ROLE_ANCRE, Material.HEART_OF_THE_SEA, "§aL'Ancre",
                    "§7Donne l'objectif + clôt l'Acte I.")),
        new RolePage("§6Chapitre II — L'Île de la Mine",
            new RoleDef(NpcManager.ROLE_CONTREMAITRE, Material.IRON_PICKAXE, "§6Le Contremaître",
                    "§7Acte II : donne le sac + lance la quête de la Tête de Pioche."),
            new RoleDef(NpcManager.ROLE_FORGERON, Material.ANVIL, "§cLe Forgeron",
                    "§7Acte II : forge la pioche contre la Tête de Pioche.")),
        new RolePage("§eChapitre III — Alabasta",
            new RoleDef(NpcManager.ROLE_GUIDE_BOUSSOLE, Material.COMPASS, "§6Le Guide (Log Pose)",
                    "§7Récite son dialogue PUIS remet la",
                    "§6Boussole du Log Pose §7(visite d'Alabasta).",
                    "§8Dialogue = §fnpcs.<id>.dialogue §8comme le Conteur."),
            new RoleDef(NpcManager.ROLE_TEMOIN_FINAL, Material.SKELETON_SKULL, "§cLe Dernier Témoin",
                    "§7Fin de la visite d'Alabasta. Ne parle QUE si",
                    "§7le joueur a fini les 3 lieux (boussole en main).",
                    "§7Récite son récit PUIS §fdonne la clé §7+ retire la boussole.",
                    "§8Dialogue = §fnpcs.<id>.dialogue§8.")),
        new RolePage("§7Divers",
            new RoleDef(NpcManager.ROLE_CONTEUR, Material.WRITABLE_BOOK, "§eLe Conteur",
                    "§7Récite un dialogue libre au clic droit.",
                    "§7Écris ses lignes dans §fnpcs.yml §7→ §fnpcs.<id>.dialogue§7,",
                    "§7une ligne par entrée, puis §f/pnj reload§7."),
            new RoleDef(NpcManager.ROLE_END, Material.DRAGON_HEAD, "§5Le Passeur du Vide",
                    "§7Au clic droit, téléporte le joueur dans",
                    "§7l'§5arène de l'End §7(combat du dragon),",
                    "§7à un endroit aléatoire (rayon 75 autour du centre).",
                    "§c⚔ PvP + perte de stuff dans l'End."),
            new RoleDef(NpcManager.ROLE_QUOTIDIEN, Material.CLOCK, "§aRécompenses quotidiennes",
                    "§7Au clic droit, ouvre la §6série de 7 jours§7.",
                    "§7Le PNJ scintille tant que le joueur n'a pas réclamé.",
                    "§8Le contenu suit son palier (mine débloquée)."))
    };

    // Slots où l'on place les rôles d'une page (centrés, ligne du milieu).
    private static final int[] ROLE_SLOTS = {11, 13, 15, 21, 23, 25};
    // Slots de navigation / actions (ligne du bas).
    private static final int SLOT_NONE = 18;   // « Aucun rôle »
    private static final int SLOT_PREV = 19;   // page précédente
    private static final int SLOT_BACK = 22;   // retour à l'édition du PNJ
    private static final int SLOT_NEXT = 25;   // page suivante  (⚠ ne pas confondre avec un rôle : ROLE_SLOTS n'utilise 25 que si 6 rôles)

    private void openRole(Player player, String id) { openRole(player, id, 0); }

    private void openRole(Player player, String id, int page) {
        int nb = ROLE_PAGES.length;
        if (page < 0) page = 0;
        if (page >= nb) page = nb - 1;
        RolePage rp = ROLE_PAGES[page];

        // Titre = "§8Rôle » <id> §8(p<page>)" : le handler y relit la page.
        Inventory menu = Bukkit.createInventory(null, 36, ROLE_PREFIX + id + " §8(p" + page + ")");
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 36; i++) menu.setItem(i, filler);

        // Bandeau : nom du chapitre (livre au centre haut).
        menu.setItem(4, named(Material.KNOWLEDGE_BOOK, rp.title,
                "§8Chapitre " + (page + 1) + "/" + nb));

        // Rôles de la page (max ROLE_SLOTS.length).
        for (int i = 0; i < rp.roles.length && i < ROLE_SLOTS.length; i++) {
            RoleDef rd = rp.roles[i];
            menu.setItem(ROLE_SLOTS[i], named(rd.icon, rd.name, rd.lore));
        }

        // Ligne du bas : Aucun rôle, navigation, retour.
        menu.setItem(SLOT_NONE, named(Material.GRAY_DYE, "§7Aucun rôle",
                "§7Le PNJ ne déclenche aucun dialogue."));
        if (page > 0) {
            menu.setItem(SLOT_PREV, named(Material.SPECTRAL_ARROW, "§e◀ Chapitre précédent",
                    "§7" + ROLE_PAGES[page - 1].title));
        }
        if (page < nb - 1) {
            menu.setItem(SLOT_NEXT, named(Material.SPECTRAL_ARROW, "§eChapitre suivant ▶",
                    "§7" + ROLE_PAGES[page + 1].title));
        }
        menu.setItem(SLOT_BACK, named(Material.ARROW, "§7« Retour", null));
        player.openInventory(menu);
    }


    // Relit le n° de page encodé dans le titre du menu rôle : "... §8(p<N>)". 0 si absent/illisible.
    private int parseRolePage(String title) {
        int i = title.lastIndexOf("(p");
        if (i < 0) return 0;
        try {
            int j = title.indexOf(')', i);
            if (j < 0) return 0;
            return Integer.parseInt(title.substring(i + 2, j).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ===== Sous-menu COULEUR =====

    private void openColor(Player player, String id) {
        Inventory menu = Bukkit.createInventory(null, 27, COLOR_PREFIX + id);
        for (String[] col : COLORS) {
            Material wool = Material.matchMaterial(col[2]);
            if (wool == null) wool = Material.WHITE_WOOL;
            menu.addItem(named(wool, "§" + col[0] + col[1], "§7Code : §f&" + col[0]));
        }
        menu.setItem(26, named(Material.ARROW, "§7« Retour", null));
        player.openInventory(menu);
    }

    // ===== Sous-menu SKIN (bibliothèque) =====

    private void openSkin(Player player, String id) {
        java.util.Set<String> names = plugin.getSkins().getNames();
        int rows = Math.max(1, (int) Math.ceil((names.size() + 1) / 9.0));
        Inventory menu = Bukkit.createInventory(null, rows * 9, SKIN_PREFIX + id);

        String current = plugin.getNpc().getCfg().getSkinValue(id);
        for (String name : names) {
            boolean applied = current != null && !current.isEmpty()
                    && current.equals(plugin.getSkins().getValue(name));
            menu.addItem(named(Material.PLAYER_HEAD,
                    (applied ? "§a✔ §f" : "§f") + name,
                    "§7Skin de la bibliothèque.",
                    applied ? "§aActuellement appliqué" : "§eClic pour appliquer"));
        }
        if (names.isEmpty()) {
            menu.setItem(4, named(Material.BARRIER, "§cAucun skin enregistré",
                    "§7Ajoute-en un avec :", "§f/pnj skin add <nom>"));
        }
        // Bouton retour en dernière ligne, dernière case.
        menu.setItem(rows * 9 - 1, named(Material.ARROW, "§7« Retour", null));
        player.openInventory(menu);
    }

    // ===== Clics =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        // --- Liste ---
        if (title.equals(LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            // Retrouve l'ID via le lore ("ID : xxx").
            String id = idFromLore(clicked);
            if (id != null) openEdit(player, id);
            return;
        }

        // --- Édition ---
        if (title.startsWith(EDIT_PREFIX)) {
            event.setCancelled(true);
            String id = editing.get(player.getUniqueId());
            if (id == null) { player.closeInventory(); return; }
            switch (event.getSlot()) {
                case 10: openRole(player, id); return;
                case 12: plugin.getNpc().startNameInput(player, id); return;
                case 14: openColor(player, id); return;
                case 16: openSkin(player, id); return;
                case 21: {
                    Location loc = plugin.getNpc().getNpcLocation(id);
                    if (loc != null) { player.closeInventory(); player.teleport(loc); }
                    return;
                }
                case 23:
                    plugin.getNpc().removeNpc(id);
                    player.sendMessage("§cPNJ §f" + id + " §csupprimé.");
                    openList(player);
                    return;
                case 26: openList(player); return;
                default: return;
            }
        }

        // --- Rôle (menu paginé par chapitre) ---
        if (title.startsWith(ROLE_PREFIX)) {
            event.setCancelled(true);
            String id = editing.get(player.getUniqueId());
            if (id == null) { player.closeInventory(); return; }
            int page = parseRolePage(title);
            int slot = event.getSlot();

            // Actions fixes (bas de menu).
            if (slot == SLOT_BACK) { openEdit(player, id); return; }
            if (slot == SLOT_NONE) {
                plugin.getNpc().setRole(id, NpcManager.ROLE_NONE);
                player.sendMessage("§7Rôle retiré."); openEdit(player, id); return;
            }
            if (slot == SLOT_PREV && page > 0) { openRole(player, id, page - 1); return; }
            if (slot == SLOT_NEXT && page < ROLE_PAGES.length - 1) { openRole(player, id, page + 1); return; }

            // Sélection d'un rôle : le slot cliqué correspond-il à un rôle de cette page ?
            RolePage rp = ROLE_PAGES[Math.max(0, Math.min(page, ROLE_PAGES.length - 1))];
            for (int i = 0; i < rp.roles.length && i < ROLE_SLOTS.length; i++) {
                if (ROLE_SLOTS[i] == slot) {
                    RoleDef rd = rp.roles[i];
                    plugin.getNpc().setRole(id, rd.role);
                    player.sendMessage("§aRôle défini : " + rd.name + "§a.");
                    openEdit(player, id);
                    return;
                }
            }
            return;
        }

        // --- Skin (bibliothèque) ---
        if (title.startsWith(SKIN_PREFIX)) {
            event.setCancelled(true);
            String id = editing.get(player.getUniqueId());
            if (id == null) { player.closeInventory(); return; }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openEdit(player, id); return; }
            if (clicked.getType() != Material.PLAYER_HEAD) return;
            // Le nom du skin = nom affiché sans le préfixe "✔ " ni les couleurs.
            String name = org.bukkit.ChatColor.stripColor(
                    clicked.getItemMeta().getDisplayName()).replace("✔ ", "").trim();
            if (plugin.getNpc().applyLibrarySkin(id, name)) {
                player.sendMessage("§a✔ Skin §f" + name + " §aappliqué au PNJ §f" + id + "§a.");
                openEdit(player, id);
            }
            return;
        }

        // --- Couleur ---
        if (title.startsWith(COLOR_PREFIX)) {
            event.setCancelled(true);
            String id = editing.get(player.getUniqueId());
            if (id == null) { player.closeInventory(); return; }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            if (clicked.getType() == Material.ARROW) { openEdit(player, id); return; }
            // Récupère le code couleur depuis le lore ("Code : &x").
            String code = colorFromLore(clicked);
            if (code != null) {
                plugin.getNpc().setColor(id, code);
                player.sendMessage("§aCouleur changée.");
                openEdit(player, id);
            }
        }
    }

    // ===== Helpers =====

    private String roleLabel(String role) {
        if (NpcManager.ROLE_VEILLEUR.equals(role)) return "§bLe Veilleur";
        if (NpcManager.ROLE_ANCRE.equals(role)) return "§aL'Ancre";
        if (NpcManager.ROLE_CONTREMAITRE.equals(role)) return "§6Le Contremaître";
        if (NpcManager.ROLE_FORGERON.equals(role)) return "§cLe Forgeron";
        if (NpcManager.ROLE_CONTEUR.equals(role)) return "§eLe Conteur";
        if (NpcManager.ROLE_GUIDE_BOUSSOLE.equals(role)) return "§6Le Guide (Log Pose)";
        if (NpcManager.ROLE_TEMOIN_FINAL.equals(role)) return "§cLe Dernier Témoin";
        if (NpcManager.ROLE_END.equals(role)) return "§5Le Passeur du Vide";
        if (NpcManager.ROLE_QUOTIDIEN.equals(role)) return "§aRécompenses quotidiennes";
        return "§7Aucun";
    }

    private String idFromLore(ItemStack item) {
        if (!item.hasItemMeta() || item.getItemMeta().getLore() == null) return null;
        for (String line : item.getItemMeta().getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            if (stripped.startsWith("ID : ")) return stripped.substring(5).trim();
        }
        return null;
    }

    private String colorFromLore(ItemStack item) {
        if (!item.hasItemMeta() || item.getItemMeta().getLore() == null) return null;
        for (String line : item.getItemMeta().getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(line);
            if (stripped.startsWith("Code : &")) return stripped.substring("Code : &".length()).trim();
        }
        return null;
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && lore.length > 0 && lore[0] != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) if (s != null) l.add(s);
            m.setLore(l);
        }
        it.setItemMeta(m);
        return it;
    }
}
