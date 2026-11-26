package org.club.tournoi;

import org.club.tournoi.model.*;
import org.club.tournoi.persistence.GestionnaireSauvegarde;
import org.club.tournoi.service.ServiceTournoi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reprise d'un tournoi sauvegarde en cours de partie : apres rechargement, les rencontres et
 * les barrages doivent referencer les memes objets equipe et joueur que les listes du tournoi.
 */
class RepriseSauvegardeTest {

    private ServiceTournoi tournoiADeuxEquipes() {
        ServiceTournoi s = new ServiceTournoi(new EtatTournoi());
        s.configurerTables(2);
        for (int numero = 1; numero <= 2; numero++) {
            Equipe e = s.ajouterEquipe(numero);
            for (int i = 0; i < 4; i++) {
                s.affecterJoueur(s.ajouterJoueur("Nom" + numero + i, "P" + i, Categorie.ADULTE_LOISIR), e);
            }
        }
        return s;
    }

    private ServiceTournoi sauvegarderPuisRecharger(ServiceTournoi s, Path dossier) throws Exception {
        GestionnaireSauvegarde g = new GestionnaireSauvegarde(dossier);
        Path fichier = g.cheminPour("reprise");
        g.sauvegarder(s.getEtat(), fichier);
        return new ServiceTournoi(g.charger(fichier));
    }

    @Test
    void rencontreRepriseLibereLesEquipesDuTournoi(@TempDir Path dossier) throws Exception {
        ServiceTournoi s = tournoiADeuxEquipes();
        s.lancerRencontre(s.equipeParNumero(1).orElseThrow(), s.equipeParNumero(2).orElseThrow(),
                s.tableParNumero(1).orElseThrow());

        ServiceTournoi repris = sauvegarderPuisRecharger(s, dossier);
        Rencontre r = repris.getRencontresEnCours().get(0);
        Equipe equipe1 = repris.equipeParNumero(1).orElseThrow();
        assertSame(equipe1, r.getEquipeA(), "la rencontre doit pointer vers l'equipe du tournoi");

        repris.enregistrerScores(r, new int[]{11, 11, 11, 11, 5}, new int[]{3, 3, 3, 3, 11});

        assertEquals(EtatEquipe.EN_ATTENTE, equipe1.getEtat());
        assertEquals(EtatEquipe.EN_ATTENTE, repris.equipeParNumero(2).orElseThrow().getEtat());
        assertTrue(repris.tableParNumero(1).orElseThrow().estDisponible());
    }

    @Test
    void joueursDesEquipesEtDesMatchsSontPartages(@TempDir Path dossier) throws Exception {
        ServiceTournoi s = tournoiADeuxEquipes();
        s.lancerRencontre(s.equipeParNumero(1).orElseThrow(), s.equipeParNumero(2).orElseThrow(),
                s.tableParNumero(1).orElseThrow());

        ServiceTournoi repris = sauvegarderPuisRecharger(s, dossier);
        Joueur premier = repris.equipeParNumero(1).orElseThrow().joueurAu(0);
        assertSame(premier, repris.getJoueurs().stream()
                .filter(j -> j.getId().equals(premier.getId())).findFirst().orElseThrow());
        Match simple1 = repris.getRencontresEnCours().get(0).getMatchs().get(0);
        assertSame(premier, simple1.getJoueurs(Match.Cote.A).get(0));
        // Un joueur affecte n'apparait plus comme "sans equipe" apres rechargement.
        assertTrue(repris.getJoueursSansEquipe().isEmpty());
    }

    @Test
    void barrageReprisLibereLesEquipes(@TempDir Path dossier) throws Exception {
        ServiceTournoi s = tournoiADeuxEquipes();
        s.preparerDepartages();
        s.lancerBarrage(s.equipeParNumero(1).orElseThrow(), s.equipeParNumero(2).orElseThrow(),
                1, 2, s.tableParNumero(1).orElseThrow());

        ServiceTournoi repris = sauvegarderPuisRecharger(s, dossier);
        Barrage b = repris.getBarrages().get(0);
        repris.enregistrerScoresBarrage(b, new int[]{11, 11, 11, 3, 3}, new int[]{5, 5, 5, 11, 11});

        assertEquals(EtatEquipe.EN_ATTENTE, repris.equipeParNumero(1).orElseThrow().getEtat());
        assertEquals(1, repris.positionFinale(repris.equipeParNumero(1).orElseThrow()));
    }
}
