# 🗂️ Index des fichiers — PrivateMines

> **But de ce fichier :** retrouver rapidement DANS QUEL fichier aller pour changer quelque chose.
> Cherche le mot-clé (Ctrl+F) de ce que tu veux modifier → tu trouves le fichier + où chercher dedans.
>
> Code source : `../src/main/java/fr/garfield/privatemines/`
> Jar déployé : `plugins/PrivateMines.jar`
>
> 🖱️ **Pour cliquer sur les fichiers :** ouvre le workspace `PrivateMines.code-workspace` (double-clic dessus)
> → les deux dossiers s'ouvrent ensemble → les liens ci-dessous deviennent cliquables (Ctrl+clic).

---

## 🔎 « Je veux changer… » → va dans ce fichier

| Je veux changer… | Fichier | Où chercher dedans (mot-clé Ctrl+F) |
|---|---|---|
| **Le % de charbon / bloc de charbon d'une mine** | `PrivateMines.java` | `MINE_B_COAL_PCT`, `MINE_C_COAL_PCT`, … `MINE_F_COALBLOCK_PCT` (vers ligne 147) |
| **Le prix de vente d'un bloc** (pierre / charbon / bloc charbon) | `PrivateMines.java` | `MINE_*_STONE_PRICE`, `*_COAL_PRICE`, `*_COALBLOCK_PRICE` + `getStoneSellPrice` / `getCoalSellPrice` |
| **Le prix d'un niveau de sac (capacité ET vente/sec)** | `PrivateMines.java` | `getUpgradeCost` (vers ligne 805) — **deux régimes depuis le 2026-08-23** (`refonte.economie.md` §32) : `BASE_COST` 50 × `COST_GROWTH` 1,10 jusqu’au niveau `COST_PIVOT` (**50**), puis × `COST_GROWTH_LATE` (**1,39**, le ratio des mines). ⚠ raccord **continu** (le niveau 50 vaut 5 336 $ des deux côtés), donc le début de jeu ne bouge pas. Motif : à ×1,10 le sac était **250× moins cher** qu’un enchant et absorbait toute l’économie. ⚠ `moneyMultCost` n’a PAS bougé (effet +0,05 %/niv, négligeable). |
| **Le prix d'achat d'une mine** | `PrivateMines.java` | Le `cost` de chaque `new MineDef(...)` dans la table `MINES` (vers ligne 261). ⚠ **Mines 15 → 25 : prix ×1,8 le 2026-08-22** (`refonte.economie.md` §30) pour compenser le +52 % de revenu d’un set d’armures **Fer** (bonus 27–45 % par pièce, 3 slots — les valeurs viennent de `Rarete.minStat()..maxStat`, PAS des plages `ArmorBonus`). ⚠ effets de bord notés en §30.3 : le palier Fer est gated sur **450 recyclages**, pas sur les mines (donc un non-recycleur subit la hausse sans le bonus) ; le mur de l'Arc II passe à **720 000 blocs** d'amorti ; et le commentaire d'ancrage de `MINE_PACED_BASE` (« niveau 40 = prix de la mine 25 ») vise désormais l'ancien prix. |
| **Le nom / la description d'une mine** | `PrivateMines.java` | `mineDisplayName`, `MineTile`, `mineTiles()` |
| **Le menu /mine (chemin serpentin)** | `PrivateMines.java` | `openMineMenu`, `MINE_PATH`, `mineTiles()` |
| **Les dialogues des PNJ (Contremaître, Forgeron…)** | `ActeManager.java` + `DialogueManager.java` | textes `sendMessage`, `onTalk…` |
| **L'intro / la scène d'ouverture** | `IntroManager.java` | textes `sendTitle` / `sendMessage` |
| **Les quêtes de l'Apprentissage (objectifs, récompenses)** | `TutorialManager.java` | `REWARDS`, `OBJ_…`, `objVendre`, `objMiner` |
| **Un enchant (chance, puissance, coût, cap)** | `EnchantManager.java` | `explosionChance`, `colonneDepth`, `harponCost`, `EFFICIENCY_MAX`… |
| **Retirer toutes les armures d'Oublié d'un joueur** | `CommandManager.java` + `PrivateMines.java` | `/resetarmures [joueur]` → `handleResetArmures` → `purgeArmuresOubli(p)` (fouille l'inventaire vivant + les 2 inventaires sauvegardés `invMine`/`invHorsMine` ; ne touche NI aux Fragments NI au compteur de recyclage) |
| **Remettre à zéro les Fragments de Souvenir** | `CommandManager.java` + `FragmentManager.java` | `/resetfragment [joueur]` (alias `resetfragments`) → `handleResetFragments` → `setFragments(uuid, ZERO)`. ⚠ **Volontairement séparée de `/resetordi`** : les Fragments sont la MONNAIE de la forge, le compteur de recyclage est de la PROGRESSION — effacer l'un ne doit jamais effacer l'autre. |
| **Remettre à zéro l'Ordinateur Quantique** | `CommandManager.java` + `FragmentManager.java` | `/resetordi [joueur]` (alias `resetordinateur`) → `handleResetOrdi` → `FragmentManager.resetOrdinateur(uuid)` : compteur `armuresRecyclees` → 0 + `autoRecycle`/`autoRecycleSeuil` → défaut. ⚠ **Ne touche PAS aux Fragments** (monnaie, choix user) ni aux armures elles-mêmes (→ `/resetarmures`). ⚠ `FragmentManager.save()` ne SUPPRIME jamais une clé de `fragments.yml` : le reset écrit des valeurs par défaut, un `remove()` laisserait l'ancienne valeur revenir au redémarrage. |
| **Remettre à zéro les stats du /bv** | `CommandManager.java` + `PrivateMines.java` | `/resetbv [joueur]` → `handleResetBv` → `resetBlockStats(uuid)` (⚠ efface aussi la section YAML `blockStats`, sinon les chiffres reviennent au redémarrage) |
| **Le menu des enchants (slots, pages, couleurs)** | `EnchantManager.java` | `refreshEnchantMenu` (page 1), `openEnchantMenuPage2` (page 2), `onEnchantMenuClick` (les DEUX pages). ⚠ **Un slot se change TOUJOURS à deux endroits** : la pose (`placeEnchant`/`setItem`) ET le `slot ==` du clic — sinon on achète le mauvais enchant. Échanges du 2026-08-21 : Contrebande 42→34 ↔ Tempête 34→42 (page 1), et **Vol ↔ Reflux entre les pages** (Vol page 1 slot 38, Reflux page 2 via le helper `placeReflux`). Échange du 2026-08-22 : **Pluie de Flèches 14→10 ↔ Reflux 10→14** (page 2). |
| **Régler un niveau d'enchant à la main (OP)** | `EnchantManager.java` | **Shift+clic droit** sur un enchant quand on est OP → saisie du niveau dans le chat (`annuler` pour sortir). ⚠ **Ctrl+clic droit est impossible** : Minecraft n'envoie pas la touche Ctrl dans les menus, le client émet le même paquet qu'un clic droit normal. Le niveau est **plafonné au max de l'enchant**. Points de code : `enchantKeyAt` (slot → clé, **à mettre à jour si un slot bouge**), `nomEnchant`/`maxEnchant`/`niveauEnchant`/`appliqueNiveau` (4 switch parallèles, une entrée par enchant), `demandeNiveau`, `onSetLevelChat` (saisie async), `ajouteIndiceOp` + `OP_SLOTS_P1`/`OP_SLOTS_P2` (la ligne grise « [OP] Shift+clic droit » ajoutée en fin de lore, invisible pour les joueurs). Un **nouvel enchant** doit être ajouté aux 4 switch, à `enchantKeyAt` et au tableau `OP_SLOTS_*`. |
| **Le raccourci /mine <-> enchants (slot 0 des 2 menus)** | `EnchantManager.java` + `PrivateMines.java` | `mineShortcutTile` (tête HeadDB `MINE_SHORTCUT_HEAD_ID` = 2303) ; `MINE_BACK_SLOT` pour la pioche de retour côté menu des mines |
| **La capacité / vente du sac (courbes)** | `PrivateMines.java` | `BASE_CAPACITY`, `CAPACITY_GROWTH`, `getSellPerSec`, `COST_GROWTH` |
| **Le menu /bv (prix + gains par bloc)** | `BlockValueMenu.java` | `construireLignes`, `tuile`, `recap`, `TILES` |
| **Les gains cumulés par type de bloc** | `PrivateMines.java` | `BLOCK_KEYS`, `addBlockMined`, `creditBlocksEarned`, `startBlockMinuteTask` |
| **Le scoreboard (lignes à droite de l'écran)** | `PrivateMines.java` | `updateScoreboard`, `SB_ENTRIES` |
| **Le reset de la mine (déclencheur, respawn)** | `PrivateMines.java` | `startResetTask`, `resetMineForPlayer`, `RESET_THRESHOLD_PCT` — ⚠ **plus de compte à rebours depuis le 2026-08-21** : la mine se régénère quand le joueur en a cassé **20 %** (`RESET_THRESHOLD_BLOCKS` = 100 000 blocs sur 500 000). Le tick d'1 s ne fait plus que tester le seuil — il existe pour que le reset ne tombe JAMAIS au milieu d'une salve d'enchant (`broken.clear()` casserait la boucle de `creditMineBlock`). `RESET_PERIOD_SEC` ne sert plus que de valeur-marqueur dans `armMineReset`. |
| **Les pets (design, arcs, bonus)** | `PetMenu.java` / `PetStorage.java` / `PetEquipMenu.java` / `PetHeadProvider.java` | `ARC1_PETS`, `aggregate`, IDs HeadDB |
| **Le shop (/shop)** | `ShopManager.java` | `openShop`, prix des items |
| **Les parcelles (/parcelle, /bp, permissions)** | `ParcelleManager.java` / `Parcelle.java` / `BackpackManager.java` | `enterParcelle`, `invited`, backpack |
| **Supprimer une Île / libérer sa place dans la grille** | `ParcelleManager.java` | `deleteParcelle`, `clearTerrain`, `freeGridIndexes` |
| **Le menu admin des Îles (/is admin)** | `IslandAdminMenu.java` | `open`, `openConfirm`, `teleporter` |
| **Les collections (/collections, items-clés d'arc)** | `CollectionMenu.java` | `TROUVAILLES` (`construireTable`), `Trouvaille` |
| **Les classements (/classements)** | `LeaderboardManager.java` | `getRank`, tri |
| **L'économie (argent illimité BigInteger, format K/M/B…)** | `EconomyManager.java` | `format`, `add`, `subtract`, `getBalance` |
| **Les armures d'Oubliés (raretés, seuils, drop, bonus, forge)** | `ArmorManager.java` | `Rarete`, `Matiere`, `tirerValeur`, `dropPercent`, `genererArmure` |
| **Les Fragments de Souvenir (recyclage, Codex)** | `FragmentManager.java` | recyclage, `Codex` |
| **L'hôtel des ventes (/ah, historique)** | `AuctionManager.java` | `openHistorique`, `acheter`, `Vente` |
| **Les crates & clés (loot, ouverture)** | `CrateManager.java` | `lootTable`, `CrateRank`, `openCrate` |
| **Les mini-jeux de chat (question, gain, fréquence)** | `ChatGameManager.java` | `INTERVALLE_TICKS`, `DUREE_SECONDES`, `GAIN_POURCENT`, `PLANCHER_BLOCS`, `genererCalcul`, `genererNombre` |
| **Le throttle des effets visuels d'enchants (éclairs, harpons)** | `PrivateMines.java` | `canShowFx`, `FX_COOLDOWN_FRACTURE`, `FX_COOLDOWN_HARPON` |
| **La faim dans la mine (gélée)** | `PrivateMines.java` | `onFoodLevelChange` — annule les BAISSES de faim quand `isInMine(p)` ; manger fonctionne toujours. ⚠ La barre n'est PAS remplie à l'entrée (choix user : pas de « restaurant gratuit » avant l'End/PvP). |
| **Les métiers (/jobs, Mineur, paliers)** | `JobsManager.java` | `REWARDS`, paliers, `claim` |
| **Les récompenses quotidiennes (série de 7 jours)** | `DailyManager.java` | `TYPE_DU_JOUR`, `PCT_ARGENT`, `J2/J4/J6/J7_*`, `PALIER_MINE_MAX` |
| **Jeton / Tête de Pioche perdus = joueur bloqué** | `ActeManager.java` | `onChestInteract`, `onChest2Interact` — le coffre redonne l objet si le joueur ne l a plus ET que l acte n est pas fini |
| **Le sac/pioche/ordi perdus et non rendus** | `PrivateMines.java` | `tpToMine()` revérifie l équipement à CHAQUE téléportation (`swapToMineIfNeeded` ne fait rien si on est déjà en mine) |
| **Les textes des dialogues PNJ (recensement)** | `DIALOGUES_PNJ.md` | tout ce que chaque PNJ peut dire + conditions ; base de la réécriture |
| **Le guidage vers les PNJ (boussole BossBar)** | `QuestCompass.java` | `cible()` = la table objectif→lieu, `fleche()`, `PORTEE`, `ARRIVEE` |
| **Les PNJ visibles dans le mauvais monde** | `NpcManager.java` | `showTo()` — filtre par monde (le paquet d apparition partait vers TOUS les joueurs) |
| **Le spawn des mobs par zone (flags mobs/animals)** | `SpawnerListener.java` | `flagDe()` = la classification, `estAuPlugin()` = les entités à ne jamais toucher |
| **La purge des mobs entrés dans une zone** | `ZoneManager.java` | `startPurgeTask()` — toutes les **5 min** (6000 ticks), 1er passage 10 s après le démarrage |
| **L annuaire des commandes (/commande)** | `CommandMenu.java` | `TABLE` (une ligne par commande), `SLOTS`, `visibles()` |
| **Les sous-commandes de /commande** | `CommandMenu.java` | `SOUS` (map commande → `Sub[]`), `openSous()`, `sousVisibles()`, `commandeAuSlot()` |
| **Prix de vente d un minerai** | `PrivateMines.java` | `getCoalSellPrice` etc → `maxUnlocked()` ; bloc de base → `getBaseBlockSellPrice()` |
| **Le hub des quêtes (/quest) + les quêtes quotidiennes** | `QuestManager.java` | `OBJ_MINER`, `OBJ_MINERAI`, `OBJ_VENDRE`, `OBJ_ENCHANT`, `OBJ_SAC`, `OBJ_MINES`, `POIDS_RECOMPENSE` |
| **La quête HEBDOMADAIRE (1/semaine, reset samedi)** | `QuestManager.java` | `OBJ_HEBDO`, `W_MINER/W_PIOCHE/W_QUOTIDIENNES/W_ARMURES`, `semaine()`, `REC_HEBDO_*` |
| **Le suivi de progression des quêtes** | `PrivateMines.java` | `questManager.onMined` (minage) · `onSold` (vente du sac) · `onBagUpgrade` (up du sac) ; `onEnchantLevel` est dans `EnchantManager.java` |
| **Le PNJ « Récompenses quotidiennes »** | `NpcManager.java` + `NpcSettingMenu.java` | `ROLE_QUOTIDIEN` (rôle à donner via `/pnj setting`) |
| **Les warps (/warp)** | `WarpMenu.java` | `SLOT_SPAWN`, `SLOT_MINE`, `SLOT_CRATES`, `WARP_TITLE` |
| **La banque d'île (dépôt fer/or, score parcelle)** | `IslandBankManager.java` | `BANK_TITLE`, score fer×1/or×3 |
| **La forge des familiers (fusion 3 pets)** | `PetForgeMenu.java` | `aggregate`, fusion niveau +1 |
| **Les murs invisibles (/mur, barrières)** | `WallManager.java` | `Wall`, `Sélecteur de Mur`, `walls.yml` |
| **La Boussole du Log Pose (visite Alabasta)** | `LogPoseManager.java` | `SPOTS`, `/logpose`, `logpose.yml` |
| **La BossBar (guide en haut de l'écran)** | `GuideManager.java` | `setQuestObjective`, `setQuestProgress` |
| **Les commandes (/mine, /stats, /shop…)** | `CommandManager.java` | `case "…"`, `handle…` |
| **Déclarer une nouvelle commande** | `plugin.yml` (dans `resources/`) | `commands:` |

---

## 📄 Tous les fichiers, un par un

### Cœur / logique principale
- [`PrivateMines.java`](../src/main/java/fr/garfield/privatemines/PrivateMines.java) — LA classe centrale (god-class en cours de refactor, extraite bout par bout). Contient : mines (%, prix, noms, achat), sac (capacité/vente/coûts), reset des mines, rendu des faux-blocs, instamine, scoreboard, sauvegardes, triggers d'enchants (explosion/forage/fracture/vein/colonne/harpon), + la plupart des listeners d'événements.
- [`CommandManager.java`](../src/main/java/fr/garfield/privatemines/CommandManager.java) — toutes les commandes joueur (`/mine`, `/stats`, `/shop`, `/pnj`, etc.).
- [`plugin.yml`](../src/main/resources/plugin.yml) — déclaration des commandes + métadonnées du plugin.

### Listeners extraits de PrivateMines.java (refactor en cours)
> Morceaux sortis de la god-class, un par un (enregistrés dans `onEnable`).
- [`CrystalProtectionListener.java`](../src/main/java/fr/garfield/privatemines/CrystalProtectionListener.java) — protège les cristaux de l'End (déco du Puits des Souvenirs) : incassables, explosions neutralisées (aucun bloc cassé, aucun joueur blessé). Autonome, ne dépend pas de `PrivateMines`.
- [`ProtectionListener.java`](../src/main/java/fr/garfield/privatemines/ProtectionListener.java) — protection des zones `/zone` : bloque le cassage de blocs (flag « break », sauf OP) et le PvP (flag « pvp ») dans les zones protégées, + respawn au spawn du serveur (`/setspawn`). Dépend du plugin (`getZones()`, `getSpawnLocation()`).
- [`SpawnerListener.java`](../src/main/java/fr/garfield/privatemines/SpawnerListener.java) — événements des spawners d'île : clic droit = menu d'amélioration (proprio only), mort d'un mob = loot fer/or fusionné au sol + respawn du stack, et contrôle du spawn naturel des mobs par zone (flags « mobs »/« animals »). Dépend du plugin (`getSpawnerManager()`, `getParcelleManager()`, `getZones()`). ⚠️ distinct de `SpawnerManager.java` (qui gère pose/fabrication/upgrades).
- [`ParcelleListener.java`](../src/main/java/fr/garfield/privatemines/ParcelleListener.java) — **protections** de terrain sur parcelle : casser/poser (avec pose & récupération des spawners d'île), coffres, portes/portails/leviers, PvP interdit, et conservation de l'inventaire à la mort. Applique les permissions proprio/amis/visiteurs de `Parcelle`. Dépend du plugin (`getParcelleManager()`, `getSpawnerManager()`, `isBag()`). ⚠️ les MENUS de parcelle (paramètres/amis, achats de taille/slots) restent dans `PrivateMines.java` ; la gestion des données reste dans `ParcelleManager.java`.
- [`WorldRulesListener.java`](../src/main/java/fr/garfield/privatemines/WorldRulesListener.java) — règles globales du monde (partout sur le serveur) : aucun dégât de chute + les blocs à gravité (sable/gravier/concrete powder/enclume...) ne tombent jamais quand on les place. Autonome, ne dépend pas de `PrivateMines`.
- [`LockedItemListener.java`](../src/main/java/fr/garfield/privatemines/LockedItemListener.java) — verrouillage des items fixes (Sac de Minage, Pioche, Ordinateur Quantique) : impossible de les jeter ou de les sortir de leur slot (clic, hotbar, drag, shift). Interdit aussi de jeter quoi que ce soit au spawn/dans la mine. Dépend du plugin (`isBag()`/`isPickaxe()`/`isOrdi()`, `getPlayerZoneMap()`, `BAG_SLOT`/`PICK_SLOT`/`ORDI_SLOT`). ⚠️ le MENU du sac (achats capacité/vente/bonus) reste dans `PrivateMines.java` (`onMenuClick`/`buyUpgrade`).
- [`ChatTagListener.java`](../src/main/java/fr/garfield/privatemines/ChatTagListener.java) — préfixe de chat : ajoute « (N) » devant le pseudo, N = numéro de la meilleure mine débloquée (rang de mine). Dépend du plugin (`getMineRank()`). ⚠️ le renommage/suppression de zone par le chat (`onRenameChat`) reste dans `PrivateMines.java` (partage l'état d'édition de zone avec le menu zone).

### Mines & économie
- [`BlockValueMenu.java`](../src/main/java/fr/garfield/privatemines/BlockValueMenu.java) — **⚠️ 2026-09-02** : `/bv` décrit désormais la **mine où le joueur se trouve** (`getPlayerMine`), plus « la meilleure mine débloquée », et n affiche **que les blocs que cette mine contient vraiment** (filtre sur `getMineCoalPct` / `getMineCoalBlockPct` / `getMineIronPct` / `getMineAbyssalPct`) : pierre seule en mine 1, grès seul en Arc II. Les **prix** affichés sont ceux réellement payés (`getXxxSellPrice`), pas ceux de la mine visitée. — menu `/bv` (alias `/blockvalue`, `/valeur`) : une fiche par type de bloc. Haut de la fiche = **le prix** (final + détail du calcul) ; bas = **ce que ce bloc t'a rapporté** (total encaissé, part de tes gains + barre, nombre miné, moyenne/bloc, record sur 1 min). Les 5 blocs occupent toujours les mêmes cases (20-24) ; ceux non débloqués restent visibles en silhouette avec la façon de les obtenir. **Le suivi des gains vit dans `PrivateMines.java`**, pas ici : `addBlockMined` (à la casse) puis `creditBlocksEarned` (à la vente, au prorata de la file d'attente — le sac ne distingue que charbon/pierre, donc l'attribution exacte par bloc est impossible autrement). Persisté sous `.blockStats.<clé>.{mined,earned,bestMin,pending}` ; compteurs démarrés à zéro le 2026-08-18.
- [`ShopManager.java`](../src/main/java/fr/garfield/privatemines/ShopManager.java) — boutique `/shop`.
- [`LeaderboardManager.java`](../src/main/java/fr/garfield/privatemines/LeaderboardManager.java) — classements `/classements`.
- [`EconomyManager.java`](../src/main/java/fr/garfield/privatemines/EconomyManager.java) — **économie maison** (argent illimité en `BigInteger`, remplace l'éco d'Essentials). Format d'affichage K/M/B/T puis AA…ZZ. `format` / `add` / `subtract` / `getBalance`.
- [`AuctionManager.java`](../src/main/java/fr/garfield/privatemines/AuctionManager.java) — hôtel des ventes `/ah` (vente entre joueurs, 5 ventes max, taxe 3%) + menu « Mes anciennes ventes » (historique). `Vente`, `acheter`, `openHistorique`.
- [`ChatGameManager.java`](../src/main/java/fr/garfield/privatemines/ChatGameManager.java) — **mini-jeux de chat** : toutes les 15 min (si ≥ 2 joueurs), le serveur pose un **calcul mental** ou un **nombre mystère** dans le chat ; le gagnant prend **+10 % de son argent** (plancher = 500 blocs de pierre au prix de sa meilleure mine). 30 s de réponse, 1 seule proposition par joueur pour le nombre mystère. ⚠ l'écoute du chat est ASYNC → départage par `AtomicBoolean.compareAndSet`, paiement renvoyé sur le thread principal. Test OP : `/testgames [calcul|nombre|stop]`.
- [`IslandBankManager.java`](../src/main/java/fr/garfield/privatemines/IslandBankManager.java) — banque d'île : le joueur dépose fer/or dans sa parcelle, score = fer×1 + or×3, alimente le classement des îles. `BANK_TITLE`.

### Armures & Fragments
- [`ArmorManager.java`](../src/main/java/fr/garfield/privatemines/ArmorManager.java) — **système d'armures d'Oubliés** : raretés (`Rarete`, maxStat/slots), matières (`Matiere`, seuils de déblocage), rolls de valeur (`tirerValeur`), taux de drop en minant (`dropPercent`), génération (`genererArmure`), forge (reroll/améliorer/ajouter).
- [`FragmentManager.java`](../src/main/java/fr/garfield/privatemines/FragmentManager.java) — Fragments de Souvenir : recyclage des armures via l'« ordinateur quantique » + Codex GUI paginé.
- [`PetForgeMenu.java`](../src/main/java/fr/garfield/privatemines/PetForgeMenu.java) — forge des familiers : fusion de 3 pets identiques (même espèce + niveau) → 1 pet de niveau +1.

### Crates, métiers, warps
- [`CrateManager.java`](../src/main/java/fr/garfield/privatemines/CrateManager.java) — crates & clés `/keys` : loot (`lootTable`, `CrateRank`), ouverture des crates physiques (tête ArmorStand), types money/enchant/fragments/pet/armor/key/block/tool/jackpot + boost de vente. Source de vérité des % = `RECOMPENSES_CRATES.md`. Liste noire des blocs du type « bloc mystère » = `BLOCS_EXCLUS`. Commandes OP : `/crate givekey`, `/crate boost`, `/crate testblock`.
- [`CrateBlockListener.java`](../src/main/java/fr/garfield/privatemines/CrateBlockListener.java) — blocs incassables **issus de crate** (command block + bedrock uniquement) : marqués en PDC (`crate_breakable`, nom violet gras), posables et récupérables au clic gauche, alors que les mêmes blocs présents dans le monde restent incassables. ⚠️ le command block est posé « à la main » (vanilla refuse la pose en survie). Positions dans `crate_blocks.yml`.
- [`JobsManager.java`](../src/main/java/fr/garfield/privatemines/JobsManager.java) — métiers `/jobs` : 1er métier Mineur, 22 paliers de blocs minés réclamables (`REWARDS`). Extensible.
- [`QuestCompass.java`](../src/main/java/fr/garfield/privatemines/QuestCompass.java) — **boussole de quête** : décore la BossBar d objectif d une **flèche 8 directions** (relative au regard), de la **distance en m**, d une couleur **rouge→jaune→verte** et d une barre qui se remplit (vide à **300 m**, « Tu y es ! » sous **5 m**). Tourne **à chaque tick**, mais ne calcule que pour les joueurs ayant un objectif ET une cible connue. ⚠️ La décoration n est écrite QUE dans la BossBar via `GuideManager.paintQuestBar` — l objectif mémorisé reste le **texte brut**, sinon la flèche s empilerait sur elle-même à chaque tick. Cibles : PNJ des Actes I/II (par RÔLE via `NpcManager.locationOfRole`, donc déplaçables sans rien casser), les 2 coffres cachés (`ActeManager.getChestLocation/getChest2Location`), et la visite d Alabasta (`LogPoseManager.cibleActuelle`, texte dynamique donc résolu par le manager).
- [`CommandMenu.java`](../src/main/java/fr/garfield/privatemines/CommandMenu.java) — **annuaire `/commande`** (alias `/commandes`, `/cmds`, `/aide`) : les 48 commandes DU PLUGIN uniquement (rien d Essentials/LuckPerms/WorldEdit), par ordre **alphabétique**, 28 par page. Les 22 commandes admin ne sont visibles **que par les OP** (26 entrées pour un joueur normal). ⚠️ **Les 8 `/reset*` sont réservées aux OP depuis le 2026-08-23** : garde en tête dans `CommandManager.onCommand`, par PRÉFIXE (`startsWith("reset")`) — toute future commande `/resetXxx` est donc protégée d office. **Sous-commandes (2026-09-02)** : cliquer une commande marquée `▸` ouvre **sa page** — une case par sous-commande, avec sa syntaxe et ce qu elle fait. Table `SOUS` (`Map<String, Sub[]>`, clé = nom SANS le slash), ~90 sous-commandes sur 18 commandes. ⚠️ **`TITRE_SOUS` (« §8§lCommande ») est un PRÉFIXE de `TITRE` (« §8§lCommandes »)** : `onClick` teste donc la page des sous-commandes EN PREMIER, sinon l annuaire attrape ses clics. Filtrage OP identique à l annuaire (une sous-commande OP est masquée aux joueurs ; une commande dont toutes les sous-commandes sont OP n est pas cliquable pour eux). Source des listes = le tab-complete de `CommandManager.onTabComplete`, complété par les handlers — ⚠️ `/mine set` et `/mine armortest` existent dans le code mais **ne sont proposées par aucun tab-complete**. Menu **passif** : cliquer ne lance rien (choix user). ⚠️ **Pour ajouter une commande : une ligne dans `TABLE`, rien d autre** — pagination et filtrage OP suivent seuls ; garder la table dans l ordre alphabétique (elle n est pas triée à l exécution).
- [`QuestManager.java`](../src/main/java/fr/garfield/privatemines/QuestManager.java) — **hub des quêtes `/quest`** (remplace l'ancien `/quest` qui n'affichait qu'une ligne). Deux sections vivantes : **quotidiennes** et **quête d'histoire** (reprend `GuideManager.getQuestObjective`). Quatre sections réservées en « bientôt » (hebdo, objectif serveur, contrats, île) → les ajouter ne demandera pas de refonte du menu.
  **Quotidiennes** : 3 quêtes/jour tirées dans un pool de 5, reset à **minuit heure serveur**, progression **perdue** si non terminée. Tirage **déterministe** (graine = UUID + jour) → un redémarrage ne rebat jamais les cartes. Pool : miner 5 000 blocs · miner 1 000 minerais · vendre pour X $ · monter X niveaux d'enchant · améliorer le sac 3 fois. ⚠️ **Le minage ne compte QUE le bloc cassé à la pioche** (même règle que `addPickaxeXp`) : sinon une salve d'Explosion (250 blocs) bouclerait la quête en 1 min — le drapeau `lastMinedWasOre` de `PrivateMines` transporte la nature du bloc jusqu'à l'appelant. ⚠️ La quête « sac » ne compte PAS le bonus d'argent (courbe de prix bien moins chère → quête quasi gratuite). ⚠️ Les objectifs de **minage sont FIXES** pour tous (choix user : avec Efficacité 7+ tout le monde one-shot) ; « vendre / enchant / sac » ont **4 jeux de valeurs par palier** (mêmes paliers que `DailyManager`). ⚠️ La quête « minerais » est **retirée du tirage** quand la mine du joueur n'a pas de minerai (Arc II = 100 % grès).
  **Récompenses** tirées à la génération et affichées d'avance : argent 50 % (3 % du prix de la prochaine mine) · fragments 20 % · clé 20 % · +1 niveau d'enchant 10 % (réutilise `CrateManager.grantRandomEnchantLevels`, passée en `public`). Réclamation au clic dans le menu.
  **Hebdomadaire** : **1 seule** grosse quête par semaine, tirée dans 4 types (miner 100 000 blocs · gagner 3 niveaux de pioche · finir 10 quotidiennes · recycler 200 armures), reset **samedi à minuit** (`semaine()` = `floorDiv(epochDay - 2, 7)`, le 1970-01-03 était un samedi). Lot : **les trois d un coup** — clé légendaire ×1 + pet ou armure (rareté du palier) + 1 000 ✦. ⚠️ « Niveaux de pioche » et « armures recyclées » ne sont PAS des compteurs neufs : on relève le total au début de semaine (`Hebdo.base`) et on fait la différence — donc **ce relevé doit être persisté** (`.questWeekBase`), sinon une reconnexion remettrait la progression à zéro.
  ⚠️ **Progression tenue en MÉMOIRE** (`onMined` est appelé à chaque bloc cassé — impossible d'écrire en config à cette fréquence) : chargée à la connexion, écrite à la déconnexion et par `saveAll()` à l'arrêt du plugin. Clés playerdata : `.questDay`, `.questProgress`, `.questClaimed`.
- [`DailyManager.java`](../src/main/java/fr/garfield/privatemines/DailyManager.java) — **récompenses quotidiennes** : cycle de **7 jours** qui repart, série **remise à J1 si un jour est sauté**, changement de jour à **minuit heure serveur** (`LocalDate.now().toEpochDay()`). Semaine alternée et croissante : `TYPE_DU_JOUR` = argent / clé / argent / fragments / argent / clé / gros lot. L'argent (`PCT_ARGENT` = 5 / 9 / 13 %, **rien au J7**) est un % du prix de la **prochaine mine** — pas de plafond ni de plancher (choix user). Le contenu des cases non-monétaires suit un **palier** (`PALIER_MINE_MAX` : mines 1-7 / 8-14 / 15-21 / 22+), changement **immédiat** ; le menu affiche en grisé le palier au-dessus. ⚠️ **Aucune clé légendaire avant la mine 15.** Accès = PNJ de rôle `quotidien` (particules tant que non réclamé) ; message chat à la connexion, **différé de 15 min à la toute première connexion** pour ne pas gêner l'Acte I. Persistance dans playerdata (`.dailyLastDay`, `.dailyStreak`). Outillage OP : `/daily reset [joueur]`, `/daily set <1-7> [joueur]`.
- [`WarpMenu.java`](../src/main/java/fr/garfield/privatemines/WarpMenu.java) — menu `/warp` : téléportation rapide (Spawn, Classements, Ma mine, **Crates** → -27/19/-149 face Ouest, L'End). Les destinations sont des `SLOT_*` en constantes + un `case` dans `onWarpClick`.
- [`CollectionMenu.java`](../src/main/java/fr/garfield/privatemines/CollectionMenu.java) — menu `/collections` (alias `/collec`) : les trouvailles rares des mines. Une case par **item-clé de fin d'arc** + la Plume ; non trouvé = silhouette « ??? » qui garde l'indice du lieu ; barre de progression globale + emplacement réservé aux 100 têtes. **Pour ajouter une trouvaille : une ligne dans `construireTable()`, rien d'autre.** Les arcs sans item codé (`codee=false`) sont générés automatiquement depuis `PrivateMines.ARCS`.
- [`LogPoseManager.java`](../src/main/java/fr/garfield/privatemines/LogPoseManager.java) — Boussole du Log Pose : visite guidée d'Alabasta (Arc II), la boussole pointe de lieu en lieu (`SPOTS`), progression persistée dans `logpose.yml`.

### Enchantements
- [`EnchantManager.java`](../src/main/java/fr/garfield/privatemines/EnchantManager.java) — tous les enchants (formules chance/puissance/coût/cap) + le menu `/enchant` (slots, pages, navigation). ⚠ **Passage au cap 200 en cours, enchant par enchant** (2026-08-20) : Vein (§16), Forage (§17), Colonne (§18) et Harpons (§19) sont faits — chance DÉCROISSANTE LINÉAIRE (7→2, 9→2, 12→4, 6→1 %), effet croissant (100/100/50/200 blocs au niv.200). Harpons a sa propre courbe de prix (`HARPON_RATIO` 1,45, base 2 000 $, sans remise) ; les 3 autres partagent `minePacedCost`, prix commun `minePacedCost(n)` = `264 × 1,39^n` calé sur le prix des mines (niveau 40 = mine 25). **Tout nouvel enchant passé au cap 200 branche son `xxxCost` sur `minePacedCost`.** Détail et tableaux dans `refonte.economie.md`. Les zones d'effet (boule de Vein, tunnel de Forage) sont dans `PrivateMines.triggerVein` / `triggerForage`. ⚠ **La Dîme du Passeur reste HORS refonte cap 200** (cap 25, choix explicite du user 2026-08-21) : seuls son palier (`REQ_DIME` = **33**) et son prix (`DIME_BASE` 75 000 $ × `DIME_RATIO` 1,39) ont été refaits — `dimeThreshold`/`dimeMult` inchangés (voir `refonte.economie.md` §23). ⚠ **Le Gouffre part au cap 100** (refonte 2026-08-22, §27) : palier `REQ_GOUFFRE` **52**, prix `GOUFFRE_BASE` 300 000 $ × 1,39, chance **décroissante 7 → 1 %**, et surtout il est désormais piloté par `gouffreBlocks` (**250 blocs au niveau max**) et non plus par un rayon — `gouffreRadius` ne sert plus qu’à l’affichage, et `PrivateMines.triggerGouffre` prend les N blocs les plus PROCHES comme `triggerExplosion`. ⚠ **Le Cœur de la Tempête part aussi au cap 100** (refonte 2026-08-22, §28) : palier `REQ_CYCLONE` **58**, prix `CYCLONE_BASE` 1 000 000 $ × 1,39, chance **décroissante 3 → 0,5 %**. ⚠ **La mécanique a changé de nature** : `cycloneBlocks` est un **budget de blocs** (20 → 200) qui **arrête la tempête dès qu’il est épuisé** ; durée (`cycloneDuration`, raccourcie à 6 → 15 s) et `cycloneBlocksPerBolt`/`cycloneBoltCount` ne sont plus que la mise en scène. `cycloneBoltsPerBurst` **n’existe plus**. ⚠ `setCycloneLevel`/`setGouffreLevel` **clampent au cap au chargement** — une sauvegarde portait `cycloneLevel: 183`, le menu aurait affiché « 183/100 ». ⚠ **Pluie de Flèches refondue** (2026-08-22, §29) : palier `REQ_FLECHE` **65** (le plus haut du jeu), `FLECHE_PRICE` 1 250 000 $ × **1,39**, chance **décroissante 4 → 0,5 %** — elle montait à **100 %** avant, la salve tombait à chaque coup ; salve ramenée de 1 000 à **300 blocs** (`flecheCount` 1→20 × `flechePierce` 3→15, calées pour tomber pile sur 300). `triggerFleche` inchangé. ⚠ **la Pluie de TNT (60) se débloque désormais AVANT les Flèches (65)** et n’est pas encore refaite.

### Narratif / quêtes
- [`IntroManager.java`](../src/main/java/fr/garfield/privatemines/IntroManager.java) — scène d'ouverture (Acte I, première connexion).
- [`ActeManager.java`](../src/main/java/fr/garfield/privatemines/ActeManager.java) — Actes I & II : PNJ (Contremaître, Forgeron), remise du sac/pioche, déclenche l'Apprentissage.
- [`TutorialManager.java`](../src/main/java/fr/garfield/privatemines/TutorialManager.java) — Acte III « L'Apprentissage » : parcours de quêtes de découverte, objectifs BossBar, récompenses.
- [`DialogueManager.java`](../src/main/java/fr/garfield/privatemines/DialogueManager.java) — utilitaire d'affichage de dialogues.
- [`GuideManager.java`](../src/main/java/fr/garfield/privatemines/GuideManager.java) — BossBar (objectif de quête affiché en haut) + astuces.

### PNJ / hologrammes / skins
- [`NpcManager.java`](../src/main/java/fr/garfield/privatemines/NpcManager.java) — gestion des PNJ (spawn, regard, interaction).
- [`NpcConfig.java`](../src/main/java/fr/garfield/privatemines/NpcConfig.java) — configuration/données des PNJ.
- [`NpcSettingMenu.java`](../src/main/java/fr/garfield/privatemines/NpcSettingMenu.java) — menu de réglage d'un PNJ (`/pnj`).
- [`HologramManager.java`](../src/main/java/fr/garfield/privatemines/HologramManager.java) — textes flottants (hologrammes). Modèles pré-remplis via `/holo set <modèle>` (alias `/hologram`) : **end** (règles + timer dragon), **passeur**, **minereset** (`lignesHoloMineReset`, ajouté le 2026-08-21 — explique le seuil de régénération ; le % est LU dans `PrivateMines.getResetThresholdPct()`, donc reposer l'holo suffit après un changement de seuil). ⚠ Ce fichier est en fins de ligne **LF**, contrairement à `PrivateMines.java`/`CommandManager.java` qui sont en **CRLF**.
- [`SkinFetcher.java`](../src/main/java/fr/garfield/privatemines/SkinFetcher.java) / [`SkinLibrary.java`](../src/main/java/fr/garfield/privatemines/SkinLibrary.java) — récupération / bibliothèque de skins de PNJ.
- [`ParticleManager.java`](../src/main/java/fr/garfield/privatemines/ParticleManager.java) — effets de particules.

### Pets (familiers)
- [`PetMenu.java`](../src/main/java/fr/garfield/privatemines/PetMenu.java) — menu `/pets` (sélecteur d'arcs, crate).
- [`PetStorage.java`](../src/main/java/fr/garfield/privatemines/PetStorage.java) — coffre « Mes Familiers » (équiper un pet).
- [`PetEquipMenu.java`](../src/main/java/fr/garfield/privatemines/PetEquipMenu.java) — menu d'équipement des pets.
- [`PetHeadProvider.java`](../src/main/java/fr/garfield/privatemines/PetHeadProvider.java) — têtes HeadDB des pets.

### Parcelles
- [`ParcelleManager.java`](../src/main/java/fr/garfield/privatemines/ParcelleManager.java) — gestion des parcelles (création, entrée, permissions, invités) + **suppression d'une Île** (`deleteParcelle`). La suppression enchaîne 5 choses : efface la clé de `parcelles.yml` (⚠️ `save()` ne fait qu'écrire, il faut un `config.set(uuid, null)` explicite), libère l'emplacement de grille dans `freeGridIndexes` (réutilisé en priorité par `createParcelle`), purge les spawners de la zone, supprime les entités, puis rase le terrain (`clearTerrain`). ⚠️ La taille d'une Île va de 10×10 à 100×100 : `clearTerrain` efface donc avec un **budget en blocs par tick** (pas en couches) et borne la plage Y à `GRID_WORLD_Y − 8` pour ne pas creuser le terrain vanilla.
- [`IslandAdminMenu.java`](../src/main/java/fr/garfield/privatemines/IslandAdminMenu.java) — menu OP `/is admin` : liste paginée de **toutes** les Îles (têtes des proprios, triées par score de banque) avec nom d'Île, en ligne/hors ligne, banque, phase OneBlock, coords et taille. Clic gauche = TP, **Shift + clic droit** = écran de confirmation puis suppression (délègue à `ParcelleManager.deleteParcelle`). Évacue les joueurs présents avant de raser.
- [`Parcelle.java`](../src/main/java/fr/garfield/privatemines/Parcelle.java) — objet représentant une parcelle (données).
- [`SpawnerManager.java`](../src/main/java/fr/garfield/privatemines/SpawnerManager.java) — spawners d'île (fer/or) : fabrication de l'item taggé, pose, niveaux (vitesse/quantité/cap/loot), mob stacké, `/spawner tool`. ⚠️ **Un spawner n'est PAS un bloc vanilla** : c'est une entrée `PlacedSpawner` de la map `placed`, persistée dans `spawners.yml`, avec une tâche qui fait apparaître les mobs **par coordonnées**. Casser ou effacer le bloc ne suffit donc jamais — il faut retirer l'entrée, sinon les mobs continuent de respawner dans le vide. Outils prévus pour ça : `removeSpawnersInArea` (appelé à la suppression d'une Île) et `/spawner cleanup` → `cleanupOrphanSpawners` (purge les spawners dont la position n'appartient plus à aucune parcelle). ⚠️ distinct de `SpawnerListener.java` (événements : menu d'upgrade, loot à la mort).
- [`BackpackManager.java`](../src/main/java/fr/garfield/privatemines/BackpackManager.java) — sac à dos `/bp`.
- [`ZoneManager.java`](../src/main/java/fr/garfield/privatemines/ZoneManager.java) — zones (spawn / mine / parcelle) et transitions.
- [`WallManager.java`](../src/main/java/fr/garfield/privatemines/WallManager.java) — murs invisibles `/mur` : barrières bloquantes (segment vertical entre 2 points) posées avec un « Sélecteur de Mur », sans builder de blocs physiques. Persistées dans `walls.yml`.

---

## 🗃️ Fichiers de DONNÉES (côté serveur, pas dans le code)

> Situés dans `plugins/PrivateMines/` (générés à l'exécution).

| Fichier | Contient |
|---|---|
| `plugins/PrivateMines/playerdata.yml` | données de jeu des joueurs (niveaux sac/enchants, mines débloquées, argent en jeu via Vault) |
| `plugins/PrivateMines/parcelles.yml` | parcelles des joueurs (centre, taille, invités, permissions, banque, OneBlock) + `nextGridIndex` et `freeGridIndexes` (emplacements de grille libérés par une suppression d'Île) |
| `plugins/PrivateMines/spawners.yml` | spawners d'île posés (position, type, niveaux, stack) — ⚠️ c'est CE fichier qui fait spawner les mobs, pas le bloc |
| `plugins/PrivateMines/crate_blocks.yml` | positions des blocs incassables issus de crate (command block / bedrock posables et récupérables) |
| `plugins/PrivateMines/walls.yml` | murs invisibles `/mur` |
| `plugins/PrivateMines/logpose.yml` | progression de la Boussole du Log Pose |
| `world/playerdata/<uuid>.dat` | données vanilla + flags NBT (`intro_seen`, progression tutoriel) |
| `ops.json` | joueurs OP |

---

## 📝 Docs & specs (ici, dans `Serv prison/`)
- `refonte.economie.md` — **état réel de l'économie**, extrait du code : chaîne de calcul d'un dollar, table des 25 mines (compo/prix/amorti), sac, XP de pioche, les 19 enchants (prix par niveau + effets), pets, armures, prestige, + les 6 problèmes d'équilibrage identifiés. **Source de vérité sur les chiffres** ; à régénérer après chaque changement de barème.
- `REFONTE_100_MINES.md` — cahier des charges des 100 mines + tableau état réel des mines existantes.
- `enchantement.md` — catalogue des enchants prévus.
- `PETS_DESIGN.md` — design des pets.
- `GUIDE_JOUEUR.md` — guide narratif joueur.
- `ONE_PIECE_PLAN.md` — plan narratif global (arcs, îles, quêtes futures).
- `AUDIT_PERFORMANCE.md` — audit lag complet (39 points classés par gravité + plan d'action priorisé).
- `EVEIL_ET_PORTAIL.md` — système d'Éveil de la pioche + Sanctuaire derrière le portail du Puits (lore, 5 épreuves, plan de dev) ; enchants associés = famille XIV d'`enchantement.md`.
- `RECOMPENSES_CRATES.md` — tableau des récompenses des 3 crates (poids/%) + liste noire des blocs. **Source de vérité** : `CrateManager.lootTable()` doit suivre ce fichier.
- `ENCHANTS_PVP.md` — spec de l'écosystème PvP « La Forge de Guerre » (Charge de Guerre, Maîtrise, économie, anti-abus, roadmap). Non codé.
- `ONEBLOCK_PARCELLE.md` — spec du OneBlock d'île (12 phases/biomes).
- `PRESTIGE_RENAISSANCE.md` — spec Prestige / Renaissance.
- `END.md` — spec de l'End et de l'event Dragon du Vide.
- `ACTE_1_GREVE_DES_OUBLIES.md` / `ACTE_2_PREMIERE_TERRE.md` — specs narratives des Actes I et II.
- `ANALYSE_ONE_PIECE_STRUCTURE.md` — analyse de la structure narrative de One Piece (référence pour le lore).
- `Armure_stats.md` — stats des armures d'Oubliés.
- `A_TESTER.md` — liste des points à tester en jeu (dette de test).

---

*Mets ce fichier à jour quand tu ajoutes/renommes un fichier important.*
