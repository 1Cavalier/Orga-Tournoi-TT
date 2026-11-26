package org.club.tournoi.model;

/**
 * Symbole de groupage attribue a un joueur.
 * <p>
 * Deux joueurs portant le meme symbole (autre que {@link #AUCUN}) doivent imperativement se
 * retrouver dans la meme equipe. C'est une contrainte que les modes de composition automatique
 * respectent en priorite. Une quinzaine de symboles distincts sont disponibles, ce qui laisse
 * de la marge pour constituer plusieurs groupes sans confusion.
 * <p>
 * Chaque symbole a un caractere affichable (pour le montrer a cote du nom) et un libelle.
 */
public enum Symbole {
    AUCUN("", "Aucun"),
    LOSANGE("\u25C6", "Losange"),
    ETOILE("\u2605", "Etoile"),
    COEUR("\u2665", "Coeur"),
    CARRE("\u25A0", "Carre"),
    ROND("\u25CF", "Rond"),
    TRIANGLE("\u25B2", "Triangle"),
    CROIX("\u2716", "Croix"),
    ECLAIR("\u26A1", "Eclair"),
    FLEUR("\u2740", "Fleur"),
    SOLEIL("\u2600", "Soleil"),
    LUNE("\u263D", "Lune"),
    NOTE("\u266B", "Note"),
    ANCRE("\u2693", "Ancre"),
    CLE("\u26BF", "Cle"),
    DRAPEAU("\u2691", "Drapeau");

    private final String caractere;
    private final String libelle;

    Symbole(String caractere, String libelle) {
        this.caractere = caractere;
        this.libelle = libelle;
    }

    /** Caractere a afficher (chaine vide pour {@link #AUCUN}). */
    public String getCaractere() {
        return caractere;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean estDefini() {
        return this != AUCUN;
    }

    @Override
    public String toString() {
        return this == AUCUN ? libelle : caractere + "  " + libelle;
    }
}
