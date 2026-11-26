# Gestion de tournoi de tennis de table par équipes

Logiciel de gestion de tournois de tennis de table par équipes : une application de bureau
(Java / Swing) qui pilote tout le tournoi en système suisse, et une page web qui permet aux
capitaines de saisir les scores depuis leur téléphone.

## Contexte

Projet personnel, conçu et développé seul pour un usage interne au club CTT Brunoy, sans but
commercial. L'objectif était de remplacer une organisation sur papier et une première version
du logiciel par un outil fiable le jour du tournoi : appariements automatiques, suivi des
tables, classement en direct et saisie des scores décentralisée.

L'outil reste générique : n'importe quel club peut l'utiliser en installant sa propre partie
serveur.

## Fonctionnalités

**Application de bureau (juge-arbitre)**

- Base de joueurs permanente, séparée des tournois : on saisit les licenciés une fois, puis on
  pointe les présents à chaque tournoi. Détection des homonymes.
- Catégories de joueurs avec un niveau de force, utilisé pour équilibrer les équipes.
- Équipes de 3 ou 4 joueurs, composition manuelle ou automatique (aléatoire, ou « rang 1 »
  avec le joueur le plus fort en tête), contrainte de symboles (deux joueurs de même symbole
  jouent dans la même équipe).
- Rencontres de 5 matchs (4 simples + 1 double), avec rotation des rôles pour les équipes de 3.
  Points configurables : avec 2 points d'écart (prolongations 12-10, 13-11...) ou sans écart.
- Appariement en système suisse sans tours fixes : dès qu'une table se libère, le logiciel
  propose la meilleure rencontre (score proche, jamais de revanche), sur 1 ou 2 tables voisines.
- Plan de la salle (lignes x colonnes), statut des tables (active, retirée, fil rouge, robot),
  chronomètre par rencontre.
- Classement en direct avec flèches de progression, préparation des départages de fin de
  tournoi (égalités sur le podium, barrages, tirage au sort).
- Écran d'affichage public pour un second écran ou un projecteur.
- Sauvegarde automatique du tournoi et de la base joueurs après chaque modification et à la
  fermeture : on peut quitter à tout moment et reprendre le tournoi plus tard (JSON).

**Application capitaines (téléphone)**

- Connexion par compte capitaine, affichage de la rencontre en cours, des compositions et du
  classement.
- Saisie des scores match par match. Le PC compare les saisies des deux capitaines : accord,
  le score est validé ; désaccord, un litige est signalé au juge-arbitre qui tranche ou fait
  resaisir.
- Écran de fin de rencontre (résultat, matchs gagnés, points marqués).

## Technologies

- Java 17 (compatible avec toutes les versions suivantes), Swing pour l'interface
- Gson 2.10.1 pour la persistance JSON
- Maven (facultatif) pour le jar exécutable et les tests, JUnit 5
- PHP 8 et MySQL / MariaDB (PDO) pour la partie serveur
- HTML / CSS / JavaScript sans dépendance pour la page des capitaines

## Structure du projet

```
.
├── application-pc/                 Application de bureau (Java)
│   ├── src/main/java/org/club/tournoi/
│   │   ├── model/                  Données : joueurs, équipes, tables, matchs, rencontres
│   │   ├── service/                Logique : ServiceTournoi, système suisse, classement, départages
│   │   ├── persistence/            Sauvegarde / chargement JSON
│   │   ├── serveur/                Client HTTP, synchronisation, comparaison des saisies
│   │   └── ui/                     Interface Swing (onglets, dialogues, thème)
│   ├── src/main/resources/images/  Image de la table (plan de salle)
│   ├── src/test/java/              Tests JUnit 5
│   ├── lib/gson-2.10.1.jar         Dépendance, pour compiler sans Maven
│   ├── compiler.bat                Compilation en un clic (Windows)
│   └── pom.xml                     Build Maven (jar exécutable, tests)
├── serveur/                        Partie serveur (PHP + MySQL)
│   ├── public/                     Contenu du dossier à déposer sur le site
│   │   ├── api.php                 API : état publié, comptes, saisies
│   │   └── index.html              Application capitaines
│   ├── outils/generer_capitaines.php   Création des mots de passe (à supprimer après usage)
│   ├── config.example.php          Modèle de configuration (à copier en config.php)
│   └── schema.sql                  Schéma de la base
├── docs/
│   ├── ARCHITECTURE.md             Architecture interne et protocole PC / serveur
│   └── screenshots/                Captures d'écran
├── LICENSE
└── README.md
```

