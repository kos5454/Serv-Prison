package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Système d'hologrammes (Text Display natifs 1.21.4, sans dépendance externe).
 * Extrait de PrivateMines pour alléger le fichier principal.
 */
public class HologramManager implements Listener {

    private final PrivateMines plugin;

    public HologramManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    static class HoloData {
        Location loc;
        java.util.List<String> lines = new java.util.ArrayList<>();
        boolean fixed = false; // true = orientation FIGÉE (ne pivote plus vers le joueur)
        org.bukkit.entity.TextDisplay entity;     // face RECTO (transitoire, recréée si besoin)
        org.bukkit.entity.TextDisplay entityBack; // face VERSO (uniquement si fixed : lisible de dos)
    }

    private final java.util.Map<String, HoloData> holograms = new java.util.LinkedHashMap<>();
    // Hologramme des règles de l'End : nom fixe + index de la ligne « timer du dragon » (rafraîchie chaque seconde).
    private static final String END_HOLO = "end";
    private static final String MINERESET_HOLO = "minereset";
    private static final int END_TIMER_LINE = 2; // 3e ligne (0-based) = le décompte du dragon
    // Hologrammes de CLASSEMENT masqués définitivement : leur nom est ici → la tâche auto ne les recrée pas.
    private final java.util.Set<String> masques = new java.util.HashSet<>();
    private java.io.File holoFile;
    private org.bukkit.configuration.file.FileConfiguration holoConfig;

    private String holoColorize(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    public void initHolograms() {
        holoFile = new java.io.File(plugin.getDataFolder(), "holograms.yml");
        if (!holoFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { holoFile.createNewFile(); } catch (java.io.IOException ignored) {}
        }
        holoConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(holoFile);
        // Classements masqués définitivement (liste de noms) : ne seront pas recréés par la tâche auto.
        masques.clear();
        for (String n : holoConfig.getStringList("masques")) masques.add(n.toLowerCase());
        org.bukkit.configuration.ConfigurationSection sec = holoConfig.getConfigurationSection("holograms");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            String path = "holograms." + name;
            String wn = holoConfig.getString(path + ".world", "world");
            World w = Bukkit.getWorld(wn);
            HoloData h = new HoloData();
            if (w != null) {
                h.loc = new Location(w,
                        holoConfig.getDouble(path + ".x"),
                        holoConfig.getDouble(path + ".y"),
                        holoConfig.getDouble(path + ".z"),
                        (float) holoConfig.getDouble(path + ".yaw", 0),
                        (float) holoConfig.getDouble(path + ".pitch", 0));
            }
            h.fixed = holoConfig.getBoolean(path + ".fixed", false);
            h.lines = new java.util.ArrayList<>(holoConfig.getStringList(path + ".lines"));
            holograms.put(name.toLowerCase(), h);
        }
    }

