-- ============================================================
--  schema.sql — Schéma complet de la partie serveur (MySQL / MariaDB)
--  À exécuter dans phpMyAdmin, sur VOTRE base MySQL.
--
--  Crée les tables nécessaires à l'application capitaines :
--    - tournoi_kv         : stockage clé/valeur (état publié par le PC)
--    - tournoi_capitaines : les comptes capitaines (mot de passe hashé)
--    - tournoi_sessions   : les sessions de connexion (jetons)
--    - tournoi_saisies    : les scores saisis, en attente de traitement
--
--  Après avoir exécuté ce script, remplissez les mots de passe des
--  comptes avec le script  outils/generer_capitaines.php  (à supprimer
--  ensuite du serveur).
-- ============================================================

CREATE TABLE IF NOT EXISTS tournoi_kv (
    cle           VARCHAR(64)  NOT NULL,
    valeur        LONGTEXT     NOT NULL,
    maj_le        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tournoi_capitaines (
    identifiant    VARCHAR(32)  NOT NULL,
    mdp_hash       VARCHAR(255) NOT NULL,
    equipe_numero  INT          NULL,
    PRIMARY KEY (identifiant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tournoi_capitaines (identifiant, mdp_hash, equipe_numero) VALUES
 ('capitaine01','',1), ('capitaine02','',2), ('capitaine03','',3),
 ('capitaine04','',4), ('capitaine05','',5), ('capitaine06','',6),
 ('capitaine07','',7), ('capitaine08','',8), ('capitaine09','',9),
 ('capitaine10','',10),('capitaine11','',11),('capitaine12','',12),
 ('capitaine13','',13),('capitaine14','',14),('capitaine15','',15),
 ('capitaine16','',16),('capitaine17','',17),('capitaine18','',18),
 ('capitaine19','',19),('capitaine20','',20),('capitaine21','',21),
 ('capitaine22','',22),('capitaine23','',23),('capitaine24','',24),
 ('capitaine25','',25),('capitaine26','',26),('capitaine27','',27),
 ('capitaine28','',28),('capitaine29','',29),('capitaine30','',30)
ON DUPLICATE KEY UPDATE equipe_numero = VALUES(equipe_numero);

CREATE TABLE IF NOT EXISTS tournoi_sessions (
    jeton          CHAR(64)     NOT NULL,
    identifiant    VARCHAR(32)  NOT NULL,
    cree_le        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_le      TIMESTAMP    NOT NULL,
    PRIMARY KEY (jeton),
    KEY idx_identifiant (identifiant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tournoi_saisies (
    id_rencontre   VARCHAR(64) NOT NULL,
    index_match    INT         NOT NULL,
    cote_capitaine CHAR(1)     NOT NULL,          -- 'A' ou 'B'
    score_a        INT         NOT NULL,
    score_b        INT         NOT NULL,
    saisi_le       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_rencontre, index_match, cote_capitaine)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