## Installation et prérequis

### Application PC

Prérequis : Java 17 ou plus récent (JDK pour compiler).

Avec Maven (recommandé) :

```
cd application-pc
mvn clean package
```

Le jar `target/tournoi.jar` contient tout (Gson compris) et fonctionne hors ligne.

Sans Maven, sous Windows : double-cliquer sur `application-pc/compiler.bat`.

Sans Maven, sous Linux ou macOS :

```
cd application-pc
javac --release 17 -encoding UTF-8 -cp lib/gson-2.10.1.jar -d build/classes $(find src/main/java -name "*.java")
cp -r src/main/resources/* build/classes/
```

### Partie serveur (application capitaines)

Prérequis : un hébergement web avec PHP 8 et une base MySQL ou MariaDB.

1. Base de données : exécuter `serveur/schema.sql` (par exemple dans phpMyAdmin). Le script
   crée les tables et 30 comptes `capitaine01` à `capitaine30`, associés aux équipes 1 à 30.
2. Configuration : copier `serveur/config.example.php` en `config.php`, puis renseigner les
   identifiants MySQL et un secret partagé `TOURNOI_SECRET` long et aléatoire (32 caractères
   ou plus).
3. Dépôt des fichiers : copier le contenu de `serveur/public/` dans un dossier `tournoi/` du
   site, et placer `config.php` un niveau au-dessus de ce dossier :
   ```
   votre-site/
   ├── config.php
   └── tournoi/
       ├── api.php
       └── index.html
   ```
4. Comptes capitaines : choisir un préfixe de mot de passe dans
   `serveur/outils/generer_capitaines.php`, déposer ce fichier dans `tournoi/outils/`, l'ouvrir
   une fois avec `https://votre-site/tournoi/outils/generer_capitaines.php?cle=VOTRE_SECRET`,
   noter les mots de passe affichés, puis supprimer le fichier du serveur.
5. Vérification (facultative) depuis le PC :
   ```
   java -cp build/classes org.club.tournoi.serveur.DiagnosticConnexion https://votre-site/tournoi VOTRE_SECRET
   ```

## Utilisation

Lancer l'application :

```
java -jar application-pc/target/tournoi.jar
```

ou, après une compilation sans Maven (remplacer `:` par `;` sous Windows) :

```
cd application-pc
java -cp build/classes:lib/gson-2.10.1.jar org.club.tournoi.ui.Application
```

Déroulement type d'un tournoi :

1. Écran de démarrage : créer un tournoi (taille de la salle, règle des points) ou reprendre
   une sauvegarde, et choisir la base de joueurs.
2. Onglet Joueurs : pointer les présents.
3. Onglet Équipes : composer les équipes (à la main ou automatiquement).
4. Onglet Rencontres : premier lancement, puis suivre les suggestions du système suisse à chaque
   table libérée.
5. Onglet Capitaines (facultatif) : renseigner l'URL du serveur et le secret, puis démarrer la
   publication. Les capitaines ouvrent `https://votre-site/tournoi/` sur leur téléphone.
6. Fin de tournoi : préparer les départages, jouer les barrages, afficher le classement final.

Sauvegarde : il n'y a rien à faire. Le tournoi en cours (équipes, rencontres, scores, tables) et
la base joueurs sont enregistrés automatiquement deux secondes après chaque modification, puis
à la fermeture. Au lancement suivant, le tournoi et la base les plus récents sont proposés en
premier sur l'écran de démarrage.

