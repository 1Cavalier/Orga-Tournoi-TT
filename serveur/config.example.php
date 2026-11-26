<?php
/**
 * MODÈLE de configuration — À COPIER en "config.php" et à remplir.
 *
 * IMPORTANT :
 *   - Copiez ce fichier sous le nom  config.php.
 *   - Sur le serveur, config.php se place UN NIVEAU AU-DESSUS du dossier
 *     qui contient api.php (api.php fait require __DIR__ . '/../config.php'),
 *     ce qui le garde hors de portée d'un accès direct par URL si ce dossier
 *     parent n'est pas public.
 *   - Renseignez vos vraies valeurs dans config.php.
 *   - Ne committez JAMAIS config.php (il est ignoré par .gitignore).
 *
 * Ce fichier-ci ne contient que des valeurs d'exemple : il peut être
 * public sans risque.
 */

// --- Base de données MySQL ---
define('DB_HOST', 'votre_hote_mysql');      // ex. monsite.mysql.db
define('DB_NAME', 'votre_base');
define('DB_USER', 'votre_utilisateur');
define('DB_PASS', 'votre_mot_de_passe');    // ne jamais publier la vraie valeur

// --- Secret partagé entre le PC (application Java) et le serveur ---
// Choisissez une longue chaîne aléatoire (par ex. 32+ caractères).
// Elle doit être identique à celle configurée dans l'onglet "Capitaines"
// de l'application Java.
define('TOURNOI_SECRET', 'CHANGEZ_MOI_par_une_longue_chaine_aleatoire');

// --- Connexion PDO réutilisable (singleton) ---
function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4';
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
    }
    return $pdo;
}
