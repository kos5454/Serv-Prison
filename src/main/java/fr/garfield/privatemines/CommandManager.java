package fr.garfield.privatemines;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Dispatcher des commandes (/mine, /spawn, /money, /zone, /parcelle, resets admin...)
 * et complétion tab. Délègue aux managers et au cœur de PrivateMines.
 * Extrait de PrivateMines pour alléger le fichier principal.
 */
public class CommandManager implements CommandExecutor, TabCompleter {

    private final PrivateMines plugin;

    public CommandManager(PrivateMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /givemoney accepte la console
        if (command.getName().equalsIgnoreCase("givemoney")) {
            Player self = (sender instanceof Player) ? (Player) sender : null;
            return handleGiveMoney(sender, self, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande doit etre utilisee en jeu.");
            return true;
        }
        Player player = (Player) sender;

        // Toutes les commandes /reset* sont RÉSERVÉES AUX OP (2026-08-23). Elles étaient
        // utilisables par n importe qui sur soi-même : un joueur pouvait effacer ses enchants,
        // son sac ou ses mines sans remboursement, par accident ou sur mauvais conseil.
        // Filtre par PRÉFIXE : une future commande /resetXxx est protégée d office.
        if (command.getName().toLowerCase().startsWith("reset") && !player.isOp()) {
            player.sendMessage("§cCette commande est réservée aux administrateurs.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "mine":
                return handleMine(player, args);
            case "spawn":
                return handleSpawn(player);
            case "setspawn":
                return handleSetSpawn(player);
            case "money":
                return handleMoney(player, args);
            case "zone":
                return handleZone(player, args);
            case "quest":
            case "quests":
            case "quete":
            case "quetes":
                plugin.getQuests().openMenu(player);
                return true;
            case "particules":
                if (!player.isOp()) {
                    player.sendMessage("§cCette commande est réservée aux admins.");
                    return true;
                }
                plugin.getParticles().openMenu(player);
                return true;
            case "shop":
                plugin.getShop().openShopMain(player);
                return true;
            case "banque":
            case "bank":
                plugin.getIslandBank().openBank(player);
                return true;
            case "bp":
                plugin.getBackpack().openBackpack(player);
                return true;
            case "ob":
            case "oneblock":
            case "is":
                return handleOneBlock(player, args);
            case "collections":
            case "collection":
            case "collec":
                plugin.getCollections().open(player);
                return true;
            case "phases":
                return handlePhases(player, args);
            case "givemoney":
                return handleGiveMoney(sender, player, args);
            case "resetenchants":
                return handleResetEnchants(player, args);
            case "resetsac":
                return handleResetSac(player, args);
            case "resetmines":
                return handleResetMines(player, args);
            case "resetbv":
                return handleResetBv(player, args);
            case "resetarmures":
                return handleResetArmures(player, args);
            case "resetordi":
            case "resetordinateur":
                return handleResetOrdi(player, args);
            case "resetfragment":
            case "resetfragments":
                return handleResetFragments(player, args);
            case "resetlevelpioche":
            case "resetpiochelevel":
            case "resetpioche":
                return handleResetLevelPioche(player, args);
            case "spawner":
                return handleSpawner(player, args);
            case "ah":
                return plugin.getAuction().handleCommand(player, args);
            case "coinflip":
            case "cf":
                return plugin.getCoinflip().handleCommand(player, args);
            case "testgames":
                // Test OP des mini-jeux de chat : force une partie sans attendre les 15 min.
                return plugin.getChatGame().handleTestCommand(player, args);
            case "end":
                // /end : TP à la salle d'attente de l'End. /end forcedragon : OP, force le spawn.
                if (args.length >= 1 && args[0].equalsIgnoreCase("forcedragon")) {
                    if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }
                    plugin.getEnd().forceDragon();
                    player.sendMessage("§a✔ Dragon du Vide forcé.");
                    return true;
                }
                plugin.getEnd().teleporterSalle(player);
                return true;
            case "holo":
                return plugin.getHoloManager().handleHolo(player, args);
            case "classements": {
                // TP vers la zone des classements : -65, 86, 627, regard plein Nord (yaw 180).
                org.bukkit.Location classLoc = new org.bukkit.Location(
                        player.getWorld(), -65 + 0.5, 86, 627 + 0.5, 180f, 0f);
                player.teleport(classLoc);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                // Valide l'étape 10 de l'Apprentissage (consulter les classements).
                if (plugin.getTutorial() != null) plugin.getTutorial().onClassementsOpened(player);
                return true;
            }
            case "blockvalue":
                plugin.getBlockValueMenu().openBlockValue(player);
                return true;
            case "warp":
                plugin.getWarpMenu().openWarp(player);
                return true;
            case "pets":
                plugin.getPetMenu().openMain(player);
                return true;
            case "jobs":
            case "job":
            case "metier":
            case "metiers":
                plugin.getJobs().openMenu(player);
                return true;
            case "daily":
            case "quotidien":
                return handleDaily(player, args);
            case "commande":
            case "commandes":
            case "cmds":
            case "aide":
                plugin.getCommandMenu().open(player);
                return true;
            case "fly":
                plugin.toggleFly(player);
                return true;
            case "pickaxelevel":
            case "piochelevel":
                if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }
                if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
                    try {
                        int lvl = Math.max(1, Integer.parseInt(args[1]));
                        // Cible : un joueur nommé (arg 3) sinon soi-même.
                        Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2]) : player;
                        if (target == null) { player.sendMessage("§cJoueur introuvable."); return true; }
                        plugin.setPickaxeLevelPublic(target, lvl);
                        player.sendMessage("§aNiveau de pioche de §e" + target.getName() + " §afixé à §b" + lvl + "§a.");
                        if (target != player) target.sendMessage("§aTon niveau de pioche a été fixé à §b" + lvl + "§a.");
                    } catch (NumberFormatException ex) {
                        player.sendMessage("§cNombre invalide. Usage : /pickaxelevel set <niveau> [joueur]");
                    }
                    return true;
                }
                player.sendMessage("§7Usage : §f/pickaxelevel set <niveau> [joueur]");
                return true;
            case "fracturelevel":
                if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }
                if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
                    try {
                        int lvl = Math.max(0, Math.min(EnchantManager.FRACTURE_MAX, Integer.parseInt(args[1])));
                        Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2]) : player;
                        if (target == null) { player.sendMessage("§cJoueur introuvable."); return true; }
                        plugin.getEnchants().setFractureLevel(target.getUniqueId(), lvl);
                        plugin.applyPickaxeEnchants(target); // rafraîchit le lore/enchant de la pioche
                        player.sendMessage("§aNiveau de Fracture de §e" + target.getName() + " §afixé à §b" + lvl + "§a.");
                        if (target != player) target.sendMessage("§aTon niveau de Fracture a été fixé à §b" + lvl + "§a.");
                    } catch (NumberFormatException ex) {
                        player.sendMessage("§cNombre invalide. Usage : /fracturelevel set <niveau> [joueur]");
                    }
                    return true;
                }
                player.sendMessage("§7Usage : §f/fracturelevel set <niveau> [joueur]");
                return true;
            case "prestige":
            case "renaissance":
                // /prestige set <niveau> [joueur] : réservé OP (test). Le menu + la vraie renaissance
                // (reset pioche/enchants au Portail) arrivent dans les briques suivantes.
                if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
                    if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }
                    try {
                        int niv = Math.max(0, Integer.parseInt(args[1]));
                        Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2]) : player;
                        if (target == null) { player.sendMessage("§cJoueur introuvable."); return true; }
                        plugin.setPrestige(target.getUniqueId(), niv);
                        plugin.savePlayer(target);
                        plugin.refreshPrestigeTab(target);
                        player.sendMessage("§aPrestige de §e" + target.getName() + " §afixé à §6⭐" + niv
                                + " §7(+§b" + plugin.getPrestigeSellPercent(target) + " %§7 vente).");
                        if (target != player) target.sendMessage("§aTon prestige a été fixé à §6⭐" + niv + "§a.");
                    } catch (NumberFormatException ex) {
                        player.sendMessage("§cNombre invalide. Usage : /prestige set <niveau> [joueur]");
                    }
                    return true;
                }
                // /prestige seul : affiche l'état courant + comment renaître.
                int pr = plugin.getPrestige(player);
                int niveauPioche = plugin.getPickaxeLevel(player);
                player.sendMessage("§8§m                                        ");
                player.sendMessage("§6§l✦ PRESTIGE ✦ §7— La Renaissance des Abysses");
                player.sendMessage("§7Ton rang : §6⭐" + pr
                        + (pr == 0 ? " §8(aucune renaissance)" : " §8(René " + PrivateMines.toRoman(pr) + ")"));
                player.sendMessage("§7Bonus de vente actuel : §a+" + plugin.getPrestigeSellPercent(player) + " %");
                player.sendMessage("§7Prochain rang §6⭐" + (pr + 1) + " §7: §a+" + ((pr + 1) * 50) + " % §7de vente");
                player.sendMessage("");
                if (plugin.peutRenaitre(player)) {
                    player.sendMessage("§a✔ Ta pioche est au bout (§e" + niveauPioche + "§a) — le §5Portail§a t'attend !");
                    player.sendMessage("§7Descends au fond du §5vieux Puits des Souvenirs §8(à la mine de l'Arc 1)§7 et §eentre dans le Portail§7.");
                } else {
                    player.sendMessage("§c🔒 Pioche §e" + niveauPioche + "§7/§d" + PrivateMines.PRESTIGE_REQ_PICKAXE
                            + " §7— monte-la au max pour allumer le §5Portail§7.");
                }
                player.sendMessage("§8Chaque renaissance : §c-pioche, enchants & argent§8, §a+50 % vente§8, pour toujours.");
                player.sendMessage("§8§m                                        ");
                return true;
            case "keys":
            case "cles":
                plugin.getCrates().openKeys(player);
                return true;
            case "crate":
            case "crates":
                return plugin.getCrates().handleCrate(player, args);
            case "logpose":
                if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }
                if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
                    plugin.getLogPose().reset(player);
                } else {
                    plugin.getLogPose().giveLogPose(player); // /logpose = se donner/recaler la boussole
                }
                return true;
            case "bvn":
                return handleBvn(player);
            case "guide":
                return handleGuide(player);
            case "stats":
                return handleStats(player);
            case "intro":
                return handleIntro(player);
            case "pnj":
            case "npc":
                return handlePnj(player, args);
            case "mur":
            case "wall":
                return plugin.getWalls().handleMur(player, args);
            default:
                return false;
        }
    }

    // /pnj : place des PNJ (à ta position) et les configure via un menu (rôle, nom, couleur, skin).
    private boolean handlePnj(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("privatemines.pnj")) {
            player.sendMessage("§cTu n'as pas la permission de gérer les PNJ.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§e§lPNJ — Acte I");
            player.sendMessage("§7/pnj place §8» crée un PNJ à ta position exacte");
            player.sendMessage("§7/pnj setting §8» ouvre le menu de config des PNJ");
            player.sendMessage("§7/pnj list §8» liste les PNJ et leurs positions");
            player.sendMessage("§7/pnj tp <id> §8» te téléporte à un PNJ (ex: /pnj tp pnj1)");
            player.sendMessage("§7/pnj remove <id> §8» supprime un PNJ");
            player.sendMessage("§7/pnj setchest §8» coffre-quête Acte I (Jeton de Marée)");
            player.sendMessage("§7/pnj setchest2 §8» coffre-quête Acte II (Tête de Pioche)");
            player.sendMessage("§8/pnj setpuits §8» obsolète (le secret du métal est automatique)");
            player.sendMessage("§7/pnj skin add <nom> §8» enregistre un skin dans la bibliothèque");
            player.sendMessage("§7/pnj skin list §8» liste les skins enregistrés");
            player.sendMessage("§7/pnj skin remove <nom> §8» supprime un skin");
            player.sendMessage("§7/pnj reload §8» recharge npcs.yml");
            player.sendMessage("§7/pnj info §8» ton avancement dans l'Acte I");
            player.sendMessage("§7/pnj resetquest [joueur] [1|2|3|4] §8» reset la quête (test) — 1=Acte I, 2=Acte II, 3=Alabasta, 4=Ordinateur Quantique, rien=tout");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "place": {
                String id = plugin.getNpc().placeNpc(player);
                player.sendMessage("§a✔ PNJ §f" + id + " §acréé à ta position !");
                player.sendMessage("§7Configure-le : §f/pnj setting §7(rôle, nom, couleur, skin).");
                plugin.getNpcSettingMenu().openEdit(player, id);
                return true;
            }
            case "setting":
            case "settings":
                plugin.getNpcSettingMenu().openList(player);
                return true;
            case "setchest": {
                org.bukkit.block.Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    player.sendMessage("§cRegarde un coffre (à moins de 6 blocs) puis refais /pnj setchest.");
                    return true;
                }
                plugin.getActe().setChestLocation(target);
                player.sendMessage("§a✔ Coffre-quête §e(Acte I) §adéfini en §f" + target.getX() + ", "
                        + target.getY() + ", " + target.getZ() + " §a(§f" + target.getWorld().getName() + "§a).");
                player.sendMessage("§7Tous les joueurs y trouveront leur propre Jeton de Marée.");
                return true;
            }
            case "setchest2": {
                org.bukkit.block.Block target = player.getTargetBlockExact(6);
                if (target == null) {
                    player.sendMessage("§cRegarde un coffre (à moins de 6 blocs) puis refais /pnj setchest2.");
                    return true;
                }
                plugin.getActe().setChest2Location(target);
                player.sendMessage("§a✔ Coffre-quête §6(Acte II) §adéfini en §f" + target.getX() + ", "
                        + target.getY() + ", " + target.getZ() + " §a(§f" + target.getWorld().getName() + "§a).");
                player.sendMessage("§7Tous les joueurs y trouveront leur propre Tête de Pioche des Souvenirs.");
                return true;
            }
            case "setpuits": {
                // Conservée pour ne pas surprendre : le secret ne dépend plus d'une position.
                player.sendMessage("§7Cette commande n'est plus utile.");
                player.sendMessage("§7Le §5secret du métal §7part maintenant tout seul, §e4 s §7après que le");
                player.sendMessage("§7joueur a pris sa §6Tête de Pioche §7au coffre §8(/pnj setchest2)§7.");
                return true;
            }
            case "skin": {
                if (args.length < 2) {
                    player.sendMessage("§cUsage : /pnj skin add <nom> | list | remove <nom>");
                    return true;
                }
                String action = args[1].toLowerCase();
                if (action.equals("list")) {
                    java.util.Set<String> names = plugin.getSkins().getNames();
                    if (names.isEmpty()) { player.sendMessage("§7Aucun skin enregistré."); return true; }
                    player.sendMessage("§e§lSkins enregistrés :");
                    for (String n : names) player.sendMessage("§7- §f" + n);
                    return true;
                }
                if (action.equals("add")) {
                    if (args.length < 3) { player.sendMessage("§cUsage : /pnj skin add <nom>"); return true; }
                    String name = args[2].toLowerCase();
                    plugin.getNpc().startLibrarySkinInput(player, name);
                    return true;
                }
                if (action.equals("remove")) {
                    if (args.length < 3) { player.sendMessage("§cUsage : /pnj skin remove <nom>"); return true; }
                    String name = args[2].toLowerCase();
                    if (!plugin.getSkins().has(name)) {
                        player.sendMessage("§cAucun skin §f" + name + " §cà supprimer.");
                        return true;
                    }
                    plugin.getSkins().remove(name);
                    player.sendMessage("§a✔ Skin §f" + name + " §asupprimé de la bibliothèque.");
                    return true;
                }
                player.sendMessage("§cUsage : /pnj skin add <nom> | list | remove <nom>");
                return true;
            }
            case "reload":
                plugin.getNpc().reloadNpcs();
                plugin.getSkins().reload();
                player.sendMessage("§a✔ npcs.yml + skins.yml rechargés.");
                return true;
            case "resetquest": {
                // Syntaxe : /pnj resetquest [joueur] [1|2|3|4]
                //   sans acte  -> reset TOUT (Acte I + II)
                //   1          -> reset seulement l'Acte I
                //   2          -> reset seulement l'Acte II
                //   3          -> reset la visite d'Alabasta (Gardien du Seuil + boussole du Log Pose)
                //   4          -> reset l'Ordinateur Quantique (retire l'item + rejoue la cinématique au niv.50)
                Player target = player;
                String acte = null; // "1", "2", "3", "4" ou null (tout)

                // On lit les 2 arguments possibles (joueur et/ou numéro d'acte) dans n'importe quel ordre.
                for (int i = 1; i < args.length && i <= 2; i++) {
                    String a = args[i];
                    if (a.equals("1") || a.equals("2") || a.equals("3") || a.equals("4")) {
                        acte = a;
                    } else {
                        if (!player.isOp()) {
                            player.sendMessage("§cSeuls les opérateurs peuvent réinitialiser un autre joueur.");
                            return true;
                        }
                        target = Bukkit.getPlayerExact(a);
                        if (target == null) {
                            player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + a);
                            return true;
                        }
                    }
                }

                String acteLabel;
                if ("1".equals(acte)) {
                    plugin.getActe().resetActe1(target);
                    acteLabel = "Acte I";
                } else if ("2".equals(acte)) {
                    plugin.getActe().resetActe2(target);
                    acteLabel = "Acte II";
                } else if ("3".equals(acte)) {
                    plugin.getLogPose().reset(target); // visite d'Alabasta : le Gardien du Seuil pourra redonner la boussole
                    acteLabel = "Alabasta (Gardien du Seuil)";
                } else if ("4".equals(acte)) {
                    plugin.getActe().resetActe4(target); // retire l'Ordinateur Quantique + le flag « déjà reçu »
                    acteLabel = "Ordinateur Quantique (rejoue au niv.50)";
                } else {
                    plugin.getActe().resetQuest(target);
                    acteLabel = "Actes I & II";
                }

                if (target == player) {
                    player.sendMessage("§a✔ Ta progression (§f" + acteLabel + "§a) a été remise à zéro.");
                } else {
                    player.sendMessage("§a✔ §f" + acteLabel + " §aréinitialisé pour §f" + target.getName() + "§a.");
                    target.sendMessage("§7Ta progression (" + acteLabel + ") a été réinitialisée par un admin.");
                }
                return true;
            }
            case "info": {
                int step = plugin.getActe().getStep(player);
                String[] labels = {
                        "0 — doit parler au Veilleur",
                        "1 — doit parler à l'Ancre",
                        "2 — doit trouver le Jeton (coffre)",
                        "3 — doit rapporter le Jeton à l'Ancre",
                        "4 — Acte I terminé (aventure débloquée)"
                };
                player.sendMessage("§e§lTon Acte I §7— étape §f" + labels[Math.min(step, 4)]);
                return true;
            }
            case "remove": {
                if (args.length < 2) { player.sendMessage("§cUsage : /pnj remove <id> (ex: pnj1)"); return true; }
                boolean removed = plugin.getNpc().removeNpc(args[1].toLowerCase());
                player.sendMessage(removed ? "§a✔ PNJ §f" + args[1] + " §asupprimé."
                                           : "§cAucun PNJ §f" + args[1] + " §cà supprimer.");
                return true;
            }
            case "tp": {
                if (args.length < 2) { player.sendMessage("§cUsage : /pnj tp <id> (ex: pnj1)"); return true; }
                Location loc = plugin.getNpc().getNpcLocation(args[1].toLowerCase());
                if (loc == null) { player.sendMessage("§cPNJ §f" + args[1] + " §cintrouvable."); return true; }
                player.teleport(loc);
                player.sendMessage("§aTéléporté au PNJ §f" + args[1] + "§a.");
                return true;
            }
            case "list": {
                java.util.Set<String> ids = plugin.getNpc().getIds();
                if (ids.isEmpty()) {
                    player.sendMessage("§7Aucun PNJ. Fais §f/pnj place §7pour en créer un.");
                    return true;
                }
                player.sendMessage("§e§lPNJ :");
                NpcConfig c = plugin.getNpc().getCfg();
                for (String id : ids) {
                    player.sendMessage("§7- §f" + id + " §8(" + c.getDisplayName(id) + "§8, rôle: "
                            + c.getRole(id) + ") §7: §f"
                            + (int) c.getX(id) + ", " + (int) c.getY(id) + ", " + (int) c.getZ(id));
                }
                return true;
            }
            default:
                player.sendMessage("§cSous-commande inconnue. /pnj pour l'aide.");
                return true;
        }
    }

    // /intro : rejoue la cinématique d'ouverture de l'Acte I (test admin). Ne touche pas au flag "déjà vu".
    private boolean handleIntro(Player player) {
        if (!player.isOp() && !player.hasPermission("privatemines.intro")) {
            player.sendMessage("§cTu n'as pas la permission de rejouer l'intro.");
            return true;
        }
        player.sendMessage("§7Rejoue de la cinématique d'ouverture...");
        plugin.getIntro().playIntro(player, false);
        return true;
    }

    // /resetenchants [joueur] : remet à 0 tous les enchants de la pioche (soi-même, ou une cible si OP).
    private boolean handleResetEnchants(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset les enchants d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        plugin.getEnchants().resetAll(target.getUniqueId());
        plugin.applyPickaxeEnchants(target);
        plugin.savePlayer(target);
        target.sendMessage("§aTes enchants de pioche ont été remis à §f0§a (Efficacité, Explosion, Forage, Fracture, Vein).");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        if (target != player) player.sendMessage("§aEnchants de §f" + target.getName() + " §areset.");
        return true;
    }

    // /resetbv [joueur] : remet à zéro les stats du menu /bv (blocs minés, argent encaissé,
    // record sur 1 min). Ne touche à RIEN d'autre : ni les niveaux, ni l'argent, ni le sac.
    private boolean handleResetBv(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset les stats d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        plugin.resetBlockStats(target.getUniqueId());
        plugin.savePlayer(target);
        target.sendMessage("§a✔ Stats des blocs remises à §f0 §7(minés, encaissés, record sur 1 min).");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        if (target != player) player.sendMessage("§aStats /bv de §f" + target.getName() + " §aremises à zéro.");
        return true;
    }

    // /resetarmures [joueur] : retire toutes les armures d'Oublié (portées, en sac, et dans les
    // DEUX inventaires sauvegardés mine/hors-mine). Ne touche NI aux Fragments de Souvenir NI au
    // compteur d'armures recyclées : les matières débloquées et les boosts de drop sont conservés.
    private boolean handleResetArmures(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset les armures d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        int retirees = plugin.purgeArmuresOubli(target);
        plugin.savePlayer(target);
        if (retirees == 0) {
            player.sendMessage("§7Aucune armure d'Oublié trouvée" + (target == player ? "." : " chez §f" + target.getName() + "§7."));
            return true;
        }
        target.sendMessage("§a✔ §f" + retirees + " §aarmure(s) d'Oublié retirée(s). §7Fragments et recyclages conservés.");
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.2f);
        if (target != player) player.sendMessage("§a✔ §f" + retirees + " §aarmure(s) retirée(s) à §f" + target.getName() + "§a.");
        return true;
    }

    // /resetfragment [joueur] : remet le solde de Fragments de Souvenir à 0.
    // ⚠ Volontairement SÉPARÉE de /resetordi : les Fragments sont la MONNAIE de la forge, le
    // compteur de recyclage est de la PROGRESSION. Effacer l'un ne doit jamais effacer l'autre.
    private boolean handleResetFragments(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset les Fragments d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        FragmentManager fm = plugin.getFragments();
        java.math.BigInteger avant = fm.getFragments(target.getUniqueId());
        if (avant.signum() == 0) {
            player.sendMessage("§7Aucun Fragment" + (target == player ? " à effacer." : " chez §f" + target.getName() + "§7."));
            return true;
        }
        fm.setFragments(target.getUniqueId(), java.math.BigInteger.ZERO);
        fm.save();
        target.sendMessage("§a✔ Fragments de Souvenir remis à §f0 §7(§d" + PrivateMines.formatNumberBig(avant)
                + "§7 effacé(s)). Recyclages et tri auto conservés.");
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.4f);
        if (target != player) player.sendMessage("§a✔ Fragments de §f" + target.getName() + " §areset.");
        return true;
    }

    // /resetordi [joueur] : remet l'Ordinateur Quantique à zéro — compteur d'armures recyclées et
    // réglages de tri auto (matières + seuil).
    // ⚠ Ne touche NI aux Fragments (c'est une monnaie, choix du user) NI aux armures elles-mêmes :
    // pour les objets c'est /resetarmures, les deux commandes sont volontairement séparées.
    private boolean handleResetOrdi(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset l'Ordinateur d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        FragmentManager fm = plugin.getFragments();
        int avant = fm.getArmuresRecyclees(target);
        fm.resetOrdinateur(target.getUniqueId());
        fm.save();
        target.sendMessage("§a✔ Ordinateur Quantique réinitialisé §7(" + avant
                + " recyclage(s) et le tri auto effacés). §7Fragments conservés.");
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
        if (target != player) player.sendMessage("§a✔ Ordinateur Quantique de §f" + target.getName() + " §areset.");
        return true;
    }

    // /resetsac [joueur] : remet le sac à l'état de départ (niveaux capacité/vente à 1, stock à 0).
    private boolean handleResetSac(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset le sac d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        plugin.getCapacityLevelMap().put(target.getUniqueId(), 1);
        plugin.getSellLevelMap().put(target.getUniqueId(), 1);
        plugin.getMoneyMultLevelMap().put(target.getUniqueId(), 0);
        plugin.setStock(target, 0);
        plugin.setBagValue(target, 0.0);
        plugin.setCoalStock(target, 0);
        plugin.setCoalValue(target, 0.0);
        plugin.savePlayer(target);
        target.sendMessage("§aTon sac a été réinitialisé §7(capacité, vente & bonus niv. 1, stock vidé).");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        if (target != player) player.sendMessage("§aSac de §f" + target.getName() + " §areset.");
        return true;
    }

    // /resetlevelpioche [joueur] : remet le niveau de pioche (barre d'XP) à 1 et l'XP à 0.
    private boolean handleResetLevelPioche(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset le niveau de pioche d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        plugin.setPickaxeLevel(target.getUniqueId(), 1);
        plugin.setPickaxeXp(target.getUniqueId(), 0);
        plugin.updatePickaxeBar(target);
        plugin.savePlayer(target);
        target.sendMessage("§aTon niveau de pioche a été remis à §b1§a.");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        if (target != player) player.sendMessage("§aNiveau de pioche de §f" + target.getName() + " §areset à 1.");
        return true;
    }

    // /resetmines [joueur] : reverrouille les mines B et C (le joueur ne les a plus).
    // /spawner tool [joueur] — donne un Pic du Démonteur (5 utilisations). Réservé aux OP.
    private boolean handleSpawner(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§cCette commande est réservée aux opérateurs.");
            return true;
        }
        // Purge les spawners restés actifs sur des Îles supprimées (mobs qui respawnent dans le vide).
        if (args.length >= 1 && args[0].equalsIgnoreCase("cleanup")) {
            int n = plugin.getSpawnerManager().cleanupOrphanSpawners();
            player.sendMessage(n == 0
                    ? "§7Aucun spawner orphelin trouvé."
                    : "§a✔ " + n + " spawner(s) orphelin(s) supprimé(s).");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("tool")) {
            player.sendMessage("§7Usage : §f/spawner tool [joueur] §7· §f/spawner cleanup");
            return true;
        }
        Player target = player;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[1]);
                return true;
            }
        }
        ItemStack tool = plugin.getSpawnerManager().makeTool();
        java.util.Map<Integer, ItemStack> left = target.getInventory().addItem(tool);
        for (ItemStack rem : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), rem);
        target.sendMessage("§5⛏ §7Tu as reçu un §5Pic du Démonteur §7("
                + SpawnerManager.TOOL_USES + " utilisations).");
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
        if (target != player) {
            player.sendMessage("§aPic du Démonteur donné à §f" + target.getName() + "§a.");
        }
        return true;
    }

    private boolean handleResetMines(Player player, String[] args) {
        Player target = player;
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage("§cSeuls les opérateurs peuvent reset les mines d'un autre joueur.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cJoueur introuvable ou hors-ligne : §f" + args[0]);
                return true;
            }
        }
        String pm = plugin.getPlayerMine(target);
        boolean wasInCoalMine = plugin.mineHasCoalPublic(pm);
        // Retire toutes les mines débloquées (table MINES) et efface leurs flags de sauvegarde.
        plugin.clearUnlockedMines(target);
        for (PrivateMines.MineDef d : PrivateMines.MINES) {
            plugin.getDataConfig().set(target.getUniqueId() + ".unlockedMine" + d.code, false);
        }
        plugin.getPlayerMineMap().put(target.getUniqueId(), "A");
        plugin.getCoalPositions(target).clear();
        plugin.getCoalBlockPositions(target).clear();
        plugin.updateMineTag(target);
        plugin.savePlayer(target);
        // Si le joueur était dans une mine à charbon, on rafraîchit sa vue vers la Mine A.
        if (wasInCoalMine && plugin.isPlayerInMineArea(target.getLocation())) {
            plugin.getBroken(target).clear();
            World w = Bukkit.getWorld("world");
            if (w != null) plugin.sendFakeMine(target, w);
        }
        target.sendMessage("§aTes mines débloquées ont été réinitialisées §7(seul le Port des Moussaillons reste).");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        if (target != player) player.sendMessage("§aMines de §f" + target.getName() + " §areset.");
        return true;
    }

    // /quest : rappelle au joueur son objectif courant (le même texte que la BossBar de guidage).
    // /mine : ouvre le menu de sélection de mine.
    private boolean handleMine(Player player, String[] args) {
        // Sous-commande admin : /mine set <n> — débloque EXACTEMENT les mines 1..n (OP uniquement).
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!player.isOp()) {
                player.sendMessage("§cCette commande est réservée aux admins.");
                return true;
            }
            int max = PrivateMines.MINES.size();
            if (args.length < 2) {
                player.sendMessage("§cUsage: §e/mine set <n> §7(1 à " + max + ")");
                return true;
            }
            int n;
            try { n = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                player.sendMessage("§cNombre invalide : §e" + args[1]);
                return true;
            }
            int applied = plugin.setMineProgress(player, n);
            String bestName = plugin.getMineDisplayNameShort(plugin.bestUnlockedMineCode(player));
            player.sendMessage("§a✔ §7Progression réglée : mines §e1 à " + applied + " §7débloquées.");
            player.sendMessage("§7Meilleure mine : " + bestName);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            return true;
        }
        // Sous-commande admin de TEST : /mine armortest <piece> — génère une armure d'Oublié à bonus.
        // <piece> = cuir|mailles|fer|or|diamant|netherite + casque|plastron|jambieres|bottes (ex: "diamant plastron").
        if (args.length >= 1 && args[0].equalsIgnoreCase("armortest")) {
            if (!player.isOp()) {
                player.sendMessage("§cCette commande est réservée aux admins.");
                return true;
            }
            String mat = args.length >= 2 ? args[1].toLowerCase() : "fer";
            String type = args.length >= 3 ? args[2].toLowerCase() : "plastron";
            org.bukkit.Material m = armorMaterialFromArgs(mat, type);
            if (m == null) {
                player.sendMessage("§cUsage: §e/mine armortest <cuir|mailles|fer|or|diamant|netherite> <casque|plastron|jambieres|bottes>");
                return true;
            }
            ArmorManager am = plugin.getArmor();
            ArmorManager.Rarete rarete = ArmorManager.rareteDeBase(m);
            long graine = player.getUniqueId().getLeastSignificantBits() ^ System.nanoTime();
            org.bukkit.inventory.ItemStack armure = am.genererArmure(new org.bukkit.inventory.ItemStack(m), rarete, graine);
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> reste = player.getInventory().addItem(armure);
            for (org.bukkit.inventory.ItemStack drop : reste.values())
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            player.sendMessage("§a✔ §7Armure d'Oublié générée : " + rarete.display + " §7(" + mat + " " + type + ").");
            player.sendMessage("§8Équipe-la pour activer ses bonus. §7Voir §e/stats§7.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.1f);
            return true;
        }
        // Acte I : /mine reste verrouillé tant que l'aventure n'est pas débloquée (sauf OP).
        if (!player.isOp() && !plugin.getActe().isActeDone(player)) {
            player.sendMessage("§b⚓ §7La grève ne te laisse pas encore partir.");
            player.sendMessage("§7Parle au §bVeilleur§7, puis à §al'Ancre§7, et trouve le §eJeton de Marée§7.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return true;
        }
        plugin.openMineMenu(player);
        return true;
    }

    /** Résout une matière+pièce d'armure ("diamant","plastron") en Material, ou null si invalide. */
    private org.bukkit.Material armorMaterialFromArgs(String mat, String type) {
        String prefix;
        switch (mat) {
            case "cuir":      prefix = "LEATHER"; break;
            case "mailles":   prefix = "CHAINMAIL"; break;
            case "fer":       prefix = "IRON"; break;
            case "or":        prefix = "GOLDEN"; break;
            case "diamant":   prefix = "DIAMOND"; break;
            case "netherite": prefix = "NETHERITE"; break;
            default: return null;
        }
        String suffix;
        switch (type) {
            case "casque":    suffix = "HELMET"; break;
            case "plastron":  suffix = "CHESTPLATE"; break;
            case "jambieres": suffix = "LEGGINGS"; break;
            case "bottes":    suffix = "BOOTS"; break;
            default: return null;
        }
        try {
            return org.bukkit.Material.valueOf(prefix + "_" + suffix);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean handleGiveMoney(CommandSender sender, Player self, String[] args) {
        if (!sender.isOp() && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§cTu n'as pas la permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /givemoney <joueur> <montant>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable : " + args[0]);
            return true;
        }
        double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) {
            sender.sendMessage("§cMontant invalide.");
            return true;
        }
        Economy economy = plugin.getEconomy();
        if (economy == null) { sender.sendMessage("§cPas d'économie disponible."); return true; }
        economy.depositPlayer(target, amount);
        sender.sendMessage("§a+" + PrivateMines.formatNumber(amount) + "$ §7donné à §e" + target.getName() + "§7.");
        if (!target.equals(self)) target.sendMessage("§a+" + PrivateMines.formatNumber(amount) + "$ §7reçus par un admin !");
        return true;
    }

    // /spawn : téléporte au spawn enregistré.
    private boolean handleSpawn(Player player) {
        Location spawn = plugin.getSpawnLocation();
        if (spawn == null) {
            player.sendMessage("§cLe spawn n'a pas encore ete defini (/setspawn).");
            return true;
        }
        // Sortie de la mine si besoin : range pioche+sac, restaure l'inventaire hors-mine.
        plugin.leaveMineIfNeeded(player);
        player.setWorldBorder(null); // supprime la WorldBorder personnelle (parcelle)
        plugin.getPlayerZoneMap().put(player.getUniqueId(), "spawn");
        player.teleport(spawn);
        player.sendMessage("§aTeleporte au spawn !");
        return true;
    }

    // /setspawn : enregistre la position actuelle comme spawn (dans config.yml).
    private boolean handleSetSpawn(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        player.sendMessage("§aSpawn defini a ta position actuelle !");
        return true;
    }

    // /zone <sous-commande> : gère les zones protégées.
    private boolean handleZone(Player player, String[] args) {
        ZoneManager zones = plugin.getZones();
        if (args.length == 0) {
            player.sendMessage("§eCommandes : §7/zone pos1, /zone pos2, /zone create <nom>, /zone list, /zone delete <nom>, /zone info");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "info":
                player.sendMessage("§b§lFonctionnement des zones");
                player.sendMessage("§7Une zone est une boite (2 coins X/Z, toute hauteur) ou tu actives des regles.");
                player.sendMessage("§e/zone pos1 §7- definit le 1er coin a ta position");
                player.sendMessage("§e/zone pos2 §7- definit le 2e coin a ta position");
                player.sendMessage("§e/zone create <nom> §7- cree la zone entre pos1 et pos2");
                player.sendMessage("§e/zone list §7- liste les zones existantes");
                player.sendMessage("§e/zone delete <nom> §7- supprime une zone");
                player.sendMessage("§e/zone preset §7- ouvre le menu pour regler les flags (casse/pose/pvp/mobs/animaux)");
                player.sendMessage("§7Par defaut : casse, pose et PvP sont bloques ; mobs et animaux sont autorises.");
                return true;
            case "preset":
                plugin.openZoneListMenu(player);
                return true;
            case "pos1":
                zones.setPos1(player, player.getLocation());
                player.sendMessage("§aCoin 1 defini a ta position.");
                return true;
            case "pos2":
                zones.setPos2(player, player.getLocation());
                player.sendMessage("§aCoin 2 defini a ta position.");
                return true;
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cUsage : /zone create <nom>");
                    return true;
                }
                String err = zones.createZone(player, args[1]);
                if (err != null) {
                    player.sendMessage("§c" + err);
                } else {
                    player.sendMessage("§aZone '" + args[1] + "' creee ! (casser/poser/PvP bloques par defaut)");
                }
                return true;
            case "list":
                java.util.Set<String> names = zones.getZoneNames();
                if (names.isEmpty()) {
                    player.sendMessage("§7Aucune zone definie.");
                } else {
                    player.sendMessage("§eZones : §7" + String.join(", ", names));
                }
                return true;
            case "delete":
                if (args.length < 2) {
                    player.sendMessage("§cUsage : /zone delete <nom>");
                    return true;
                }
                if (!zones.zoneExists(args[1])) {
                    player.sendMessage("§cCette zone n'existe pas.");
                } else {
                    zones.deleteZone(args[1]);
                    player.sendMessage("§aZone '" + args[1] + "' supprimee.");
                }
                return true;
            default:
                player.sendMessage("§cSous-commande inconnue.");
                return true;
        }
    }

    // /ob — OneBlock de parcelle (« Le Bloc du Grand Appel »).
    //   /ob                    -> TP devant ton bloc
    //   /ob help               -> aide
    //   [OP] /ob setphase <1-12> [joueur]
    //   [OP] /ob setblocs <n> [joueur]
    //   [OP] /ob next [joueur]
    //   [OP] /ob reset [joueur]
    //   [OP] /ob info [joueur]
    private boolean handleOneBlock(Player player, String[] args) {
        OneBlockManager ob = plugin.getOneBlock();

        // Sans argument : TP sur l'Île (tpToParcelle pose le bloc sous les pieds + WorldBorder).
        if (args.length == 0) {
            plugin.tpToParcelle(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            player.sendMessage("§6§l🏝 Ton Île — §e/ob");
            player.sendMessage("§e/ob §7— te dépose sur ton Île (sur ton Bloc du Grand Appel).");
            player.sendMessage("§e/ob setting §7— paramètres de l'Île (visiteurs, agrandir...).");
            player.sendMessage("§e/ob visit <joueur> §7— visiter l'Île d'un joueur.");
            player.sendMessage("§e/ob friend <joueur> §7— ajouter un ami. §7/ob accept §7— accepter.");
            player.sendMessage("§e/ob unfriend <joueur> §7— retirer un ami.");
            player.sendMessage("§e/phases §7— voir les blocs obtenables et les paliers.");
            player.sendMessage("§e/ob top §7— classement des Îles (les plus avancées).");
            player.sendMessage("§e/ob merchant §7— le marchand ambulant. §8(à venir)");
            if (player.isOp()) {
                player.sendMessage("§8[OP] /ob forcemob §7· §8forcechest §7· §8forcebutin §7· §8forcemerchant §7(tests)");
            }
            return true;
        }

        // ===== Sous-commandes ÎLE (ex-/parcelle) =====
        switch (sub) {
            case "setting":
            case "settings":
                plugin.openParcelleSettings(player);
                return true;
            case "visit": {
                if (args.length < 2) { player.sendMessage("§cUsage : /ob visit <joueur>"); return true; }
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore()) { player.sendMessage("§cJoueur introuvable."); return true; }
                Parcelle targetParc = plugin.getParcelleManager().getParcelle(target.getUniqueId());
                if (targetParc == null) { player.sendMessage("§cCe joueur n'a pas d'Île."); return true; }
                if (!targetParc.canEnter(player.getUniqueId())) { player.sendMessage("§cCette Île est privée."); return true; }
                plugin.leaveMineIfNeeded(player);
                plugin.getPlayerZoneMap().put(player.getUniqueId(), "parcelle");
                org.bukkit.World w = Bukkit.getWorld("world");
                player.teleport(new org.bukkit.Location(w,
                        targetParc.getCenterX() + 0.5, plugin.getParcelleManager().getFloorY() + 1, targetParc.getCenterZ() + 0.5));
                org.bukkit.WorldBorder wbVisit = Bukkit.createWorldBorder();
                wbVisit.setCenter(targetParc.getCenterX() + 0.5, targetParc.getCenterZ() + 0.5);
                wbVisit.setSize(targetParc.getSize() + 1); // +1 pour englober les blocs de bord (centre sur +0.5)
                wbVisit.setWarningDistance(0); wbVisit.setWarningTime(0);
                player.setWorldBorder(wbVisit);
                player.sendMessage("§aTu visites " + plugin.islandDisplayName(targetParc, target.getName()) + "§a !");
                return true;
            }
            case "friend": {
                if (args.length < 2) { player.sendMessage("§cUsage : /ob friend <joueur>"); return true; }
                Parcelle myParc = plugin.getParcelleManager().getParcelle(player.getUniqueId());
                if (myParc == null) { player.sendMessage("§cTu n'as pas d'Île."); return true; }
                org.bukkit.OfflinePlayer invitee = Bukkit.getOfflinePlayer(args[1]);
                if (invitee == null || !invitee.hasPlayedBefore()) { player.sendMessage("§cJoueur introuvable."); return true; }
                if (invitee.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("§cTu ne peux pas t'ajouter toi-même."); return true; }
                if (myParc.getInvited().contains(invitee.getUniqueId())) { player.sendMessage("§e" + invitee.getName() + " §7est déjà ton ami."); return true; }
                if (myParc.getInvited().size() >= myParc.getMaxInvited()) {
                    player.sendMessage("§cLimite d'amis atteinte (§e" + myParc.getMaxInvited() + "§c). Achète plus de slots dans §e/ob setting§c.");
                    return true;
                }
                myParc.getInvited().add(invitee.getUniqueId());
                plugin.getParcelleManager().save();
                player.sendMessage("§a§e" + invitee.getName() + " §aest désormais ton ami !");
                plugin.getPendingInvitations().put(invitee.getUniqueId(), player.getUniqueId());
                Player inviteeOnline = Bukkit.getPlayer(invitee.getUniqueId());
                if (inviteeOnline != null) {
                    inviteeOnline.sendMessage("§e" + player.getName() + " §avous a ajouté en ami ! Rejoignez son Île avec §e/ob accept");
                }
                return true;
            }
            case "accept": {
                java.util.UUID ownerUuid = plugin.getPendingInvitations().remove(player.getUniqueId());
                if (ownerUuid == null) { player.sendMessage("§cTu n'as pas d'invitation en attente."); return true; }
                Parcelle ownerParc = plugin.getParcelleManager().getParcelle(ownerUuid);
                if (ownerParc == null) { player.sendMessage("§cL'Île n'existe plus."); return true; }
                plugin.leaveMineIfNeeded(player);
                plugin.getPlayerZoneMap().put(player.getUniqueId(), "parcelle");
                org.bukkit.World wAcc = Bukkit.getWorld("world");
                player.teleport(new org.bukkit.Location(wAcc,
                        ownerParc.getCenterX() + 0.5, plugin.getParcelleManager().getFloorY() + 1, ownerParc.getCenterZ() + 0.5));
                org.bukkit.WorldBorder wbAcc = Bukkit.createWorldBorder();
                wbAcc.setCenter(ownerParc.getCenterX() + 0.5, ownerParc.getCenterZ() + 0.5);
                wbAcc.setSize(ownerParc.getSize() + 1);
                wbAcc.setWarningDistance(0); wbAcc.setWarningTime(0);
                player.setWorldBorder(wbAcc);
                player.sendMessage("§aTu visites l'Île de §e" + Bukkit.getOfflinePlayer(ownerUuid).getName() + "§a !");
                Player ownerOnline = Bukkit.getPlayer(ownerUuid);
                if (ownerOnline != null) ownerOnline.sendMessage("§e" + player.getName() + " §aa rejoint ton Île !");
                return true;
            }
            case "unfriend": {
                if (args.length < 2) { player.sendMessage("§cUsage : /ob unfriend <joueur>"); return true; }
                Parcelle myParc2 = plugin.getParcelleManager().getParcelle(player.getUniqueId());
                if (myParc2 == null) { player.sendMessage("§cTu n'as pas d'Île."); return true; }
                org.bukkit.OfflinePlayer uninvitee = Bukkit.getOfflinePlayer(args[1]);
                if (uninvitee != null) myParc2.getInvited().remove(uninvitee.getUniqueId());
                plugin.getParcelleManager().save();
                player.sendMessage("§e" + args[1] + " §7n'est plus ton ami.");
                return true;
            }
            case "top":
            case "classement":
                return handleObTop(player);
        }

        // ===== Commandes ADMIN/TEST (OP) =====
        if (!player.isOp()) { player.sendMessage("§cCommande inconnue. §7/ob help"); return true; }

        // /is admin — menu de toutes les Îles du serveur (tp + suppression).
        if (sub.equals("admin")) {
            plugin.getIslandAdmin().open(player, 0);
            return true;
        }

        // Cible : dernier argument = joueur si présent, sinon soi-même.
        java.util.function.Function<Integer, Parcelle> targetParc = (idx) -> {
            if (args.length > idx) {
                org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayer(args[idx]);
                if (t != null && t.hasPlayedBefore()) return ob.parcelleOf(t.getUniqueId());
            }
            return ob.parcelleOf(player);
        };

        switch (sub) {
            case "setphase": {
                if (args.length < 2) { player.sendMessage("§cUsage : /ob setphase <1-12> [joueur]"); return true; }
                int n;
                try { n = Integer.parseInt(args[1]); } catch (Exception e) { player.sendMessage("§cNombre invalide."); return true; }
                Parcelle parc = targetParc.apply(2);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                parc.setObPhase(Math.max(1, Math.min(OneBlockManager.PHASES, n)));
                parc.setObBlocs(0);
                ob.programmeCoffres(parc);
                plugin.getParcelleManager().save();
                ob.spawnBloc(parc);
                player.sendMessage("§aPhase forcée à §e" + parc.getObPhase() + " §7(" + ob.currentPhase(parc).nom + "§7).");
                return true;
            }
            case "setblocs": {
                if (args.length < 2) { player.sendMessage("§cUsage : /ob setblocs <n> [joueur]"); return true; }
                int n;
                try { n = Integer.parseInt(args[1]); } catch (Exception e) { player.sendMessage("§cNombre invalide."); return true; }
                Parcelle parc = targetParc.apply(2);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                parc.setObBlocs(Math.max(0, n));
                plugin.getParcelleManager().save();
                player.sendMessage("§aCompteur de blocs = §e" + parc.getObBlocs() + " §7(palier " + ob.currentPhase(parc).palier + ").");
                return true;
            }
            case "next": {
                Parcelle parc = targetParc.apply(1);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                int np = parc.getObPhase() + 1;
                if (np > OneBlockManager.PHASES) { np = 1; parc.setObCycle(parc.getObCycle() + 1); }
                parc.setObPhase(np);
                parc.setObBlocs(0);
                ob.programmeCoffres(parc);
                plugin.getParcelleManager().save();
                ob.spawnBloc(parc);
                player.sendMessage("§aPhase suivante → §e" + parc.getObPhase() + " §7(" + ob.currentPhase(parc).nom + "§7), cycle " + parc.getObCycle() + ".");
                return true;
            }
            case "reset": {
                Parcelle parc = targetParc.apply(1);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                parc.setObPhase(1); parc.setObBlocs(0); parc.setObCycle(0);
                ob.programmeCoffres(parc);
                plugin.getParcelleManager().save();
                ob.spawnBloc(parc);
                player.sendMessage("§aOneBlock remis à zéro (phase 1, blocs 0, cycle 0).");
                return true;
            }
            case "info": {
                Parcelle parc = targetParc.apply(1);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                OneBlockManager.Phase ph = ob.currentPhase(parc);
                player.sendMessage("§6§l⛏ OneBlock — info");
                player.sendMessage("§7Phase : §e" + ph.num + "§7/§e" + OneBlockManager.PHASES + " §8(" + ph.nom + "§8)");
                player.sendMessage("§7Blocs : §e" + parc.getObBlocs() + "§7/§e" + ph.palier);
                player.sendMessage("§7Cycle : §e" + parc.getObCycle());
                return true;
            }
            case "forcemob": {
                Parcelle parc = targetParc.apply(1);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                boolean ok = ob.forceMob(player, parc);
                player.sendMessage(ok
                        ? "§aMob hostile de la phase " + ob.getPhaseNum(parc) + " forcé."
                        : "§cAucun mob défini pour cette phase.");
                return true;
            }
            case "forcechest": {
                Parcelle parc = targetParc.apply(1);
                if (parc == null) { player.sendMessage("§cParcelle introuvable."); return true; }
                ob.forceChest(parc);
                player.sendMessage("§aLe prochain bloc cassé sera un §6coffre de biome§a.");
                return true;
            }
            case "forcebutin": {
                ob.forceButin(player);
                player.sendMessage("§aButin surprise forcé.");
                return true;
            }
            case "forcemerchant": {
                boolean ok = plugin.getMerchant().faireVenir(player);
                player.sendMessage(ok
                        ? "§aLa Caravane arrive (clic droit dessus)."
                        : "§cImpossible (déjà présente, ou pas d'Île).");
                return true;
            }
            default:
                player.sendMessage("§cSous-commande inconnue. §7/ob help");
                return true;
        }
    }

    // /ob top — classement des Îles par PROGRESSION (cycles × 12 + phase actuelle).
    // L'Île la plus « avancée » dans la boucle des biomes est première.
    private boolean handleObTop(Player player) {
        OneBlockManager ob = plugin.getOneBlock();

        // On rassemble (score, uuid) pour chaque Île, puis on trie décroissant.
        java.util.List<Parcelle> parcs = new java.util.ArrayList<>(plugin.getParcelleManager().getAllParcelles());
        parcs.sort((a, b) -> Integer.compare(ob.progressScore(b), ob.progressScore(a)));

        player.sendMessage("§8§m                                        ");
        player.sendMessage("§6§l🏝 Top des Îles §7— les plus avancées");
        player.sendMessage("");

        if (parcs.isEmpty()) {
            player.sendMessage("§7Aucune Île pour l'instant. Sois le premier !");
            player.sendMessage("§8§m                                        ");
            return true;
        }

        int rang = 0;
        int monRang = -1;
        java.util.UUID moi = player.getUniqueId();
        for (Parcelle parc : parcs) {
            if (parc.getOwner().equals(moi)) monRang = rang + 1;
            if (rang >= 10) { rang++; continue; } // on continue à compter pour trouver mon rang
            rang++;
            String nom = Bukkit.getOfflinePlayer(parc.getOwner()).getName();
            if (nom == null) nom = "§8(inconnu)";
            OneBlockManager.Phase ph = ob.phase(ob.getPhaseNum(parc));
            String medaille = rang == 1 ? "§e🥇" : rang == 2 ? "§7🥈" : rang == 3 ? "§6🥉" : "§8" + rang + ".";
            boolean estMoi = parc.getOwner().equals(moi);
            player.sendMessage(medaille + " " + (estMoi ? "§a" : "§f") + nom
                    + " §7— cycle §6" + (ob.getCycle(parc) + 1) + " §7· §f" + ph.nom);
        }

        // Rappel de ta position si tu es hors du top 10.
        if (monRang > 10) {
            Parcelle mienne = ob.parcelleOf(player);
            OneBlockManager.Phase ph = ob.phase(ob.getPhaseNum(mienne));
            player.sendMessage("");
            player.sendMessage("§8" + monRang + ". §a" + player.getName()
                    + " §7— cycle §6" + (ob.getCycle(mienne) + 1) + " §7· §f" + ph.nom + " §8(toi)");
        } else if (monRang == -1) {
            player.sendMessage("");
            player.sendMessage("§7Tu n'as pas encore d'Île. Fais §e/ob§7 !");
        }
        player.sendMessage("§8§m                                        ");
        return true;
    }

    // /phases — menu GUI des 12 phases (grille cliquable → détail des blocs obtenables).
    private boolean handlePhases(Player player, String[] args) {
        plugin.getPhasesMenu().openGrille(player);
        return true;
    }

    // /money : affiche le solde du joueur via l'économie Vault.
    private boolean handleMoney(Player player, String[] args) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage("§cL'economie n'est pas disponible.");
            return true;
        }
        // /money set <joueur> <montant> : fixe le solde exact (réservé OP).
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!player.isOp()) { player.sendMessage("§cCommande réservée aux opérateurs."); return true; }
            // /money set <montant> : fixe TON propre solde (sans nom de joueur).
            // Le montant accepte les suffixes : 1000000, 1.5K, 2.3M, 10T, 999ZZ... (précision BigInteger).
            if (args.length == 2) {
                java.math.BigInteger amount = EconomyManager.parseMontant(args[1]);
                if (amount == null) {
                    player.sendMessage("§cUsage : §e/money set <montant> §7(soi) §cou §e/money set <joueur> <montant>");
                    player.sendMessage("§7Exemples : §f100000 §7· §f1.5K §7· §f2.3M §7· §f10T §7· §f999ZZ");
                    return true;
                }
                if (amount.signum() < 0) { player.sendMessage("§cLe montant ne peut pas être négatif."); return true; }
                plugin.getCustomEco().setBalanceBig(player.getUniqueId(), amount);
                player.sendMessage("§aTon solde a été fixé à §6" + PrivateMines.formatNumberBig(amount) + "$§a.");
                return true;
            }
            if (args.length < 3) { player.sendMessage("§cUsage : §e/money set <montant> §7ou §e/money set <joueur> <montant>"); return true; }
            org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getName() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage("§cJoueur introuvable : §f" + args[1]);
                return true;
            }
            java.math.BigInteger amount = EconomyManager.parseMontant(args[2]);
            if (amount == null) {
                player.sendMessage("§cMontant invalide : §f" + args[2] + " §7(ex: 100000, 1.5K, 10T, 999ZZ)"); return true;
            }
            if (amount.signum() < 0) { player.sendMessage("§cLe montant ne peut pas être négatif."); return true; }
            // On fixe le solde EXACT (BigInteger) directement.
            plugin.getCustomEco().setBalanceBig(target.getUniqueId(), amount);
            player.sendMessage("§aSolde de §f" + target.getName() + " §afixé à §6" + PrivateMines.formatNumberBig(amount) + "$§a.");
            if (target.isOnline() && target.getPlayer() != null && !target.getPlayer().equals(player)) {
                target.getPlayer().sendMessage("§eTon solde a été fixé à §6" + PrivateMines.formatNumberBig(amount) + "$§e.");
            }
            return true;
        }
        player.sendMessage("§6Solde : §a" + plugin.formatBig(player));
        return true;
    }

    // /bvn : accueille le dernier nouveau joueur arrivé et donne +2% du solde de celui qui l'utilise.
    // [VERSION TEST] Aucune restriction. Si aucun vrai nouveau, on accueille un nom fictif pour pouvoir tester.
    // /daily — le joueur ouvre sa série de 7 jours (normalement via le PNJ « Récompenses
    // quotidiennes », cette commande est surtout le filet de secours + l'outillage de test OP).
    //   /daily              ouvre le menu
    //   /daily reset [j]    OP : casse la série (le joueur repart à J1 dès aujourd'hui)
    //   /daily set <1-7> [j] OP : place la série sur ce jour, réclamable tout de suite
    private boolean handleDaily(Player player, String[] args) {
        DailyManager daily = plugin.getDaily();
        if (daily == null) { player.sendMessage("§cLes récompenses quotidiennes ne sont pas disponibles."); return true; }
        if (args.length == 0) { daily.openMenu(player); return true; }
        if (!player.isOp()) { player.sendMessage("§cRéservé aux OP."); return true; }

        String sub = args[0].toLowerCase();
        if (sub.equals("reset")) {
            Player cible = args.length >= 2 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : player;
            if (cible == null) { player.sendMessage("§cJoueur introuvable."); return true; }
            daily.reset(cible.getUniqueId());
            player.sendMessage("§aSérie quotidienne remise à zéro pour §f" + cible.getName() + "§a.");
            return true;
        }
        if (sub.equals("set")) {
            if (args.length < 2) { player.sendMessage("§cUsage : /daily set <1-7> [joueur]"); return true; }
            int jour;
            try { jour = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { player.sendMessage("§cJour invalide (1-7)."); return true; }
            Player cible = args.length >= 3 ? org.bukkit.Bukkit.getPlayerExact(args[2]) : player;
            if (cible == null) { player.sendMessage("§cJoueur introuvable."); return true; }
            daily.setStreak(cible.getUniqueId(), jour);
            player.sendMessage("§a" + cible.getName() + " §7peut maintenant réclamer le §fJour "
                    + Math.max(1, Math.min(DailyManager.CYCLE, jour)) + "§7.");
            return true;
        }
        player.sendMessage("§cUsage : /daily [reset|set <1-7>] [joueur]");
        return true;
    }

    private boolean handleBvn(Player player) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage("§cL'economie n'est pas disponible.");
            return true;
        }
        String nouveau = plugin.getLastNewPlayer();
        if (nouveau == null) nouveau = "NouveauJoueur"; // [TEST] cible fictive quand personne n'est arrivé.

        // Bonus = 2% du solde actuel de celui qui tape la commande.
        double bonus = economy.getBalance(player) * 0.02;
        if (bonus > 0) economy.depositPlayer(player, bonus);

        // Annonce de bienvenue diffusée à tout le serveur.
        Bukkit.broadcastMessage("§aBienvenue à §e" + nouveau + " §a! §6👋");
        player.sendMessage("§aMerci ! Tu reçois §6" + PrivateMines.formatNumber(bonus) + "$ §a(+2% de ton solde).");
        return true;
    }

    // /guide : affiche ou masque la BossBar de guide (astuces pour débutants).
    private boolean handleGuide(Player player) {
        boolean nowVisible = plugin.getGuide().toggle(player);
        if (nowVisible) {
            player.sendMessage("§aGuide affiché. Les astuces apparaissent en haut de l'écran.");
        } else {
            player.sendMessage("§7Guide masqué. Refais §e/guide §7pour le réafficher.");
        }
        return true;
    }

    // /stats : affiche TOUS les bonus du joueur en %, regroupés par source.
    private boolean handleStats(Player player) {
        PetEquipMenu.Bonus pet = plugin.getPetBonus(player);
        double sacMult   = plugin.getMoneyBonusPercent(player);        // multiplicateur de vente du Sac
        double dimeAvg   = plugin.getDimeAvgMoneyPercent(player);      // Dîme (gain moyen)
        double totalArgent = plugin.getTotalMoneyBonusPercent(player); // agrégat argent (comme le HUD)

        player.sendMessage("§8§m                        ");
        player.sendMessage("§e§l✦ TES BONUS §7— §f" + player.getName());
        player.sendMessage("");
        player.sendMessage("§6§lArgent");
        player.sendMessage("  §7Multiplicateur de vente (Sac) §a+" + fmtPct(sacMult) + "%");
        player.sendMessage("  §7Argent à la vente (familiers) §a+" + fmtPct(pet.sellMoneyPct) + "%");
        player.sendMessage("  §7Valeur des blocs (familiers) §a+" + fmtPct(pet.blockValuePct) + "%");
        player.sendMessage("  §7Dîme du Passeur (moyenne) §a+" + fmtPct(dimeAvg) + "%");
        int prestigePct = plugin.getPrestigeSellPercent(player);
        if (prestigePct > 0)
            player.sendMessage("  §7Prestige §6⭐" + plugin.getPrestige(player) + " §a+" + fmtPct(prestigePct) + "%");
        player.sendMessage("  §d➥ Total argent §d+" + fmtPct(totalArgent) + "%");
        player.sendMessage("");
        player.sendMessage("§b§lMinage & Sac");
        player.sendMessage("  §7Capacité du sac (familiers) §a+" + fmtPct(pet.capacityPct) + "%");
        player.sendMessage("  §7Vitesse de minage (familiers) §a+" + fmtPct(pet.miningSpeedPct) + "%");
        player.sendMessage("  §7Chance de double bloc (familiers) §a+" + fmtPct(pet.doubleBlockPct) + "%");

        // Bonus des armures d'Oublié PORTÉES (Acte IV). Affiché seulement si le joueur en porte.
        ArmorManager.WornArmor arm = plugin.getArmorBonus(player);
        boolean aArmure = !arm.bonus.isEmpty() || arm.recyclePct != 0 || arm.pickaxeXpPct != 0 || arm.keyPct != 0;
        if (aArmure) {
            player.sendMessage("");
            player.sendMessage("§d§lArmures d'Oublié §7(portées)");
            if (arm.bonus.sellMoneyPct != 0)  player.sendMessage("  §7Vente des blocs §a+" + fmtPct(arm.bonus.sellMoneyPct) + "%");
            if (arm.bonus.blockValuePct != 0) player.sendMessage("  §7Argent en minant §a+" + fmtPct(arm.bonus.blockValuePct) + "%");
            if (arm.bonus.capacityPct != 0)   player.sendMessage("  §7Taille du sac §a+" + fmtPct(arm.bonus.capacityPct) + "%");
            if (arm.bonus.doubleBlockPct != 0)player.sendMessage("  §7Chance de double bloc §a+" + fmtPct(arm.bonus.doubleBlockPct) + "%");
            if (arm.recyclePct != 0)          player.sendMessage("  §7Gain de recyclage §a+" + fmtPct(arm.recyclePct) + "%");
            if (arm.pickaxeXpPct != 0)        player.sendMessage("  §7XP de pioche §a+" + fmtPct(arm.pickaxeXpPct) + "%");
            if (arm.keyPct != 0)              player.sendMessage("  §7Chance de clé §a+" + fmtPct(arm.keyPct) + "%");
        }

        // Blocs cassés par les enchants de zone (compteurs à vie). Affiché seulement pour les
        // enchants qui ont déjà cassé au moins un bloc, + un total.
        long totalEnch = 0L;
        java.util.List<String> lignesEnch = new java.util.ArrayList<>();
        for (String[] k : PrivateMines.ENCHANT_BLOCK_KEYS) {
            long v = plugin.getEnchantBlocks(player, k[0]);
            if (v <= 0) continue;
            totalEnch += v;
            lignesEnch.add("  §7" + k[1] + " §b" + PrivateMines.formatNumber(v) + " §8blocs");
        }
        if (totalEnch > 0) {
            player.sendMessage("");
            player.sendMessage("§3§lBlocs cassés par enchant");
            for (String l : lignesEnch) player.sendMessage(l);
            player.sendMessage("  §b➥ Total §b" + PrivateMines.formatNumber(totalEnch) + " §8blocs");
        }

        // Gains apportés par les bonus (compteurs à vie). Affiché seulement s'il y a du contenu.
        long   dblBlocks   = plugin.getBonusCount(player, "dblBlocks");
        double dblMoney    = plugin.getBonusMoney(player, "dblMoney");
        long   dimeBlocks  = plugin.getBonusCount(player, "dimeBlocks");
        double dimeMoney   = plugin.getBonusMoney(player, "dimeMoney");
        double contreMoney = plugin.getBonusMoney(player, "contrebandeMoney");
        double bvMoney     = plugin.getBonusMoney(player, "blockValueMoney");
        double totalBonusMoney = dblMoney + dimeMoney + contreMoney + bvMoney;
        boolean aBonus = dblBlocks > 0 || dimeBlocks > 0 || totalBonusMoney > 0;
        if (aBonus) {
            player.sendMessage("");
            player.sendMessage("§6§l💰 Gains grâce aux bonus");
            if (dblBlocks > 0)
                player.sendMessage("  §7Blocs doublés §f" + PrivateMines.formatNumber(dblBlocks)
                        + " §8(§6+" + PrivateMines.formatNumber(dblMoney) + "$§8)");
            if (dimeBlocks > 0)
                player.sendMessage("  §7Dîme du Passeur §f" + PrivateMines.formatNumber(dimeBlocks)
                        + " §7blocs payés §8(§6+" + PrivateMines.formatNumber(dimeMoney) + "$§8)");
            if (contreMoney > 0)
                player.sendMessage("  §7Sel de Contrebande §8(§6+" + PrivateMines.formatNumber(contreMoney) + "$§8)");
            if (bvMoney > 0)
                player.sendMessage("  §7Valeur des blocs §8(§6+" + PrivateMines.formatNumber(bvMoney) + "$§8)");
            player.sendMessage("  §6➥ Total argent bonus §6+" + PrivateMines.formatNumber(totalBonusMoney) + "$");
        }
        player.sendMessage("§8§m                        ");
        return true;
    }

    // Formate un pourcentage à 2 décimales, virgule française.
    private String fmtPct(double v) {
        return String.format(java.util.Locale.FRANCE, "%.2f", v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        List<String> online = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getName());

        switch (cmd) {
            case "zone":
                if (args.length == 1) {
                    return filter(java.util.Arrays.asList("create","pos1","pos2","list","delete","preset","info"), args[0]);
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("delete")
                        || args[0].equalsIgnoreCase("preset"))) {
                    List<String> names = new java.util.ArrayList<>(plugin.getZones().getZoneNames());
                    return filter(names, args[1]);
                }
                return java.util.Collections.emptyList();
            case "money":
                if (args.length == 1) return filter(java.util.Arrays.asList("set"), args[0]);
                if (args.length == 2 && args[0].equalsIgnoreCase("set")) return filter(online, args[1]);
                return java.util.Collections.emptyList();
            case "givemoney":
            case "resetenchants":
            case "resetsac":
            case "resetmines":
            case "resetbv":
            case "resetarmures":
            case "resetordi":
            case "resetordinateur":
            case "resetfragment":
            case "resetfragments":
                if (args.length == 1) return filter(online, args[0]);
                return java.util.Collections.emptyList();
            case "ah":
                if (args.length == 1) return filter(java.util.Arrays.asList("sell", "help"), args[0]);
                // 2e arg de sell = un prix : on propose des ordres de grandeur courants.
                if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
                    return filter(java.util.Arrays.asList("1000", "10K", "100K", "1M", "10M", "100M", "1B"), args[1]);
                }
                return java.util.Collections.emptyList();
            case "coinflip":
            case "cf":
                // 1er arg = une mise (ordres de grandeur courants) ou l'aide.
                if (args.length == 1) return filter(java.util.Arrays.asList("1000", "10K", "100K", "1M", "10M", "100M", "1B", "help"), args[0]);
                return java.util.Collections.emptyList();
            case "testgames":
                if (args.length == 1 && sender.isOp()) return filter(java.util.Arrays.asList("calcul", "nombre", "stop"), args[0]);
                return java.util.Collections.emptyList();
            case "end":
                if (args.length == 1 && sender.isOp()) return filter(java.util.Arrays.asList("forcedragon"), args[0]);
                return java.util.Collections.emptyList();
            case "spawner":
                if (args.length == 1) return filter(java.util.Arrays.asList("tool","cleanup"), args[0]);
                if (args.length == 2 && args[0].equalsIgnoreCase("tool")) return filter(online, args[1]);
                return java.util.Collections.emptyList();
            case "holo":
                if (args.length == 1) {
                    return filter(java.util.Arrays.asList("setting","set","create","edit","addline","setline","removeline","movehere","delete","list"), args[0]);
                }
                // /holo set <modèle> : propose les modèles pré-remplis.
                if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                    return filter(java.util.Arrays.asList("end", "passeur", "minereset"), args[1]);
                }
                if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list")
                        && !args[0].equalsIgnoreCase("setting") && !args[0].equalsIgnoreCase("set")) {
                    return filter(plugin.getHoloManager().getHologramNames(), args[1]);
                }
                return java.util.Collections.emptyList();
            case "pnj":
            case "npc":
                if (args.length == 1) {
                    return filter(java.util.Arrays.asList("place","setting","list","tp","remove","setchest","setchest2","setpuits","skin","reload","info","resetquest"), args[0]);
                }
                // 2e arg de tp/remove = un ID de PNJ existant.
                if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("remove"))) {
                    return filter(new java.util.ArrayList<>(plugin.getNpc().getIds()), args[1]);
                }
                // 2e arg de resetquest = un joueur en ligne OU le numéro d'acte (1/2/3/4).
                if (args.length == 2 && args[0].equalsIgnoreCase("resetquest")) {
                    java.util.List<String> opts = new java.util.ArrayList<>();
                    opts.add("1");
                    opts.add("2");
                    opts.add("3");
                    opts.add("4");
                    for (Player pl : Bukkit.getOnlinePlayers()) opts.add(pl.getName());
                    return filter(opts, args[1]);
                }
                // 3e arg de resetquest = le numéro d'acte (1/2/3/4).
                if (args.length == 3 && args[0].equalsIgnoreCase("resetquest")) {
                    return filter(java.util.Arrays.asList("1","2","3","4"), args[2]);
                }
                // /pnj skin <add|list|remove>
                if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
                    return filter(java.util.Arrays.asList("add","list","remove"), args[1]);
                }
                // /pnj skin remove <nom> = un skin existant.
                if (args.length == 3 && args[0].equalsIgnoreCase("skin") && args[1].equalsIgnoreCase("remove")) {
                    return filter(new java.util.ArrayList<>(plugin.getSkins().getNames()), args[2]);
                }
                return java.util.Collections.emptyList();
            case "ob":
            case "oneblock":
            case "is":
                if (args.length == 1) {
                    java.util.List<String> subs = new java.util.ArrayList<>(java.util.Arrays.asList(
                            "help","setting","visit","friend","accept","unfriend","top",
                            "setphase","setblocs","next","reset","info","forcemob","forcechest","forcebutin","forcemerchant"));
                    if (sender.isOp()) subs.add("admin");
                    return filter(subs, args[0]);
                }
                // Sous-commandes île avec argument joueur.
                if (args.length == 2 && (args[0].equalsIgnoreCase("visit")
                        || args[0].equalsIgnoreCase("friend")
                        || args[0].equalsIgnoreCase("unfriend"))) {
                    return filter(online, args[1]);
                }
                // setphase : proposer 1..12.
                if (args.length == 2 && args[0].equalsIgnoreCase("setphase")) {
                    return filter(java.util.Arrays.asList("1","2","3","4","5","6","7","8","9","10","11","12"), args[1]);
                }
                // dernier arg admin = joueur.
                if (args.length == 3 && (args[0].equalsIgnoreCase("setphase") || args[0].equalsIgnoreCase("setblocs"))) {
                    return filter(online, args[2]);
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("next") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("info")
                        || args[0].equalsIgnoreCase("forcemob") || args[0].equalsIgnoreCase("forcechest"))) {
                    return filter(online, args[1]);
                }
                return java.util.Collections.emptyList();
            case "crate":
            case "crates":
                if (!sender.isOp()) return java.util.Collections.emptyList();
                if (args.length == 1) {
                    return filter(java.util.Arrays.asList("sethere","settings","remove","clearall","list","givekey","boost","testblock"), args[0]);
                }
                // /crate boost <mult> [minutes] [joueur]
                if (args.length == 2 && args[0].equalsIgnoreCase("boost")) {
                    return filter(java.util.Arrays.asList("1.5","2.5","5"), args[1]);
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("boost")) {
                    return filter(java.util.Arrays.asList("1","5","10","30"), args[2]);
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("boost")) {
                    return filter(online, args[3]);
                }
                // /crate sethere <rang>
                if (args.length == 2 && args[0].equalsIgnoreCase("sethere")) {
                    return filter(java.util.Arrays.asList("commune","rare","legendaire"), args[1]);
                }
                // /crate givekey <joueur> <rang> [nombre] — on tolère "givekey" et "givekeys".
                boolean gk = args[0].equalsIgnoreCase("givekey") || args[0].equalsIgnoreCase("givekeys");
                if (args.length == 2 && gk) {
                    return filter(online, args[1]);
                }
                if (args.length == 3 && gk) {
                    return filter(java.util.Arrays.asList("commune","rare","legendaire"), args[2]);
                }
                if (args.length == 4 && gk) {
                    return filter(java.util.Arrays.asList("1","5","10","32","64"), args[3]);
                }
                return java.util.Collections.emptyList();
            case "mur":
            case "wall":
                if (args.length == 1) {
                    return filter(java.util.Arrays.asList("settings","wand","create","list","remove","toggle","tp"), args[0]);
                }
                // 2e arg de remove/toggle/tp = un nom de mur existant.
                if (args.length == 2 && (args[0].equalsIgnoreCase("remove")
                        || args[0].equalsIgnoreCase("toggle")
                        || args[0].equalsIgnoreCase("tp"))) {
                    return filter(plugin.getWalls().wallNames(), args[1]);
                }
                return java.util.Collections.emptyList();
            case "pickaxelevel":
            case "piochelevel":
                if (args.length == 1) return filter(java.util.Arrays.asList("set"), args[0]);
                if (args.length == 3 && args[0].equalsIgnoreCase("set")) return filter(online, args[2]);
                return java.util.Collections.emptyList();
            case "fracturelevel":
                if (args.length == 1) return filter(java.util.Arrays.asList("set"), args[0]);
                if (args.length == 3 && args[0].equalsIgnoreCase("set")) return filter(online, args[2]);
                return java.util.Collections.emptyList();
            case "prestige":
            case "renaissance":
                if (args.length == 1) return filter(java.util.Arrays.asList("set"), args[0]);
                if (args.length == 3 && args[0].equalsIgnoreCase("set")) return filter(online, args[2]);
                return java.util.Collections.emptyList();
            case "setspawn":
            case "mine":
            case "spawn":
            case "shop":
            case "bp":
            case "daily":
            case "quotidien":
                if (!sender.isOp()) return java.util.Collections.emptyList();
                if (args.length == 1) return filter(java.util.Arrays.asList("reset", "set"), args[0]);
                if (args.length == 2 && "set".equalsIgnoreCase(args[0]))
                    return filter(java.util.Arrays.asList("1","2","3","4","5","6","7"), args[1]);
                if ((args.length == 2 && "reset".equalsIgnoreCase(args[0]))
                        || (args.length == 3 && "set".equalsIgnoreCase(args[0]))) {
                    java.util.List<String> noms = new java.util.ArrayList<>();
                    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) noms.add(pl.getName());
                    return filter(noms, args[args.length - 1]);
                }
                return java.util.Collections.emptyList();
            case "commande":   // annuaire, aucun argument
            case "commandes":
            case "cmds":
            case "aide":
            case "jobs":   // menu sans sous-commande
            case "job":
            case "metier":
            case "metiers":
            case "fly":
            default:
                return java.util.Collections.emptyList();
        }
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new java.util.ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
