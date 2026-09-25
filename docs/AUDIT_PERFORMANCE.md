# 🔍 AUDIT PERFORMANCE — Serv Prison (2026-07-06)

Analyse complète du plugin `PrivateMines` (source `PrivateMines-dev`) + configs serveur + JVM + logs.
Chaque point cite le fichier et la méthode concernés. Classement par gravité.

**Preuve de lag observée** : `logs/latest.log` → `Can't keep up! Running 2581ms or 51 ticks behind` au démarrage (boot 14,5 s).

---

## 🔴 CRITIQUE — les vrais responsables des freezes

### 1. `sendFakeMine` : ~500 000 blocs envoyés à CHAQUE entrée/TP de mine
`PrivateMines.java` → `sendFakeMine()` (~ligne 3384)
- La mine fait 100×100×50 = **500 000 blocs**. À chaque TP vers une mine :
  - **Vagues par chunk** : ~49 chunks × 12 800 BlockState créés = 500k objets `BlockState` + 500k `getBlockAt().getState()`.
  - **PIRE : la « passe de fiabilité » 1 s après** reconstruit les **500 000 BlockState d'un coup** dans UNE liste et l'envoie en un seul `sendBlockChanges` → gros hoquet garanti (allocation ~1-2 millions d'objets au total, dont 500k `Location` créées par `getBlockForPlayer`).
- **C'est très probablement LE freeze ressenti quand on change de mine.**
- 💡 Pistes : passe de fiabilité par chunk étalée (comme les vagues) au lieu d'une liste unique ; ne renvoyer en fiabilité QUE les chunks proches du joueur ; réutiliser les BlockState des vagues au lieu de tout reconstruire.

### 2. `generateCoalPositions` : 500 000 `Location` + shuffle TOUTES LES 60 s
`PrivateMines.java` → `generateCoalPositions()` (~ligne 280)
- Construit une **ArrayList de 500 000 objets `Location`**, la **shuffle**, puis garde jusqu'à ~45 % (mine F : 30 % charbon + 15 % blocs = **225 000 Locations** dans 2 HashSet) — **par joueur**.
- Appelé à **chaque reset de mine (60 s)** ET à chaque TP de mine → énorme pression sur le GC (ramasse-miettes) = micro-freezes réguliers.
- Le diff du reset (`resetMineForPlayer`) reparcourt ensuite ~2×225 000 entrées pour la différence symétrique.
- 💡 Pistes : encoder les positions en `long` (x<<40|y<<20|z) dans des `LongOpenHashSet`-like au lieu d'objets `Location` (÷10 la mémoire, zéro allocation) ; tirer les positions par échantillonnage aléatoire d'index SANS construire la liste des 500k (tirage de N index uniques).

### 3. Rafales Explosion/Fracture : jusqu'à ~30 000 `creditMineBlock` en UN tick
`PrivateMines.java` → `triggerFracture` (~2522), `triggerExplosion` (~2333), `creditMineBlock` (~2273)
- Fracture niv. 1000 (après buff) : **28 éclairs × sphère rayon 6,5 ≈ 1 150 blocs = ~32 000 appels** dans le même tick.
- Chaque `creditMineBlock` fait : 1 packet `sendBlockChange` + `Location.clone()` + ajout HashSet + retraits sets charbon + `petEquip.aggregate()` (voir #11) + compteur Dîme + **`updatePickaxeBar` (packet XP)** + PDC tutoriel + deque blocs/min.
- Résultat : ~30 000 packets bloc + ~30 000 packets XP + ~30 000 écritures PDC en 1 tick → freeze + burst réseau.
- Explosion niv. 1000 : rayon 7 ≈ 1 400 blocs — même problème en plus petit.
- 💡 Pistes : dans les rafales, collecter les blocs puis UN SEUL `sendBlockChanges(liste)` ; appeler `updatePickaxeBar` UNE fois par rafale (XP ajouté en masse) ; agréger les bonus pets une fois par rafale ; éventuellement étaler les gros procs sur 2-3 ticks.

### 4. `saveDataConfig` : écriture DISQUE SYNCHRONE du fichier complet sur le thread principal
`PrivateMines.java:691` + appels depuis `PetStorage.save()` (:134), `PetEquipMenu.save()` (:74), `BackpackManager` (:122), `savePlayer` (déco, commandes reset…)
- `playerdata.yml` fait déjà **196 Ko pour 2 joueurs** (inventaires + pets sérialisés). Chaque save = re-sérialisation YAML COMPLÈTE + écriture disque **sur le main thread**.
- Déclenché à **chaque clic** dans le coffre des familiers (équiper/retirer/déposer), chaque fermeture de BP, etc.
- Grossira linéairement avec le nombre de joueurs → à 50 joueurs, chaque clic pet = écrire ~5 Mo en synchrone. ⚠️ Bombe à retardement.
- 💡 Pistes : « dirty flag » + autosave périodique (30-60 s) asynchrone (`runTaskTimerAsynchronously` sur une COPIE sérialisée) ; ou un fichier par joueur ; ne JAMAIS écrire sur le main thread.

### 5. `saveSpawners` : réécriture YAML potentiellement CHAQUE SECONDE
`SpawnerManager.java` → `startSpawnerTask()` (:274, save :288) + `onMobDeath` (:309)
- La task de génération tourne chaque seconde ; dès qu'UN spawner incrémente son stack → `saveSpawners()` = reconstruction complète de la config + **écriture disque synchrone**. Avec plusieurs spawners aux cycles différents, ça écrit quasi toutes les secondes.
- Chaque mort de mob = une écriture de plus.
- 💡 Pistes : dirty flag + save toutes les 60 s + à l'arrêt. Le stack peut se recalculer au boot depuis un timestamp (aucune perte).

---

## 🟠 MOYEN — coût permanent évitable

### 6. `startFullBagTask` (toutes les 0,5 s) : scan d'inventaire + meta réappliqué en boucle
`PrivateMines.java:1694` → `giveBag` (:2712), `givePickaxe` (:2749), `applyPickaxeEnchants` (:2763)
- Pour chaque joueur en mine, **toutes les 10 ticks** :
  - `giveBag` + `givePickaxe` scannent **les 41 slots ×2**, et `isBag`/`isPickaxe` appellent `getItemMeta()` qui **CLONE le meta** de chaque item → des centaines de clones/seconde/joueur.
  - `givePickaxe` finit par `applyPickaxeEnchants` **inconditionnellement** : reconstruit lore + enchants + `setItemMeta` → **packet de slot renvoyé toutes les 0,5 s** même si rien n'a changé.
- 💡 Pistes : n'appeler `applyPickaxeEnchants` que quand un enchant change (achat) ou à l'entrée en mine ; alléger le scan (vérifier seulement PICK_SLOT/BAG_SLOT en temps normal, scan complet seulement à l'entrée en mine).

### 7. `startHidePlayersTask` : O(n²) joueurs chaque seconde
`PrivateMines.java:1879`
- Double boucle `getOnlinePlayers × getOnlinePlayers` + `hidePlayer`/`showPlayer` appelés **même quand l'état ne change pas**. À 2 joueurs = rien ; à 40 joueurs = 1 600 itérations/s avec appels API.
- 💡 Piste : mémoriser l'état (`Set<UUID> hidden`) et n'appeler hide/show QUE sur transition entrée/sortie de mine.

### 8. Scoreboard rafraîchi toutes les 6 ticks (3,3×/s) par joueur
`PrivateMines.java` → `startScoreboardTask` (:1977, période `6L`) + `updateScoreboard` (:872)
- Chaque refresh reconstruit ~10 strings + appelle `formatBig` (BigInteger), `getBlocksPerMinute`, et **`mineTiles()` qui RÉALLOUE une liste de tuiles complète à chaque appel** (:321, appelé :892 juste pour `.size()`).
- 💡 Pistes : passer la période à 20 ticks (1 s suffit largement) ; remplacer `mineTiles().size()` par `MINES.length` ; ne mettre à jour une team que si son texte a changé.

### 9. `LeaderboardManager` : scan de TOUS les joueurs connus toutes les 60 s
`LeaderboardManager.java:36-120`
- Parcourt toutes les clés de `playerdata.yml`, appelle `Bukkit.getOfflinePlayer(uuid)` (lookup profil, parfois disque) et lit plusieurs clés YAML par joueur. OK à 2 joueurs, lourd à 500 profils.
- Les valeurs argent en centimes dans un `long` → **overflow au-delà de ~92 000 000T** (92 quadrillions de centimes) : le TOP ARGENT affichera n'importe quoi bien avant 999ZZ.
- 💡 Pistes : recalcul asynchrone (lecture seule) puis application sync ; cache des noms ; stocker en `BigInteger`/String pour le top argent.

### 10. `clearRealZone` au boot : 500 000 `setType` synchrones
`PrivateMines.java:3315` (appelé ~:1672)
- Vide réellement les 500k blocs de la zone mine à CHAQUE démarrage → contribue aux **14,5 s de boot** et au « Can't keep up » de démarrage. Une fois la zone déjà vide, ces setType sont presque tous des no-ops mais le parcours + getBlockAt reste payé.
- 💡 Piste : flag « déjà vidée » dans un fichier (ou vérifier un bloc-témoin) pour sauter le nettoyage ; ou nettoyage par chunks étalé sur plusieurs ticks.

### 11. `petEquip.aggregate()` recalculé PAR BLOC MINÉ et PAR SECONDE
`PetEquipMenu.aggregate` — appelé dans `creditMineBlock` (:2291) et dans la task de vente (:1745)
- Chaque appel relit le PDC des pets équipés via `getItemMeta()` (clone) + lookup `defById`. Par bloc miné (donc ×30 000 dans une rafale Fracture !) et chaque seconde par joueur.
- 💡 Piste : cacher le `Bonus` agrégé par joueur, invalider seulement quand on équipe/retire un pet ou qu'un pet monte de niveau.

### 12. Reset différentiel des mines à charbon : diff sur ~450 000 entrées
`PrivateMines.java` → `resetMineForPlayer` (:1815)
- Le principe différentiel est BON (✅), mais avec 45 % de minerai (mine F), oldCoal+newCoal ≈ 450k `Location` parcourues + `toUpdate` qui peut contenir ~200k blocs → `sendBlockChanges` massif toutes les 60 s par joueur en mine F+.
- Dépend directement du fix #2 (encodage long) pour devenir indolore.

### 13. Log de diagnostic `[FRACTURE]` encore actif
`PrivateMines.java` → `triggerFracture` (fin de méthode)
- Écrit une ligne console à CHAQUE proc de Fracture. I/O console inutile en prod. **À retirer dès que le bug sac est résolu.**

---

## 🟡 MICRO — petits détails qui s'additionnent

14. **`dropAndMergeLoot`** (:1471) : `getNearbyEntities` (scan spatial) à chaque mort de mob de spawner. OK à faible taux, coûteux si fermes géantes.
15. **`NpcManager.tickLook`** (:442, toutes les 5 ticks) : par PNJ × par joueur, distance² + 2 packets de rotation de tête pour les joueurs à ≤8 blocs, 4×/s. Négligeable maintenant, ×N PNJ plus tard.
16. **`ParticleManager`** (:145, toutes les 12 ticks) : 8 particules × spots × joueurs ≤60 blocs. S'additionne avec le nombre de spots posés.
17. **`ActeManager.startChestParticles`** (:291, toutes les 12 ticks) : 16 particules vers les joueurs proches n'ayant pas fini l'étape. OK (gaté par distance + étape).
18. **`startZoneParticleTask`** (:1996) : dessine des **murs PLEINS** de particules par zone visualisée, chaque seconde. Admin seulement, mais une grosse zone = des milliers de particules/s. À n'utiliser que ponctuellement.
19. **`SpawnerManager.spawnerOfEntity`** (:267) : recherche linéaire O(n spawners) à chaque mort de mob. OK petit, HashMap uuid→spawner si ça grossit.
20. **`CrateManager.onChunkLoad`** : itère toutes les crates à chaque chargement de chunk. Trivial avec <50 crates.
21. **`updatePickaxeBar` par bloc miné** (:591) : 1-2 packets XP par bloc. Voir fix groupé #3.
22. **`TutorialManager.setCounter`** (:114) : écriture PDC par bloc miné tant que l'étape 1 n'est pas finie (borné à 30 blocs, puis stop). OK.
23. **Menu enchants** : `explosionBlockCount` = boucle O(R³) (rayon 7 → 3 375 itérations) ×2 (Explosion+Fracture) à chaque OUVERTURE/refresh du menu (chaque achat refresh aussi). Invisible à l'échelle d'un clic, mais calculable une fois et caché par niveau.
24. **`isPickaxe`/`isBag`** : chaque appel fait `getItemMeta()` = clone complet du meta. Utilisés dans les scans 0,5 s (#6), les listeners de clic/drop… Piste : tag PDC + `PersistentDataContainerView` (Paper) sans clone, ou comparer d'abord le `Material`.
25. **`mineTiles()`** (:321) : réalloue la liste des mines à chaque appel (scoreboard 3,3×/s + menus). Piste : liste statique construite une fois.
26. **`formatBig`/BigInteger au scoreboard** : divisions BigInteger 3,3×/s/joueur. Trivial, fusionné avec #8.
27. **`crates.yml`** : sauvegardé seulement sur action admin/ouverture — ✅ OK (les clés gagnées en minant ne déclenchent PAS de save).
28. **Roulette pets** : task 1 tick pendant l'animation d'éclosion — transitoire, OK.
29. **`blockTimes`** (deque fenêtre 60 s par joueur) : propre, borné par le débit de minage. OK.
30. **`onDrop`/`onCreatureSpawn`/listeners divers** : vérifs légères, RAS.
31. **Playit.gg** : le tunnel ajoute de la **latence réseau** (ping) et un peu de CPU sur le même PC — ce n'est PAS du lag TPS, mais les joueurs le ressentent comme du lag. Rien à corriger côté plugin.

---

## ⚙️ SERVEUR / JVM / CONFIGS

### 32. `start.bat` : `-Xms16G -Xmx16G` SANS flags GC — à revoir
- 16 Go de heap fixe sur un PC qui fait AUSSI tourner ton client Minecraft (+ Playit) : risque de pression mémoire/swap Windows, et **de longues pauses GC** (grand heap + G1 par défaut sans tuning).
- Pour un serveur 2-10 joueurs : **6 Go suffisent largement** et donnent des pauses GC plus courtes.
- 💡 Recommandé (flags Aikar) :
```
java -Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -jar paper.jar --nogui
```

### 33. `server.properties` : `view-distance=10`, `simulation-distance=10`
- Le gameplay est à 99 % dans la mine/le spawn/les parcelles (zones fixes). **`view-distance=7` et `simulation-distance=5-6`** réduisent nettement chunks/entités tickés sans impact visible pour un Prison.

### 34. `bukkit.yml` : `spawn-limits.monsters=70`
- Le serveur n'utilise pas de mobs naturels (zones bloquent le spawn via `onCreatureSpawn` — qui ANNULE les spawns mais le jeu continue d'en TENTER). Baisser à `monsters: 20, animals: 5` évite des tentatives de spawn + l'event spam.
- `chunk-gc` : garder par défaut (ok).

### 35. `spigot.yml` : `entity-activation-range` par défaut (32/32)
- Peut passer `animals: 16, monsters: 24, misc: 8` — moins d'IA tickée. Impact faible ici (peu d'entités) mais gratuit.

### 36. Plugin **MineResetLite probablement INUTILISÉ** → à retirer
- Le reset des mines est 100 % maison (fake blocks par joueur). MineResetLite charge ses listeners/tasks pour rien. Supprimer le jar = moins de listeners, moins de RAM. *(Vérifier qu'aucune mine MRL n'est configurée avant.)*

### 37. Essentials
- Économie déjà coupée (✅). Il garde ses propres autosaves userdata (léger à 2 joueurs). RAS de plus.

### 38. `spark` est installé — sers-t'en pour MESURER
- `/spark tps` → TPS + MSPT en direct.
- `/spark profiler --timeout 60` pendant une session de minage intensif → montrera exactement lesquels des points ci-dessus coûtent le plus CHEZ TOI. À refaire après chaque optimisation.

### 39. Anti-Xray Paper : `enabled: false` — ✅ correct ici
- La mine est en fake blocks côté client, l'anti-xray n'apporterait que du CPU pour rien.

---

## ✅ DÉJÀ BIEN (à ne pas casser)

- **Reset différentiel** des mines (~15 % renvoyé au lieu de 100 %) — bonne idée, à conserver.
- **Économie maison en mémoire** : aucun I/O par transaction, autosave 2 min + onDisable. Propre.
- **`giveKey` (Fortune)** n'écrit PAS sur disque à chaque clé gagnée.
- **Scoreboard par teams/préfixes** : pas de re-register, pas de flicker — la bonne technique.
- **Hologrammes = Text Display natifs statiques** : zéro coût par tick (mise à jour classements 60 s seulement).
- **Packets minage** : décodage sur le thread réseau, actions repassées au main thread via `runTask` — correct.
- **Particules gatées par distance²** (PNJ, coffre d'acte, spots) — bon réflexe partout.
- **`blockTimes`** en deque à fenêtre glissante — propre et borné.

---

## 🛠️ PLAN D'ACTION PRIORISÉ

**Quick wins (0 code / 5 min)**
1. `start.bat` → 6G + flags Aikar (#32)
2. `view-distance 7` / `simulation-distance 6` (#33) + spawn-limits (#34)
3. Retirer `MineResetLite.jar` (#36)
4. Retirer le log `[FRACTURE]` une fois le bug sac réglé (#13)

**Gros gains (code, dans l'ordre d'impact)**
5. `sendFakeMine` : passe de fiabilité par chunks étalés (#1)
6. Positions charbon encodées en `long` + tirage sans liste 500k (#2, débloque #12)
7. Rafales Explosion/Fracture : batcher `sendBlockChanges` + 1 seul update XP/pets par rafale (#3)
8. Saves asynchrones + debounce (`playerdata.yml` #4, `spawners.yml` #5)
9. `applyPickaxeEnchants` seulement sur changement (#6)
10. Cache du `Bonus` pets (#11) ; scoreboard à 20 ticks + `MINES.length` (#8)

**Ensuite** : mesurer avec `/spark profiler` (#38) et re-prioriser selon les chiffres réels.
