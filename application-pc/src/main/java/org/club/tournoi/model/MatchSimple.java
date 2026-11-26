package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;

/** Match simple : un joueur de chaque cote. */
public class MatchSimple extends MatchAbstrait {

    private final String libelle;
    private Joueur joueurA;
    private Joueur joueurB;

    public MatchSimple(String libelle, Joueur joueurA, Joueur joueurB) {
        this(libelle, joueurA, joueurB, true);
    }

    public MatchSimple(String libelle, Joueur joueurA, Joueur joueurB, boolean avecEcart) {
        super(avecEcart);
        this.libelle = libelle;
        this.joueurA = joueurA;
        this.joueurB = joueurB;
    }

    @Override
    public String getLibelle() {
        return libelle;
    }

    @Override
    void relierJoueurs(java.util.function.UnaryOperator<Joueur> canonique) {
        joueurA = canonique.apply(joueurA);
        joueurB = canonique.apply(joueurB);
    }

    @Override
    public List<Joueur> getJoueurs(Cote cote) {
        List<Joueur> liste = new ArrayList<>(1);
        Joueur j = (cote == Cote.A) ? joueurA : joueurB;
        if (j != null) {
            liste.add(j);
        }
        return liste;
    }
}
