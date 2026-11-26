package org.club.tournoi.service;

/**
 * Observateur des changements du tournoi.
 * <p>
 * Remplace le systeme fragile de l'ancienne version, ou chaque action devait rappeler
 * a la main une demi-douzaine de classes {@code Maj*} (un oubli = une vue desynchronisee).
 * Ici, une vue s'abonne une fois via {@link ServiceTournoi#ajouterListener} et reagit aux
 * evenements qui la concernent. Une methode par famille de changement, avec une
 * implementation par defaut vide pour ne surcharger que ce qui interesse la vue.
 */
public interface TournoiListener {

    /** La liste des joueurs a change (ajout / modification / suppression). */
    default void joueursModifies() { }

    /** La composition ou la liste des equipes a change. */
    default void equipesModifiees() { }

    /** L'etat des tables a change (activation, occupation). */
    default void tablesModifiees() { }

    /** Les rencontres ont change (creation, score, fin). */
    default void rencontresModifiees() { }

    /** Le classement doit etre recalcule (declenche apres toute fin de rencontre). */
    default void classementModifie() { }
}
