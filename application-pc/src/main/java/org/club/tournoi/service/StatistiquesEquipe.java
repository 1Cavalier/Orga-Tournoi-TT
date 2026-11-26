package org.club.tournoi.service;

/**
 * Photographie immuable des statistiques d'une equipe, calculee a partir des rencontres
 * reellement jouees. Remplace la douzaine de champs mutables de l'ancienne {@code Equipe}
 * qu'il fallait incrementer a la main a chaque score (source de bugs de coherence).
 */
public record StatistiquesEquipe(
        int rencontresJouees,
        int rencontresGagnees,
        int rencontresPerdues,
        int matchsJoues,
        int matchsGagnes,
        int matchsPerdus,
        int pointsPour,
        int pointsContre) {

    public double ratioRencontres() {
        return rencontresJouees == 0 ? 0.0 : (double) rencontresGagnees / rencontresJouees;
    }

    public double ratioMatchs() {
        return matchsJoues == 0 ? 0.0 : (double) matchsGagnes / matchsJoues;
    }

    public double ratioPoints() {
        int total = pointsPour + pointsContre;
        return total == 0 ? 0.0 : (double) pointsPour / total;
    }

    public int differencePoints() {
        return pointsPour - pointsContre;
    }
}
