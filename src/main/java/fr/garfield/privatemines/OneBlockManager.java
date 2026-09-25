package fr.garfield.privatemines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OneBlock de parcelle — « Le Bloc du Grand Appel ».
 *
 * Un unique bloc SOLIDE au centre-est de la parcelle (centerX+2, floorY+1, centerZ) que le
 * joueur casse à l'infini. Le bloc évolue par PHASES (1..12 = 12 biomes) selon le nombre de
 * blocs cassés ; après la phase 12, le CYCLE recommence à la phase 1 (cycle +1).
 *
 * Cette brique (n°1) pose : la table des 12 phases (bloc central + palier), la position du
 * bloc, et l'accès à l'état stocké dans la {@link Parcelle} (phase / blocs / cycle).
 *
 * Design complet : « Serv prison/ONEBLOCK_PARCELLE.md ».
 */
public class OneBlockManager {

    private final PrivateMines plugin;

    public OneBlockManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    // Nombre de phases (biomes) dans un cycle.
    public static final int PHASES = 12;

    // Le bloc central est PILE au centre de la parcelle, au niveau du SOL (floorY) :
    // le joueur qui fait /parcelle spawn à floorY+1, donc le bloc est directement sous ses pieds.

    /**
     * Une phase = un biome : son nom, le bloc central affiché (SOLIDE), et le palier
     * (nombre de blocs à casser pour passer à la phase suivante).
     */
    public static final class Phase {
        public final int      num;      // 1..12
        public final String   nom;      // nom d'ambiance (biome)
        public final Material bloc;     // bloc central affiché (solide)
        public final int      palier;   // blocs à casser dans cette phase

        Phase(int num, String nom, Material bloc, int palier) {
            this.num = num; this.nom = nom; this.bloc = bloc; this.palier = palier;
        }
    }

    // Table des 12 phases. Bloc central = un bloc SOLIDE représentatif du biome.
    // Paliers = courbe ~500 (voir §4 du doc). Palier phase 12 = fin de cycle.
    private static final Phase[] TABLE = {
        new Phase(1,  "🌾 Plaines",              Material.GRASS_BLOCK,        400),
        new Phase(2,  "🌸 Prairie",              Material.HAY_BLOCK,          400),
        new Phase(3,  "🌲 Forêt",                Material.OAK_LOG,            450),
        new Phase(4,  "🏜️ Désert",               Material.SAND,               450),
        new Phase(5,  "🌑 Forêt de chênes noirs",Material.DARK_OAK_LOG,       500),
        new Phase(6,  "🌴 Jungle",               Material.JUNGLE_LOG,         500),
        new Phase(7,  "🪴 Grottes luxuriantes",  Material.MOSS_BLOCK,          550),
        new Phase(8,  "🐸 Marais",               Material.MUD,                550),
        new Phase(9,  "🌿 Mangrove",             Material.MANGROVE_LOG,       600),
        new Phase(10, "🏔️ Pics enneigés",        Material.SNOW_BLOCK,         600),
        new Phase(11, "🌊 Océan corallien",      Material.PRISMARINE,         650),
        new Phase(12, "🌸 Forêt de cerisiers",   Material.CHERRY_LOG,         700),
    };

    // Renvoie la définition d'une phase (num 1..12). Clamp de sécurité.
    public Phase phase(int num) {
        if (num < 1) num = 1;
        if (num > PHASES) num = PHASES;
        return TABLE[num - 1];
    }

    // ===== Composition d'une phase (brique 7) =====
    // À chaque réapparition, le bloc central est TIRÉ AU HASARD parmi les blocs pleins du biome
    // selon un poids (%). Le drop reste VANILLA (getDrops) : grass -> terre, minerai -> gemme brute…
    // Seule la phase 1 est remplie pour l'instant ; les autres se remplissent au fil des briques.

    // Un bloc possible du bloc central + son poids relatif (sur ~100).
    private static final class Compo {
        final Material bloc; final int poids;
        Compo(Material bloc, int poids) { this.bloc = bloc; this.poids = poids; }
    }

