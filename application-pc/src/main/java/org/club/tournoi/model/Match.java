package org.club.tournoi.model;

import java.util.List;

/**
 * Abstraction commune a un match simple et a un match double.
 * <p>
 * C'est le remplacant du vieux {@code List<Object>} + {@code instanceof} : le reste
 * du code manipule des {@code Match} sans jamais avoir besoin de connaitre le type
 * concret. Un match porte un score et un cote gagnant ; il ne modifie jamais les
 * statistiques des equipes (ce role appartient au service).
 */
public interface Match {

    /** Cote d'une rencontre. */
    enum Cote { A, B }

    /** Libelle affichable du match (ex. "Simple 1" ou "Double"). */
    String getLibelle();

    /** Joueurs alignes du cote demande (1 pour un simple, 2 pour un double, {@code null} ignore). */
    List<Joueur> getJoueurs(Cote cote);

    int getScore(Cote cote);

    boolean estTermine();

    /**
     * Enregistre le score du match.
     * @throws IllegalStateException si le match est deja termine
     * @throws IllegalArgumentException si les scores sont invalides
     */
    void enregistrerScore(int scoreA, int scoreB);

    /** Efface le score et repasse le match en "non joue" (permet une correction). */
    void reinitialiser();

    /** Cote gagnant, ou {@code null} si le match n'est pas encore termine. */
    default Cote getGagnant() {
        if (!estTermine()) {
            return null;
        }
        return getScore(Cote.A) > getScore(Cote.B) ? Cote.A : Cote.B;
    }
}
