package org.club.tournoi.persistence;

import org.club.tournoi.model.BaseJoueurs;
import org.club.tournoi.model.Categorie;
import org.club.tournoi.model.EtatTournoi;
import org.club.tournoi.service.ServiceTournoi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ecriture des sauvegardes, base joueurs permanente et dossier de donnees. */
class SauvegardeTest {

    @Test
    void baseJoueursRelueApresRelance(@TempDir Path dossier) throws Exception {
        GestionnaireSauvegarde g = new GestionnaireSauvegarde(dossier.resolve("Base_Tournoi"));
        Path fichierBase = dossier.resolve("Base_Joueur").resolve("club.json");

        ServiceTournoi s = new ServiceTournoi(new EtatTournoi());
        s.setBaseJoueurs(g.chargerBaseJoueurs(fichierBase)); // premier lancement : base vide
        s.ajouterJoueurBase("Martin", "Lea", Categorie.ADULTE_LOISIR, false);
        g.sauvegarderBaseJoueurs(s.getBaseJoueurs(), fichierBase);

        BaseJoueurs relue = new GestionnaireSauvegarde(dossier).chargerBaseJoueurs(fichierBase);
        assertEquals(1, relue.getJoueurs().size());
        assertEquals("Martin", relue.getJoueurs().get(0).getNom());
    }

    @Test
    void ecritureRemplaceSansLaisserDeFichierTemporaire(@TempDir Path dossier) throws Exception {
        GestionnaireSauvegarde g = new GestionnaireSauvegarde(dossier);
        Path f = g.cheminPour("t");
        ServiceTournoi s = new ServiceTournoi(new EtatTournoi());
        g.sauvegarder(s.getEtat(), f);
        s.ajouterEquipe(1);
        g.sauvegarder(s.getEtat(), f); // ecrase la version precedente

        assertEquals(1, g.charger(f).getEquipes().size());
        try (var fichiers = Files.list(dossier)) {
            assertEquals(List.of(f.getFileName().toString()),
                    fichiers.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void sauvegardesListeesDeLaPlusRecenteALaPlusAncienne(@TempDir Path dossier) throws Exception {
        GestionnaireSauvegarde g = new GestionnaireSauvegarde(dossier);
        EtatTournoi vide = new EtatTournoi();
        g.sauvegarder(vide, g.cheminPour("a_ancien"));
        g.sauvegarder(vide, g.cheminPour("z_recent"));
        Files.setLastModifiedTime(g.cheminPour("a_ancien"), FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(g.cheminPour("z_recent"), FileTime.fromMillis(9_000_000L));

        assertEquals(g.cheminPour("z_recent"), g.listerSauvegardes().get(0));
    }

    @Test
    void migrationCopieSansEcraser(@TempDir Path dossier) throws Exception {
        Path ancien = dossier.resolve("ancien");
        Path racine = dossier.resolve("racine");
        Files.createDirectories(ancien.resolve("Base_Joueur"));
        Files.writeString(ancien.resolve("Base_Joueur").resolve("club.json"), "{\"ancien\":1}");
        Files.writeString(ancien.resolve("Base_Joueur").resolve("autre.json"), "{}");
        Files.createDirectories(racine.resolve("Base_Joueur"));
        Files.writeString(racine.resolve("Base_Joueur").resolve("club.json"), "{\"recent\":1}");

        int copies = DossierDonnees.migrerAnciensDossiers(ancien, racine);

        assertEquals(1, copies);
        assertEquals("{\"recent\":1}", Files.readString(racine.resolve("Base_Joueur").resolve("club.json")));
        assertTrue(Files.exists(racine.resolve("Base_Joueur").resolve("autre.json")));
        assertTrue(Files.exists(ancien.resolve("Base_Joueur").resolve("club.json")), "originaux conserves");
    }
}
