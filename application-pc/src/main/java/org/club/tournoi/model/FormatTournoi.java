package org.club.tournoi.model;

/**
 * Format d'un tournoi. Pour l'instant un seul format est actif (équipes de
 * 3 ou 4 joueurs, rencontres de 5 matchs). L'énumération est prévue pour
 * accueillir d'autres formats plus tard (par exemple un tournoi de double)
 * sans avoir à retoucher toute l'architecture : il suffira d'ajouter une
 * valeur ici et d'adapter la génération des matchs.
 */
public enum FormatTournoi {

    /** Format actuel : équipes de 3 ou 4 joueurs, 5 matchs par rencontre. */
    EQUIPES_3_4("Equipes de 3-4 joueurs", true),

    /** Réservé pour plus tard : tournoi de double (pas encore disponible). */
    DOUBLE("Tournoi de double (bientot)", false);

    private final String libelle;
    private final boolean disponible;

    FormatTournoi(String libelle, boolean disponible) {
        this.libelle = libelle;
        this.disponible = disponible;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Un format non disponible est affiché mais non sélectionnable. */
    public boolean estDisponible() {
        return disponible;
    }

    @Override
    public String toString() {
        return libelle;
    }
}
