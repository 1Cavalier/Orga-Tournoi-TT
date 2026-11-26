package org.club.tournoi.serveur;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Client HTTP minimal vers l'API PHP du serveur ("boîte aux lettres" entre le PC et les
 * téléphones des capitaines).
 * <p>
 * Actions couvertes : {@code ping}, {@code lire} / {@code ecrire} une clé (publication de
 * l'état), {@code pull_saisies} et {@code purger_saisies} (relevé des scores saisis). Les
 * actions réservées au PC envoient le secret partagé dans l'en-tête {@code X-Tournoi-Cle}.
 * <p>
 * Cette classe n'appelle jamais {@code ServiceTournoi} et n'a aucun effet de bord sur le
 * tournoi : c'est un simple transporteur réseau. Le retour sur le thread Swing est géré par
 * {@link AgentSynchro}.
 */
public class ClientServeur {

    /** URL de base de l'API, SANS slash final. Ex. https://votre-site.fr/tournoi */
    private final String baseUrl;

    /** Le même secret que TOURNOI_SECRET dans api.php. */
    private final String secret;

    private final HttpClient http;

    public ClientServeur(String baseUrl, String secret) {
        // Retire un éventuel slash final pour éviter les doubles //
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.secret = secret;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Réponse brute d'une requête : code HTTP + corps texte. */
    public record Reponse(int code, String corps) {
        public boolean ok() { return code >= 200 && code < 300; }
    }

    /** Test de vie : GET ?action=ping. Ne touche pas la base côté serveur. */
    public Reponse ping() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api.php?action=ping"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return envoyer(req);
    }

    /** Lit la valeur d'une clé : GET ?action=lire&cle=... */
    public Reponse lire(String cle) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api.php?action=lire&cle=" + encoder(cle)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return envoyer(req);
    }

    /**
     * Écrit une valeur pour une clé : POST ?action=ecrire&cle=...
     * Le corps de la requête est la valeur (typiquement du JSON).
     * Nécessite le secret partagé (en-tête X-Tournoi-Cle).
     */
    public Reponse ecrire(String cle, String valeur) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api.php?action=ecrire&cle=" + encoder(cle)))
                .timeout(Duration.ofSeconds(10))
                .header("X-Tournoi-Cle", secret)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(valeur, StandardCharsets.UTF_8))
                .build();
        return envoyer(req);
    }

    private Reponse envoyer(HttpRequest req) throws Exception {
        HttpResponse<String> rep = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reponse(rep.statusCode(), rep.body());
    }

    /** Récupère toutes les saisies en attente (réservé au PC, jeton requis). */
    public Reponse pullSaisies() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api.php?action=pull_saisies"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Tournoi-Cle", secret)
                .GET()
                .build();
        return envoyer(req);
    }

    /** Supprime les saisies d'un match traité (réservé au PC, jeton requis). */
    public Reponse purgerSaisies(String idRencontre, int indexMatch) throws Exception {
        String corps = "{\"id_rencontre\":\"" + echapper(idRencontre)
                + "\",\"index_match\":" + indexMatch + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api.php?action=purger_saisies"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Tournoi-Cle", secret)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(corps, StandardCharsets.UTF_8))
                .build();
        return envoyer(req);
    }

    private static String echapper(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encoder(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
