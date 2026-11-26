package org.club.tournoi.model;

/**
 * Statut d'une table. Seule {@link #ACTIVE} rend la table jouable ; les autres statuts
 * l'indisponibilisent tout en indiquant la raison (retiree de la salle, reservee au fil rouge,
 * occupee par un robot lance-balles, etc.).
 */
public enum StatutTable {
    ACTIVE("Active"),
    RETIREE("Retiree"),
    FIL_ROUGE("Fil rouge"),
    ROBOT("Robot");

    private final String libelle;

    StatutTable(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Seule une table ACTIVE peut accueillir une rencontre. */
    public boolean estJouable() {
        return this == ACTIVE;
    }

    @Override
    public String toString() {
        return libelle;
    }
}