    // Composition par phase (index 0 = phase 1). null = pas encore définie -> on retombe sur ph.bloc.
    private static final Compo[][] COMPO = new Compo[PHASES][];
    static {
        // Phase 1 — 🌾 Plaines (total 100 ; pierre à 15 %).
        COMPO[0] = new Compo[] {
            new Compo(Material.GRASS_BLOCK, 30),
            new Compo(Material.OAK_LOG,     24),
            new Compo(Material.DIRT,        15),
            new Compo(Material.STONE,       15),
            new Compo(Material.OAK_LEAVES,  10),
            new Compo(Material.PUMPKIN,      6),
        };
        // Phase 2 — 🌸 Prairie (total 100 ; pierre 10 + fer 12, foin réduit).
        COMPO[1] = new Compo[] {
            new Compo(Material.GRASS_BLOCK,  28),
            new Compo(Material.BIRCH_LOG,    24),
            new Compo(Material.OAK_LOG,      15),
            new Compo(Material.IRON_ORE,     12),
            new Compo(Material.STONE,        10),
            new Compo(Material.BIRCH_LEAVES,  6),
            new Compo(Material.HAY_BLOCK,     5),
        };
        // Phase 3 — 🌲 Forêt (total 100 ; fer 12 + pierre 10). Bibliothèques -> coffre.
        COMPO[2] = new Compo[] {
            new Compo(Material.OAK_LOG,      26),
            new Compo(Material.BIRCH_LOG,    18),
            new Compo(Material.OAK_LEAVES,   12),
            new Compo(Material.GRASS_BLOCK,  12),
            new Compo(Material.IRON_ORE,     12),
            new Compo(Material.MOSS_BLOCK,   10),
            new Compo(Material.STONE,        10),
        };
        // Phase 4 — 🏜️ Désert (total 100 ; fer 8 + pierre 10). Cactus -> coffre.
        COMPO[3] = new Compo[] {
            new Compo(Material.SAND,           30),
            new Compo(Material.SANDSTONE,      30),
            new Compo(Material.TERRACOTTA,     12),
            new Compo(Material.STONE,          10),
            new Compo(Material.CHISELED_SANDSTONE, 8),
            new Compo(Material.IRON_ORE,        8),
            new Compo(Material.SMOOTH_SANDSTONE, 2),
        };
        // Phase 5 — 🌑 Forêt de chênes noirs (total 100 ; lapis 7 + pierre 12, pas de fer).
        COMPO[4] = new Compo[] {
            new Compo(Material.DARK_OAK_LOG,          30),
            new Compo(Material.MOSS_BLOCK,            15),
            new Compo(Material.BROWN_MUSHROOM_BLOCK,  12),
            new Compo(Material.STONE,                 12),
            new Compo(Material.RED_MUSHROOM_BLOCK,    10),
            new Compo(Material.MUSHROOM_STEM,          8),
            new Compo(Material.LAPIS_ORE,              7),
            new Compo(Material.DARK_OAK_LEAVES,        6),
        };
        // Phase 6 — 🌴 Jungle (total 100 ; redstone 8 + pierre 12). Bambou (pousse) -> coffre.
        COMPO[5] = new Compo[] {
            new Compo(Material.JUNGLE_LOG,     32),
            new Compo(Material.MELON,          16),
            new Compo(Material.MOSS_BLOCK,     16),
            new Compo(Material.STONE,          12),
            new Compo(Material.GRASS_BLOCK,    10),
            new Compo(Material.REDSTONE_ORE,    8),
            new Compo(Material.JUNGLE_LEAVES,   6),
        };
        // Phase 7 — 🪴 Grottes luxuriantes (total 100 ; cuivre 8 + pierre 14). Non-solides -> coffre.
        COMPO[6] = new Compo[] {
            new Compo(Material.MOSS_BLOCK,             28),
            new Compo(Material.CLAY,                   18),
            new Compo(Material.FLOWERING_AZALEA_LEAVES, 14),
            new Compo(Material.STONE,                  14),
            new Compo(Material.DIRT,                   10),
            new Compo(Material.AZALEA_LEAVES,           8),
            new Compo(Material.COPPER_ORE,              8),
        };
        // 8 — 🐸 Marais (Swamp) : boue, argile, chênes, champignons, minerai ~8%.
        COMPO[7] = new Compo[] {
            new Compo(Material.MUD,          26),
            new Compo(Material.OAK_LOG,      18),
            new Compo(Material.CLAY,         16),
            new Compo(Material.MOSS_BLOCK,   12),
            new Compo(Material.STONE,        12),
            new Compo(Material.OAK_LEAVES,    8),
            new Compo(Material.LAPIS_ORE,     8),
        };
        // 9 — 🌿 Mangrove : palétuvier, racines, boue, mousse, argile, minerai ~8%.
        COMPO[8] = new Compo[] {
            new Compo(Material.MANGROVE_LOG,    24),
            new Compo(Material.MANGROVE_ROOTS,  18),
            new Compo(Material.MUD,             16),
            new Compo(Material.MOSS_BLOCK,      12),
            new Compo(Material.MANGROVE_LEAVES, 12),
            new Compo(Material.CLAY,            10),
            new Compo(Material.GOLD_ORE,         8),
        };
        // 10 — 🏔️ Pics enneigés : roche (pierre/calcaire/ardoise), neige & glace, émeraude rare ~4% + fer.
        COMPO[9] = new Compo[] {
            new Compo(Material.STONE,        22),
            new Compo(Material.SNOW_BLOCK,   16),
            new Compo(Material.CALCITE,      12),
            new Compo(Material.DEEPSLATE,    12),
            new Compo(Material.PACKED_ICE,   10),
            new Compo(Material.GRAVEL,        8),
            new Compo(Material.ANDESITE,      6),
            new Compo(Material.ICE,           6),
            new Compo(Material.IRON_ORE,      4),
            new Compo(Material.EMERALD_ORE,   4),
        };
        // 11 — 🌊 Océan corallien : fond marin (sable/gravier/terre), prismarine, coraux 5 couleurs. Pas de minerai.
        COMPO[10] = new Compo[] {
            new Compo(Material.SAND,               22),
            new Compo(Material.PRISMARINE,         14),
            new Compo(Material.GRAVEL,             10),
            new Compo(Material.PRISMARINE_BRICKS,  10),
            new Compo(Material.DIRT,                8),
            new Compo(Material.DARK_PRISMARINE,     8),
            new Compo(Material.TUBE_CORAL_BLOCK,    6),
            new Compo(Material.BRAIN_CORAL_BLOCK,   6),
            new Compo(Material.BUBBLE_CORAL_BLOCK,  6),
            new Compo(Material.FIRE_CORAL_BLOCK,    5),
            new Compo(Material.HORN_CORAL_BLOCK,    5),
        };
        // 12 — 🌸 Forêt de cerisiers (fin de cycle) : bois/feuilles rose, sol, touches fleuries, diamant rare ~4%.
        COMPO[11] = new Compo[] {
            new Compo(Material.CHERRY_LOG,             26),
            new Compo(Material.CHERRY_LEAVES,          20),
            new Compo(Material.GRASS_BLOCK,            16),
            new Compo(Material.DIRT,                   10),
            new Compo(Material.STONE,                  10),
            new Compo(Material.FLOWERING_AZALEA_LEAVES, 8),
            new Compo(Material.MOSS_BLOCK,              6),
            new Compo(Material.DIAMOND_ORE,             4),
        };
    }

