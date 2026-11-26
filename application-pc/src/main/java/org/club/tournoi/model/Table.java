package org.club.tournoi.model;

/**
 * Une table de jeu de la salle.
 * <p>
 * Ne detient volontairement plus de reference vers la {@link Rencontre} en cours :
 * c'est le service qui connait la correspondance table &rarr; rencontre. Cela evite
 * les references circulaires (qui compliquaient la serialisation) et garde la table
 * comme une simple donnee.
 */
public class Table {

    private final int numero;
    private StatutTable statut;
    private boolean occupee;

    public Table(int numero) {
        this.numero = numero;
        this.statut = StatutTable.ACTIVE;
        this.occupee = false;
    }

    public int getNumero() {
        return numero;
    }

    public StatutTable getStatut() {
        return statut == null ? StatutTable.ACTIVE : statut;
    }

    public void setStatut(StatutTable statut) {
        this.statut = statut == null ? StatutTable.ACTIVE : statut;
    }

    /** Compat : une table est "active" si son statut est ACTIVE. */
    public boolean isActive() {
        return getStatut().estJouable();
    }

    public boolean isOccupee() {
        return occupee;
    }

    public void setOccupee(boolean occupee) {
        this.occupee = occupee;
    }

    /** Une table est utilisable pour lancer une rencontre si elle est active (jouable) et libre. */
    public boolean estDisponible() {
        return getStatut().estJouable() && !occupee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Table autre)) return false;
        return numero == autre.numero;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(numero);
    }

    @Override
    public String toString() {
        return "Table " + numero;
    }
}