Les données sont rangées dans un dossier fixe, quel que soit le dossier de lancement :

```
C:\Users\<nom>\Tournoi-TT\
├── Base_Tournoi\    un fichier .json par tournoi
└── Base_Joueur\     un fichier .json par base de joueurs
```

Pour utiliser un autre dossier (par exemple sur une clé USB), ajouter
`-Dtournoi.donnees=E:\Tournoi-TT` juste après `java`. Les dossiers `Base_Tournoi` et
`Base_Joueur` d'une ancienne version, présents dans le dossier de lancement, sont recopiés
automatiquement au premier démarrage (sans rien écraser).

## Captures d'écran et résultats

[À COMPLÉTER : ajouter dans `docs/screenshots/` des captures de l'onglet Rencontres, du plan de
la salle, de l'affichage public et de l'écran capitaine sur téléphone, puis les référencer ici.]

<!-- Exemple :
![Onglet Rencontres](docs/screenshots/rencontres.png)
![Application capitaines](docs/screenshots/capitaine-mobile.png)
-->

Tests automatisés (`mvn test`, 18 tests) : validation des scores, cycle complet d'une
rencontre, refus des revanches, classement, sauvegarde et reprise d'un tournoi en cours,
base joueurs, comparaison des saisies des capitaines.

## Notes sur cette version (corrections apportées)

- Reprise d'un tournoi sauvegardé en cours de rencontre ou de barrage : après rechargement, les
  rencontres pointaient vers des copies des équipes et des joueurs. Terminer la rencontre ne
  libérait pas les équipes, qui restaient « En match ». Les références sont maintenant
  reconstruites au chargement (`EtatTournoi.relierReferences`), tests à l'appui.
- Serveur : l'action `saisir` faisait confiance au côté (A ou B) envoyé par le téléphone. Un
  capitaine pouvait saisir pour les deux équipes et fabriquer un faux accord. Le côté est
  désormais déduit de l'état publié par le PC, et la rencontre, l'index du match et les bornes
  du score sont contrôlés. Les sessions expirées sont purgées à chaque connexion.
- Synchronisation : une saisie avec un index de match invalide faisait échouer chaque cycle et
  bloquait toute la publication. Elle est maintenant ignorée et purgée.
- Sauvegarde : auparavant, répondre « Non » à la question posée à la fermeture faisait aussi
  perdre la base joueurs, et les données étaient rangées dans le dossier de lancement (relancer
  depuis un autre dossier donnait l'impression de tout avoir perdu). Le tournoi et la base sont
  désormais sauvegardés automatiquement, dans un dossier fixe, avec une écriture atomique qui
  protège des coupures pendant l'enregistrement. Créer un nouveau tournoi sous un nom déjà pris
  demande confirmation.
- Compilation sans Maven : le script copie les ressources, sinon l'image du plan de salle
  manquait.
- Réorganisation du dépôt (`application-pc/`, `serveur/`, `docs/`), renommage de `ClientOvh`
  en `ClientServeur` et de `TestTuyau` en `DiagnosticConnexion`, commentaires mis à jour,
  code mort retiré.

## Pistes d'amélioration

- Générer des mots de passe capitaines aléatoires plutôt qu'un préfixe suivi d'un numéro.
- Stocker le secret partagé côté PC de façon chiffrée (il est aujourd'hui en clair dans les
  préférences Java de l'utilisateur).
- Ajouter le format « tournoi de double », déjà prévu dans `FormatTournoi`.
- Exporter le classement final (PDF ou CSV).
- Ajouter une intégration continue (GitHub Actions) qui lance `mvn test`.

## Auteurs

Quentin Soumet

## Licence

Code distribué sous licence MIT (voir `LICENSE`). La bibliothèque Gson fournie dans
`application-pc/lib/` est distribuée par Google sous licence Apache 2.0.
