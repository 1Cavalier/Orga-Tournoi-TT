package org.club.tournoi;

import org.club.tournoi.model.*;
import org.club.tournoi.persistence.GestionnaireSauvegarde;
import org.club.tournoi.service.Classement;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.StatistiquesEquipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTournoiTest {

    private ServiceTournoi service() {
        return new ServiceTournoi(new EtatTournoi());
    }

    private Equipe equipeAvecJoueurs(ServiceTournoi s, int numero, int nbJoueurs) {
        Equipe e = s.ajouterEquipe(numero);
        for (int i = 0; i < nbJoueurs; i++) {
            Joueur j = s.ajouterJoueur("Nom" + numero + i, "P" + i, Categorie.ADULTE_LOISIR);
            s.affecterJoueur(j, e);
        }
        return e;
    }

    @Test
    void scoreInvalideEstRejete() {
        MatchSimple m = new MatchSimple("test", null, null);
        assertThrows(IllegalArgumentException.class, () -> m.enregistrerScore(11, 11)); // egalite
        assertThrows(IllegalArgumentException.class, () -> m.enregistrerScore(5, 3));   // gagnant < 11
        assertThrows(IllegalArgumentException.class, () -> m.enregistrerScore(12, 3));  // > 11
        assertDoesNotThrow(() -> m.enregistrerScore(11, 9));
        assertThrows(IllegalStateException.class, () -> m.enregistrerScore(11, 0));     // deja termine
    }

    @Test
    void lancerRencontreOccupeTableEtEquipes() {
        ServiceTournoi s = service();
        s.configurerTables(2);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Table t = s.tableParNumero(1).orElseThrow();

        Rencontre r = s.lancerRencontre(a, b, t);

        assertEquals(EtatEquipe.EN_MATCH, a.getEtat());
        assertEquals(EtatEquipe.EN_MATCH, b.getEtat());
        assertTrue(t.isOccupee());
        assertFalse(t.estDisponible());
        assertEquals(5, r.getMatchs().size());
    }

    @Test
    void refuseEquipesDejaEnMatch() {
        ServiceTournoi s = service();
        s.configurerTables(2);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        s.lancerRencontre(a, b, s.tableParNumero(1).orElseThrow());
        Table t2 = s.tableParNumero(2).orElseThrow();
        assertThrows(IllegalStateException.class, () -> s.lancerRencontre(a, b, t2));
    }

    @Test
    void refuseRematch() {
        ServiceTournoi s = service();
        s.configurerTables(2);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Rencontre r = s.lancerRencontre(a, b, s.tableParNumero(1).orElseThrow());
        s.enregistrerScores(r,
                new int[]{11, 11, 11, 11, 11},
                new int[]{5, 5, 5, 5, 5});
        assertTrue(s.dejaAffrontees(a, b));
        assertThrows(IllegalStateException.class,
                () -> s.lancerRencontre(a, b, s.tableParNumero(2).orElseThrow()));
    }

    @Test
    void enregistrerScoresTermineEtLibere() {
        ServiceTournoi s = service();
        s.configurerTables(1);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Table t = s.tableParNumero(1).orElseThrow();
        Rencontre r = s.lancerRencontre(a, b, t);

        // A gagne 4 matchs, B gagne 1 => A gagne la rencontre
        s.enregistrerScores(r,
                new int[]{11, 11, 11, 11, 5},
                new int[]{3, 3, 3, 3, 11});

        assertTrue(r.estTerminee());
        assertEquals(a, r.getGagnante());
        assertEquals(EtatEquipe.EN_ATTENTE, a.getEtat());
        assertTrue(t.estDisponible());

        StatistiquesEquipe sa = s.statistiques(a);
        assertEquals(1, sa.rencontresJouees());
        assertEquals(1, sa.rencontresGagnees());
        assertEquals(4, sa.matchsGagnes());
        assertEquals(1, sa.matchsPerdus());
    }

    @Test
    void enregistrerScoresInvalidesNAppliqueRien() {
        ServiceTournoi s = service();
        s.configurerTables(1);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Rencontre r = s.lancerRencontre(a, b, s.tableParNumero(1).orElseThrow());

        // Dernier score invalide (egalite) : tout doit etre rejete
        assertThrows(IllegalArgumentException.class, () -> s.enregistrerScores(r,
                new int[]{11, 11, 11, 11, 7},
                new int[]{3, 3, 3, 3, 7}));

        assertFalse(r.estTerminee());
        assertEquals(EtatEquipe.EN_MATCH, a.getEtat());
        assertTrue(r.getMatchs().stream().noneMatch(Match::estTermine));
    }

    @Test
    void classementTrieParRencontresPuisMatchsPuisPoints() {
        ServiceTournoi s = service();
        s.configurerTables(2);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Equipe c = equipeAvecJoueurs(s, 3, 4);

        Rencontre r1 = s.lancerRencontre(a, b, s.tableParNumero(1).orElseThrow());
        s.enregistrerScores(r1, new int[]{11,11,11,11,11}, new int[]{0,0,0,0,0}); // a bat b 5-0

        Rencontre r2 = s.lancerRencontre(a, c, s.tableParNumero(1).orElseThrow());
        s.enregistrerScores(r2, new int[]{11,11,11,0,0}, new int[]{0,0,0,11,11}); // a bat c 3-2

        List<Classement.Ligne> cl = s.classement();
        // a : 2 rencontres gagnees -> 1re, quel que soit le reste
        assertEquals(a, cl.get(0).equipe());
        assertEquals(1, cl.get(0).rang());
        assertEquals(2, cl.get(0).stats().rencontresGagnees());

        // b et c ont chacune 0 rencontre gagnee : c a gagne 2 matchs (contre a), b en a gagne 0
        // -> c passe devant b grace au critere "matchs gagnes"
        int posB = -1, posC = -1;
        for (int i = 0; i < cl.size(); i++) {
            if (cl.get(i).equipe().equals(b)) posB = i;
            if (cl.get(i).equipe().equals(c)) posC = i;
        }
        assertTrue(posC < posB, "c doit devancer b via les matchs gagnes");
        assertEquals(2, cl.get(posC).stats().matchsGagnes());
        assertEquals(0, cl.get(posB).stats().matchsGagnes());
    }

    @Test
    void roundTripJsonConservetout(@TempDir Path dossier) throws Exception {
        ServiceTournoi s = service();
        s.configurerTables(2);
        Equipe a = equipeAvecJoueurs(s, 1, 4);
        Equipe b = equipeAvecJoueurs(s, 2, 4);
        Rencontre r = s.lancerRencontre(a, b, s.tableParNumero(1).orElseThrow());
        s.enregistrerScores(r, new int[]{11,11,11,11,5}, new int[]{3,3,3,3,11});

        GestionnaireSauvegarde g = new GestionnaireSauvegarde(dossier);
        Path fichier = g.cheminPour("test");
        g.sauvegarder(s.getEtat(), fichier);

        EtatTournoi recharge = g.charger(fichier);
        assertEquals(2, recharge.getEquipes().size());
        assertEquals(8, recharge.getJoueurs().size());
        assertEquals(2, recharge.getTables().size());
        assertEquals(1, recharge.getRencontres().size());

        Rencontre rr = recharge.getRencontres().get(0);
        assertTrue(rr.estTerminee());
        assertEquals(5, rr.getMatchs().size());
        // Le match double doit avoir ete reconstruit avec le bon type
        assertInstanceOf(MatchDouble.class, rr.getMatchs().get(4));
        assertInstanceOf(MatchSimple.class, rr.getMatchs().get(0));
    }
}
