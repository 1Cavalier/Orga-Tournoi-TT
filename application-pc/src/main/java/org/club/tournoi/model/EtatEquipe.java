package org.club.tournoi.model;

/** Etat courant d'une equipe dans le tournoi. */
public enum EtatEquipe {
    EN_ATTENTE("En attente"),
    EN_MATCH("En match"),
    FORFAIT("Forfait");

    private final String libelle;

    EtatEquipe(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    @Override
    public String toString() {
        return libelle;
    }
}
