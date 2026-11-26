package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;

/** Match double : deux joueurs de chaque cote. */
public class MatchDouble extends MatchAbstrait {

    private Joueur joueurA1;
    private Joueur joueurA2;
    private Joueur joueurB1;
    private Joueur joueurB2;

    public MatchDouble(Joueur joueurA1, Joueur joueurA2, Joueur joueurB1, Joueur joueurB2) {
        this(joueurA1, joueurA2, joueurB1, joueurB2, true);
    }

    public MatchDouble(Joueur joueurA1, Joueur joueurA2, Joueur joueurB1, Joueur joueurB2, boolean avecEcart) {
        super(avecEcart);
        this.joueurA1 = joueurA1;
        this.joueurA2 = joueurA2;
        this.joueurB1 = joueurB1;
        this.joueurB2 = joueurB2;
    }

    @Override
    public String getLibelle() {
        return "Double";
    }

    @Override
    void relierJoueurs(java.util.function.UnaryOperator<Joueur> canonique) {
        joueurA1 = canonique.apply(joueurA1);
        joueurA2 = canonique.apply(joueurA2);
        joueurB1 = canonique.apply(joueurB1);
        joueurB2 = canonique.apply(joueurB2);
    }

    @Override
    public List<Joueur> getJoueurs(Cote cote) {
        List<Joueur> liste = new ArrayList<>(2);
        if (cote == Cote.A) {
            if (joueurA1 != null) liste.add(joueurA1);
            if (joueurA2 != null) liste.add(joueurA2);
        } else {
            if (joueurB1 != null) liste.add(joueurB1);
            if (joueurB2 != null) liste.add(joueurB2);
        }
        return liste;
    }
}
