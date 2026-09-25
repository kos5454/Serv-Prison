package fr.garfield.privatemines;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Économie MAISON en BigInteger (précision infinie) qui remplace Essentials/Vault.
 *
 * <p>Implémente l'interface Vault {@link Economy} pour que TOUT le code existant du plugin
 * (economy.getBalance / depositPlayer / withdrawPlayer / has / format) continue de marcher
 * sans modification. On l'enregistre comme provider Vault prioritaire au démarrage.</p>
 *
 * <p>Unité de stockage interne = CENTIMES (BigInteger). 1,00$ = 100 centimes. Cela garde les
 * 2 décimales tout en restant en entiers exacts, sans aucune limite (max 999ZZ et bien au-delà).</p>
 *
 * <p>Les méthodes de l'interface Vault renvoient/prennent des {@code double} (contrainte de Vault) :
 * au-delà de ~9 quadrillions un double perd en précision, mais le STOCKAGE reste exact. Pour un
 * affichage exact très haut, utiliser {@link #getBalanceBig(UUID)} + {@link PrivateMines#formatBig}.</p>
 */
public class EconomyManager implements Economy {

    private final PrivateMines plugin;
    // Solde de chaque joueur, en CENTIMES (BigInteger, précision infinie).
    private final Map<UUID, BigInteger> soldes = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration config;

    private static final BigInteger CENT = BigInteger.valueOf(100);

    public EconomyManager(PrivateMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    // ------------------------------------------------------------------ persistance

    private void load() {
        if (config.isConfigurationSection("soldes")) {
            for (String key : config.getConfigurationSection("soldes").getKeys(false)) {
                try {
                    soldes.put(UUID.fromString(key), new BigInteger(config.getString("soldes." + key, "0")));
                } catch (IllegalArgumentException ignored) { /* clé corrompue -> on ignore */ }
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, BigInteger> e : soldes.entrySet()) {
            config.set("soldes." + e.getKey(), e.getValue().toString());
        }
        try {
            config.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Impossible de sauvegarder economy.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ accès BigInteger (exact)

    /** Solde en centimes (BigInteger, exact). */
    public BigInteger getBalanceCents(UUID id) {
        return soldes.getOrDefault(id, BigInteger.ZERO);
    }

    /** Solde en unités entières (dollars, BigInteger exact, sans les centimes). */
    public BigInteger getBalanceBig(UUID id) {
        return getBalanceCents(id).divide(CENT);
    }

    /** Ajoute un montant en centimes (peut être négatif). */
    private void addCents(UUID id, BigInteger cents) {
        soldes.merge(id, cents, BigInteger::add);
    }

    /** Fixe le solde EXACT d'un joueur, en dollars entiers (BigInteger). Utilisé par /money set. */
    public void setBalanceBig(UUID id, BigInteger dollars) {
        if (dollars.signum() < 0) dollars = BigInteger.ZERO;
        soldes.put(id, dollars.multiply(CENT));
    }

    /**
     * Parse un montant tapé par un joueur : nombre brut ("1000000"), décimal ("1.5"),
     * ou avec suffixe court ("1.5K", "2.3M", "10T", "999ZZ"...). Renvoie des DOLLARS entiers
     * (BigInteger) ou {@code null} si la saisie est invalide. Insensible à la casse pour K/M/B/T.
     */
    public static BigInteger parseMontant(String saisie) {
        if (saisie == null || saisie.isEmpty()) return null;
        String s = saisie.trim().replace(",", ".").replace("$", "");
        // Sépare la partie chiffres (+ point) de la partie suffixe (lettres).
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        String nombre = s.substring(0, i);
        String suffixe = s.substring(i).trim();
        if (nombre.isEmpty()) return null;
        java.math.BigDecimal base;
        try {
            base = new java.math.BigDecimal(nombre);
        } catch (NumberFormatException e) {
            return null;
        }
        int cran = cranDepuisSuffixe(suffixe);
        if (cran < 0) return null;
        // valeur = base * 1000^cran
        java.math.BigDecimal mult = java.math.BigDecimal.TEN.pow(3 * cran);
        return base.multiply(mult).toBigInteger();
    }

    /** Renvoie le cran (puissance de 1000) associé à un suffixe : ""=0, K=1, M=2, B=3, T=4, AA=5, AB=6... ZZ. -1 si invalide. */
    private static int cranDepuisSuffixe(String suf) {
        if (suf == null || suf.isEmpty()) return 0;
        String u = suf.toUpperCase(java.util.Locale.ROOT);
        switch (u) {
            case "K": return 1;
            case "M": return 2;
            case "B": return 3;
            case "T": return 4;
            default:
                // 2 lettres A-Z -> AA=5, AB=6, ... ZZ.
                if (u.length() == 2 && u.charAt(0) >= 'A' && u.charAt(0) <= 'Z'
                        && u.charAt(1) >= 'A' && u.charAt(1) <= 'Z') {
                    int n = (u.charAt(0) - 'A') * 26 + (u.charAt(1) - 'A');
                    return 5 + n;
                }
                return -1;
        }
    }

    /** Convertit un double dollars -> centimes BigInteger (arrondi au centime). */
    private static BigInteger toCents(double dollars) {
        if (dollars <= 0) return BigInteger.ZERO;
        return BigDecimal.valueOf(dollars).multiply(BigDecimal.valueOf(100)).toBigInteger();
    }

    /** Convertit des centimes BigInteger -> double dollars (perd en précision au-delà de ~9Qa). */
    private static double toDollars(BigInteger cents) {
        return new BigDecimal(cents).divide(BigDecimal.valueOf(100)).doubleValue();
    }

    // ------------------------------------------------------------------ interface Vault : soldes

    @Override public double getBalance(OfflinePlayer player) {
        return toDollars(getBalanceCents(player.getUniqueId()));
    }
    @Override public double getBalance(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return getBalance(p);
    }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(OfflinePlayer player, double amount) {
        return getBalanceCents(player.getUniqueId()).compareTo(toCents(amount)) >= 0;
    }
    @Override public boolean has(String playerName, double amount) { return has(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }

    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Montant négatif");
        addCents(player.getUniqueId(), toCents(amount));
        return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String world, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return depositPlayer(player, amount); }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Montant négatif");
        BigInteger cost = toCents(amount);
        BigInteger bal = getBalanceCents(player.getUniqueId());
        if (bal.compareTo(cost) < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Fonds insuffisants");
        }
        addCents(player.getUniqueId(), cost.negate());
        return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String world, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdrawPlayer(player, amount); }

    // ------------------------------------------------------------------ interface Vault : comptes

    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(String playerName) { return true; }
    @Override public boolean hasAccount(String playerName, String world) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return true; }

    @Override public boolean createPlayerAccount(OfflinePlayer player) {
        soldes.putIfAbsent(player.getUniqueId(), BigInteger.ZERO);
        return true;
    }
    @Override public boolean createPlayerAccount(String playerName) { return createPlayerAccount(Bukkit.getOfflinePlayer(playerName)); }
    @Override public boolean createPlayerAccount(String playerName, String world) { return createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return createPlayerAccount(player); }

    // ------------------------------------------------------------------ interface Vault : format & méta

    @Override public String format(double amount) {
        return PrivateMines.formatNumber(amount) + "$";
    }
    @Override public String currencyNamePlural() { return "$"; }
    @Override public String currencyNameSingular() { return "$"; }
    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "PrivateMinesEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }

    // ------------------------------------------------------------------ interface Vault : banques (non supporté)

    @Override public EconomyResponse createBank(String name, String player) { return unsupported(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse deleteBank(String name) { return unsupported(); }
    @Override public EconomyResponse bankBalance(String name) { return unsupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return unsupported(); }
    @Override public java.util.List<String> getBanks() { return java.util.Collections.emptyList(); }

    private EconomyResponse unsupported() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banques non supportées");
    }
}