    private void saveHolograms() {
        if (holoConfig == null) return;
        holoConfig.set("holograms", null);
        holoConfig.set("masques", new java.util.ArrayList<>(masques));
        for (java.util.Map.Entry<String, HoloData> e : holograms.entrySet()) {
            HoloData h = e.getValue();
            if (h.loc == null) continue;
            String path = "holograms." + e.getKey();
            holoConfig.set(path + ".world", h.loc.getWorld().getName());
            holoConfig.set(path + ".x", h.loc.getX());
            holoConfig.set(path + ".y", h.loc.getY());
            holoConfig.set(path + ".z", h.loc.getZ());
            holoConfig.set(path + ".yaw", h.loc.getYaw());
            holoConfig.set(path + ".pitch", h.loc.getPitch());
            holoConfig.set(path + ".fixed", h.fixed);
            holoConfig.set(path + ".lines", h.lines);
        }
        try { holoConfig.save(holoFile); } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder les hologrammes.");
        }
    }

    private String holoRenderText(HoloData h) {
        java.util.List<String> colored = new java.util.ArrayList<>();
        for (String l : h.lines) colored.add(holoColorize(l));
        String joined = String.join("\n", colored);
        return joined.isEmpty() ? " " : joined;
    }

    private void spawnHologram(HoloData h) {
        if (h.loc == null || h.loc.getWorld() == null) return;
        // Face RECTO : à l'orientation de la position (yaw h.loc), ou en billboard si non fixé.
        h.entity = spawnOneDisplay(h, h.loc);
        // Face VERSO : uniquement si l'holo est FIGÉ → un 2e panneau tourné à 180° pour être
        // lisible de l'autre côté (un TextDisplay n'affiche son texte que sur UNE face).
        if (h.fixed) {
            Location back = h.loc.clone();
            back.setYaw(h.loc.getYaw() + 180f);
            h.entityBack = spawnOneDisplay(h, back);
        }
    }

    // Crée un TextDisplay (une face) à la location donnée, avec le style commun.
    private org.bukkit.entity.TextDisplay spawnOneDisplay(HoloData h, Location loc) {
        return loc.getWorld().spawn(loc, org.bukkit.entity.TextDisplay.class, d -> {
            // FIXED = orientation figée (selon le yaw/pitch de la position) ; CENTER = pivote vers le joueur.
            d.setBillboard(h.fixed ? org.bukkit.entity.Display.Billboard.FIXED
                                   : org.bukkit.entity.Display.Billboard.CENTER);
            d.setText(holoRenderText(h));
            d.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // pas de fond (texte seul)
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setPersistent(false); // on les recrée nous-mêmes depuis holograms.yml
            // Marqueur : permet de retrouver et supprimer les TextDisplay d'hologramme « orphelins »
            // (restés dans le monde après un /reload) au prochain démarrage → évite les doublons.
            d.getPersistentDataContainer().set(holoKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
    }

    private org.bukkit.NamespacedKey holoKey() {
        return new org.bukkit.NamespacedKey(plugin, "hologram");
    }

    /**
     * Supprime tous les TextDisplay d'hologramme marqués encore présents dans les mondes chargés.
     * À appeler AVANT de (re)créer les hologrammes au démarrage, pour éliminer les doublons laissés
     * par un /reload (où les entités non-persistantes ne sont pas toujours retirées proprement).
     */
    public void nettoyerOrphelins() {
        org.bukkit.NamespacedKey key = holoKey();
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                    e.remove();
                    n++;
                }
            }
        }
        if (n > 0) plugin.getLogger().info("Hologrammes : " + n + " entité(s) orpheline(s) nettoyée(s).");
    }

    private void refreshHologram(HoloData h) {
        // Si l'état recto/verso ne correspond plus (ex. on vient de fixer), on recrée proprement.
        boolean rectoOk = h.entity != null && h.entity.isValid();
        boolean versoAttendu = h.fixed;
        boolean versoOk = h.entityBack != null && h.entityBack.isValid();
        if (rectoOk && versoAttendu == versoOk) {
            h.entity.setText(holoRenderText(h));
            if (versoOk) h.entityBack.setText(holoRenderText(h));
        } else {
            removeHologramEntity(h);
            spawnHologram(h);
        }
    }

    private void removeHologramEntity(HoloData h) {
        if (h.entity != null && h.entity.isValid()) h.entity.remove();
        if (h.entityBack != null && h.entityBack.isValid()) h.entityBack.remove();
        h.entity = null;
        h.entityBack = null;
    }

    public void spawnAllHolograms() {
        for (HoloData h : holograms.values()) {
            if (h.loc != null && (h.entity == null || !h.entity.isValid())) spawnHologram(h);
        }
    }

    /** Retire toutes les entités d'hologrammes (à l'arrêt) puis sauvegarde leur définition. */
    public void shutdown() {
        for (HoloData h : holograms.values()) removeHologramEntity(h);
        saveHolograms();
    }

    /** True si un hologramme de ce nom existe déjà. */
    public boolean exists(String name) {
        return holograms.containsKey(name.toLowerCase());
    }

    /** Noms de tous les hologrammes (pour la complétion tab). */
    public java.util.List<String> getHologramNames() {
        return new java.util.ArrayList<>(holograms.keySet());
    }

    /**
     * Crée l'hologramme s'il n'existe pas (à la position {@code spawnIfNew}), met à jour ses
     * lignes et le rafraîchit. Utilisé par les classements pour leurs hologrammes auto.
     * Si l'hologramme n'existe pas et que {@code spawnIfNew} est null, ne fait rien.
     */
    public void setHologramLines(String name, Location spawnIfNew, java.util.List<String> lines) {
        String key = name.toLowerCase();
        HoloData h = holograms.get(key);
        if (h == null) {
            if (spawnIfNew == null) return;
            h = new HoloData();
            h.loc = spawnIfNew;
            holograms.put(key, h);
            spawnHologram(h);
        }
        h.lines = lines;
        refreshHologram(h);
    }

    /** Sauvegarde manuelle (exposée pour les appelants externes comme les classements). */
    public void save() {
        saveHolograms();
    }

    /** Supprime un hologramme (entité + définition), s'il existe. Utilisé par les PNJ. */
    public void removeHologram(String name) {
        String key = name.toLowerCase();
        HoloData h = holograms.remove(key);
        if (h != null) {
            removeHologramEntity(h);
            saveHolograms();
        }
    }

    /**
     * Supprime un hologramme ET le masque définitivement s'il fait partie des classements auto :
     * il ne sera plus jamais recréé par la tâche des classements tant qu'il reste masqué.
     */
    public void removeAndMask(String name) {
        String key = name.toLowerCase();
        masques.add(key);
        HoloData h = holograms.remove(key);
        if (h != null) removeHologramEntity(h);
        saveHolograms();
    }

    /** True si ce nom d'hologramme est masqué (supprimé définitivement). */
    public boolean estMasque(String name) {
        return masques.contains(name.toLowerCase());
    }

    /** Réaffiche un classement précédemment masqué (il sera recréé au prochain refresh). */
    public void demasquer(String name) {
        if (masques.remove(name.toLowerCase())) saveHolograms();
    }

    /** Ajoute une ligne à un hologramme existant (menu de gestion). */
    public boolean addLine(String name, String line) {
        HoloData h = holograms.get(name.toLowerCase());
        if (h == null) return false;
        h.lines.add(line);
        refreshHologram(h);
        saveHolograms();
        return true;
    }

    /** Supprime la ligne d'index idx (0-based) d'un hologramme. */
    public boolean removeLine(String name, int idx) {
        HoloData h = holograms.get(name.toLowerCase());
        if (h == null || idx < 0 || idx >= h.lines.size()) return false;
        h.lines.remove(idx);
        refreshHologram(h);
        saveHolograms();
        return true;
    }

    /** Lignes d'un hologramme (copie), ou liste vide si inconnu. */
    public java.util.List<String> getLines(String name) {
        HoloData h = holograms.get(name.toLowerCase());
        return h == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(h.lines);
    }

    /** Déplace un hologramme existant à une nouvelle position (movehere). */
    public boolean moveTo(String name, Location loc) {
        HoloData h = holograms.get(name.toLowerCase());
        if (h == null || loc == null) return false;
        h.loc = loc.clone();
        removeHologramEntity(h);
        spawnHologram(h);
        saveHolograms();
        return true;
    }

    /** Crée un hologramme vide (1 ligne) à une position, s'il n'existe pas déjà. */
    public boolean createHologram(String name, Location loc, String firstLine) {
        String key = name.toLowerCase();
        if (holograms.containsKey(key) || loc == null) return false;
        HoloData h = new HoloData();
        h.loc = loc.clone();
        h.lines.add(firstLine == null ? "&fNouvel hologramme" : firstLine);
        holograms.put(key, h);
        spawnHologram(h);
        saveHolograms();
        return true;
    }

    // Recrée un hologramme quand son chunk se recharge (entités non persistantes).
    @EventHandler
    public void onChunkLoadHolo(org.bukkit.event.world.ChunkLoadEvent event) {
        for (HoloData h : holograms.values()) {
            if (h.loc == null || h.loc.getWorld() == null) continue;
            if (!h.loc.getWorld().equals(event.getWorld())) continue;
            if ((h.loc.getBlockX() >> 4) == event.getChunk().getX()
                    && (h.loc.getBlockZ() >> 4) == event.getChunk().getZ()
                    && (h.entity == null || !h.entity.isValid()
                        || (h.fixed && (h.entityBack == null || !h.entityBack.isValid())))) {
                removeHologramEntity(h); // repart propre (évite un recto/verso en double)
                spawnHologram(h);
            }
        }
    }

    public boolean handleHolo(Player player, String[] args) {
        if (!player.isOp()) { player.sendMessage("§cCommande réservée aux opérateurs."); return true; }
        if (args.length == 0) {
            player.sendMessage("§6Hologrammes : §e/holo setting §7(menu complet : liste, créer, gérer, supprimer)");
            player.sendMessage("§7En commande : §e/holo create|edit|movehere|delete|list");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("setting") || sub.equals("settings") || sub.equals("menu")) {
            openMenu(player);
            return true;
        }
        if (sub.equals("list")) {
            if (holograms.isEmpty()) { player.sendMessage("§7Aucun hologramme."); return true; }
            player.sendMessage("§6Hologrammes (" + holograms.size() + ") : §f" + String.join(", ", holograms.keySet()));
            return true;
        }
        if (args.length < 2) { player.sendMessage("§cUsage : /holo " + sub + " <nom> ..."); return true; }
        String name = args[1].toLowerCase();

        if (sub.equals("create")) {
            if (holograms.containsKey(name)) { player.sendMessage("§cUn hologramme nommé §f" + name + " §cexiste déjà."); return true; }
            HoloData h = new HoloData();
            h.loc = player.getLocation().clone().add(0, 2.2, 0);
            h.lines.add("&fNouvel hologramme");
            holograms.put(name, h);
            spawnHologram(h);
            saveHolograms();
            player.sendMessage("§aHologramme §f" + name + " §acréé. Ajoute des lignes avec §e/holo addline " + name + " <texte>");
            return true;
        }

        // /holo set <modèle> : crée (ou remplace) un hologramme pré-rempli à ta position.
        // Modèles : « end » (règles + timer dragon), « passeur » (au-dessus du PNJ Passeur du Vide)
        // et « minereset » (explique le seuil de régénération de la mine, à poser à l'entrée des mines).
        if (sub.equals("set")) {
            if (name.equals("end")) {
                HoloData h = holograms.computeIfAbsent(END_HOLO, k -> new HoloData());
                h.loc = player.getLocation().clone().add(0, 2.4, 0);
                h.lines.clear();
                h.lines.addAll(lignesHoloEnd());
                removeHologramEntity(h);
                spawnHologram(h);
                saveHolograms();
                player.sendMessage("§a✔ Hologramme §f" + END_HOLO + " §aposé à ta position. Le timer du dragon se met à jour tout seul.");
                return true;
            }
            if (name.equals("passeur")) {
                HoloData h = holograms.computeIfAbsent("passeur", k -> new HoloData());
                h.loc = player.getLocation().clone().add(0, 2.4, 0);
                h.lines.clear();
                h.lines.add("&5&l🐉 LE PASSEUR DU VIDE");
                h.lines.add("&e▶ Clic droit &7pour entrer dans l'&5arène du Dragon");
                removeHologramEntity(h);
                spawnHologram(h);
                saveHolograms();
                player.sendMessage("§a✔ Hologramme §fpasseur §aposé. Place-toi sur le PNJ avant de le poser, puis §f/holo setting §apour l'ajuster.");
                return true;
            }
            if (name.equals("minereset")) {
                HoloData h = holograms.computeIfAbsent(MINERESET_HOLO, k -> new HoloData());
                h.loc = player.getLocation().clone().add(0, 2.4, 0);
                h.lines.clear();
                h.lines.addAll(lignesHoloMineReset());
                removeHologramEntity(h);
                spawnHologram(h);
                saveHolograms();
                player.sendMessage("§a✔ Hologramme §f" + MINERESET_HOLO + " §aposé à ta position (seuil actuel : §f"
                        + PrivateMines.getResetThresholdPct() + " %§a).");
                return true;
            }
            player.sendMessage("§cModèle inconnu : §f" + name + " §7(disponibles : §fend§7, §fpasseur§7, §fminereset§7).");
            return true;
        }

        HoloData h = holograms.get(name);
        if (h == null) { player.sendMessage("§cHologramme introuvable : §f" + name); return true; }

        switch (sub) {
            case "edit": {
                openHoloEdit(player, name);
                return true;
            }
            case "addline": {
                if (args.length < 3) { player.sendMessage("§cUsage : /holo addline " + name + " <texte>"); return true; }
                h.lines.add(joinFrom(args, 2));
                refreshHologram(h); saveHolograms();
                player.sendMessage("§aLigne ajoutée (" + h.lines.size() + ").");
                return true;
            }
            case "setline": {
                if (args.length < 4) { player.sendMessage("§cUsage : /holo setline " + name + " <n°> <texte>"); return true; }
                int idx = parseLineIndex(player, args[2], h.lines.size());
                if (idx < 0) return true;
                h.lines.set(idx, joinFrom(args, 3));
                refreshHologram(h); saveHolograms();
                player.sendMessage("§aLigne " + (idx + 1) + " modifiée.");
                return true;
            }
            case "removeline": {
                if (args.length < 3) { player.sendMessage("§cUsage : /holo removeline " + name + " <n°>"); return true; }
                int idx = parseLineIndex(player, args[2], h.lines.size());
                if (idx < 0) return true;
                h.lines.remove(idx);
                refreshHologram(h); saveHolograms();
                player.sendMessage("§aLigne supprimée. (" + h.lines.size() + " restantes)");
                return true;
            }
            case "movehere": {
                removeHologramEntity(h);
                h.loc = player.getLocation().clone().add(0, 2.2, 0);
                spawnHologram(h); saveHolograms();
                player.sendMessage("§aHologramme §f" + name + " §adéplacé ici.");
                return true;
            }
            case "delete": {
                // removeAndMask : supprime ET masque définitivement (un classement ne se recréera plus).
                removeAndMask(name);
                player.sendMessage("§aHologramme §f" + name + " §asupprimé §7(ne réapparaîtra plus).");
                return true;
            }
            default:
                player.sendMessage("§cSous-commande inconnue. §e/holo create|addline|setline|removeline|movehere|delete|list");
                return true;
        }
    }

    private String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) { if (i > start) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }

    private int parseLineIndex(Player player, String s, int size) {
        int idx;
        try { idx = Integer.parseInt(s) - 1; } catch (NumberFormatException e) {
            player.sendMessage("§cNuméro de ligne invalide."); return -1;
        }
        if (idx < 0 || idx >= size) { player.sendMessage("§cCe numéro de ligne n'existe pas (1-" + size + ")."); return -1; }
        return idx;
    }

    // ----- Édition par interface (accroupi + clic droit en visant l'hologramme) -----
    private static final String HOLO_EDIT_TITLE = "§5§lÉditer l'hologramme";
    private static final String HOLO_MOVE_TITLE = "§5§l✥ Ajuster la position";
    private static final String HOLO_MENU_TITLE = "§5§l✦ Gestion des Hologrammes";
    private final java.util.Map<java.util.UUID, String> holoEditing = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> holoInputName = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> holoInputLine = new java.util.HashMap<>();
    // Joueurs en train de saisir le NOM d'un nouvel hologramme au chat (flux "créer").
    private final java.util.Set<java.util.UUID> holoInputCreate = new java.util.HashSet<>();

    // Liste des noms de classements auto (pour proposer de les réafficher s'ils sont masqués).
    private static final String[] CLASS_HOLO_NAMES =
            {"top-argent", "top-gains", "top-progression", "top-blocs", "top-enchants", "top-iles"};

    // Lignes de l'hologramme des règles de l'End. La ligne d'index END_TIMER_LINE (2) est
    // le décompte du dragon, rafraîchie chaque seconde par startEndTimerTask().
    // Panneau « régénération de la mine ». Le pourcentage et le nombre de blocs sont LUS DANS LE
    // CODE (PrivateMines.RESET_THRESHOLD_PCT) : si on change le seuil, il suffit de reposer
    // l'hologramme pour qu'il suive — impossible qu'il annonce une valeur périmée.
    private java.util.List<String> lignesHoloMineReset() {
        int pct = PrivateMines.getResetThresholdPct();
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("&b&l⛏ RÉGÉNÉRATION DE LA MINE");
        l.add("&7──────────────────────");
        l.add("&fLa mine se régénère quand vous en");
        l.add("&favez cassé &e&l" + pct + " %&f.");
        return l;
    }

    private java.util.List<String> lignesHoloEnd() {
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("&5&l🐉 L'ANTRE DU VIDE");
        l.add("&7──────────────────────");
        // Index 2 = timer (valeur initiale ; remplacée à chaud par la tâche).
        String timer = plugin.getEnd() != null ? plugin.getEnd().texteTimerDragon() : "&d🐉 Prochain Dragon";
        l.add(timer.replace('§', '&'));
        l.add("&7──────────────────────");
        l.add("&c⚔ &lPvP ACTIVÉ &7hors de cette salle");
        l.add("&c☠ &lPERTE DE STUFF &7à la mort");
        l.add("&c🔒 &lDÉCO EN COMBAT = MORT");
        l.add("&c🚫 &lCommandes bloquées &7en combat");
        l.add("&7(&f/spawn /warp /home...&7 → interdits 15s)");
        l.add("&7──────────────────────");
        l.add("&a✔ &7Cette salle est une &lzone sûre");
        l.add("&6🏆 &7Le &fTop 3 &7des dégâts se partage le butin");
        return l;
    }

    /** Tâche (1 s) : met à jour la ligne « timer du dragon » de l'hologramme « end », s'il existe. */
    public void startEndTimerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            HoloData h = holograms.get(END_HOLO);
            if (h == null || plugin.getEnd() == null) return;
            if (h.lines.size() <= END_TIMER_LINE) return;
            String nouveau = plugin.getEnd().texteTimerDragon().replace('§', '&');
            if (!nouveau.equals(h.lines.get(END_TIMER_LINE))) {
                h.lines.set(END_TIMER_LINE, nouveau);
                refreshHologram(h);
            }
        }, 40L, 20L); // démarre à +2 s, puis toutes les 1 s
    }

    // ----- Menu principal /hologram setting : liste + créer + accès édition -----
    public void openMenu(Player player) {
        if (!player.isOp()) { player.sendMessage("§cCommande réservée aux opérateurs."); return; }
        Inventory menu = Bukkit.createInventory(null, 54, HOLO_MENU_TITLE);
        refreshMenu(menu);
        player.openInventory(menu);
    }

    private void refreshMenu(Inventory menu) {
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        // Une tuile par hologramme existant (max 36).
        int i = 0;
        for (java.util.Map.Entry<String, HoloData> e : holograms.entrySet()) {
            if (i >= 36) break;
            HoloData h = e.getValue();
            ItemStack it = new ItemStack(Material.ITEM_FRAME);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§d§l" + e.getKey());
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§8" + h.lines.size() + " ligne(s)");
            lore.add("");
            for (int k = 0; k < h.lines.size() && k < 4; k++) lore.add("§7" + holoColorize(h.lines.get(k)));
            if (h.lines.size() > 4) lore.add("§8…");
            lore.add("");
            lore.add("§eClic gauche §7» gérer (lignes, déplacer)");
            lore.add("§cClic droit §7» supprimer définitivement");
            m.setLore(lore);
            it.setItemMeta(m);
            menu.setItem(i++, it);
        }
        if (holograms.isEmpty()) {
            menu.setItem(22, plugin.namedItem(Material.GRAY_DYE, "§7Aucun hologramme",
                    "§8Clique sur « Créer » pour en ajouter un."));
        }
        // Barre du bas.
        ItemStack border = plugin.pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int s = 45; s < 54; s++) menu.setItem(s, border);
        menu.setItem(47, plugin.namedItem(Material.LIME_DYE, "§a➕ Créer un hologramme",
                "§7Clic pour taper son nom dans le chat.", "§8Il apparaîtra à ta position."));
        // Bouton « réafficher les classements masqués » si au moins un est masqué.
        int nbMasques = 0;
        for (String cn : CLASS_HOLO_NAMES) if (estMasque(cn)) nbMasques++;
        if (nbMasques > 0) {
            menu.setItem(49, plugin.namedItem(Material.NETHER_STAR, "§b↺ Réafficher les classements masqués",
                    "§7Classements masqués : §e" + nbMasques,
                    "§7Clic pour tous les réactiver."));
        }
        menu.setItem(53, plugin.namedItem(Material.ARROW, "§7Fermer", null));
    }

    @EventHandler
    public void onHoloMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!HOLO_MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 53) { player.closeInventory(); return; }
        if (slot == 47) { // créer : saisie du nom au chat
            holoInputCreate.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§eTape le §fnom§e du nouvel hologramme dans le chat §7(ou 'annuler').");
            return;
        }
        if (slot == 49) { // réafficher tous les classements masqués
            for (String cn : CLASS_HOLO_NAMES) demasquer(cn);
            player.sendMessage("§aClassements réactivés §7(ils réapparaîtront au prochain rafraîchissement).");
            refreshMenu(event.getInventory());
            return;
        }
        // Clic sur une tuile d'hologramme.
        if (slot < 36) {
            java.util.List<String> noms = new java.util.ArrayList<>(holograms.keySet());
            if (slot >= noms.size()) return;
            String name = noms.get(slot);
            if (event.isRightClick()) {
                removeAndMask(name);
                player.sendMessage("§aHologramme §f" + name + " §asupprimé §7(ne réapparaîtra plus).");
                refreshMenu(event.getInventory());
            } else {
                openHoloEdit(player, name);
            }
        }
    }


    // Renvoie le nom de l'hologramme visé (cône ~10°, portée 6 blocs), ou null.
    private String getLookedHologramName(Player player) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection();
        String best = null; double bestDist = Double.MAX_VALUE;
        for (java.util.Map.Entry<String, HoloData> e : holograms.entrySet()) {
            HoloData h = e.getValue();
            if (h.loc == null || h.loc.getWorld() == null || !h.loc.getWorld().equals(eye.getWorld())) continue;
            org.bukkit.util.Vector to = h.loc.clone().add(0, 0.3, 0).toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 0.1 || dist > 6.0) continue;
            double dot = to.normalize().dot(dir);
            if (dot < 0.985) continue; // ~10°
            if (dist < bestDist) { bestDist = dist; best = e.getKey(); }
        }
        return best;
    }

    private void openHoloEdit(Player player, String name) {
        holoEditing.put(player.getUniqueId(), name);
        Inventory menu = Bukkit.createInventory(null, 54, HOLO_EDIT_TITLE);
        refreshHoloEdit(player, menu, name);
        player.openInventory(menu);
    }

    private void refreshHoloEdit(Player player, Inventory menu, String name) {
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        HoloData h = holograms.get(name);
        if (h == null) return;
        for (int i = 0; i < h.lines.size() && i < 36; i++) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            String txt = holoColorize(h.lines.get(i));
            m.setDisplayName(txt.isEmpty() ? "§7(ligne vide)" : txt);
            m.setLore(java.util.Arrays.asList("§7Ligne " + (i + 1),
                    "§eClic §7» éditer (texte / couleur / suppr.)"));
            it.setItemMeta(m);
            menu.setItem(i, it);
        }
        ItemStack border = plugin.pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) menu.setItem(i, border);
        menu.setItem(45, boutonFixer(h));
        menu.setItem(46, plugin.namedItem(Material.LIME_DYE, "§a➕ Ajouter une ligne", "§7Clic pour taper le texte dans le chat."));
        menu.setItem(48, plugin.namedItem(Material.ENDER_PEARL, "§b⤓ Déplacer ici", "§7Place l'hologramme à ta position."));
        menu.setItem(49, plugin.namedItem(Material.COMPASS, "§e✥ Ajuster la position", "§7Déplace l'hologramme bloc par bloc", "§7(haut/bas + Nord/Sud/Est/Ouest)."));
        menu.setItem(51, plugin.namedItem(Material.BARRIER, "§c🗑 Supprimer l'hologramme", "§7Supprime définitivement cet hologramme."));
        menu.setItem(53, plugin.namedItem(Material.ARROW, "§7Fermer", null));
    }

    // Bouton de bascule « orientation figée / suit le joueur ».
    private ItemStack boutonFixer(HoloData h) {
        if (h.fixed) {
            return plugin.namedItem(Material.LODESTONE, "§b📌 Hologramme FIXÉ",
                    "§7Orientation figée : il ne pivote plus.",
                    "", "§eClic §7» le remettre en mode §fsuit le joueur§7.");
        }
        return plugin.namedItem(Material.COMPASS, "§e🧭 Suit le joueur",
                "§7Il pivote toujours pour te faire face.",
                "", "§eClic §7» le §fFIGER §7dans l'orientation où tu regardes.");
    }

    @EventHandler
    public void onHoloEditClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!HOLO_EDIT_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        String name = holoEditing.get(player.getUniqueId());
        if (name == null) return;
        HoloData h = holograms.get(name);
        if (h == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Clic sur une ligne existante -> page d'édition de la ligne
        if (slot < 36 && slot < h.lines.size()) {
            openHoloLine(player, name, slot);
            return;
        }
        switch (slot) {
            case 45:
                // Bascule figé/suit-le-joueur. En figeant, on capte l'orientation actuelle de l'OP.
                h.fixed = !h.fixed;
                if (h.fixed && h.loc != null) {
                    h.loc.setYaw(player.getLocation().getYaw());
                    h.loc.setPitch(0f); // vertical : lisible de face (on ne penche pas le panneau)
                }
                removeHologramEntity(h);
                spawnHologram(h);
                saveHolograms();
                player.sendMessage(h.fixed
                        ? "§b📌 Hologramme §ffigé §b: il ne pivote plus vers les joueurs."
                        : "§e🧭 Hologramme remis en mode §fsuit le joueur§e.");
                refreshHoloEdit(player, event.getInventory(), name);
                break;
            case 46:
                holoInputName.put(player.getUniqueId(), name);
                holoInputLine.put(player.getUniqueId(), -1);
                player.closeInventory();
                player.sendMessage("§eTape le texte de la nouvelle ligne dans le chat §7(ou 'annuler'). §8Couleurs avec &.");
                break;
            case 48:
                removeHologramEntity(h);
                h.loc = player.getLocation().clone().add(0, 2.2, 0);
                spawnHologram(h); saveHolograms();
                player.sendMessage("§aHologramme déplacé ici.");
                refreshHoloEdit(player, event.getInventory(), name);
                break;
            case 49:
                openHoloMove(player, name);
                break;
            case 51:
                removeAndMask(name); // supprime + masque (un classement ne se recréera plus)
                holoEditing.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§aHologramme §f" + name + " §asupprimé §7(ne réapparaîtra plus).");
                break;
            case 53:
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    // ----- Menu « Ajuster la position » : déplace l'hologramme de 1 bloc (6 directions) -----

    // Slots des flèches (disposition en croix dans un inventaire 27).
    private static final int MOVE_UP = 4, MOVE_DOWN = 22, MOVE_NORTH = 11, MOVE_SOUTH = 15,
                             MOVE_WEST = 12, MOVE_EAST = 14, MOVE_INFO = 13, MOVE_BACK = 18, MOVE_STEP = 26;
    // Pas de déplacement disponibles (en blocs), cyclés par le bouton MOVE_STEP.
    private static final double[] MOVE_STEPS = {1.0, 0.5, 0.25, 0.1};
    // Index du pas courant choisi par chaque joueur dans le menu (défaut = 1 bloc).
    private final java.util.Map<java.util.UUID, Integer> moveStepIdx = new java.util.HashMap<>();

    private void openHoloMove(Player player, String name) {
        holoEditing.put(player.getUniqueId(), name);
        Inventory menu = Bukkit.createInventory(null, 27, HOLO_MOVE_TITLE);
        refreshHoloMove(player, menu, name);
        player.openInventory(menu);
    }

    private void refreshHoloMove(Player player, Inventory menu, String name) {
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 27; i++) menu.setItem(i, bg);
        HoloData h = holograms.get(name);
        if (h == null || h.loc == null) return;

        double step = stepDe(player);
        String pas = "§f" + trim(step) + " bloc" + (step >= 2 ? "s" : "");
        menu.setItem(MOVE_UP,    plugin.namedItem(Material.LIME_DYE,   "§a⬆ Monter",  "§7+" + trim(step) + " en hauteur (Y)"));
        menu.setItem(MOVE_DOWN,  plugin.namedItem(Material.RED_DYE,    "§c⬇ Descendre","§7-" + trim(step) + " en hauteur (Y)"));
        menu.setItem(MOVE_NORTH, plugin.namedItem(Material.ARROW,      "§b⬆ Nord",    "§7-" + trim(step) + " en Z"));
        menu.setItem(MOVE_SOUTH, plugin.namedItem(Material.ARROW,      "§b⬇ Sud",     "§7+" + trim(step) + " en Z"));
        menu.setItem(MOVE_WEST,  plugin.namedItem(Material.ARROW,      "§b⬅ Ouest",   "§7-" + trim(step) + " en X"));
        menu.setItem(MOVE_EAST,  plugin.namedItem(Material.ARROW,      "§b➡ Est",     "§7+" + trim(step) + " en X"));
        menu.setItem(MOVE_INFO,  plugin.namedItem(Material.COMPASS, "§e✥ Position",
                "§7X: §f" + arrondi(h.loc.getX()),
                "§7Y: §f" + arrondi(h.loc.getY()),
                "§7Z: §f" + arrondi(h.loc.getZ()),
                "", "§7Pas actuel : " + pas));
        menu.setItem(MOVE_STEP,  plugin.namedItem(Material.COMPARATOR, "§e⚙ Pas : " + pas,
                "§7Distance déplacée à chaque flèche.",
                "", "§eClic §7» changer §8(1 → 0.5 → 0.25 → 0.1)"));
        menu.setItem(MOVE_BACK,  plugin.namedItem(Material.BARRIER, "§7◀ Retour", "§7Revenir à l'édition."));
    }

    // Pas de déplacement courant du joueur (en blocs).
    private double stepDe(Player p) {
        int idx = moveStepIdx.getOrDefault(p.getUniqueId(), 0);
        return MOVE_STEPS[Math.max(0, Math.min(idx, MOVE_STEPS.length - 1))];
    }

    // Affichage propre d'un nombre (sans .0 inutile).
    private String trim(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
    private double arrondi(double d) { return Math.round(d * 100) / 100.0; }

    @EventHandler
    public void onHoloMoveClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!HOLO_MOVE_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        String name = holoEditing.get(player.getUniqueId());
        if (name == null) return;
        HoloData h = holograms.get(name);
        if (h == null || h.loc == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();

        // Bouton « Pas » : cycle 1 → 0.5 → 0.25 → 0.1 → 1...
        if (slot == MOVE_STEP) {
            int idx = (moveStepIdx.getOrDefault(player.getUniqueId(), 0) + 1) % MOVE_STEPS.length;
            moveStepIdx.put(player.getUniqueId(), idx);
            refreshHoloMove(player, event.getInventory(), name);
            return;
        }
        if (slot == MOVE_BACK) { openHoloEdit(player, name); return; }

        double s = stepDe(player);
        double dx = 0, dy = 0, dz = 0;
        switch (slot) {
            case MOVE_UP:    dy = s;  break;
            case MOVE_DOWN:  dy = -s; break;
            case MOVE_NORTH: dz = -s; break;
            case MOVE_SOUTH: dz = s;  break;
            case MOVE_WEST:  dx = -s; break;
            case MOVE_EAST:  dx = s;  break;
            default: return;
        }
        h.loc.add(dx, dy, dz);
        removeHologramEntity(h);
        spawnHologram(h);
        saveHolograms();
        refreshHoloMove(player, event.getInventory(), name);
    }

    @EventHandler
    public void onHoloChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        // Flux "créer" : le joueur tape le NOM d'un nouvel hologramme.
        if (holoInputCreate.remove(player.getUniqueId())) {
            event.setCancelled(true);
            String saisie = event.getMessage().trim().toLowerCase().replace(' ', '-');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (saisie.equalsIgnoreCase("annuler") || saisie.isEmpty()) {
                    player.sendMessage("§7Création annulée.");
                    return;
                }
                if (exists(saisie)) { player.sendMessage("§cUn hologramme nommé §f" + saisie + " §cexiste déjà."); return; }
                Location loc = player.getLocation().clone().add(0, 2.2, 0);
                createHologram(saisie, loc, "&f" + saisie);
                demasquer(saisie); // au cas où le nom était masqué
                player.sendMessage("§aHologramme §f" + saisie + " §acréé à ta position.");
                openHoloEdit(player, saisie);
            });
            return;
        }
        String name = holoInputName.get(player.getUniqueId());
        if (name == null) return;
        event.setCancelled(true);
        Integer lineIdx = holoInputLine.remove(player.getUniqueId());
        holoInputName.remove(player.getUniqueId());
        String msg = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            HoloData h = holograms.get(name);
            if (h == null) { player.sendMessage("§cHologramme introuvable."); return; }
            if (!msg.equalsIgnoreCase("annuler")) {
                if (lineIdx == null || lineIdx < 0) h.lines.add(msg);
                else if (lineIdx < h.lines.size()) h.lines.set(lineIdx, msg);
                refreshHologram(h); saveHolograms();
                player.sendMessage("§aHologramme mis à jour.");
            } else {
                player.sendMessage("§7Annulé.");
            }
            openHoloEdit(player, name);
        });
    }

    // ----- Page d'une ligne (modifier le texte / changer la couleur / supprimer) -----
    private static final String HOLO_LINE_TITLE = "§5§lÉditer la ligne";
    private final java.util.Map<java.util.UUID, Integer> holoEditingLine = new java.util.HashMap<>();
    private static final int[] HOLO_COLOR_SLOTS = {28,29,30,31,32,33,34,35, 37,38,39,40,41,42,43,44};
    private static final String[] HOLO_COLOR_CODES = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
    private static final Material[] HOLO_COLOR_MATS = {
        Material.BLACK_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL,
        Material.RED_WOOL, Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.LIGHT_GRAY_WOOL,
        Material.GRAY_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL, Material.CYAN_WOOL,
        Material.RED_WOOL, Material.MAGENTA_WOOL, Material.YELLOW_WOOL, Material.WHITE_WOOL
    };
    private static final String[] HOLO_COLOR_NAMES = {
        "Noir","Bleu foncé","Vert foncé","Cyan foncé","Rouge foncé","Violet","Or","Gris clair",
        "Gris foncé","Bleu","Vert","Aqua","Rouge","Rose","Jaune","Blanc"
    };
    // Formats (toggles) : gras, italique, souligné, barré, aléatoire.
    private static final int[]      HOLO_FMT_SLOTS = {19, 20, 21, 22, 23};
    private static final char[]     HOLO_FMT_CODES = {'l', 'o', 'n', 'm', 'k'};
    private static final String[]   HOLO_FMT_NAMES = {"&lGras", "&oItalique", "&nSouligné", "&mBarré", "&kAaAa &r&dAléatoire"};
    private static final Material[] HOLO_FMT_MATS  = {
        Material.ANVIL, Material.FEATHER, Material.STRING, Material.SHEARS, Material.ENDER_EYE
    };
    private static final int HOLO_RESET_SLOT = 24;

    private void openHoloLine(Player player, String name, int lineIndex) {
        holoEditing.put(player.getUniqueId(), name);
        holoEditingLine.put(player.getUniqueId(), lineIndex);
        Inventory menu = Bukkit.createInventory(null, 54, HOLO_LINE_TITLE);
        refreshHoloLine(player, menu, name, lineIndex);
        player.openInventory(menu);
    }

    private void refreshHoloLine(Player player, Inventory menu, String name, int lineIndex) {
        // Fond sombre + cadre haut/bas (look propre).
        ItemStack bg = plugin.makeFiller();
        for (int i = 0; i < 54; i++) menu.setItem(i, bg);
        ItemStack border = plugin.pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) menu.setItem(i, border);
        for (int i = 45; i < 54; i++) menu.setItem(i, border);

        HoloData h = holograms.get(name);
        if (h == null || lineIndex < 0 || lineIndex >= h.lines.size()) return;

        // Aperçu de la ligne (en-tête)
        ItemStack head = new ItemStack(Material.PAPER);
        ItemMeta hm = head.getItemMeta();
        String txt = holoColorize(h.lines.get(lineIndex));
        hm.setDisplayName(txt.isEmpty() ? "§7(ligne vide)" : txt);
        hm.setLore(java.util.Arrays.asList("§7Ligne " + (lineIndex + 1) + " de §f" + name, "§8Code : " + h.lines.get(lineIndex)));
        head.setItemMeta(hm);
        menu.setItem(4, head);

        // Actions principales
        menu.setItem(11, plugin.namedItem(Material.NAME_TAG, "§e✏ Modifier le texte", "§7Tape un nouveau texte dans le chat."));
        menu.setItem(15, plugin.namedItem(Material.BARRIER, "§c🗑 Supprimer la ligne", "§7Supprime cette ligne."));

        // Formats (toggles) : on indique activé/désactivé selon le style de début de ligne.
        String[] parsed = holoParseLeading(h.lines.get(lineIndex));
        String activeFmts = parsed[1];
        for (int i = 0; i < HOLO_FMT_SLOTS.length; i++) {
            boolean on = activeFmts.indexOf(HOLO_FMT_CODES[i]) >= 0;
            ItemStack it = new ItemStack(HOLO_FMT_MATS[i]);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(holoColorize(HOLO_FMT_NAMES[i]));
            m.setLore(java.util.Arrays.asList(on ? "§aActivé ✔" : "§7Désactivé", "§7Clic pour basculer."));
            if (on) {
                m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(m);
            menu.setItem(HOLO_FMT_SLOTS[i], it);
        }
        menu.setItem(HOLO_RESET_SLOT, plugin.namedItem(Material.WATER_BUCKET, "§f♻ Réinitialiser le style", "§7Retire couleur et formats de la ligne."));
        menu.setItem(25, plugin.namedItem(Material.PAINTING, "§d🎨 Couleur §7↓", "§7Choisis une couleur ci-dessous."));

        // Palette de 16 couleurs
        for (int i = 0; i < HOLO_COLOR_SLOTS.length; i++) {
            ItemStack c = new ItemStack(HOLO_COLOR_MATS[i]);
            ItemMeta cm = c.getItemMeta();
            cm.setDisplayName(holoColorize("&" + HOLO_COLOR_CODES[i] + "&l■ ") + "§f" + HOLO_COLOR_NAMES[i]);
            cm.setLore(java.util.Arrays.asList("§7Clic pour colorer toute la ligne."));
            c.setItemMeta(cm);
            menu.setItem(HOLO_COLOR_SLOTS[i], c);
        }

        menu.setItem(49, plugin.namedItem(Material.ARROW, "§7« Retour", null));
    }

    // Analyse les codes de début de ligne -> [couleur (""/0-f), formats (sous-ensemble de l,o,n,m,k), texte restant].
    private String[] holoParseLeading(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^((?:&[0-9a-fk-or])+)").matcher(line);
        String codes = m.find() ? m.group(1) : "";
        String remainder = line.substring(codes.length());
        String color = "";
        java.util.LinkedHashSet<Character> fmts = new java.util.LinkedHashSet<>();
        for (int i = 0; i + 1 < codes.length(); i += 2) {
            char c = Character.toLowerCase(codes.charAt(i + 1));
            if ("0123456789abcdef".indexOf(c) >= 0) { color = String.valueOf(c); fmts.clear(); } // une couleur réinitialise le format
            else if ("klmno".indexOf(c) >= 0) fmts.add(c);
            else if (c == 'r') { color = ""; fmts.clear(); }
        }
        StringBuilder fs = new StringBuilder();
        for (char f : new char[]{'l', 'o', 'n', 'm', 'k'}) if (fmts.contains(f)) fs.append(f);
        return new String[]{ color, fs.toString(), remainder };
    }

    private String holoRebuild(String color, String fmts, String remainder) {
        StringBuilder sb = new StringBuilder();
        if (!color.isEmpty()) sb.append("&").append(color);
        for (char f : fmts.toCharArray()) sb.append("&").append(f);
        sb.append(remainder);
        return sb.toString();
    }

    // Applique une couleur de base (conserve les formats).
    private String setLineColor(String line, String code) {
        String[] p = holoParseLeading(line);
        return holoRebuild(code, p[1], p[2]);
    }

    // Active/désactive un format de début de ligne (conserve la couleur).
    private String toggleLineFormat(String line, char fmt) {
        String[] p = holoParseLeading(line);
        String fmts = p[1];
        fmts = (fmts.indexOf(fmt) >= 0) ? fmts.replace(String.valueOf(fmt), "") : fmts + fmt;
        StringBuilder ord = new StringBuilder();
        for (char f : new char[]{'l', 'o', 'n', 'm', 'k'}) if (fmts.indexOf(f) >= 0) ord.append(f);
        return holoRebuild(p[0], ord.toString(), p[2]);
    }

    // Retire couleur ET formats de début de ligne.
    private String resetLineStyle(String line) {
        return holoParseLeading(line)[2];
    }

    @EventHandler
    public void onHoloLineClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!HOLO_LINE_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        String name = holoEditing.get(player.getUniqueId());
        Integer li = holoEditingLine.get(player.getUniqueId());
        if (name == null || li == null) return;
        HoloData h = holograms.get(name);
        if (h == null || li < 0 || li >= h.lines.size()) { openHoloEdit(player, name); return; }
        int slot = event.getRawSlot();

        if (slot == 11) { // modifier le texte
            holoInputName.put(player.getUniqueId(), name);
            holoInputLine.put(player.getUniqueId(), li);
            player.closeInventory();
            player.sendMessage("§eTape le nouveau texte de la ligne " + (li + 1) + " dans le chat §7(ou 'annuler'). §8Couleurs avec &.");
            return;
        }
        if (slot == 15) { // supprimer la ligne
            h.lines.remove((int) li);
            refreshHologram(h); saveHolograms();
            openHoloEdit(player, name);
            return;
        }
        if (slot == 49) { openHoloEdit(player, name); return; }

        if (slot == HOLO_RESET_SLOT) { // réinitialiser couleur + formats
            h.lines.set(li, resetLineStyle(h.lines.get(li)));
            refreshHologram(h); saveHolograms();
            refreshHoloLine(player, event.getInventory(), name, li);
            return;
        }
        // Formats (toggles)
        for (int i = 0; i < HOLO_FMT_SLOTS.length; i++) {
            if (slot == HOLO_FMT_SLOTS[i]) {
                h.lines.set(li, toggleLineFormat(h.lines.get(li), HOLO_FMT_CODES[i]));
                refreshHologram(h); saveHolograms();
                refreshHoloLine(player, event.getInventory(), name, li);
                return;
            }
        }
        // Couleurs
        for (int i = 0; i < HOLO_COLOR_SLOTS.length; i++) {
            if (slot == HOLO_COLOR_SLOTS[i]) {
                h.lines.set(li, setLineColor(h.lines.get(li), HOLO_COLOR_CODES[i]));
                refreshHologram(h); saveHolograms();
                refreshHoloLine(player, event.getInventory(), name, li);
                return;
            }
        }
    }
}
