package org.club.tournoi.ui;

import org.club.tournoi.persistence.GestionnaireSauvegarde;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.TournoiListener;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Sauvegarde automatique du tournoi et de la base joueurs.
 * <p>
 * S'abonne au service : a chaque changement (joueur ajoute ou modifie, equipe composee, score
 * saisi, rencontre terminee...), un minuteur est relance ; quand rien n'a bouge pendant
 * {@link #DELAI_MS} millisecondes, les deux fichiers sont ecrits. On regroupe ainsi une rafale
 * de modifications en une seule ecriture, tout en garantissant qu'un arret brutal du PC ne fait
 * perdre que les toutes dernieres secondes.
 * <p>
 * Tout se passe sur le thread Swing, comme les modifications du tournoi elles-memes : la
 * sauvegarde voit donc toujours un etat coherent.
 */
public class SauvegardeAutomatique implements TournoiListener {

    /** Delai sans modification avant d'ecrire sur le disque. */
    static final int DELAI_MS = 2000;

    private final ServiceTournoi service;
    private final GestionnaireSauvegarde gestionnaire;
    private final Path fichierTournoi;
    private final Path fichierBase;
    private final Component parent;
    private final Timer minuteur;
    /** Evite d'afficher le meme message d'erreur a chaque tentative. */
    private boolean erreurSignalee;

    public SauvegardeAutomatique(ServiceTournoi service, GestionnaireSauvegarde gestionnaire,
                                 Path fichierTournoi, Path fichierBase, Component parent) {
        this.service = service;
        this.gestionnaire = gestionnaire;
        this.fichierTournoi = fichierTournoi;
        this.fichierBase = fichierBase;
        this.parent = parent;
        this.minuteur = new Timer(DELAI_MS, e -> sauvegarderEnSilence());
        this.minuteur.setRepeats(false);
        service.ajouterListener(this);
    }

    /** Ecrit immediatement le tournoi puis la base joueurs (utilise aussi a la fermeture). */
    public void sauvegarderMaintenant() throws IOException {
        minuteur.stop();
        gestionnaire.sauvegarder(service.getEtat(), fichierTournoi);
        gestionnaire.sauvegarderBaseJoueurs(service.getBaseJoueurs(), fichierBase);
    }

    /** Planifie une sauvegarde (utile apres un changement que le service ne signale pas). */
    public void planifier() {
        minuteur.restart();
    }

    private void sauvegarderEnSilence() {
        try {
            sauvegarderMaintenant();
            erreurSignalee = false;
        } catch (IOException e) {
            if (!erreurSignalee) {
                erreurSignalee = true;
                JOptionPane.showMessageDialog(parent,
                        "La sauvegarde automatique a echoue :\n" + e.getMessage()
                                + "\n\nLe logiciel reessaiera au prochain changement.",
                        "Sauvegarde automatique", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    @Override public void joueursModifies()     { planifier(); }
    @Override public void equipesModifiees()    { planifier(); }
    @Override public void tablesModifiees()     { planifier(); }
    @Override public void rencontresModifiees() { planifier(); }
    @Override public void classementModifie()   { planifier(); }
}
