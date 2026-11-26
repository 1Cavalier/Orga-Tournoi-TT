package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Une equipe (jusqu'a {@value #TAILLE_MAX} joueurs).
 * <p>
 * Contrairement a l'ancienne version, cette classe ne stocke plus les statistiques
 * en champs mutables tenus a jour a la main (source de desynchronisation). Les stats
 * sont calculees a la demande par {@code service.CalculStatistiques} a partir des
 * rencontres reellement jouees : une seule source de verite.
 * <p>
 * Cette classe n'a aucun effet de bord : construire ou modifier une equipe ne touche
 * jamais aux tables ni aux autres equipes. Toute la coordination passe par le service.
 */
public class Equipe {

    public static final int TAILLE_MAX = 4;

    private final int numero;
    private final List<Joueur> joueurs;
    private EtatEquipe etat;
    /** Instant (millis epoch) ou l'equipe est (re)devenue disponible, pour prioriser l'attente. */
    private long enAttenteDepuisMillis;

    public Equipe(int numero) {
        this.numero = numero;
        this.joueurs = new ArrayList<>();
        this.etat = EtatEquipe.EN_ATTENTE;
        this.enAttenteDepuisMillis = 0;
    }

    public int getNumero() {
        return numero;
    }

    /** Liste modifiable des joueurs (l'ordre correspond a l'ordre de jeu). */
    public List<Joueur> getJoueurs() {
        return joueurs;
    }

    public EtatEquipe getEtat() {
        return etat;
    }

    public void setEtat(EtatEquipe etat) {
        this.etat = etat;
    }

    public long getEnAttenteDepuisMillis() {
        return enAttenteDepuisMillis;
    }

    /** Marque l'instant ou l'equipe entre en attente (pour la priorite du moteur suisse). */
    public void setEnAttenteDepuisMillis(long instant) {
        this.enAttenteDepuisMillis = instant;
    }

    public boolean estPleine() {
        return joueurs.size() >= TAILLE_MAX;
    }

    public boolean estVide() {
        return joueurs.isEmpty();
    }

    /**
     * Ajoute un joueur si la place le permet.
     * @return true si l'ajout a reussi, false si l'equipe etait deja pleine.
     */
    public boolean ajouterJoueur(Joueur joueur) {
        Objects.requireNonNull(joueur, "joueur");
        if (estPleine()) {
            return false;
        }
        return joueurs.add(joueur);
    }

    public boolean retirerJoueur(Joueur joueur) {
        return joueurs.remove(joueur);
    }

    /** Echange la position de deux joueurs (ordre de jeu). */
    public void echangerJoueurs(int i, int j) {
        if (i < 0 || j < 0 || i >= joueurs.size() || j >= joueurs.size()) {
            throw new IndexOutOfBoundsException("Indices d'echange hors bornes : " + i + ", " + j);
        }
        Joueur tmp = joueurs.get(i);
        joueurs.set(i, joueurs.get(j));
        joueurs.set(j, tmp);
    }

    /** Renvoie le n-ieme joueur ou null si le poste n'est pas pourvu. */
    public Joueur joueurAu(int index) {
        return index >= 0 && index < joueurs.size() ? joueurs.get(index) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Equipe autre)) return false;
        return numero == autre.numero;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(numero);
    }

    @Override
    public String toString() {
        return "Equipe " + numero;
    }
}
