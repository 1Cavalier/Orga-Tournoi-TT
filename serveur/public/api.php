<?php
// ============================================================
//  api.php — Boîte aux lettres PC <-> serveur + API capitaines
//  Déposé dans :  votre-site.fr/tournoi/api.php
//  (config.php se trouve un niveau au-dessus, hors du dossier public)
//
//  Actions :
//    ping, lire, ecrire             transport de l'état publié par le PC
//    login, moi, logout             comptes et sessions capitaines
//    saisir, mes_saisies            saisie des scores par les capitaines
//    pull_saisies, purger_saisies   relevé des saisies par le PC
//  Les actions "ecrire", "pull_saisies" et "purger_saisies" exigent le
//  secret partagé (en-tête X-Tournoi-Cle).
//
//  Ce fichier reste "bête" côté règles du tennis de table : il gère
//  seulement le transport, les comptes et les sessions. Toute la
//  logique de tournoi reste dans le logiciel Java.
// ============================================================

require_once __DIR__ . '/../config.php';   // identifiants MySQL + getDB() + TOURNOI_SECRET

if (!defined('TOURNOI_SECRET')) {
    http_response_code(500);
    echo json_encode(['ok' => false, 'erreur' => 'TOURNOI_SECRET non defini dans config.php']);
    exit;
}

// Durée de validité d'une session capitaine (heures).
const SESSION_HEURES = 12;

header('Content-Type: application/json; charset=utf-8');

// ---- Helpers de réponse --------------------------------------
function repondre(int $code, array $donnees): never {
    http_response_code($code);
    echo json_encode($donnees, JSON_UNESCAPED_UNICODE);
    exit;
}
function corpsBrut(): string {
    $c = file_get_contents('php://input');
    return $c === false ? '' : $c;
}
function corpsJson(): array {
    $c = corpsBrut();
    if ($c === '') return [];
    $d = json_decode($c, true);
    return is_array($d) ? $d : [];
}
function verifierSecret(): void {
    $recu = $_SERVER['HTTP_X_TOURNOI_CLE'] ?? '';
    if (!hash_equals(TOURNOI_SECRET, $recu)) {
        repondre(403, ['ok' => false, 'erreur' => 'Jeton invalide.']);
    }
}

// ---- Sessions capitaines -------------------------------------
// Renvoie l'identifiant du capitaine si le jeton de session est valide,
// sinon coupe avec une 401. Le jeton arrive dans l'en-tête
// X-Capitaine-Jeton (posé par la page web après login).
function capitaineConnecte(PDO $pdo): string {
    $jeton = $_SERVER['HTTP_X_CAPITAINE_JETON'] ?? ($_GET['jeton'] ?? '');
    if ($jeton === '') {
        repondre(401, ['ok' => false, 'erreur' => 'Non connecté.']);
    }
    $stmt = $pdo->prepare(
        'SELECT identifiant FROM tournoi_sessions
         WHERE jeton = ? AND expire_le > NOW()'
    );
    $stmt->execute([$jeton]);
    $id = $stmt->fetchColumn();
    if ($id === false) {
        repondre(401, ['ok' => false, 'erreur' => 'Session expirée, reconnectez-vous.']);
    }
    return $id;
}

// ---- Contrôle des saisies ------------------------------------
// Score maximal accepté pour une manche (prolongations comprises). Garde-fou
// contre les valeurs absurdes ; la validation fine est faite par le PC.
const SCORE_MAX = 99;

// Renvoie l'entrée de l'équipe du capitaine dans l'état publié par le PC
// (tableau "equipes" de la clé "etat"), ou null si elle est introuvable.
function equipeDuCapitaine(PDO $pdo, string $identifiant): ?array {
    $stmt = $pdo->prepare('SELECT equipe_numero FROM tournoi_capitaines WHERE identifiant = ?');
    $stmt->execute([$identifiant]);
    $numero = $stmt->fetchColumn();
    if ($numero === false || $numero === null) {
        return null;
    }
    $stmt = $pdo->prepare('SELECT valeur FROM tournoi_kv WHERE cle = ?');
    $stmt->execute(['etat']);
    $json = $stmt->fetchColumn();
    $etat = is_string($json) ? json_decode($json, true) : null;
    return trouverEquipe(is_array($etat) ? $etat : null, (int) $numero);
}

// Cherche l'équipe numéro $numero dans un état décodé (fonction pure).
function trouverEquipe(?array $etat, int $numero): ?array {
    foreach (($etat['equipes'] ?? []) as $eq) {
        if (is_array($eq) && (int) ($eq['numero'] ?? -1) === $numero) {
            return $eq;
        }
    }
    return null;
}

