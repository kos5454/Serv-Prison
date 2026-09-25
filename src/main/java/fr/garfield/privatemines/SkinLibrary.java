package fr.garfield.privatemines;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Bibliothèque de skins nommés : plugins/PrivateMines/skins.yml.
 *
 * <p>Chaque skin = un nom (ex "veilleur") + value + signature (données MineSkin/Mojang).
 * On les enregistre UNE fois ici, puis on les applique à n'importe quel PNJ depuis
 * le menu /pnj setting → Skin (aucun collage à refaire).
 *
 * <p>Au premier démarrage, un skin par défaut "veilleur" est pré-inscrit avec les
 * valeurs fournies par l'admin (skin spifftopia9).
 */
public class SkinLibrary {

    private final PrivateMines plugin;
    private final File file;
    private FileConfiguration cfg;

    // Skin par défaut du Veilleur (fourni par l'admin — skin spifftopia9, modèle slim).
    private static final String VEILLEUR_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc4MjYyMjAzMzk1NywKICAicHJvZmlsZUlkIiA6ICI0MjJlMzdiNjc4ZGU0OTA2YWFiMWU5NGY0N2E5NGM0OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGlmZnRvcGlhOSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kZWYwZjE1OTgwNzQ2NTcyYzI5YTJkZjM4Y2MxZGViYTFiZjg5NjJhOWRiZjAyODkwNjBiMDI1NTczMDNkODY0IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
    private static final String VEILLEUR_SIGNATURE =
            "o7Toe6nd7J1MlW9w8GaZElFlrgZJCIzlHTGU/+dqon6VZGNpJsqCWc4r3G8cB70Ez8uc1BmfwrS0HZ4dMOoZ/cOGzz+y3kGBLz2shp7mtBV7FWnOPkY+P7Pn0skQJUonU8x4T9WBw0AlqcQwhnpLNoNBgtbNg9/loWB7OGzJE0++4g97sVK2Txgnj1iwSJ0hZsxJUVJeZxcBDR2ycLsVUyy9A7CB12wpXi35CiyebBH6NwvW78f7wAknlTbupmCX41tf+YOqm84Dn9ex2tU+CIksJUB0iRHg8cv1kmauxb9pgFti0AqKwgE56uaZGoTzoe2j/Qr6NLsy0lBPcYrXlWqjLwwUYVRXv0eL0jFsnU5bWxDji9SSs2I1wE7kBpC8pGra7ByHHctx1ona6oaQXLW2ZvRKmP20HzcqqGcdfVjQNIxSAo3yQRK26Ii6gtc/Kbi51AJge86d66wvkJ6Wg8H5o2yVNWBedSV044Fq7YVWK7i42jQ9WRzmYEIEn4o5y/lfes33B0NDac4iHPn+mCPua3hEpw+1WvBySANPviCKpxWCQJ4hUbfSssiBvy+gPNnfi9iPhD/pvT1l13OEaD3wMQ51SmuieXTpMqe/kNwnnpl3ncPZOszCCnHl0spThNrzV/9sWjUIVKJjhaRU3lwid3f5ubIoRsXNRyfPS3A=";

    public SkinLibrary(PrivateMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "skins.yml");
        reload();
    }

    public void reload() {
        boolean freshFile = !file.exists();
        if (freshFile) {
            plugin.getDataFolder().mkdirs();
            cfg = new YamlConfiguration();
        } else {
            cfg = YamlConfiguration.loadConfiguration(file);
        }
        // Pré-inscrit le skin du Veilleur s'il n'existe pas encore.
        if (!cfg.contains("skins.veilleur")) {
            cfg.set("skins.veilleur.value", VEILLEUR_VALUE);
            cfg.set("skins.veilleur.signature", VEILLEUR_SIGNATURE);
            save();
        }
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder skins.yml : " + e.getMessage());
        }
    }

    /** Noms de tous les skins enregistrés (ex "veilleur"). */
    public Set<String> getNames() {
        ConfigurationSection sec = cfg.getConfigurationSection("skins");
        if (sec == null) return Collections.emptySet();
        return sec.getKeys(false);
    }

    public boolean has(String name) { return cfg.contains("skins." + name); }

    public String getValue(String name)     { return cfg.getString("skins." + name + ".value", ""); }
    public String getSignature(String name) { return cfg.getString("skins." + name + ".signature", ""); }

    /** Enregistre (ou remplace) un skin nommé dans la bibliothèque. */
    public void put(String name, String value, String signature) {
        cfg.set("skins." + name + ".value", value);
        cfg.set("skins." + name + ".signature", signature);
        save();
    }

    public void remove(String name) {
        cfg.set("skins." + name, null);
        save();
    }
}
