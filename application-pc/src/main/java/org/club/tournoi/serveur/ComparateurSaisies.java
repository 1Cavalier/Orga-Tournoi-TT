package org.club.tournoi.serveur;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compare les saisies des deux capitaines, match par match, et en déduit
 * un statut. Logique <b>pure</b> (aucun effet de bord, aucun réseau) : on
 * lui donne la liste des saisies récupérées du serveur, elle rend un verdict
 * par match. C'est le "juge de paix" du système.
 * <p>
 * Rappel de convention : les scores sont toujours stockés du point de vue
 * de l'équipe A de la rencontre (score_a, score_b), quel que soit le
 * capitaine qui a saisi. Comparer deux saisies revient donc à comparer
 * leurs couples (score_a, score_b).
 */
public final class ComparateurSaisies {

    /** Une saisie brute telle que reçue du serveur (via l'action pull_saisies). */
    public record Saisie(String idRencontre, int indexMatch, String cote, int scoreA, int scoreB) {}

    /** Les statuts possibles d'un match du point de vue "saisie capitaines". */
    public enum Statut {
        AUCUNE,        // personne n'a saisi
        ATTENTE_AUTRE, // un seul capitaine a saisi, on attend l'autre
        ACCORD,        // les deux ont saisi la même chose -> à enregistrer
        LITIGE         // les deux ont saisi, mais différemment -> au JA
    }

    /**
     * Le verdict pour un match : statut + (si accord) le score à enregistrer.
     * En cas de litige, on conserve aussi ce que chaque capitaine a saisi
     * (scoreA/scoreB du capitaine A, et du capitaine B), pour que le
     * juge-arbitre voie les deux versions et puisse trancher.
     */
    public record Verdict(
            Statut statut,
            Integer scoreA, Integer scoreB,           // score commun si ACCORD
            Integer aScoreA, Integer aScoreB,         // ce qu'a saisi le capitaine A
            Integer bScoreA, Integer bScoreB          // ce qu'a saisi le capitaine B
    ) {
        public static Verdict aucune()  { return new Verdict(Statut.AUCUNE, null, null, null, null, null, null); }
        public static Verdict attente() { return new Verdict(Statut.ATTENTE_AUTRE, null, null, null, null, null, null); }
        public static Verdict accord(int a, int b) { return new Verdict(Statut.ACCORD, a, b, null, null, null, null); }
        public static Verdict litige(int aA, int aB, int bA, int bB) {
            return new Verdict(Statut.LITIGE, null, null, aA, aB, bA, bB);
        }
    }

    /** Clé identifiant un match précis d'une rencontre précise. */
    public record CleMatch(String idRencontre, int indexMatch) {}

    private ComparateurSaisies() {}

    /**
     * Analyse toutes les saisies et rend, pour chaque match ayant au moins
     * une saisie, son verdict.
     */
    public static Map<CleMatch, Verdict> analyser(List<Saisie> saisies) {
        // Regroupe par match : on retient la saisie côté A et côté B.
        Map<CleMatch, Saisie> parA = new HashMap<>();
        Map<CleMatch, Saisie> parB = new HashMap<>();
        for (Saisie s : saisies) {
            CleMatch cle = new CleMatch(s.idRencontre(), s.indexMatch());
            if ("A".equals(s.cote())) parA.put(cle, s);
            else if ("B".equals(s.cote())) parB.put(cle, s);
        }

        Map<CleMatch, Verdict> verdicts = new HashMap<>();
        // Ensemble de toutes les clés vues (côté A ou B).
        Map<CleMatch, Boolean> toutes = new HashMap<>();
        parA.keySet().forEach(k -> toutes.put(k, true));
        parB.keySet().forEach(k -> toutes.put(k, true));

        for (CleMatch cle : toutes.keySet()) {
            Saisie a = parA.get(cle);
            Saisie b = parB.get(cle);
            if (a != null && b != null) {
                if (a.scoreA() == b.scoreA() && a.scoreB() == b.scoreB()) {
                    verdicts.put(cle, Verdict.accord(a.scoreA(), a.scoreB()));
                } else {
                    verdicts.put(cle, Verdict.litige(a.scoreA(), a.scoreB(), b.scoreA(), b.scoreB()));
                }
            } else {
                verdicts.put(cle, Verdict.attente());
            }
        }
        return verdicts;
    }
}