    // Tire un bloc central au hasard selon la composition de la phase (ou ph.bloc si non définie).
    // Fenêtre de démarrage : pendant les 25 tout premiers blocs (cycle 0, phase 1), on EXCLUT la
    // pierre — le joueur n'a pas encore de pioche pour la miner, il pourrait rester bloqué.
    private Material tireBloc(Parcelle parc, Phase ph) {
        Compo[] table = COMPO[ph.num - 1];
        if (table == null || table.length == 0) return ph.bloc;

        boolean sansPierre = parc != null && parc.getObCycle() == 0
                && ph.num == 1 && parc.getObBlocs() < 25;

        int total = 0;
        for (Compo c : table) {
            if (sansPierre && c.bloc == Material.STONE) continue;
            total += c.poids;
        }
        int r = (int) (Math.random() * total);
        Material dernier = ph.bloc;
        for (Compo c : table) {
            if (sansPierre && c.bloc == Material.STONE) continue;
            dernier = c.bloc;
            r -= c.poids;
            if (r < 0) return c.bloc;
        }
        return dernier;
    }

    // ===== Position du bloc central sur une parcelle =====

    public Location blocLocation(Parcelle parc) {
        World w = org.bukkit.Bukkit.getWorld("world");
        if (w == null || parc == null) return null;
        // Pile au centre, au niveau du sol -> directement sous les pieds du joueur (qui spawn à floorY+1).
        int y = plugin.getParcelleManager().getFloorY();
        return new Location(w, parc.getCenterX(), y, parc.getCenterZ());
    }

    // Vrai si (x,y,z) correspond exactement au bloc central de cette parcelle.
    public boolean isBlocCentral(Parcelle parc, int x, int y, int z) {
        Location l = blocLocation(parc);
        return l != null && l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z;
    }

