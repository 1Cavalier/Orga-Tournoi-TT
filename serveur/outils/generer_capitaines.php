<?php
// ============================================================
//  generer_capitaines.php — remplit les mots de passe des comptes capitaines
//  À déposer dans  <votre-site>/tournoi/outils/generer_capitaines.php
//  puis à ouvrir UNE fois dans le navigateur.
//
//  Rôle : pour chaque compte "capitaineNN", calcule le hash bcrypt d'un
//  mot de passe et l'enregistre. Les mots de passe ne sont jamais stockés
//  en clair (seulement leur hash).
//
//  SÉCURITÉ :
//   - Le secret vient de config.php (constante TOURNOI_SECRET), jamais en dur.
//   - Choisissez votre propre préfixe de mot de passe ci-dessous.
//   - SUPPRIMEZ ce fichier du serveur une fois les comptes créés.
//
//  Utilisation :
//    https://<votre-site>/tournoi/outils/generer_capitaines.php?cle=VOTRE_SECRET
// ============================================================

require_once __DIR__ . '/../../config.php';   // outils/ -> tournoi/ -> racine

// --- Réglages ---
const NB_CAPITAINES     = 30;        // nombre de comptes à générer
const PREFIXE_MDP       = 'CHANGEZ'; // préfixe des mots de passe (ex. "CHANGEZ01".."CHANGEZ30")

header('Content-Type: text/plain; charset=utf-8');

if (!defined('TOURNOI_SECRET')) {
    http_response_code(500);
    echo "TOURNOI_SECRET non défini dans config.php.\n";
    exit;
}

$cle = $_GET['cle'] ?? '';
if (!hash_equals(TOURNOI_SECRET, $cle)) {
    http_response_code(403);
    echo "Accès refusé : clé invalide.\n";
    exit;
}

$pdo  = getDB();
$stmt = $pdo->prepare('UPDATE tournoi_capitaines SET mdp_hash = ? WHERE identifiant = ?');

$fait = 0;
for ($i = 1; $i <= NB_CAPITAINES; $i++) {
    $num  = str_pad((string) $i, 2, '0', STR_PAD_LEFT); // 01..NB
    $id   = 'capitaine' . $num;
    $mdp  = PREFIXE_MDP . $num;                          // ex. CHANGEZ01..
    $hash = password_hash($mdp, PASSWORD_BCRYPT);
    $stmt->execute([$hash, $id]);
    $fait += $stmt->rowCount() > 0 ? 1 : 0;
    echo "$id  ->  mot de passe : $mdp\n";
}

echo "\n$fait comptes mis à jour.\n";
echo "\n*** PENSEZ À SUPPRIMER ce fichier du serveur maintenant. ***\n";
