# Architecture technique

Ce document complète le README : il décrit l'organisation interne de l'application PC et le
protocole d'échange avec le serveur.

## Vue d'ensemble

```
Application PC (Java)  ──  dépose l'état / relève les saisies  ──►  Serveur (PHP + MySQL)
   source de vérité                                                    boîte aux lettres
                                                                             ▲
                                                                             │ lit l'état / envoie des scores
                                                                    Téléphones des capitaines
                                                                    (page web HTML/JS)
```

Toute la logique du tournoi vit sur le PC. Le serveur ne fait que stocker et transmettre ; les
téléphones affichent l'état publié et renvoient des saisies. Le web ne permettant pas au serveur
de « pousser » vers un téléphone, tout fonctionne par interrogation régulière (toutes les
3 secondes côté PC comme côté téléphone).

## Application PC : architecture en couches

| Package | Rôle |
| --- | --- |
| `model` | Données pures : joueur, équipe, table, match (simple / double), rencontre, barrage, paramètres. Aucune classe ne déclenche d'effet de bord ni ne touche à l'affichage. |
| `service` | `ServiceTournoi` est la seule classe qui modifie l'état. Autour d'elle : moteur d'appariement suisse, classement, statistiques, composition automatique des équipes, départages. |
| `persistence` | Sauvegarde et chargement JSON (Gson) de l'état complet et de la base joueurs permanente. |
| `serveur` | Client HTTP, agent de synchronisation, comparaison des saisies, construction de l'état public. |
| `ui` | Interface Swing. Chaque écran s'abonne au service (`TournoiListener`) et se redessine tout seul à chaque changement. |

Conséquence : la génération automatique de rencontres (système suisse) passe par les mêmes
méthodes que les clics manuels (`lancerRencontre`, `enregistrerScoreMatch`...), sans code
dupliqué.

### Statistiques recalculées

Les statistiques d'une équipe (rencontres, matchs, points) ne sont jamais stockées : elles sont
recalculées à la demande à partir des rencontres terminées (`CalculStatistiques`). Il n'existe
donc qu'une seule source de vérité.

### Classement

Ordre appliqué par `Classement` :

1. nombre de rencontres gagnées ;
2. à égalité, position attribuée par un barrage de départage (s'il y en a une) ;
3. nombre de matchs gagnés ;
4. nombre de points marqués ;
5. numéro d'équipe (ordre stable).

### Système suisse sans tours fixes

`MoteurSuisse` propose, quand une table se libère, la meilleure rencontre à lancer :
l'équipe en attente depuis le plus longtemps affronte l'adversaire au nombre de victoires le plus
proche, jamais déjà rencontré. Le moteur choisit 1 ou 2 tables (voisines) selon le rapport entre
tables libres et équipes en attente, et peut conseiller d'attendre si une rencontre en cours doit
se terminer sous peu (durée estimée et seuil réglables).

### Persistance JSON

Gson sérialise l'objet `EtatTournoi` complet (joueurs, équipes, tables, rencontres, barrages,
paramètres). Deux points techniques :

- `Match` étant une interface, un adaptateur écrit le type concret (`MatchSimple` /
  `MatchDouble`) dans le JSON et le relit.
- Gson crée un objet distinct pour chaque occurrence. Après chargement,
  `EtatTournoi.relierReferences()` remplace donc chaque équipe et chaque joueur référencé par une
  rencontre, un barrage ou un match par l'instance canonique (par numéro d'équipe et identifiant
  de joueur). Sans cette étape, une rencontre reprise après rechargement modifiait une copie de
  l'équipe, qui restait « En match » indéfiniment.

## Protocole PC, serveur et téléphones

API : un seul point d'entrée `api.php?action=...`, réponses JSON `{ "ok": true|false, ... }`.

| Action | Appelant | Authentification | Rôle |
| --- | --- | --- | --- |
| `ping` | PC | aucune | test de vie |
| `lire` | PC, téléphones | aucune | lit une clé (l'état publié est sous la clé `etat`) |
| `ecrire` | PC | secret partagé | publie une clé |
| `login`, `moi`, `logout` | téléphones | identifiant + mot de passe, puis jeton | sessions capitaines (12 h) |
| `saisir` | téléphones | jeton | enregistre le score d'un match |
| `mes_saisies` | téléphones | jeton | relit ses propres saisies |
| `pull_saisies` | PC | secret partagé | relève toutes les saisies en attente |
| `purger_saisies` | PC | secret partagé | supprime les saisies d'un match traité |

Le secret partagé circule dans l'en-tête `X-Tournoi-Cle`, le jeton de session dans
`X-Capitaine-Jeton`.

### Cycle de l'agent de synchronisation (côté PC)

1. relever les saisies (`pull_saisies`) ;
2. les comparer deux à deux (`ComparateurSaisies`) : une seule saisie donne « en attente »,
   deux saisies identiques un accord, deux saisies différentes un litige ;
3. enregistrer les accords dans le tournoi, puis purger les saisies correspondantes ;
4. republier l'état enrichi du statut de chaque match (`ecrire`, clé `etat`).

Les litiges restent en attente : le juge-arbitre tranche lui-même le score ou fait resaisir
les deux capitaines depuis l'onglet « Capitaines ».

### Contrôles côté serveur

Les scores sont toujours exprimés du point de vue de l'équipe A de la rencontre. Pour une
saisie, le serveur retrouve l'équipe du capitaine dans l'état publié et vérifie qu'elle joue
bien la rencontre visée et que l'index de match existe. Le côté (A ou B) est déduit de cet état,
jamais fourni par le téléphone : un capitaine ne peut donc pas saisir à la place de
l'adversaire, ce qui aurait permis de fabriquer un faux « accord ».