    // (Re)pose le bloc central à sa position. Le bloc affiché est TIRÉ AU HASARD dans la
    // composition de la phase (blocs pleins du biome + minerai) — cf. brique 7.
    // Exception : si le PROCHAIN bloc cassé (obBlocs+1) est un des 2 coffres programmés de la
    // phase, on pose un COFFRE (CHEST) à la place.
    // Appelé à la visite de la parcelle, après un cassage, ou après un changement de phase.
    public void spawnBloc(Parcelle parc) {
        Location l = blocLocation(parc);
        if (l == null) return;
        // Garde-fou : si aucun coffre n'est programmé pour la phase (ancien joueur, jamais initialisé),
        // on en programme 2 maintenant.
        if (parc.getObChest1() == 0 && parc.getObChest2() == 0) programmeCoffres(parc);
        Material bloc = estCoffreProchain(parc) ? Material.CHEST : tireBloc(parc, currentPhase(parc));
        org.bukkit.block.Block b = l.getBlock();
        b.setType(bloc);
        // Les feuilles posées par le plugin ne doivent PAS se dégrader (decay) faute de tronc :
        // sinon elles disparaissent sans BlockBreakEvent et le OneBlock ne réapparaît pas (trou).
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
            leaves.setPersistent(true);
            b.setBlockData(leaves, false);
        }
    }

    // ===== Coffre de phase (brique 9 — partie coffre) =====
    // 2 coffres par phase, à 2 numéros de bloc tirés au hasard dans [1, palier].
    // Le bloc central DEVIENT un CHEST à ces moments ; en le cassant, un petit loot aléatoire
    // du biome va dans l'inventaire (ou aux pieds si plein).

    // Le prochain bloc à casser (obBlocs+1) est-il un des 2 coffres programmés ?
    private boolean estCoffreProchain(Parcelle parc) {
        int prochain = getBlocs(parc) + 1;
        return prochain == parc.getObChest1() || prochain == parc.getObChest2();
    }

    // Programme 2 coffres à des positions aléatoires DISTINCTES dans la phase courante.
    // Appelé à chaque entrée de phase (avancePhase, reset).
    public void programmeCoffres(Parcelle parc) {
        int palier = Math.max(2, currentPhase(parc).palier);
        int a = 1 + (int) (Math.random() * palier);
        int b = 1 + (int) (Math.random() * palier);
        // On évite le doublon (2 coffres sur le même bloc).
        int garde = 0;
        while (b == a && garde++ < 20) b = 1 + (int) (Math.random() * palier);
        parc.setObChest1(a);
        parc.setObChest2(b);
    }

    // Force le PROCHAIN bloc à être un coffre de biome (commande de test /ob forcechest) :
    // on programme un coffre exactement sur obBlocs+1 et on réaffiche le bloc (qui devient un CHEST).
    public void forceChest(Parcelle parc) {
        if (parc == null) return;
        parc.setObChest1(getBlocs(parc) + 1);
        spawnBloc(parc);
    }

    // ===== Accès à l'état (stocké dans la Parcelle) =====

    public Parcelle parcelleOf(Player p) {
        return plugin.getParcelleManager().getParcelle(p.getUniqueId());
    }
    public Parcelle parcelleOf(UUID id) {
        return plugin.getParcelleManager().getParcelle(id);
    }

    public int getPhaseNum(Parcelle parc) { return parc == null ? 1 : parc.getObPhase(); }
    public int getBlocs(Parcelle parc)    { return parc == null ? 0 : parc.getObBlocs(); }
    public int getCycle(Parcelle parc)    { return parc == null ? 0 : parc.getObCycle(); }
    public Phase currentPhase(Parcelle parc) { return phase(getPhaseNum(parc)); }

    // ===== Accès pour l'UI (brique 8) =====

    /**
     * Liste ORDONNÉE (du plus fréquent au plus rare) des blocs obtenables dans une phase,
     * SANS les pourcentages (règle : on ne révèle pas la compo, cf. menu /mine).
     * Utilisée par le menu /phases. Si la compo n'est pas définie, on retombe sur le bloc central.
     */
    public java.util.List<Material> blocsDePhase(int num) {
        Compo[] table = (num >= 1 && num <= PHASES) ? COMPO[num - 1] : null;
        java.util.List<Material> out = new java.util.ArrayList<>();
        if (table == null) { out.add(phase(num).bloc); return out; }
        // Trié par poids décroissant pour un affichage « principaux d'abord », sans montrer le poids.
        java.util.List<Compo> copie = new java.util.ArrayList<>(java.util.Arrays.asList(table));
        copie.sort((a, b) -> Integer.compare(b.poids, a.poids));
        for (Compo c : copie) if (!out.contains(c.bloc)) out.add(c.bloc);
        return out;
    }

    /**
     * Score de progression d'une Île dans le OneBlock : cycles complets × 12 + phase actuelle.
     * Sert au classement /ob top (une Île « plus avancée » a un score plus haut).
     */
    public int progressScore(Parcelle parc) {
        if (parc == null) return 0;
        return getCycle(parc) * PHASES + getPhaseNum(parc);
    }

    // ===== Cassage du bloc central (brique 4) =====

    /**
     * Traite le cassage du bloc central : incrémente le compteur de blocs cassés (+1),
     * replace instantanément le bloc à sa position (même phase), et donne le bloc de biome
     * au joueur (directement dans l'inventaire, ou à ses pieds si l'inventaire est plein).
     *
     * La transition de phase (quand obBlocs atteint le palier) est gérée par la brique 5.
     */
    public void onBlocCasse(Player p, Parcelle parc, org.bukkit.block.Block b) {
        if (parc == null) return;
        Phase ph = currentPhase(parc);

        // Le bloc cassé était-il un COFFRE de phase ? (le bloc posé est un CHEST à ces moments)
        boolean etaitCoffre = (b.getType() == Material.CHEST);

        // +1 bloc cassé.
        parc.setObBlocs(getBlocs(parc) + 1);

        // Drop du bloc cassé :
        //  - coffre → petit loot aléatoire du biome (graines/pousses/seau/carottes…) ;
        //  - sinon → drop VANILLA réel (grass -> terre, minerai -> gemme brute, Fortune, Silk Touch…).
        java.util.List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>();
        if (etaitCoffre) {
            drops.addAll(lootCoffre(ph));
            p.sendMessage("§6✦ Coffre du biome ouvert ! §7(butin dans ton inventaire)");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1.1f);
        } else {
            drops.addAll(b.getDrops(p.getInventory().getItemInMainHand(), p));
        }

        // ── Butin surprise rare (1 %) : argent (75) / fragments (20) / clé de crate (5).
        rollButin(p);

        // ── Animaux du biome (déco vivante) : ~2,5 % de chance de faire spawn un animal
        //    thématique de la phase, avec un plafond par espèce près du bloc (anti-lag).
        rollAnimal(parc, ph);

        // ── Mob hostile du biome (brique 9) : ~2 % de chance. Vraie menace (IA active, il attaque),
        //    aucune récompense spéciale : juste le drop vanilla normal quand on le tue.
        rollMobHostile(p, parc, ph);

        // ── Palier atteint ? -> passage à la phase suivante (brique 5).
        boolean transition = getBlocs(parc) >= ph.palier;
        if (transition) avancePhase(p, parc);

        // Le bloc central réapparaît instantanément. On diffère d'1 tick : le BlockBreakEvent
        // met le bloc en AIR APRÈS l'event, donc replacer tout de suite ne tiendrait pas.
        // (avancePhase a déjà mis à jour la phase, donc spawnBloc pose le bon bloc.)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> spawnBloc(parc));

        // Les drops vont directement dans l'inventaire (ou aux pieds si plein).
        for (org.bukkit.inventory.ItemStack drop : drops) giveOrDrop(p, drop);
    }

    // Loot aléatoire d'un coffre de phase (petits paquets). Seule la phase 1 est remplie ;
    // les autres phases (loot non défini) donnent un loot générique léger.
    private java.util.List<org.bukkit.inventory.ItemStack> lootCoffre(Phase ph) {
        java.util.List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>();
        if (ph.num == 1) {
            // Phase 1 — 🌾 Plaines : pousses & graines + seau d'eau + carottes.
            out.add(new org.bukkit.inventory.ItemStack(Material.WHEAT_SEEDS, 2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.OAK_SAPLING, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.WATER_BUCKET, 1));
            out.add(new org.bukkit.inventory.ItemStack(Material.CARROT,       1 + (int)(Math.random() * 4))); // 1-4
        } else if (ph.num == 2) {
            // Phase 2 — 🌸 Prairie : pousses & baies + fleurs de prairie.
            out.add(new org.bukkit.inventory.ItemStack(Material.BIRCH_SAPLING,  1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.SWEET_BERRIES,  2 + (int)(Math.random() * 4))); // 2-5
            // Une fleur de prairie au hasard (allium / muguet / bleuet / marguerite).
            Material[] fleurs = { Material.ALLIUM, Material.LILY_OF_THE_VALLEY, Material.CORNFLOWER, Material.OXEYE_DAISY };
            out.add(new org.bukkit.inventory.ItemStack(fleurs[(int)(Math.random() * fleurs.length)], 1 + (int)(Math.random() * 2))); // 1-2
        } else if (ph.num == 3) {
            // Phase 3 — 🌲 Forêt : bibliothèques + pousses + petits champignons + pommes.
            out.add(new org.bukkit.inventory.ItemStack(Material.BOOKSHELF,       1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.OAK_SAPLING,     1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.BIRCH_SAPLING,   1 + (int)(Math.random() * 3))); // 1-3
            // Un petit champignon au hasard (brun / rouge).
            Material champi = Math.random() < 0.5 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
            out.add(new org.bukkit.inventory.ItemStack(champi,                  1 + (int)(Math.random() * 2))); // 1-2
            out.add(new org.bukkit.inventory.ItemStack(Material.APPLE,          1 + (int)(Math.random() * 3))); // 1-3
        } else if (ph.num == 4) {
            // Phase 4 — 🏜️ Désert : décors + os/fossile + cactus + graines de patate.
            out.add(new org.bukkit.inventory.ItemStack(Material.DEAD_BUSH,      1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.CACTUS,         1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.BONE,           1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.POTATO,         1 + (int)(Math.random() * 3))); // 1-3 (se plante)
        } else if (ph.num == 5) {
            // Phase 5 — 🌑 Forêt de chênes noirs : pousse chêne noir + petits champignons + mousse/déco sombre.
            out.add(new org.bukkit.inventory.ItemStack(Material.DARK_OAK_SAPLING, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.BROWN_MUSHROOM,   1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.RED_MUSHROOM,     1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.MOSS_CARPET,      1 + (int)(Math.random() * 3))); // 1-3
            // Un peu de déco sombre au hasard (podzol / mycélium).
            Material sombre = Math.random() < 0.5 ? Material.PODZOL : Material.MYCELIUM;
            out.add(new org.bukkit.inventory.ItemStack(sombre,                   1 + (int)(Math.random() * 2))); // 1-2
        } else if (ph.num == 6) {
            // Phase 6 — 🌴 Jungle : bambou + pousse jungle + graines de melon + fougère/vigne + cacao.
            out.add(new org.bukkit.inventory.ItemStack(Material.BAMBOO,         2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.JUNGLE_SAPLING, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.MELON_SEEDS,    2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.COCOA_BEANS,    1 + (int)(Math.random() * 3))); // 1-3
            // Un non-solide de jungle au hasard (fougère / vigne).
            Material vert = Math.random() < 0.5 ? Material.FERN : Material.VINE;
            out.add(new org.bukkit.inventory.ItemStack(vert,                   1 + (int)(Math.random() * 3))); // 1-3
        } else if (ph.num == 7) {
            // Phase 7 — 🪴 Grottes luxuriantes : baies brillantes + azalée + dripleaf + spore blossom + mousse/racines.
            out.add(new org.bukkit.inventory.ItemStack(Material.GLOW_BERRIES,    2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.AZALEA,          1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.MOSS_CARPET,     1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.HANGING_ROOTS,   1 + (int)(Math.random() * 3))); // 1-3
            // Une plante déco du biome au hasard (grand dripleaf / petit dripleaf / spore blossom).
            Material[] deco = { Material.BIG_DRIPLEAF, Material.SMALL_DRIPLEAF, Material.SPORE_BLOSSOM };
            out.add(new org.bukkit.inventory.ItemStack(deco[(int)(Math.random() * deco.length)], 1 + (int)(Math.random() * 2))); // 1-2
        } else if (ph.num == 8) {
            // Phase 8 — 🐸 Marais : boue/argile + nénuphar + champignons + baies + déco marécage.
            out.add(new org.bukkit.inventory.ItemStack(Material.LILY_PAD,        1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.CLAY_BALL,       2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.MANGROVE_PROPAGULE, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.SLIME_BALL,      1 + (int)(Math.random() * 2))); // 1-2
            // Un petit champignon de marais au hasard (brun / rouge).
            Material champiMarais = Math.random() < 0.5 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
            out.add(new org.bukkit.inventory.ItemStack(champiMarais,            1 + (int)(Math.random() * 3))); // 1-3
        } else if (ph.num == 9) {
            // Phase 9 — 🌿 Mangrove : propagule + nénuphar + lumière de grenouille (items hors bloc central).
            out.add(new org.bukkit.inventory.ItemStack(Material.MANGROVE_PROPAGULE, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.LILY_PAD,           1 + (int)(Math.random() * 3))); // 1-3
            // Une lumière de grenouille au hasard (ocre / verdoyante / nacrée).
            Material[] froglights = { Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT };
            out.add(new org.bukkit.inventory.ItemStack(froglights[(int)(Math.random() * froglights.length)], 1 + (int)(Math.random() * 2))); // 1-2
        } else if (ph.num == 10) {
            // Phase 10 — 🏔️ Pics enneigés : corne de chèvre + seau de neige poudreuse + pousse de sapin.
            out.add(new org.bukkit.inventory.ItemStack(Material.GOAT_HORN, 1));
            out.add(new org.bukkit.inventory.ItemStack(Material.POWDER_SNOW_BUCKET, 1));
            out.add(new org.bukkit.inventory.ItemStack(Material.SPRUCE_SAPLING, 1 + (int)(Math.random() * 3))); // 1-3
        } else if (ph.num == 11) {
            // Phase 11 — 🌊 Océan corallien : kelp/herbe + cristaux prismarine (toujours) ; éponge & coquillage RARES.
            out.add(new org.bukkit.inventory.ItemStack(Material.KELP,               2 + (int)(Math.random() * 4))); // 2-5
            out.add(new org.bukkit.inventory.ItemStack(Material.SEAGRASS,           1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.PRISMARINE_CRYSTALS,1 + (int)(Math.random() * 3))); // 1-3
            if (Math.random() < 0.15) out.add(new org.bukkit.inventory.ItemStack(Material.SPONGE, 1 + (int)(Math.random() * 2))); // rare 1-2
            if (Math.random() < 0.15) out.add(new org.bukkit.inventory.ItemStack(Material.NAUTILUS_SHELL, 1)); // rare 1
        } else if (ph.num == 12) {
            // Phase 12 — 🌸 Forêt de cerisiers : pousse de cerisier + pétales roses + bouquet de fleurs.
            out.add(new org.bukkit.inventory.ItemStack(Material.CHERRY_SAPLING, 1 + (int)(Math.random() * 3))); // 1-3
            out.add(new org.bukkit.inventory.ItemStack(Material.PINK_PETALS,    1 + (int)(Math.random() * 3))); // 1-3
            // Une fleur rose au hasard (tulipe rose / pivoine / rosier / lilas).
            Material[] fleursRoses = { Material.PINK_TULIP, Material.PEONY, Material.ROSE_BUSH, Material.LILAC };
            out.add(new org.bukkit.inventory.ItemStack(fleursRoses[(int)(Math.random() * fleursRoses.length)], 1 + (int)(Math.random() * 2))); // 1-2
        } else {
            // Loot générique tant que la phase n'a pas sa table dédiée.
            out.add(new org.bukkit.inventory.ItemStack(Material.WHEAT_SEEDS, 2 + (int)(Math.random() * 4)));
            out.add(new org.bukkit.inventory.ItemStack(Material.OAK_SAPLING, 1 + (int)(Math.random() * 3)));
        }
        return out;
    }

    /**
     * Palier atteint : on passe à la phase suivante et on remet le compteur de blocs à 0.
     * Après la phase 12, on reboucle à la phase 1 et on incrémente le cycle (+1).
     * Déclenche le feedback (message chat + son + particules ; message + son renforcés en fin de cycle).
     */
    private void avancePhase(Player p, Parcelle parc) {
        int ancienne = getPhaseNum(parc);
        boolean finDeCycle = ancienne >= PHASES;

        int nouvelle = finDeCycle ? 1 : ancienne + 1;
        parc.setObPhase(nouvelle);
        parc.setObBlocs(0);
        if (finDeCycle) parc.setObCycle(getCycle(parc) + 1);
        // Programme les 2 coffres de la nouvelle phase (positions aléatoires).
        programmeCoffres(parc);

        Phase suivante = phase(nouvelle);
        Location l = blocLocation(parc);

        if (finDeCycle) {
            // Fin de cycle : message spécial + son plus fort.
            p.sendMessage("");
            p.sendMessage("§6§l✦ CYCLE TERMINÉ ! §7Tu recommences un nouveau tour §e(cycle " + getCycle(parc) + ")§7.");
            p.sendMessage("§7Nouveau biome découvert : " + suivante.nom + " §7!");
            p.sendMessage("");
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
        } else {
            // Passage de phase normal : message chat + son.
            p.sendMessage("§a✦ Nouveau biome découvert : " + suivante.nom + " §7!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }

        // Particules autour du bloc pour marquer le moment.
        if (l != null && l.getWorld() != null) {
            l.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                    l.clone().add(0.5, 1.0, 0.5), 40, 0.5, 0.6, 0.5, 0.0);
            l.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                    l.clone().add(0.5, 1.0, 0.5), 15, 0.3, 0.4, 0.3, 0.02);
        }
    }

    // Petit nom lisible d'un Material (grass_block -> "Grass Block").
    public String niceName(Material m) {
        String[] mots = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String mot : mots) {
            if (mot.isEmpty()) continue;
            sb.append(Character.toUpperCase(mot.charAt(0))).append(mot.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ===== Butin surprise (brique 7) =====
    // 1 % de chance à chaque cassage. Quand il tombe : argent (75 %) / fragments (20 %) / clé (5 %).

    private void rollButin(Player p) {
        if (Math.random() >= 0.01) return; // 1 % global
        donneButin(p);
    }

    // Applique un butin surprise (le tirage argent/fragments/clé) sans le jet de probabilité.
    // Séparé de rollButin pour être forçable via /ob forcebutin.
    private void donneButin(Player p) {
        double r = Math.random() * 100.0;
        if (r < 75.0) {
            // Argent : petit gain aléatoire (50 à 250 $).
            long gain = 50 + (long) (Math.random() * 201);
            if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(p, gain);
            p.sendMessage("§6✦ Butin ! §a+" + PrivateMines.formatNumber(gain) + "$");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        } else if (r < 95.0) {
            // Fragments de Souvenir : 1 à 3.
            long n = 1 + (long) (Math.random() * 3);
            if (plugin.getFragments() != null) plugin.getFragments().addFragments(p, n);
            p.sendMessage("§6✦ Butin ! §b+" + n + " Fragment" + (n > 1 ? "s" : "") + " de Souvenir §7✦");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        } else {
            // Clé de crate commune (le gros lot).
            if (plugin.getCrates() != null) plugin.getCrates().giveKey(p.getUniqueId(), "commune", 1);
            p.sendMessage("§6§l✦ BUTIN RARE ! §fUne §eClé de Crate Commune §f! §7(/keys)");
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
    }

    // ===== Animaux du biome (déco vivante) =====
    // ~2,5 % par bloc cassé : fait spawn un animal thématique de la phase autour du bloc central.
    // Plafond par ESPÈCE (8) dans un rayon autour du bloc : au-delà, on ne spawn plus (anti-lag).
    // Les animaux sont marqués « ob_animal » et gardent leur IA (ils vivent / se baladent),
    // et se tuent normalement (drop vanilla) : c'est de la déco vivante, pas un stack figé.
    private static final int ANIMAL_CAP_PAR_ESPECE = 8;

    private void rollAnimal(Parcelle parc, Phase ph) {
        if (Math.random() >= 0.025) return; // ~2,5 %
        org.bukkit.entity.EntityType[] especes = animauxDe(ph.num);
        if (especes.length == 0) return;

        Location centre = blocLocation(parc);
        if (centre == null || centre.getWorld() == null) return;
        org.bukkit.World w = centre.getWorld();

        // Espèce tirée au hasard parmi celles de la phase.
        org.bukkit.entity.EntityType type = especes[(int) (Math.random() * especes.length)];

        // Plafond par espèce : compte les animaux « ob_animal » de ce type déjà présents autour.
        int count = 0;
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(centre, 8.0, 5.0, 8.0)) {
            if (e.getType() == type && e.hasMetadata("ob_animal")) count++;
        }
        if (count >= ANIMAL_CAP_PAR_ESPECE) return;

        // Position de spawn : légèrement à côté du bloc, sur le sol.
        double dx = (Math.random() * 4.0) - 2.0;
        double dz = (Math.random() * 4.0) - 2.0;
        Location spot = centre.clone().add(0.5 + dx, 1.0, 0.5 + dz);
        org.bukkit.entity.Entity ent = w.spawnEntity(spot, type);
        ent.setMetadata("ob_animal", new org.bukkit.metadata.FixedMetadataValue(plugin, ph.num));
        if (ent instanceof org.bukkit.entity.LivingEntity le) {
            le.setRemoveWhenFarAway(true); // despawn naturel si le joueur s'éloigne longtemps (anti-accumulation)
        }
        if (ent instanceof org.bukkit.entity.Ageable age) age.setAdult();
    }

    // Table des animaux passifs par phase (num 1..12). Vide = aucun animal pour cette phase.
    private org.bukkit.entity.EntityType[] animauxDe(int num) {
        switch (num) {
            case 1:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.COW, org.bukkit.entity.EntityType.SHEEP, org.bukkit.entity.EntityType.PIG };
            case 2:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.SHEEP, org.bukkit.entity.EntityType.RABBIT, org.bukkit.entity.EntityType.BEE };
            case 3:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.WOLF, org.bukkit.entity.EntityType.FOX };
            case 4:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.RABBIT };
            case 5:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.CAT, org.bukkit.entity.EntityType.FOX };
            case 6:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.PARROT, org.bukkit.entity.EntityType.OCELOT, org.bukkit.entity.EntityType.PANDA };
            case 7:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.AXOLOTL };
            case 8:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.FROG, org.bukkit.entity.EntityType.SLIME };
            case 9:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.FROG };
            case 10: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.GOAT };
            case 11: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.COD, org.bukkit.entity.EntityType.SALMON, org.bukkit.entity.EntityType.DOLPHIN };
            case 12: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.BEE, org.bukkit.entity.EntityType.PANDA };
            default: return new org.bukkit.entity.EntityType[0];
        }
    }

    // ===== Mobs hostiles du biome (brique 9) =====
    // ~2 % par bloc cassé : fait surgir un mob hostile thématique de la phase, près du bloc central.
    // Vraie menace (IA active, il attaque le joueur), MAIS aucune récompense spéciale : juste le
    // drop vanilla normal à sa mort. Plafond global (5) dans un rayon autour du bloc (anti-accumulation).
    private static final int MOB_CAP_TOTAL = 5;
    // Espacement : au moins ce nombre de blocs minés entre deux spawns de mob (évite le paquet de 5 d'un coup).
    private static final int MOB_COOLDOWN_BLOCS = 75;
    // Blocs minés depuis le dernier spawn de mob, par propriétaire d'île.
    private final java.util.Map<java.util.UUID, Integer> blocsDepuisMob = new java.util.HashMap<>();

    private void rollMobHostile(Player p, Parcelle parc, Phase ph) {
        // Cooldown de blocs : on incrémente à chaque bloc miné, et on n'autorise un spawn que
        // si au moins MOB_COOLDOWN_BLOCS blocs ont été minés depuis le dernier mob.
        java.util.UUID owner = parc.getOwner();
        int depuis = blocsDepuisMob.getOrDefault(owner, MOB_COOLDOWN_BLOCS) + 1;
        blocsDepuisMob.put(owner, depuis);
        if (depuis < MOB_COOLDOWN_BLOCS) return;

        if (Math.random() >= 0.02) return; // ~2 %
        org.bukkit.entity.EntityType[] mobs = mobsDe(ph.num);
        if (mobs.length == 0) return;

        Location centre = blocLocation(parc);
        if (centre == null || centre.getWorld() == null) return;
        org.bukkit.World w = centre.getWorld();

        // Plafond global : compte les mobs « ob_mob » déjà présents sur TOUTE la parcelle (les mobs
        // s'éparpillent jusqu'au bord, donc le rayon de comptage doit couvrir la parcelle entière).
        double rayonCompte = parc.getSize() / 2.0 + 2;
        int count = 0;
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(centre, rayonCompte, 10.0, rayonCompte)) {
            if (e.hasMetadata("ob_mob")) count++;
        }
        if (count >= MOB_CAP_TOTAL) return;

        org.bukkit.entity.EntityType type = mobs[(int) (Math.random() * mobs.length)];
        if (spawnMob(parc, type, p)) {
            blocsDepuisMob.put(parc.getOwner(), 0); // reset le cooldown : prochain mob dans 75 blocs mini
        }
    }

    // Fait apparaître un mob hostile marqué « ob_mob » à côté du bloc central, et le cible sur le joueur.
    // Renvoie true si le mob a été créé (utilisé par la commande de test /ob forcemob).
    private boolean spawnMob(Parcelle parc, org.bukkit.entity.EntityType type, Player cible) {
        Location centre = blocLocation(parc);
        if (centre == null || centre.getWorld() == null) return false;
        org.bukkit.World w = centre.getWorld();

        // Les mobs s'ÉPARPILLENT sur l'île : rayon large (jusqu'au bord de la parcelle, -2 de marge),
        // au lieu de tous surgir collés au bloc central. On cherche un sol solide sous chaque point.
        int rayon = Math.max(4, parc.getSize() / 2 - 2);
        Location spot = null;
        for (int essai = 0; essai < 10 && spot == null; essai++) {
            double dx = (Math.random() * 2 - 1) * rayon;
            double dz = (Math.random() * 2 - 1) * rayon;
            Location sol = solSous(w, centre.getBlockX() + (int) dx, centre.getBlockZ() + (int) dz, centre.getBlockY());
            if (sol != null) spot = sol;
        }
        // Aucun sol trouvé après 10 essais → on retombe près du bloc central.
        if (spot == null) spot = centre.clone().add(0.5, 1.0, 0.5);
        org.bukkit.entity.Entity ent = w.spawnEntity(spot, type);
        ent.setMetadata("ob_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, parc.getObPhase()));
        if (ent instanceof org.bukkit.entity.LivingEntity le) {
            le.setRemoveWhenFarAway(true); // despawn naturel si le joueur s'éloigne longtemps
        }
        // Cible immédiatement le joueur (sinon un Creeper/zombie de jour peut rester passif un instant).
        if (cible != null && ent instanceof org.bukkit.entity.Monster mon) {
            mon.setTarget(cible);
        }
        return true;
    }

    // Cherche un emplacement de spawn valide en (x,z) : un bloc solide surmonté d'air, proche de yRef.
    // Renvoie la Location juste au-dessus du sol, ou null si rien de correct dans la fenêtre verticale.
    private Location solSous(org.bukkit.World w, int x, int z, int yRef) {
        for (int y = yRef + 3; y >= yRef - 3; y--) {
            org.bukkit.block.Block sol = w.getBlockAt(x, y, z);
            org.bukkit.block.Block dessus = w.getBlockAt(x, y + 1, z);
            if (sol.getType().isSolid() && dessus.getType().isAir()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    // Force l'apparition d'un mob hostile de la phase actuelle (commande de test /ob forcemob).
    public boolean forceMob(Player p, Parcelle parc) {
        if (parc == null) return false;
        org.bukkit.entity.EntityType[] mobs = mobsDe(getPhaseNum(parc));
        if (mobs.length == 0) return false;
        return spawnMob(parc, mobs[(int) (Math.random() * mobs.length)], p);
    }

    // Table des mobs hostiles par phase (num 1..12). Vide = aucun mob pour cette phase.
    // Thématique du biome ; évite les doublons avec les animaux passifs (animauxDe).
    private org.bukkit.entity.EntityType[] mobsDe(int num) {
        switch (num) {
            case 1:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.ZOMBIE };
            case 2:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.SPIDER };
            case 3:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SPIDER };
            case 4:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.SKELETON, org.bukkit.entity.EntityType.CAVE_SPIDER };
            case 5:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.WITCH, org.bukkit.entity.EntityType.ENDERMAN };
            case 6:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.SPIDER, org.bukkit.entity.EntityType.ZOMBIE };
            case 7:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.CAVE_SPIDER };
            case 8:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.DROWNED, org.bukkit.entity.EntityType.SLIME };
            case 9:  return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.DROWNED };
            case 10: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.SKELETON, org.bukkit.entity.EntityType.STRAY };
            case 11: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.DROWNED };
            case 12: return new org.bukkit.entity.EntityType[]{ org.bukkit.entity.EntityType.WITCH };
            default: return new org.bukkit.entity.EntityType[0];
        }
    }

    // Force un tirage de butin surprise immédiat (commande de test /ob forcebutin).
    public void forceButin(Player p) { donneButin(p); }

    // Donne un item au joueur ; s'il n'y a pas de place, le lâche à ses pieds.
    private void giveOrDrop(Player p, org.bukkit.inventory.ItemStack item) {
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(item);
        for (org.bukkit.inventory.ItemStack rem : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    // ===== BossBar de progression (brique 6) =====
    // Une barre en haut de l'écran affichée UNIQUEMENT quand le joueur est sur SA propre Île :
    //   « phase (nom) · blocs/palier · cycle », la barre se remplissant selon l'avancement.
    // Tant qu'elle est affichée, la barre d'astuces du guide est masquée (rétablie en quittant).

    private final Map<UUID, BossBar> bars = new HashMap<>();

    // Le joueur est-il physiquement sur SA propre Île ? (parcelle sous ses pieds == la sienne)
    private boolean estSurSonIle(Player p) {
        Parcelle sienne = plugin.getParcelleManager().getParcelle(p.getUniqueId());
        if (sienne == null) return false;
        return sienne.contains(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
    }

    // Lance la tâche répétée (toutes les 10 ticks = 0,5 s) qui gère l'affichage des BossBars.
    public void startBossBarTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (estSurSonIle(p)) showOrUpdateBar(p);
                else hideBar(p);
            }
        }, 20L, 10L);
    }

    // Affiche (ou met à jour) la BossBar OneBlock d'un joueur sur son Île.
    private void showOrUpdateBar(Player p) {
        Parcelle parc = plugin.getParcelleManager().getParcelle(p.getUniqueId());
        if (parc == null) return;
        // PRIORITÉ À LA QUÊTE : tant qu'un objectif d'Acte/Apprentissage est en cours, on ne
        // montre PAS la barre de progression du OneBlock (elle masquerait l'objectif). On retire
        // même une barre déjà présente pour laisser la barre de quête visible.
        if (plugin.getGuide() != null && plugin.getGuide().hasQuestObjective(p.getUniqueId())) {
            BossBar dejal = bars.remove(p.getUniqueId());
            if (dejal != null) dejal.removeAll();
            return;
        }
        Phase ph = currentPhase(parc);
        int blocs  = getBlocs(parc);
        int palier = Math.max(1, ph.palier);
        double prog = Math.max(0.0, Math.min(1.0, (double) blocs / palier));

        String titre = ph.nom + " §7— §e" + blocs + "§7/§e" + palier
                + " §8· §7cycle §e" + getCycle(parc);

        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = org.bukkit.Bukkit.createBossBar(titre, BarColor.GREEN, BarStyle.SEGMENTED_10);
            bars.put(p.getUniqueId(), bar);
            bar.addPlayer(p);
            // On masque la barre d'astuces du guide pendant qu'on est sur l'Île.
            if (plugin.getGuide() != null) plugin.getGuide().setSuppressed(p, true);
        }
        bar.setTitle(titre);
        bar.setProgress(prog);
        bar.setVisible(true);
    }

    // Retire la BossBar OneBlock d'un joueur (quand il quitte son Île ou se déconnecte)
    // et rétablit sa barre d'astuces.
    public void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
            if (plugin.getGuide() != null) plugin.getGuide().setSuppressed(p, false);
        }
    }
}
