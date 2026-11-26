package org.club.tournoi.service;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Match;
import org.club.tournoi.model.Rencontre;

import java.util.List;

/**
 * Calcule les statistiques d'une equipe a partir de la liste des rencontres terminees.
 * <p>
 * Toute la comptabilite (victoires, points...) est deduite ici, en un seul endroit, au
 * lieu d'etre eparpillee dans les {@code enregistrerScores} de chaque match comme avant.
 * Recalculer plutot qu'incrementer supprime toute possibilite de desynchronisation.
 */
public final class CalculStatistiques {

    private CalculStatistiques() { }

    public static StatistiquesEquipe pour(Equipe equipe, List<Rencontre> rencontres) {
        int rencJouees = 0, rencGagnees = 0, rencPerdues = 0;
        int matchsJoues = 0, matchsGagnes = 0, matchsPerdus = 0;
        int pointsPour = 0, pointsContre = 0;

        for (Rencontre r : rencontres) {
            if (!r.impliqueEquipe(equipe) || !r.estTerminee()) {
                continue;
            }
            Match.Cote cote = r.getEquipeA().equals(equipe) ? Match.Cote.A : Match.Cote.B;
            Match.Cote autre = (cote == Match.Cote.A) ? Match.Cote.B : Match.Cote.A;

            rencJouees++;
            if (equipe.equals(r.getGagnante())) {
                rencGagnees++;
            } else {
                rencPerdues++;
            }

            for (Match m : r.getMatchs()) {
                if (!m.estTermine()) {
                    continue;
                }
                matchsJoues++;
                if (m.getGagnant() == cote) {
                    matchsGagnes++;
                } else {
                    matchsPerdus++;
                }
                pointsPour += m.getScore(cote);
                pointsContre += m.getScore(autre);
            }
        }

        return new StatistiquesEquipe(rencJouees, rencGagnees, rencPerdues,
                matchsJoues, matchsGagnes, matchsPerdus, pointsPour, pointsContre);
    }
}
