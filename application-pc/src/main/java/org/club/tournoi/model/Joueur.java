package org.club.tournoi.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Un joueur du club. Possede un identifiant stable ({@code id}) genere une seule
 * fois : c'est lui qui sert de reference dans la persistance et les comparaisons,
 * pas l'egalite structurelle nom/prenom (deux joueurs homonymes restent distincts).
 */
public class Joueur {

    private final String id;
    private String nom;
    private String prenom;
    private Categorie categorie;
    private Symbole symbole = Symbole.AUCUN;

    public Joueur(String nom, String prenom, Categorie categorie) {
        this(UUID.randomUUID().toString(), nom, prenom, categorie);
    }

    /** Constructeur complet, utilise notamment lors du chargement JSON. */
    public Joueur(String id, String nom, String prenom, Categorie categorie) {
        this.id = Objects.requireNonNull(id, "id");
        this.nom = nom;
        this.prenom = prenom;
        this.categorie = categorie;
    }

    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public Categorie getCategorie() {
        return categorie;
    }

    public void setCategorie(Categorie categorie) {
        this.categorie = categorie;
    }

    /** Symbole de groupage (jamais null : {@link Symbole#AUCUN} par defaut). */
    public Symbole getSymbole() {
        return symbole == null ? Symbole.AUCUN : symbole;
    }

    public void setSymbole(Symbole symbole) {
        this.symbole = symbole == null ? Symbole.AUCUN : symbole;
    }

    public String getNomComplet() {
        return nom + " " + prenom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Joueur autre)) return false;
        return id.equals(autre.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getNomComplet() + " (" + categorie.getLibelle() + ")";
    }
}
