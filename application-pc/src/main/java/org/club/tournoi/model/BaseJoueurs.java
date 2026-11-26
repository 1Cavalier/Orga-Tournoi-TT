package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * La base permanente des joueurs du club, indépendante des tournois.
 * <p>
 * Elle vit dans son propre fichier (ex. {@code joueurs_club.json}) et ne
 * contient QUE des joueurs — pas d'équipes, pas de rencontres. Un tournoi,
 * lui, garde sa propre <b>copie</b> des joueurs présents (via l'{@code id}
 * stable). Quand on modifie un joueur (typiquement sa catégorie), on met à
 * jour la base permanente ET la copie dans le tournoi ouvert.
 */
public class BaseJoueurs {

    private final List<Joueur> joueurs = new ArrayList<>();

    public List<Joueur> getJoueurs() {
        return joueurs;
    }

    /** Cherche un joueur par son identifiant stable. */
    public Joueur parId(String id) {
        for (Joueur j : joueurs) {
            if (j.getId().equals(id)) return j;
        }
        return null;
    }

    /**
     * Cherche un joueur existant de même nom ET prénom (comparaison
     * insensible à la casse et aux espaces superflus). Sert à détecter les
     * doublons au moment d'ajouter un joueur. Renvoie le premier trouvé ou
     * {@code null}.
     */
    public Joueur trouverHomonyme(String nom, String prenom) {
        String n = normaliser(nom), p = normaliser(prenom);
        for (Joueur j : joueurs) {
            if (normaliser(j.getNom()).equals(n) && normaliser(j.getPrenom()).equals(p)) {
                return j;
            }
        }
        return null;
    }

    /** Ajoute un joueur déjà construit (conserve son id). */
    public void ajouter(Joueur joueur) {
        joueurs.add(joueur);
    }

    /** Retire un joueur de la base permanente. */
    public boolean supprimer(Joueur joueur) {
        return joueurs.removeIf(j -> j.getId().equals(joueur.getId()));
    }

    private static String normaliser(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
