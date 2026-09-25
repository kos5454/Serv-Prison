package fr.garfield.privatemines;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monnaie narrative « Fragments de Souvenir » (Acte IV — Ordinateur Quantique).
 *
 * <p>Chaque joueur possède un solde de Fragments en {@link BigInteger} (précision infinie,
 * même philosophie que l'économie maison {@link EconomyManager}). On les gagne en recyclant
 * les armures des anciens mineurs (les Oubliés) dans l'Ordinateur Quantique, et plus tard
 * en minant (drop rare). Ils serviront à améliorer les armures, débloquer le lore des Oubliés
 * et franchir des paliers de collection.</p>
 *
 * <p>Stockage : {@code fragments.yml}, section {@code fragments.<uuid>} = valeur BigInteger en texte.</p>
 */
public class FragmentManager {

    private final PrivateMines plugin;
    // Solde de Fragments de Souvenir de chaque joueur (BigInteger, précision infinie).
    private final Map<UUID, BigInteger> soldes = new ConcurrentHashMap<>();
    // Nombre total d'armures recyclées par joueur (compteur de collection, pour le palier « rangs rares »).
    private final Map<UUID, Integer> armuresRecyclees = new ConcurrentHashMap<>();
    // Tri auto : matières (noms d'enum ArmorManager.Matiere) que le joueur recycle automatiquement au drop.
    private final Map<UUID, java.util.Set<String>> autoRecycle = new ConcurrentHashMap<>();
    // Seuil de tri : on ne recycle auto que les armures ayant AU PLUS ce nombre de bonus (1..5). Défaut 1.
    private final Map<UUID, Integer> autoRecycleSeuil = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration config;

    public FragmentManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fragments.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    // ------------------------------------------------------------------ persistance

    private void load() {
        if (config.isConfigurationSection("fragments")) {
            for (String key : config.getConfigurationSection("fragments").getKeys(false)) {
                try {
                    soldes.put(UUID.fromString(key), new BigInteger(config.getString("fragments." + key, "0")));
                } catch (IllegalArgumentException ignored) { /* clé corrompue -> ignorée */ }
            }
        }
        if (config.isConfigurationSection("recyclees")) {
            for (String key : config.getConfigurationSection("recyclees").getKeys(false)) {
                try {
                    armuresRecyclees.put(UUID.fromString(key), config.getInt("recyclees." + key, 0));
                } catch (IllegalArgumentException ignored) { /* clé corrompue -> ignorée */ }
            }
        }
        if (config.isConfigurationSection("autoRecycle")) {
            for (String key : config.getConfigurationSection("autoRecycle").getKeys(false)) {
                try {
                    autoRecycle.put(UUID.fromString(key),
                            new java.util.HashSet<>(config.getStringList("autoRecycle." + key)));
                } catch (IllegalArgumentException ignored) { /* clé corrompue -> ignorée */ }
            }
        }
        if (config.isConfigurationSection("autoRecycleSeuil")) {
            for (String key : config.getConfigurationSection("autoRecycleSeuil").getKeys(false)) {
                try {
                    autoRecycleSeuil.put(UUID.fromString(key), config.getInt("autoRecycleSeuil." + key, 0));
                } catch (IllegalArgumentException ignored) { /* clé corrompue -> ignorée */ }
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, BigInteger> e : soldes.entrySet()) {
            config.set("fragments." + e.getKey(), e.getValue().toString());
        }
        for (Map.Entry<UUID, Integer> e : armuresRecyclees.entrySet()) {
            config.set("recyclees." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, java.util.Set<String>> e : autoRecycle.entrySet()) {
            config.set("autoRecycle." + e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        for (Map.Entry<UUID, Integer> e : autoRecycleSeuil.entrySet()) {
            config.set("autoRecycleSeuil." + e.getKey(), e.getValue());
        }
        try {
            config.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Impossible de sauvegarder fragments.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ solde de Fragments

    /** Solde de Fragments de Souvenir d'un joueur (BigInteger, exact). */
    public BigInteger getFragments(UUID id) {
        return soldes.getOrDefault(id, BigInteger.ZERO);
    }

    public BigInteger getFragments(Player p) {
        return getFragments(p.getUniqueId());
    }

    /** Ajoute des Fragments (peut être négatif). Le solde ne descend jamais sous zéro. */
    public void addFragments(UUID id, BigInteger amount) {
        BigInteger nouveau = getFragments(id).add(amount);
        if (nouveau.signum() < 0) nouveau = BigInteger.ZERO;
        soldes.put(id, nouveau);
    }

    public void addFragments(Player p, long amount) {
        addFragments(p.getUniqueId(), BigInteger.valueOf(amount));
    }

    /** Vrai si le joueur possède au moins {@code amount} Fragments. */
    public boolean has(UUID id, BigInteger amount) {
        return getFragments(id).compareTo(amount) >= 0;
    }

    /** Retire des Fragments si le solde le permet ; renvoie {@code false} sinon (rien retiré). */
    public boolean withdraw(UUID id, BigInteger amount) {
        if (amount.signum() <= 0) return true;
        if (!has(id, amount)) return false;
        soldes.put(id, getFragments(id).subtract(amount));
        return true;
    }

    /** Fixe le solde EXACT de Fragments (outil d'admin). */
    public void setFragments(UUID id, BigInteger amount) {
        if (amount.signum() < 0) amount = BigInteger.ZERO;
        soldes.put(id, amount);
    }

    // ------------------------------------------------------------------ compteur de collection

    /** Nombre total d'armures recyclées par ce joueur (pour le palier « rangs rares »). */
    public int getArmuresRecyclees(UUID id) {
        return armuresRecyclees.getOrDefault(id, 0);
    }

    public int getArmuresRecyclees(Player p) {
        return getArmuresRecyclees(p.getUniqueId());
    }

    /** Incrémente le compteur d'armures recyclées de {@code n}. */
    public void addArmuresRecyclees(UUID id, int n) {
        armuresRecyclees.merge(id, n, Integer::sum);
    }

    /**
     * Remet l'Ordinateur Quantique de ce joueur à zéro : compteur d'armures recyclées et réglages
     * de tri auto (matières cochées + seuil).
     *
     * <p>⚠ Le <b>solde de Fragments n'est PAS touché</b> — choix explicite du user : c'est une
     * monnaie, pas de la progression. Pour l'effacer aussi : {@link #setFragments}.</p>
     *
     * <p>⚠ On écrit des valeurs par DÉFAUT au lieu de retirer les clés des maps : {@link #save()}
     * ne fait que réécrire les entrées présentes en mémoire, il n'efface jamais une clé de
     * fragments.yml. Un simple {@code remove()} laisserait l'ancienne valeur dans le fichier et
     * elle reviendrait au redémarrage.</p>
     */
    public void resetOrdinateur(UUID id) {
        armuresRecyclees.put(id, 0);
        autoRecycle.put(id, new java.util.HashSet<>());
        autoRecycleSeuil.put(id, 0);
    }

    // ------------------------------------------------------------------ tri auto (recyclage au drop)

    /** Vrai si le joueur recycle automatiquement les armures de cette matière (nom d'enum). */
    public boolean isAutoRecycle(UUID id, String matiere) {
        java.util.Set<String> set = autoRecycle.get(id);
        return set != null && set.contains(matiere);
    }

    /** Active/désactive le recyclage auto d'une matière ; renvoie le nouvel état (true = actif). */
    public boolean toggleAutoRecycle(UUID id, String matiere) {
        java.util.Set<String> set = autoRecycle.computeIfAbsent(id, k -> new java.util.HashSet<>());
        if (set.contains(matiere)) { set.remove(matiere); return false; }
        set.add(matiere); return true;
    }

    /**
     * Seuil de tri : on recycle auto les armures ayant AU PLUS ce nombre de bonus.
     * Bornes 0..6 (0 = seuil désactivé : le seuil ne recycle rien ; 6 = jusqu'à la Netherite). Défaut 0.
     */
    public int getAutoRecycleSeuil(UUID id) {
        return autoRecycleSeuil.getOrDefault(id, 0);
    }

    public static final int SEUIL_MIN = 0;
    public static final int SEUIL_MAX = 6;

    /** Modifie le seuil de {@code delta} (borné 0..6) et renvoie la nouvelle valeur. */
    public int changeAutoRecycleSeuil(UUID id, int delta) {
        int s = getAutoRecycleSeuil(id) + delta;
        if (s < SEUIL_MIN) s = SEUIL_MIN;
        if (s > SEUIL_MAX) s = SEUIL_MAX;
        autoRecycleSeuil.put(id, s);
        return s;
    }
}
