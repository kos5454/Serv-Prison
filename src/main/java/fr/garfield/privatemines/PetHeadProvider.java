package fr.garfield.privatemines;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fournit les têtes de mobs (pets & arcs) via le plugin HeadDB — SANS dépendance de compilation.
 *
 * <p>On appelle l'API de HeadDB par <b>réflexion</b> (le plugin est chargé au runtime) :
 * <ul>
 *   <li>{@code HeadDB.getHeadApi()} → objet {@code HeadAPI}</li>
 *   <li>{@code HeadAPI.findById(int)} → {@code CompletableFuture<Optional<Head>>} (async)</li>
 *   <li>{@code Head.getItem()} → l'{@link ItemStack} de la tête</li>
 * </ul>
 *
 * <p><b>Stratégie :</b> au démarrage (quand HeadDB est prêt), on précharge toutes les têtes dont on a
 * besoin dans un cache. Le menu /pets lit ensuite le cache → ouverture instantanée, aucune latence.
 * Si HeadDB est absent ou pas encore prêt, on renvoie une tête vanilla de secours (fallback).
 */
public class PetHeadProvider {

    private final PrivateMines plugin;

    // Cache : id HeadDB -> ItemStack de la tête (déjà résolue).
    private final ConcurrentHashMap<Integer, ItemStack> cache = new ConcurrentHashMap<>();

    // Objet HeadAPI de HeadDB (résolu par réflexion), null si HeadDB indisponible.
    private Object headApi;
    private Method findByIdMethod; // HeadAPI#findById(int)
    private Method getItemMethod;  // Head#getItem()

    private boolean available = false;

    public PetHeadProvider(PrivateMines plugin) {
        this.plugin = plugin;
    }

    /**
     * À appeler une fois au démarrage (après que les plugins soient chargés).
     * Résout l'API HeadDB par réflexion puis précharge les têtes fournies.
     */
    public void init(int... idsToPreload) {
        try {
            Plugin hdb = Bukkit.getPluginManager().getPlugin("HeadDB");
            if (hdb == null || !hdb.isEnabled()) {
                plugin.getLogger().warning("[Pets] HeadDB introuvable ou désactivé : têtes vanilla de secours utilisées.");
                return;
            }

            // hdb.getHeadApi()  (méthode d'instance de la classe principale de HeadDB)
            Method getHeadApi = hdb.getClass().getMethod("getHeadApi");
            this.headApi = getHeadApi.invoke(hdb);
            if (headApi == null) {
                plugin.getLogger().warning("[Pets] getHeadApi() a renvoyé null : têtes vanilla de secours utilisées.");
                return;
            }

            // HeadAPI#findById(int) -> CompletableFuture<Optional<Head>>
            this.findByIdMethod = headApi.getClass().getMethod("findById", int.class);

            this.available = true;
            plugin.getLogger().info("[Pets] API HeadDB connectée. Préchargement de " + idsToPreload.length + " têtes...");

            // Précharge chaque tête (async, sans bloquer le thread principal).
            for (int id : idsToPreload) preload(id);

        } catch (Throwable t) {
            plugin.getLogger().warning("[Pets] Impossible de brancher HeadDB (" + t.getClass().getSimpleName()
                    + ": " + t.getMessage() + "). Têtes vanilla de secours utilisées.");
            this.available = false;
        }
    }

    /**
     * Lance la résolution async d'un id de tête et la stocke dans le cache une fois prête.
     */
    @SuppressWarnings("unchecked")
    private void preload(int id) {
        if (!available) return;
        try {
            Object future = findByIdMethod.invoke(headApi, id); // CompletableFuture<Optional<Head>>
            if (!(future instanceof CompletableFuture)) return;

            ((CompletableFuture<Object>) future).thenAccept(optionalHead -> {
                try {
                    if (!(optionalHead instanceof Optional)) return;
                    Optional<Object> opt = (Optional<Object>) optionalHead;
                    if (opt.isEmpty()) {
                        plugin.getLogger().warning("[Pets] Tête HeadDB #" + id + " introuvable (id inconnu ?).");
                        return;
                    }
                    Object head = opt.get();
                    // Head#getItem() -> ItemStack. On résout la méthode une seule fois.
                    if (getItemMethod == null) getItemMethod = head.getClass().getMethod("getItem");
                    Object item = getItemMethod.invoke(head);
                    if (item instanceof ItemStack) {
                        cache.put(id, ((ItemStack) item).clone());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[Pets] Erreur résolution tête #" + id + " : " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[Pets] Erreur preload tête #" + id + " : " + t.getMessage());
        }
    }

    /**
     * Renvoie une COPIE de la tête HeadDB (prête à personnaliser nom/lore).
     * Si la tête n'est pas (encore) en cache ou si HeadDB est indisponible,
     * renvoie une tête vanilla de secours (PLAYER_HEAD nue).
     */
    public ItemStack getHead(int id) {
        ItemStack cached = cache.get(id);
        if (cached != null) return cached.clone();
        // Fallback : tête de joueur nue (le menu reste utilisable même sans HeadDB).
        return new ItemStack(Material.PLAYER_HEAD);
    }

    /** true si l'API HeadDB a été branchée avec succès. */
    public boolean isAvailable() {
        return available;
    }

    /** true si la tête HeadDB est RÉELLEMENT résolue en cache (pas le fallback nu). */
    public boolean isCached(int id) {
        return cache.containsKey(id);
    }
}
