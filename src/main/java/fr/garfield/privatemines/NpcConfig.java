package fr.garfield.privatemines;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Gère le fichier de paramètres des PNJ : plugins/PrivateMines/npcs.yml.
 *
 * <p>Modèle : chaque PNJ a un ID auto (pnj1, pnj2...) et des paramètres éditables à la main
 * ou via le menu /pnj setting : rôle (veilleur/ancre/none), nom, couleur, position, slim, skin.
 * Rechargé à chaud via {@code /pnj reload}.
 */
public class NpcConfig {

    private final PrivateMines plugin;
    private final File file;
    private FileConfiguration cfg;

    public NpcConfig(PrivateMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            cfg = new YamlConfiguration();
            save(); // crée un fichier vide (aucun PNJ tant qu'on n'en place pas)
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder npcs.yml : " + e.getMessage());
        }
    }

    public FileConfiguration raw() { return cfg; }

    /** Les IDs de PNJ définis (pnj1, pnj2...). */
    public java.util.Set<String> getIds() {
        org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection("npcs");
        if (sec == null) return java.util.Collections.emptySet();
        return sec.getKeys(false);
    }

    public boolean has(String id) { return cfg.contains("npcs." + id); }

    /** Trouve un ID libre (pnj1, pnj2, ...). */
    public String nextId() {
        int n = 1;
        while (has("pnj" + n)) n++;
        return "pnj" + n;
    }

    // ----- Lecture -----
    public String getRole(String id)   { return cfg.getString("npcs." + id + ".role", "none"); }
    public String getName(String id)   { return cfg.getString("npcs." + id + ".name", "PNJ"); }
    public String getColor(String id)  { return cfg.getString("npcs." + id + ".color", "f"); }
    public boolean isBold(String id)   { return cfg.getBoolean("npcs." + id + ".bold", true); }
    public String getWorld(String id)  { return cfg.getString("npcs." + id + ".world", "world"); }
    public double getX(String id)      { return cfg.getDouble("npcs." + id + ".x"); }
    public double getY(String id)      { return cfg.getDouble("npcs." + id + ".y"); }
    public double getZ(String id)      { return cfg.getDouble("npcs." + id + ".z"); }
    public float  getYaw(String id)    { return (float) cfg.getDouble("npcs." + id + ".yaw"); }
    public float  getPitch(String id)  { return (float) cfg.getDouble("npcs." + id + ".pitch"); }
    public boolean isSlim(String id)   { return cfg.getBoolean("npcs." + id + ".slim", false); }
    public String getSkinValue(String id)     { return cfg.getString("npcs." + id + ".skin.value", ""); }
    public String getSkinSignature(String id) { return cfg.getString("npcs." + id + ".skin.signature", ""); }

    /** Nom formaté avec couleur + gras (ex "§b§lLe Veilleur"). */
    public String getDisplayName(String id) {
        String s = "§" + getColor(id);
        if (isBold(id)) s += "§l";
        return s + getName(id);
    }

    // ----- Écriture -----
    public void setRole(String id, String role)   { cfg.set("npcs." + id + ".role", role); }
    public void setName(String id, String name)   { cfg.set("npcs." + id + ".name", name); }
    public void setColor(String id, String color) { cfg.set("npcs." + id + ".color", color); }
    public void setBold(String id, boolean bold)  { cfg.set("npcs." + id + ".bold", bold); }
    public void setSlim(String id, boolean slim)  { cfg.set("npcs." + id + ".slim", slim); }
    public void setSkin(String id, String value, String signature) {
        cfg.set("npcs." + id + ".skin.value", value);
        cfg.set("npcs." + id + ".skin.signature", signature);
    }
    public void setPosition(String id, org.bukkit.Location loc) {
        String p = "npcs." + id;
        cfg.set(p + ".world", loc.getWorld().getName());
        cfg.set(p + ".x", loc.getX());
        cfg.set(p + ".y", loc.getY());
        cfg.set(p + ".z", loc.getZ());
        cfg.set(p + ".yaw", (double) loc.getYaw());
        cfg.set(p + ".pitch", (double) loc.getPitch());
    }
    public void remove(String id) { cfg.set("npcs." + id, null); }

    /** Crée une entrée PNJ avec des valeurs par défaut (rôle none, nom générique). */
    public void createDefault(String id, org.bukkit.Location loc) {
        setRole(id, "none");
        setName(id, "Nouveau PNJ");
        setColor(id, "f");
        setBold(id, true);
        setSlim(id, false);
        setSkin(id, "", "");
        setPosition(id, loc);
    }
}