// Vérifie qu'une saisie est permise pour cette équipe : elle doit être en
// match dans la rencontre visée, et l'index de match doit exister. Renvoie
// le côté ('A' ou 'B') de l'équipe, ou null si la saisie est refusée.
// Le côté est toujours déduit de l'état publié, jamais du téléphone :
// un capitaine ne peut donc saisir ni pour l'adversaire, ni pour une
// autre rencontre.
function coteAutorise(?array $equipe, string $idRencontre, ?int $indexMatch = null): ?string {
    if ($equipe === null || ($equipe['etat'] ?? '') !== 'EN_MATCH') {
        return null;
    }
    if (($equipe['idRencontre'] ?? '') !== $idRencontre) {
        return null;
    }
    $cote = $equipe['cote'] ?? '';
    if ($cote !== 'A' && $cote !== 'B') {
        return null;
    }
    if ($indexMatch !== null) {
        $nbMatchs = count($equipe['matchs'] ?? []);
        if ($indexMatch < 0 || $indexMatch >= $nbMatchs) {
            return null;
        }
    }
    return $cote;
}

// Permet d'inclure ce fichier (tests) sans exécuter le routage.
if (defined('TOURNOI_SANS_ROUTAGE')) {
    return;
}

// ---- Routage -------------------------------------------------
$action = $_GET['action'] ?? '';

