package fr.garfield.privatemines;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ « joueur » natifs (SANS Citizens), affichés via des packets ProtocolLib.
 *
 * <p>Chaque PNJ a un ID auto (pnj1, pnj2...) et un rôle (veilleur/ancre/none) qui détermine
 * son dialogue. Tous les paramètres sont dans {@code npcs.yml} (voir {@link NpcConfig}), gérés
 * par la commande /pnj place (création) et le menu /pnj setting (nom, couleur, skin, rôle).
 */
public class NpcManager implements Listener {

    private final PrivateMines plugin;
    private final ActeManager acte;
    private final ProtocolManager protocol;
    private final NpcConfig config;
    // Anti-doublon de clic PNJ (le client envoie 1 paquet INTERACT par main). UUID → dernier clic (ms).
    private final java.util.Map<java.util.UUID, Long> lastNpcClick = new java.util.HashMap<>();

    // Rôles possibles.
    public static final String ROLE_NONE = "none";
    public static final String ROLE_VEILLEUR = "veilleur";
    public static final String ROLE_ANCRE = "ancre";
    // Acte II (sur l'île de la mine) : le Contremaître (donne le sac + lance la quête)
    // et le Forgeron (échange la Tête de Pioche contre la pioche).
    public static final String ROLE_CONTREMAITRE = "contremaitre";
    public static final String ROLE_FORGERON = "forgeron";
    // Rôle GÉNÉRIQUE réutilisable partout : au clic droit, le PNJ récite ses lignes de
    // dialogue (définies par PNJ dans npcs.yml → npcs.<id>.dialogue, une ligne par entrée).
    public static final String ROLE_CONTEUR = "conteur";
    // Comme le conteur, mais REMET aussi la Boussole du Log Pose (lance la visite d'Alabasta).
    public static final String ROLE_GUIDE_BOUSSOLE = "guide_boussole";
    // Le Dernier Témoin : ne parle QUE si la visite d'Alabasta est finie ; récite son grand récit
    // (npcs.<id>.dialogue) puis, à la dernière réplique, clôt la visite (clé de crate + retrait boussole).
    public static final String ROLE_TEMOIN_FINAL = "temoin_final";
    // Le Passeur du Vide : au clic droit, téléporte le joueur dans l'ARÈNE de l'End (près du dragon),
    // à une position aléatoire dans un rayon de 75 blocs autour du centre (0, *, 0).
    public static final String ROLE_END = "end";
    // Récompenses quotidiennes : au clic droit, ouvre le menu de la série de 7 jours.
    public static final String ROLE_QUOTIDIEN = "quotidien";

    // Index de métadonnée du byte "parties de skin affichées" (1.17+).
    private static final int SKIN_PARTS_INDEX = 17;
    private static final byte ALL_SKIN_PARTS = 0x7F;

    // Un PNJ vivant en mémoire.
    private static class Npc {
        final String id;
        final int entityId;
        final UUID uuid;
        final Location loc;
        final String displayName;
        final WrappedGameProfile profile;
        Npc(String id, int entityId, UUID uuid, Location loc, String displayName,
            WrappedGameProfile profile) {
            this.id = id; this.entityId = entityId; this.uuid = uuid; this.loc = loc;
            this.displayName = displayName; this.profile = profile;
        }
    }

    private final Map<String, Npc> npcs = new HashMap<>(); // id -> Npc
    private int nextEntityId = -4200;

    // Saisie chat en cours : nom d'un PNJ, ou ajout d'un skin nommé à la bibliothèque.
    private static class ChatInput {
        final String id;         // pour "name" : ID du PNJ ; pour "libskin" : nom du skin
        final String type;       // "name" | "libskin"
        String skinValue;        // pour le skin : value en attente de la signature
        ChatInput(String id, String type) { this.id = id; this.type = type; }
    }
    private final Map<UUID, ChatInput> chatInputs = new HashMap<>();

    public NpcManager(PrivateMines plugin, ActeManager acte) {
        this.plugin = plugin;
        this.acte = acte;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.config = new NpcConfig(plugin);
        registerClickListener();
    }

    public NpcConfig getCfg() { return config; }
    public java.util.Set<String> getIds() { return config.getIds(); }

    // ===== Chargement =====

