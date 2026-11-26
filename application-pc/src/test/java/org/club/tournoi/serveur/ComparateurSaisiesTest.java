package org.club.tournoi.serveur;

import org.club.tournoi.serveur.ComparateurSaisies.CleMatch;
import org.club.tournoi.serveur.ComparateurSaisies.Saisie;
import org.club.tournoi.serveur.ComparateurSaisies.Statut;
import org.club.tournoi.serveur.ComparateurSaisies.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Comparaison des saisies des deux capitaines (logique pure, sans reseau). */
class ComparateurSaisiesTest {

    @Test
    void uneSeuleSaisieDonneAttente() {
        Map<CleMatch, Verdict> v = ComparateurSaisies.analyser(List.of(new Saisie("r1", 0, "A", 11, 5)));
        assertEquals(Statut.ATTENTE_AUTRE, v.get(new CleMatch("r1", 0)).statut());
    }

    @Test
    void deuxSaisiesIdentiquesDonnentAccord() {
        Map<CleMatch, Verdict> v = ComparateurSaisies.analyser(List.of(
                new Saisie("r1", 2, "A", 11, 9), new Saisie("r1", 2, "B", 11, 9)));
        Verdict verdict = v.get(new CleMatch("r1", 2));
        assertEquals(Statut.ACCORD, verdict.statut());
        assertEquals(11, verdict.scoreA());
        assertEquals(9, verdict.scoreB());
    }

    @Test
    void deuxSaisiesDifferentesDonnentLitige() {
        Map<CleMatch, Verdict> v = ComparateurSaisies.analyser(List.of(
                new Saisie("r1", 4, "A", 11, 9), new Saisie("r1", 4, "B", 9, 11)));
        Verdict verdict = v.get(new CleMatch("r1", 4));
        assertEquals(Statut.LITIGE, verdict.statut());
        assertEquals(11, verdict.aScoreA());
        assertEquals(9, verdict.bScoreA());
    }
}
