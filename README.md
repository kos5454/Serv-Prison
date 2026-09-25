# PrivateMines

Plugin Java maison pour un serveur Minecraft **Prison**, développé de zéro sans framework de
gameplay tiers. Chaque joueur possède sa propre mine, progresse à travers une centaine de paliers
et fait tourner une économie complète : vente, hôtel des ventes entre joueurs, familiers, armures
à bonus, caisses à butin, quêtes et îles.

**Paper 1.21.4 · Java 21 · Maven · ~31 000 lignes réparties sur 58 classes.**

---

## Ce que fait le plugin

**Les mines.** Une mine privée par joueur, générée et réinitialisée à la volée. Le contenu, la
géométrie et les prix de vente sont décrits dans une table de définitions (`MineDef`) plutôt que
codés en dur, ce qui permet d'ajouter une mine en modifiant une seule ligne. Les blocs déjà minés
sont renvoyés au client en faux blocs via **ProtocolLib**, pour que la mine paraisse pleine sans
toucher au monde réel.

**L'économie.** Implémentation maison en `BigInteger` : les sommes ne débordent jamais, et
l'affichage passe automatiquement de K/M/B/T aux suffixes AA…ZZ. Elle remplace complètement
l'économie d'Essentials.

**L'hôtel des ventes.** Vente entre joueurs avec expiration, prolongation payante à coût croissant,
taxe, vitrine premium et liste noire d'objets non échangeables.

**Progression et contenu.** Niveau de pioche adossé à la barre d'XP native, 266 enchantements au
catalogue, familiers avec fusion, armures à bonus cumulés et forge de recyclage, caisses à butin
avec distribution validée sur 4 000 ouvertures, quêtes quotidiennes, mini-jeux de chat, pari pile ou
face entre joueurs, combat de dragon quotidien dans l'End, et un mode OneBlock par île.

---

## Architecture

```
src/main/java/fr/garfield/privatemines/
├── PrivateMines.java        point d'entrée, table des mines, prix de vente
├── CommandManager.java      toutes les commandes et leur tab-complete
├── EnchantManager.java      catalogue d'enchantements et effets au minage
├── ActeManager.java         quêtes narratives, PNJ, forge
├── EconomyManager.java      économie BigInteger
├── AuctionManager.java      hôtel des ventes
├── CrateManager.java        caisses et tables de butin
├── ArmorManager.java        armures à bonus, génération, reroll
├── PetStorage.java          familiers, équipement, fusion
└── …                        48 autres gestionnaires et écouteurs
```

`INDEX_FICHIERS.md` est la carte du projet : pour chaque comportement du jeu, il indique le fichier
et la fonction qui le produisent.

---

## Compiler et déployer

```bash
mvn -f pom.xml package        # produit target/PrivateMines.jar
```

`deploy.ps1` enchaîne la compilation et la copie du jar dans le dossier `plugins/` du serveur — la
copie n'a lieu que si le build réussit.

Dépendances (toutes en `provided`, fournies par le serveur) : `paper-api`, `ProtocolLib`, `VaultAPI`.

---

## Performance

`AUDIT_PERFORMANCE.md` recense 39 points chauds relevés sur le code, classés par coût. Les
principaux tiennent au fait qu'un serveur Minecraft simule le monde sur **un seul thread** à 20
ticks par seconde : tout ce qui dépasse 50 ms dans un tick se voit immédiatement par tous les
joueurs. Les points identifiés concernent l'allocation de centaines de milliers d'objets lors de la
régénération d'une mine, les rafales de cassage d'un enchantement de zone, et les sauvegardes
synchrones sur le thread principal.

---

## Limites connues

`PrivateMines.java` dépasse 5 500 lignes et concentre trop de responsabilités. Un découpage
progressif est en cours, une classe à la fois, chaque extraction étant validée en jeu avant la
suivante.

La persistance repose sur des fichiers YAML. Cela convient à un serveur unique, mais ne tiendrait
pas sur une architecture multi-serveurs, où une base clé-valeur serait nécessaire.