    public void loadAll() {
        for (Npc npc : new ArrayList<>(npcs.values())) {
            for (Player p : Bukkit.getOnlinePlayers()) hideFrom(p, npc);
        }
        npcs.clear();

        for (String id : config.getIds()) {
            org.bukkit.World w = Bukkit.getWorld(config.getWorld(id));
            if (w == null) continue;
            Location loc = new Location(w, config.getX(id), config.getY(id), config.getZ(id),
                    config.getYaw(id), config.getPitch(id));
            spawnNpc(id, loc);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Npc npc : npcs.values()) showTo(p, npc);
        }
    }

    public void reloadNpcs() {
        config.reload();
        loadAll();
    }

    // ===== Hologramme du nom (le nom réel s'affiche au-dessus de la tête) =====

    // Nom de l'hologramme associé à un PNJ dans HologramManager.
    private String holoName(String id) { return "npc_" + id; }

    // Crée/met à jour l'hologramme du nom au-dessus de la tête du PNJ.
    private void updateNameHologram(String id, Location loc) {
        Location holoLoc = loc.clone().add(0, 2.05, 0);
        java.util.List<String> lines =
                java.util.Collections.singletonList(config.getDisplayName(id));
        plugin.getHoloManager().setHologramLines(holoName(id), holoLoc, lines);
    }

    // Crée l'objet Npc en mémoire (profil + skin depuis la config).
    private void spawnNpc(String id, Location loc) {
        UUID uuid = UUID.randomUUID();
        // Nom de profil "invisible" : uniquement des codes couleur -> aucun texte lisible
        // au-dessus de la tête. Le vrai nom stylé est affiché par un hologramme (updateNameHologram).
        WrappedGameProfile profile = new WrappedGameProfile(uuid, "§8§7§8§0");
        String value = config.getSkinValue(id);
        if (value != null && !value.isEmpty()) {
            profile.getProperties().put("textures",
                    new WrappedSignedProperty("textures", value,
                            config.getSkinSignature(id) == null ? "" : config.getSkinSignature(id)));
        }
        npcs.put(id, new Npc(id, nextEntityId--, uuid, loc, config.getDisplayName(id), profile));
        updateNameHologram(id, loc);
    }

    // Recrée + réaffiche un PNJ après un changement (nom, skin...).
    private void refresh(String id) {
        Npc old = npcs.remove(id);
        if (old != null) for (Player p : Bukkit.getOnlinePlayers()) hideFrom(p, old);
        if (!config.has(id)) return;
        org.bukkit.World w = Bukkit.getWorld(config.getWorld(id));
        if (w == null) return;
        Location loc = new Location(w, config.getX(id), config.getY(id), config.getZ(id),
                config.getYaw(id), config.getPitch(id));
        spawnNpc(id, loc);
        Npc npc = npcs.get(id);
        for (Player p : Bukkit.getOnlinePlayers()) showTo(p, npc);
    }

    // ===== Placement / suppression =====

    /** /pnj place : crée un PNJ à la position EXACTE du joueur (bon Y). */
    public String placeNpc(Player admin) {
        String id = config.nextId();
        config.createDefault(id, admin.getLocation());
        config.save();
        spawnNpc(id, admin.getLocation());
        Npc npc = npcs.get(id);
        for (Player p : Bukkit.getOnlinePlayers()) showTo(p, npc);
        return id;
    }

    public boolean removeNpc(String id) {
        Npc npc = npcs.remove(id);
        if (npc != null) for (Player p : Bukkit.getOnlinePlayers()) hideFrom(p, npc);
        plugin.getHoloManager().removeHologram(holoName(id));
        boolean existed = config.has(id);
        config.remove(id);
        config.save();
        return npc != null || existed;
    }

    /**
     * Position du premier PNJ portant ce rôle, ou {@code null} s'il n'est pas posé.
     * Sert à la boussole de quête : les objectifs désignent un RÔLE (« le Veilleur »),
     * pas un identifiant de PNJ — l'admin peut le déplacer ou le reposer sans rien casser.
     */
    public Location locationOfRole(String role) {
        for (String id : config.getIds()) {
            if (!role.equals(config.getRole(id))) continue;
            Location loc = getNpcLocation(id);
            if (loc != null) return loc;
        }
        return null;
    }

    public Location getNpcLocation(String id) {
        Npc npc = npcs.get(id);
        return npc == null ? null : npc.loc.clone();
    }

    // ===== Édition (menu /pnj setting) =====

    public void setRole(String id, String role) {
        config.setRole(id, role);
        // Le PNJ des récompenses quotidiennes se nomme tout seul, EN VERT, quand on lui donne son
        // rôle — mais uniquement s'il porte encore le nom par défaut : un nom choisi à la main
        // par l'admin n'est jamais écrasé.
        if (ROLE_QUOTIDIEN.equals(role) && "PNJ".equals(config.getName(id))) {
            config.setName(id, "Récompenses quotidiennes");
            config.setColor(id, "a");
        }
        config.save();
        refresh(id);
    }

    public void setColor(String id, String colorCode) {
        config.setColor(id, colorCode);
        config.save();
        refresh(id);
    }

    /** Applique un skin (value + signature) et sauvegarde + re-spawn. */
    public void applySkin(String id, String value, String signature) {
        config.setSkin(id, value, signature);
        config.save();
        refresh(id);
    }

    /** Applique un skin de la bibliothèque (skins.yml) au PNJ. */
    public boolean applyLibrarySkin(String id, String skinName) {
        SkinLibrary lib = plugin.getSkins();
        if (!lib.has(skinName)) return false;
        applySkin(id, lib.getValue(skinName), lib.getSignature(skinName));
        return true;
    }

    // ===== Saisie chat (nom / skin) =====

    public void startNameInput(Player admin, String id) {
        chatInputs.put(admin.getUniqueId(), new ChatInput(id, "name"));
        admin.closeInventory();
        admin.sendMessage("§8§m                                        ");
        admin.sendMessage("§e✏ Tape le §fnouveau nom §edu PNJ dans le chat.");
        admin.sendMessage("§7(sans les codes couleur — la couleur se choisit dans le menu)");
        admin.sendMessage("§7Tape §cannuler §7pour abandonner.");
        admin.sendMessage("§8§m                                        ");
    }

    /** Enregistre un NOUVEAU skin nommé dans la bibliothèque (value puis signature au chat). */
    public void startLibrarySkinInput(Player admin, String skinName) {
        chatInputs.put(admin.getUniqueId(), new ChatInput(skinName, "libskin"));
        admin.closeInventory();
        admin.sendMessage("§8§m                                        ");
        admin.sendMessage("§b🖼 Nouveau skin : §f" + skinName);
        admin.sendMessage("§71) Va sur §fhttps://mineskin.org §7, upload ton skin.");
        admin.sendMessage("§72) Colle ici la §fvalue §7(le très long texte), puis Entrée.");
        admin.sendMessage("§7Une fois enregistré, applique-le depuis §f/pnj setting §7→ Skin.");
        admin.sendMessage("§7Tape §cannuler §7pour abandonner.");
        admin.sendMessage("§8§m                                        ");
    }

    // Renvoie true si le message a été consommé (une saisie était en cours).
    private boolean handleChat(Player player, String message) {
        ChatInput input = chatInputs.get(player.getUniqueId());
        if (input == null) return false;

        String msg = message.trim();
        if (msg.equalsIgnoreCase("annuler")) {
            chatInputs.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§7Saisie annulée."));
            return true;
        }

        if (input.type.equals("name")) {
            chatInputs.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                config.setName(input.id, msg);
                config.save();
                refresh(input.id);
                player.sendMessage("§a✔ Nom changé en §f" + config.getDisplayName(input.id) + "§a.");
                plugin.getNpcSettingMenu().openEdit(player, input.id);
            });
            return true;
        }

        // libskin : enregistre un skin nommé dans la bibliothèque (value puis signature).
        if (input.skinValue == null) {
            input.skinValue = msg;
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✔ Value reçue.");
                player.sendMessage("§73) Colle maintenant la §fsignature§7, puis Entrée.");
            });
            return true;
        }
        final String value = input.skinValue;
        final String signature = msg;
        final String skinName = input.id;
        chatInputs.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getSkins().put(skinName, value, signature);
            player.sendMessage("§a✔ Skin §f" + skinName + " §aenregistré dans la bibliothèque !");
            player.sendMessage("§7Applique-le avec §f/pnj setting §7→ Skin.");
        });
        return true;
    }

    // ===== Events =====

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (Npc npc : npcs.values()) showTo(p, npc);
        }, 30L);
    }

    // Les PNJ sont affichés par packets par-joueur : quand un joueur change de monde (ex. aller
    // dans l'End puis revenir), le client oublie les entités-packets. On les lui réaffiche.
    @EventHandler
    public void onChangeWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (Npc npc : npcs.values()) showTo(p, npc);
        }, 30L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    // ===== Packets =====

    private void showTo(Player viewer, Npc npc) {
        // ⚠ UN PNJ N EXISTE QUE DANS SON MONDE. Sans ce test, le paquet d apparition partait vers
        // TOUS les joueurs : un PNJ du spawn se dessinait aux mêmes coordonnées dans l End, et
        // inversement. tickLook filtrait déjà par monde, showTo l avait oublié (corrigé 2026-08-23).
        if (npc.loc == null || npc.loc.getWorld() == null) return;
        if (!npc.loc.getWorld().equals(viewer.getWorld())) { hideFrom(viewer, npc); return; }
        try {
            PacketContainer info = buildPlayerInfoPacket(npc);
            protocol.sendServerPacket(viewer, info);

            PacketContainer spawn = protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, npc.entityId);
            spawn.getUUIDs().write(0, npc.uuid);
            spawn.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
            spawn.getDoubles().write(0, npc.loc.getX());
            spawn.getDoubles().write(1, npc.loc.getY());
            spawn.getDoubles().write(2, npc.loc.getZ());
            spawn.getBytes().write(0, (byte) (npc.loc.getYaw() * 256.0F / 360.0F));
            spawn.getBytes().write(1, (byte) (npc.loc.getPitch() * 256.0F / 360.0F));
            protocol.sendServerPacket(viewer, spawn);

            sendSkinLayers(viewer, npc);
            sendHeadRotation(viewer, npc, npc.loc.getYaw(), npc.loc.getPitch());
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible d'afficher le PNJ " + npc.id + " : " + e.getMessage());
        }
    }

    /**
     * Construit le packet PLAYER_INFO (ADD_PLAYER + UPDATE_LISTED) pour un PNJ.
     *
     * <p>Sur Paper 1.21.x, les champs {@code actions}/{@code entries} du packet sont FINAL et
     * ProtocolLib n'arrive pas à les réécrire via {@code write()} (« Unable to set value of field
     * private final ... »). On construit donc directement le packet NMS via réflexion (constructeur
     * {@code (EnumSet actions, List entries)}) puis on l'emballe dans un PacketContainer.
     */
    private PacketContainer buildPlayerInfoPacket(Npc npc) throws Exception {
        Class<?> packetClass = PacketType.Play.Server.PLAYER_INFO.getPacketClass();
        // Classes internes du packet.
        Class<?> actionClass = null, entryClass = null;
        for (Class<?> inner : packetClass.getDeclaredClasses()) {
            if (inner.isEnum()) actionClass = inner;
            else if (inner.isRecord()) entryClass = inner;
        }
        if (actionClass == null || entryClass == null) {
            throw new IllegalStateException("Structure du packet PLAYER_INFO inattendue");
        }

        // EnumSet des actions : ADD_PLAYER + UPDATE_LISTED (par nom, robuste au remap).
        @SuppressWarnings({"unchecked", "rawtypes"})
        EnumSet actions = EnumSet.noneOf((Class<? extends Enum>) actionClass);
        for (Object c : actionClass.getEnumConstants()) {
            String n = ((Enum<?>) c).name();
            if (n.equals("ADD_PLAYER") || n.equals("UPDATE_LISTED")) actions.add(c);
        }

        // GameProfile NMS (handle du WrappedGameProfile, contient déjà le skin).
        Object gameProfile = npc.profile.getHandle();
        // Component NMS du nom affiché (via ProtocolLib pour garder les couleurs §).
        Object displayName = WrappedChatComponent.fromText(npc.displayName).getHandle();
        // GameType.SURVIVAL.
        Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
        Object survival = null;
        for (Object gt : gameTypeClass.getEnumConstants()) {
            if (((Enum<?>) gt).name().equals("SURVIVAL")) { survival = gt; break; }
        }

        // Constructeur Entry(UUID, GameProfile, boolean, int, GameType, Component, boolean, int, RemoteChatSession.Data).
        java.lang.reflect.Constructor<?> entryCtor = null;
        for (java.lang.reflect.Constructor<?> ct : entryClass.getDeclaredConstructors()) {
            if (ct.getParameterCount() == 9) { entryCtor = ct; break; }
        }
        if (entryCtor == null) throw new IllegalStateException("Constructeur Entry(9) introuvable");
        entryCtor.setAccessible(true);
        Object entry = entryCtor.newInstance(
                npc.uuid, gameProfile, false, 0, survival, displayName, false, 0, null);

        java.util.List<Object> entries = new java.util.ArrayList<>();
        entries.add(entry);

        // Constructeur packet (EnumSet, List/Collection).
        java.lang.reflect.Constructor<?> pktCtor = null;
        for (java.lang.reflect.Constructor<?> ct : packetClass.getDeclaredConstructors()) {
            Class<?>[] p = ct.getParameterTypes();
            if (p.length == 2 && EnumSet.class.isAssignableFrom(p[0])
                    && java.util.List.class.isAssignableFrom(p[1])) { pktCtor = ct; break; }
        }
        if (pktCtor == null) {
            for (java.lang.reflect.Constructor<?> ct : packetClass.getDeclaredConstructors()) {
                Class<?>[] p = ct.getParameterTypes();
                if (p.length == 2 && EnumSet.class.isAssignableFrom(p[0])
                        && java.util.Collection.class.isAssignableFrom(p[1])) { pktCtor = ct; break; }
            }
        }
        if (pktCtor == null) throw new IllegalStateException("Constructeur PLAYER_INFO(EnumSet,List) introuvable");
        pktCtor.setAccessible(true);
        Object nmsPacket = pktCtor.newInstance(actions, entries);

        return PacketContainer.fromPacket(nmsPacket);
    }

    private void sendSkinLayers(Player viewer, Npc npc) {
        try {
            PacketContainer meta = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, npc.entityId);
            WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
            List<WrappedDataValue> values = new ArrayList<>();
            values.add(new WrappedDataValue(SKIN_PARTS_INDEX, byteSerializer, ALL_SKIN_PARTS));
            meta.getDataValueCollectionModifier().write(0, values);
            protocol.sendServerPacket(viewer, meta);
        } catch (Exception ignored) {}
    }

    private void hideFrom(Player viewer, Npc npc) {
        try {
            PacketContainer destroy = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, java.util.Collections.singletonList(npc.entityId));
            protocol.sendServerPacket(viewer, destroy);
        } catch (Exception ignored) {}
    }

    private void sendHeadRotation(Player viewer, Npc npc, float yaw, float pitch) {
        try {
            byte yawByte = (byte) (yaw * 256.0F / 360.0F);
            byte pitchByte = (byte) (pitch * 256.0F / 360.0F);
            PacketContainer look = protocol.createPacket(PacketType.Play.Server.ENTITY_LOOK);
            look.getIntegers().write(0, npc.entityId);
            look.getBytes().write(0, yawByte);
            look.getBytes().write(1, pitchByte);
            look.getBooleans().write(0, true);
            protocol.sendServerPacket(viewer, look);
            PacketContainer head = protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, npc.entityId);
            head.getBytes().write(0, yawByte);
            protocol.sendServerPacket(viewer, head);
        } catch (Exception ignored) {}
    }

    public void tickLook() {
        for (Npc npc : npcs.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(npc.loc.getWorld())) continue;
                if (p.getLocation().distanceSquared(npc.loc) > 64) continue;
                double dx = p.getLocation().getX() - npc.loc.getX();
                double dy = p.getEyeLocation().getY() - (npc.loc.getY() + 1.62);
                double dz = p.getLocation().getZ() - npc.loc.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
                sendHeadRotation(p, npc, yaw, pitch);
            }
        }
    }

    // ===== Clic droit -> dialogue selon le RÔLE =====

    private void registerClickListener() {
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                int targetId = event.getPacket().getIntegers().read(0);
                Npc clicked = null;
                for (Npc npc : npcs.values()) {
                    if (npc.entityId == targetId) { clicked = npc; break; }
                }
                if (clicked == null) return;

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();
                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    event.setCancelled(true);
                    return;
                }
                if (action != EnumWrappers.EntityUseAction.INTERACT) return;

                final String id = clicked.id;
                final Player p = event.getPlayer();
                // Anti-doublon : le client envoie un paquet INTERACT pour CHAQUE main (main + off-hand),
                // ce qui déclenchait 2× l'action. On ignore un 2e clic du même joueur en < 250 ms.
                long now = System.currentTimeMillis();
                Long last = lastNpcClick.get(p.getUniqueId());
                if (last != null && now - last < 250L) return;
                lastNpcClick.put(p.getUniqueId(), now);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String role = config.getRole(id);
                    if (ROLE_VEILLEUR.equals(role)) acte.onTalkVeilleur(p);
                    else if (ROLE_ANCRE.equals(role)) acte.onTalkAncre(p);
                    else if (ROLE_CONTREMAITRE.equals(role)) acte.onTalkContremaitre(p);
                    else if (ROLE_FORGERON.equals(role)) acte.onTalkForgeron(p);
                    else if (ROLE_CONTEUR.equals(role)) speakConteur(p, id);
                    else if (ROLE_GUIDE_BOUSSOLE.equals(role)) speakGuideBoussole(p, id);
                    else if (ROLE_TEMOIN_FINAL.equals(role)) speakTemoinFinal(p, id);
                    else if (ROLE_END.equals(role)) { if (NpcManager.this.plugin.getEnd() != null) NpcManager.this.plugin.getEnd().teleporterArene(p); }
                    else if (ROLE_QUOTIDIEN.equals(role)) { if (NpcManager.this.plugin.getDaily() != null) NpcManager.this.plugin.getDaily().openMenu(p); }
                    else p.sendMessage("§7(Ce PNJ n'a pas encore de rôle. /pnj setting pour lui en donner un.)");
                });
            }
        });
    }

    // Anti-spam : empêche de re-déclencher le dialogue d'un même PNJ trop vite (clics répétés).
    private final java.util.Map<java.util.UUID, Long> conteurCooldown = new java.util.HashMap<>();

    // Rôle « conteur » : récite les lignes de npcs.<id>.dialogue (une par entrée), préfixées du
    // nom coloré du PNJ. Le texte est libre (couleurs §), éditable à la main + /pnj reload.
    private void speakConteur(Player p, String id) {
        // Cooldown court par joueur (2 s) pour éviter le spam sur double-clic.
        long now = System.currentTimeMillis();
        Long until = conteurCooldown.get(p.getUniqueId());
        if (until != null && until > now) return;
        conteurCooldown.put(p.getUniqueId(), now + 2000L);

        java.util.List<String> lines = config.raw().getStringList("npcs." + id + ".dialogue");
        String name = config.getDisplayName(id);
        if (lines == null || lines.isEmpty()) {
            if (p.isOp()) {
                p.sendMessage("§e[Admin] §7Ce conteur n'a pas de dialogue. Ajoute des lignes dans "
                        + "§fnpcs.yml §7→ §fnpcs." + id + ".dialogue §7puis §f/pnj reload§7.");
            } else {
                p.sendMessage(name + " §7vous salue en silence.");
            }
            return;
        }
        // On récite les lignes avec un léger délai entre chacune, pour un effet « parole ».
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 0.7f, 1.1f);
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(name + " §7» §f" + line);
            }, i * 12L); // ~0,6 s entre chaque ligne
        }
    }

    // Rôle « guide_boussole » : joue le dialogue dans le cadre immersif (comme le Contremaître/Forgeron,
    // une réplique toutes les ~5 s, le temps de LIRE) PUIS — et seulement à la fin — remet le Log Pose.
    // Fini le spam de lignes + boussole donnée trop tôt : la remise colle à la dernière réplique.
    private void speakGuideBoussole(Player p, String id) {
        DialogueManager dlg = acte.getDialogues();
        // Un dialogue déjà en cours (le sien ou un autre) : on ne superpose pas.
        if (dlg.isTalking(p)) return;

        String name = config.getDisplayName(id);
        String accent = "§" + config.getColor(id);

        // S'il a DÉJÀ confié la boussole (visite en cours ou terminée), il ne rejoue PAS tout son
        // récit : une seule phrase de rappel qui oriente le joueur. Le grand dialogue ne se joue qu'à
        // la première rencontre.
        LogPoseManager lpState = plugin.getLogPose();
        if (lpState != null && (lpState.isVisitInProgress(p) || lpState.isVisitDone(p))) {
            if (lpState.isVisitFullyDone(p)) {
                p.sendMessage(name + " §7» §fTu es allé au bout, et tu es revenu. C'est déjà plus que la plupart.");
            } else if (lpState.isVisitDone(p)) {
                p.sendMessage(name + " §7» §fTu as tout vu. Il ne te reste qu'une chose : §cécouter celui qui attend au bout§f.");
            } else {
                p.sendMessage(name + " §7» §fTu l'as déjà, l'aiguille. §cSuis-la §7— §fle sable te dira le reste.");
            }
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 0.7f, 0.9f);
            return;
        }

        java.util.List<String> raw = config.raw().getStringList("npcs." + id + ".dialogue");

        if (raw == null || raw.isEmpty()) {
            if (p.isOp()) {
                p.sendMessage("§e[Admin] §7Ce guide n'a pas de dialogue. Ajoute des lignes dans "
                        + "§fnpcs.yml §7→ §fnpcs." + id + ".dialogue §7puis §f/pnj reload§7.");
            }
            // Pas de dialogue : on remet quand même la boussole pour ne pas bloquer la visite.
            LogPoseManager lp = plugin.getLogPose();
            if (lp != null) lp.giveLogPose(p);
            return;
        }

        String[] lines = raw.toArray(new String[0]);
        dlg.startDialogue(p, name, accent, lines, () -> {
            LogPoseManager lp = plugin.getLogPose();
            if (lp != null) lp.giveLogPose(p);
        });
    }


    // Rôle « temoin_final » (Le Dernier Témoin) : le PNJ au bout de la visite d'Alabasta. Il ne
    // délivre son grand récit QUE si le joueur a terminé les 3 lieux (boussole en main). À la
    // dernière réplique, il clôt la visite : remise de la clé de crate + retrait de la boussole
    // (via LogPoseManager.completeVisit). Avant/après, il ne fait qu'orienter le joueur.
    private void speakTemoinFinal(Player p, String id) {
        DialogueManager dlg = acte.getDialogues();
        if (dlg.isTalking(p)) return;

        LogPoseManager lp = plugin.getLogPose();
        String name = config.getDisplayName(id);
        String accent = "§" + config.getColor(id);

        // Récit DÉJÀ écouté (visite entièrement clôturée) : il ne rejoue pas tout, juste une phrase de rappel.
        if (lp != null && lp.isVisitFullyDone(p)) {
            p.sendMessage(name + " §7» §fTu connais l'histoire, maintenant. §cNe la laisse pas s'effacer §7— souviens-toi de la Fontaine Muette.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 0.7f, 0.9f);
            return;
        }

        // Le témoin ne parle qu'à qui a fait la visite complète (boussole encore en main = pas encore clôturée).
        if (lp == null || !lp.isVisitDone(p)) {
            if (lp != null && lp.isVisitInProgress(p)) {
                p.sendMessage(name + " §7» §fReviens quand tu auras vu de tes yeux. Le sable ne se raconte pas, il se traverse.");
            } else {
                p.sendMessage(name + " §7» §fJe n'ai rien à dire à qui n'a rien vu. Trouve d'abord ton chemin dans ces terres.");
            }
            return;
        }

        java.util.List<String> raw = config.raw().getStringList("npcs." + id + ".dialogue");
        if (raw == null || raw.isEmpty()) {
            if (p.isOp()) {
                p.sendMessage("§e[Admin] §7Ce témoin n'a pas de récit. Ajoute des lignes dans "
                        + "§fnpcs.yml §7→ §fnpcs." + id + ".dialogue §7puis §f/pnj reload§7.");
            }
            // Pas de dialogue : on clôt quand même pour ne pas bloquer la récompense.
            lp.completeVisit(p);
            return;
        }

        String[] lines = raw.toArray(new String[0]);
        // Le délai s'adapte déjà à la longueur de chaque réplique ; on ajoute juste un léger ×1.1 pour
        // que le Dernier Témoin ait un débit un peu plus posé que les autres PNJ.
        dlg.startDialogue(p, name, accent, lines, () -> {
            LogPoseManager lp2 = plugin.getLogPose();
            if (lp2 != null) lp2.completeVisit(p); // dernière réplique atteinte → clé + retrait boussole
        }, 1.1);
    }
}