try {
    $pdo = getDB();

    switch ($action) {

        // ---------- Transport de l'état --------------------------
        case 'ping':
            repondre(200, ['ok' => true, 'serveur' => 'tournoi', 'heure' => date('c')]);

        case 'lire':
            $cle = $_GET['cle'] ?? '';
            if ($cle === '') repondre(400, ['ok' => false, 'erreur' => 'Paramètre cle manquant.']);
            $stmt = $pdo->prepare('SELECT valeur, maj_le FROM tournoi_kv WHERE cle = ?');
            $stmt->execute([$cle]);
            $ligne = $stmt->fetch();
            repondre(200, [
                'ok' => true, 'cle' => $cle,
                'valeur' => $ligne ? $ligne['valeur'] : null,
                'maj_le' => $ligne ? $ligne['maj_le'] : null,
            ]);

        case 'ecrire':
            verifierSecret();
            $cle = $_GET['cle'] ?? '';
            if ($cle === '') repondre(400, ['ok' => false, 'erreur' => 'Paramètre cle manquant.']);
            $valeur = corpsBrut();
            $stmt = $pdo->prepare(
                'INSERT INTO tournoi_kv (cle, valeur) VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)'
            );
            $stmt->execute([$cle, $valeur]);
            repondre(200, ['ok' => true, 'cle' => $cle, 'octets' => strlen($valeur)]);

        // ---------- Comptes capitaines -----------------------------

        // Connexion. Corps JSON attendu : {identifiant, mdp}.
        // Renvoie un jeton de session à conserver côté téléphone.
        case 'login':
            $in  = corpsJson();
            $id  = trim((string) ($in['identifiant'] ?? ''));
            $mdp = (string) ($in['mdp'] ?? '');
            if ($id === '' || $mdp === '') {
                repondre(400, ['ok' => false, 'erreur' => 'Identifiant et mot de passe requis.']);
            }
            $stmt = $pdo->prepare('SELECT mdp_hash, equipe_numero FROM tournoi_capitaines WHERE identifiant = ?');
            $stmt->execute([$id]);
            $ligne = $stmt->fetch();
            // Message volontairement identique si l'identifiant n'existe
            // pas OU si le mot de passe est faux (on n'aide pas à deviner).
            if (!$ligne || $ligne['mdp_hash'] === '' || !password_verify($mdp, $ligne['mdp_hash'])) {
                repondre(401, ['ok' => false, 'erreur' => 'Identifiant ou mot de passe incorrect.']);
            }
            // Ménage : supprime les sessions expirées (aucune tâche planifiée nécessaire).
            $pdo->exec('DELETE FROM tournoi_sessions WHERE expire_le <= NOW()');
            // Crée un jeton de session aléatoire.
            $jeton = bin2hex(random_bytes(32)); // 64 caractères
            $ins = $pdo->prepare(
                'INSERT INTO tournoi_sessions (jeton, identifiant, expire_le)
                 VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))'
            );
            $ins->execute([$jeton, $id, SESSION_HEURES]);
            repondre(200, [
                'ok'            => true,
                'jeton'         => $jeton,
                'identifiant'   => $id,
                'equipe_numero' => $ligne['equipe_numero'] !== null ? (int) $ligne['equipe_numero'] : null,
            ]);

        // Qui suis-je ? Vérifie la session et renvoie l'identité.
        // Sert à la page web pour savoir si le jeton est encore valable.
        case 'moi':
            $idConnecte = capitaineConnecte($pdo);
            $stmt = $pdo->prepare('SELECT equipe_numero FROM tournoi_capitaines WHERE identifiant = ?');
            $stmt->execute([$idConnecte]);
            $eq = $stmt->fetchColumn();
            repondre(200, [
                'ok'            => true,
                'identifiant'   => $idConnecte,
                'equipe_numero' => $eq !== false && $eq !== null ? (int) $eq : null,
            ]);

        // Déconnexion : supprime la session courante.
        case 'logout':
            $jeton = $_SERVER['HTTP_X_CAPITAINE_JETON'] ?? ($_GET['jeton'] ?? '');
            if ($jeton !== '') {
                $pdo->prepare('DELETE FROM tournoi_sessions WHERE jeton = ?')->execute([$jeton]);
            }
            repondre(200, ['ok' => true]);

        // ---------- Saisies des capitaines ------------------------

        // Un capitaine envoie son score pour un match.
        // Corps JSON : {id_rencontre, index_match, score_a, score_b}
        // (déjà convertis "point de vue équipe A" par la page web).
        // La session dit qui est le capitaine ; son côté est déduit de
        // l'état publié par le PC (un éventuel champ "cote" envoyé par
        // le téléphone est ignoré).
        case 'saisir':
            $idConnecte = capitaineConnecte($pdo);
            $in = corpsJson();
            $idR   = trim((string)($in['id_rencontre'] ?? ''));
            $idxM  = $in['index_match']  ?? null;
            $sa    = $in['score_a']      ?? null;
            $sb    = $in['score_b']      ?? null;
            if ($idR === '' || !is_numeric($idxM) || !is_numeric($sa) || !is_numeric($sb)) {
                repondre(400, ['ok' => false, 'erreur' => 'Données de saisie incomplètes.']);
            }
            if ((int) $sa < 0 || (int) $sb < 0 || (int) $sa > SCORE_MAX || (int) $sb > SCORE_MAX) {
                repondre(400, ['ok' => false, 'erreur' => 'Score hors limites.']);
            }
            $cote = coteAutorise(equipeDuCapitaine($pdo, $idConnecte), $idR, (int) $idxM);
            if ($cote === null) {
                repondre(403, ['ok' => false, 'erreur' => 'Cette saisie ne correspond pas à votre rencontre en cours.']);
            }
            $stmt = $pdo->prepare(
                'INSERT INTO tournoi_saisies (id_rencontre, index_match, cote_capitaine, score_a, score_b)
                 VALUES (?, ?, ?, ?, ?)
                 ON DUPLICATE KEY UPDATE score_a = VALUES(score_a), score_b = VALUES(score_b)'
            );
            $stmt->execute([$idR, (int)$idxM, $cote, (int)$sa, (int)$sb]);
            repondre(200, ['ok' => true]);

        // Le téléphone relit SES saisies pour une rencontre (pour réafficher
        // la roue au rechargement de page). Renvoie la liste des saisies du
        // côté demandé.
        case 'mes_saisies':
            $idConnecte = capitaineConnecte($pdo);
            $idR  = trim((string)($_GET['id_rencontre'] ?? ''));
            if ($idR === '') {
                repondre(400, ['ok' => false, 'erreur' => 'Paramètres manquants.']);
            }
            // Comme pour "saisir", le côté vient de l'état publié : un
            // capitaine ne peut relire que ses propres saisies.
            $cote = coteAutorise(equipeDuCapitaine($pdo, $idConnecte), $idR);
            if ($cote === null) {
                repondre(200, ['ok' => true, 'saisies' => []]);
            }
            $stmt = $pdo->prepare(
                'SELECT index_match, score_a, score_b FROM tournoi_saisies
                 WHERE id_rencontre = ? AND cote_capitaine = ?'
            );
            $stmt->execute([$idR, $cote]);
            repondre(200, ['ok' => true, 'saisies' => $stmt->fetchAll()]);

        // RÉSERVÉ AU PC : récupère toutes les saisies en attente de traitement.
        case 'pull_saisies':
            verifierSecret();
            $stmt = $pdo->query(
                'SELECT id_rencontre, index_match, cote_capitaine, score_a, score_b
                 FROM tournoi_saisies ORDER BY id_rencontre, index_match'
            );
            repondre(200, ['ok' => true, 'saisies' => $stmt->fetchAll()]);

        // RÉSERVÉ AU PC : supprime les saisies d'un match traité (accord
        // enregistré, ou litige résolu par le JA).
        // Corps JSON : {id_rencontre, index_match}
        case 'purger_saisies':
            verifierSecret();
            $in = corpsJson();
            $idR  = trim((string)($in['id_rencontre'] ?? ''));
            $idxM = $in['index_match'] ?? null;
            if ($idR === '' || !is_numeric($idxM)) {
                repondre(400, ['ok' => false, 'erreur' => 'Paramètres manquants.']);
            }
            $stmt = $pdo->prepare(
                'DELETE FROM tournoi_saisies WHERE id_rencontre = ? AND index_match = ?'
            );
            $stmt->execute([$idR, (int)$idxM]);
            repondre(200, ['ok' => true, 'supprimees' => $stmt->rowCount()]);

        default:
            repondre(404, ['ok' => false, 'erreur' => 'Action inconnue : ' . $action]);
    }

} catch (Throwable $e) {
    if (defined('DEV_MODE') && DEV_MODE) {
        repondre(500, ['ok' => false, 'erreur' => $e->getMessage()]);
    }
    repondre(500, ['ok' => false, 'erreur' => 'Erreur serveur.']);
}
