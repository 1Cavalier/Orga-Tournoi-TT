package org.club.tournoi.serveur;

/**
 * Diagnostic autonome de la liaison PC &rarr; serveur.
 * <p>
 * Ce n'est pas le logiciel de tournoi : c'est un petit programme en ligne de commande qui
 * vérifie, après l'installation du serveur, que le PC arrive bien à lui parler. On le lance
 * à la main une fois l'API déposée, avant d'utiliser l'onglet "Capitaines".
 * <p>
 * Il enchaîne trois vérifications :
 *   1. ping        — le serveur répond-il ?
 *   2. écrire      — le PC peut-il écrire (jeton accepté) ?
 *   3. relire      — retrouve-t-on ce qu'on vient d'écrire ?
 *
 * <h2>Comment le lancer (sous Windows, une fois compilé)</h2>
 * <pre>
 *   java -cp build\classes org.club.tournoi.serveur.DiagnosticConnexion ^
 *        https://votre-site.fr/tournoi  LE-MEME-SECRET-QUE-DANS-api.php
 * </pre>
 * (Le {@code ^} est la continuation de ligne sous Windows ; on peut aussi
 * tout mettre sur une seule ligne.)
 */
public class DiagnosticConnexion {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage : DiagnosticConnexion <baseUrl> <secret>");
            System.out.println("Ex.  : DiagnosticConnexion https://votre-site.fr/tournoi MON-SECRET");
            return;
        }
        String baseUrl = args[0];
        String secret = args[1];

        ClientServeur client = new ClientServeur(baseUrl, secret);
        System.out.println("=== Diagnostic de la connexion vers " + baseUrl + " ===\n");

        // 1) ping -----------------------------------------------------
        try {
            ClientServeur.Reponse r = client.ping();
            afficher("1. PING", r);
            if (!r.ok()) {
                System.out.println("   -> Le serveur ne répond pas correctement. On s'arrête.");
                return;
            }
        } catch (Exception e) {
            System.out.println("1. PING a échoué : " + e.getMessage());
            System.out.println("   -> Vérifie l'URL, ta connexion, et que api.php est bien déposé.");
            return;
        }

        // 2) écrire ---------------------------------------------------
        String valeurTest = "{\"message\":\"bonjour depuis le PC\",\"nombre\":42}";
        try {
            ClientServeur.Reponse r = client.ecrire("ping", valeurTest);
            afficher("2. ÉCRIRE", r);
            if (r.code() == 403) {
                System.out.println("   -> Jeton refusé : le secret Java ne correspond pas à celui de api.php.");
                return;
            }
            if (!r.ok()) {
                System.out.println("   -> L'écriture a échoué. Voir le message ci-dessus.");
                return;
            }
        } catch (Exception e) {
            System.out.println("2. ÉCRIRE a échoué : " + e.getMessage());
            return;
        }

        // 3) relire ---------------------------------------------------
        try {
            ClientServeur.Reponse r = client.lire("ping");
            afficher("3. RELIRE", r);
            if (r.ok() && r.corps().contains("bonjour depuis le PC")) {
                System.out.println("\nSUCCÈS : le PC écrit sur le serveur et relit. La connexion fonctionne.");
            } else {
                System.out.println("\nATTENTION : la relecture ne contient pas ce qu'on a écrit. À vérifier.");
            }
        } catch (Exception e) {
            System.out.println("3. RELIRE a échoué : " + e.getMessage());
        }
    }

    private static void afficher(String titre, ClientServeur.Reponse r) {
        System.out.println(titre + "  (HTTP " + r.code() + ")");
        System.out.println("   " + r.corps());
        System.out.println();
    }
}
