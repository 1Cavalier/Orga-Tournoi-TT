package org.club.tournoi.ui;

import org.club.tournoi.model.EtatTournoi;
import org.club.tournoi.model.BaseJoueurs;
import org.club.tournoi.persistence.DossierDonnees;
import org.club.tournoi.persistence.GestionnaireSauvegarde;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.ui.panel.*;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Point d'entree de l'application.
 * <p>
 * Enchaine : ecran de demarrage &rarr; chargement ou creation de l'etat &rarr; configuration
 * des tables &rarr; fenetre principale a onglets.
 * <p>
 * Le tournoi (rencontres et tables comprises) et la base joueurs sont sauvegardes
 * automatiquement apres chaque modification et a la fermeture ({@link SauvegardeAutomatique}),
 * dans un dossier fixe ({@link DossierDonnees}) : on peut fermer le logiciel a tout moment et
 * reprendre le tournoi plus tard, depuis n'importe quel dossier de lancement.
 */
public class Application {

    private static final Path RACINE_DONNEES = DossierDonnees.racine();
    private static final Path DOSSIER_SAUVEGARDES = RACINE_DONNEES.resolve(DossierDonnees.SOUS_DOSSIER_TOURNOIS);
    private static final Path DOSSIER_BASES = RACINE_DONNEES.resolve(DossierDonnees.SOUS_DOSSIER_JOUEURS);

    /** Sauvegarde automatique du tournoi ouvert (creee a l'ouverture de la fenetre). */
    private static SauvegardeAutomatique sauvegardeAuto;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Application::demarrer);
    }

    private static void demarrer() {
        appliquerLookAndFeel();

        // Recupere les donnees laissees dans le dossier de lancement par l'ancienne version.
        DossierDonnees.migrerAnciensDossiers(Paths.get(""), RACINE_DONNEES);

        GestionnaireSauvegarde gestionnaire = new GestionnaireSauvegarde(DOSSIER_SAUVEGARDES);
        DialogueDemarrage demarrage = new DialogueDemarrage(gestionnaire, DOSSIER_BASES);
        demarrage.setVisible(true);
        DialogueDemarrage.Resultat choix = demarrage.getResultat();
        if (choix == null) {
            return; // l'utilisateur a quitte
        }

        EtatTournoi etat = chargerOuCreer(gestionnaire, choix);
        if (etat == null) {
            return;
        }

        // Charge la base joueurs permanente (ou une base vide si nouvelle).
        BaseJoueurs base;
        try {
            base = gestionnaire.chargerBaseJoueurs(choix.baseJoueurs());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "La base joueurs n'a pas pu etre chargee :\n" + e.getMessage()
                            + "\n\nUne base vide sera utilisee.",
                    "Base joueurs", JOptionPane.WARNING_MESSAGE);
            base = new BaseJoueurs();
        }

        ServiceTournoi service = new ServiceTournoi(etat);
        service.setBaseJoueurs(base);
        // (Re)configure la salle a la taille demandee si besoin
        int nbTables = choix.lignes() * choix.colonnes();
        if (etat.getTables().isEmpty() || etat.getTables().size() != nbTables) {
            service.configurerTables(nbTables);
        }
        // Mémorise la taille de salle pour pouvoir la modifier ensuite.
        if (!choix.existant()) {
            service.getParametres().setLignes(choix.lignes());
            service.getParametres().setColonnes(choix.colonnes());
        }
        if (!choix.existant()) {
            service.getParametres().setPointEcart(choix.pointEcart());
            service.getParametres().setCoinDepartTables(choix.coinTables());
            service.getParametres().setFormat(choix.format());
        }

        ouvrirFenetre(service, gestionnaire, choix);
    }

    private static EtatTournoi chargerOuCreer(GestionnaireSauvegarde gestionnaire,
                                              DialogueDemarrage.Resultat choix) {
        if (!choix.existant()) {
            return new EtatTournoi();
        }
        try {
            return gestionnaire.charger(choix.fichier());
        } catch (IOException e) {
            int reponse = JOptionPane.showConfirmDialog(null,
                    "Le fichier n'a pas pu etre charge :\n" + e.getMessage()
                            + "\n\nDemarrer un tournoi vide a la place ?",
                    "Chargement impossible", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            return reponse == JOptionPane.YES_OPTION ? new EtatTournoi() : null;
        }
    }

    private static void ouvrirFenetre(ServiceTournoi service, GestionnaireSauvegarde gestionnaire,
                                      DialogueDemarrage.Resultat choix) {
        JFrame frame = new JFrame("Tournoi - " + choix.fichier().getFileName()
                + "   (sauvegarde automatique dans " + RACINE_DONNEES + ")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JTabbedPane onglets = new JTabbedPane();
        onglets.setFont(Theme.CORPS_GRAS);
        onglets.addTab("Joueurs", new PanelJoueurs(service));
        onglets.addTab("Equipes", new PanelEquipes(service));
        onglets.addTab("Rencontres", new PanelRencontres(service));
        onglets.addTab("Salle", new PanelSalle(service, choix.colonnes()));
        onglets.addTab("Classement", new PanelClassement(service));
        onglets.addTab("Capitaines", new org.club.tournoi.ui.panel.PanelCapitainesServeur(service));

        // Barre superieure avec le bouton d'affichage public (2e ecran).
        JPanel racine = new JPanel(new BorderLayout());
        racine.setBackground(Theme.FOND);
        JPanel barre = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_S, Theme.ESPACE_S));
        barre.setBackground(Theme.FOND);
        JButton parametres = org.club.tournoi.ui.theme.Composants.boutonSecondaire("Parametres");
        parametres.addActionListener(e -> ouvrirParametres(service, (Frame) SwingUtilities.getWindowAncestor((java.awt.Component) e.getSource())));
        barre.add(parametres);
        JButton affichagePublic = org.club.tournoi.ui.theme.Composants.boutonSecondaire("Affichage public (2e ecran)");
        affichagePublic.addActionListener(e -> ouvrirAffichagePublic(service));
        barre.add(affichagePublic);
        racine.add(barre, BorderLayout.NORTH);
        racine.add(onglets, BorderLayout.CENTER);

        frame.setContentPane(racine);
        frame.setSize(1100, 720);
        frame.setLocationRelativeTo(null);

        sauvegardeAuto = new SauvegardeAutomatique(service, gestionnaire,
                choix.fichier(), choix.baseJoueurs(), frame);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                fermer(frame);
            }
        });

        frame.setVisible(true);
        // Premiere ecriture tout de suite : le tournoi et la base existent sur le disque des
        // l'ouverture (et les reglages choisis au demarrage sont conserves).
        sauvegardeAuto.planifier();
    }

    /** Fenetre d'affichage public unique : si elle existe deja, on la ramene au premier plan. */
    private static FenetrePublique fenetrePublique;

    /** Dialogue de reglages modifiables en cours de tournoi (points, durees). */
    private static void ouvrirParametres(ServiceTournoi service, Frame parent) {
        var params = service.getParametres();

        JComboBox<String> points = new JComboBox<>(
                new String[]{"Avec 2 points d'ecart", "Sans point d'ecart (premier a 11)"});
        points.setSelectedIndex(params.isPointEcart() ? 0 : 1);

        JSpinner duree = new JSpinner(new SpinnerNumberModel(params.getDureeEstimeeMinutes(), 1, 120, 1));
        JSpinner seuil = new JSpinner(new SpinnerNumberModel(params.getSeuilConseilAttenteMinutes(), 0, 60, 1));

        String[] coinsLibelles = {"En haut a gauche", "En haut a droite", "En bas a gauche", "En bas a droite"};
        String[] coinsValeurs = {"HAUT_GAUCHE", "HAUT_DROITE", "BAS_GAUCHE", "BAS_DROITE"};
        JComboBox<String> coin = new JComboBox<>(coinsLibelles);
        for (int i = 0; i < coinsValeurs.length; i++) {
            if (coinsValeurs[i].equals(params.getCoinDepartTables())) coin.setSelectedIndex(i);
        }

        // Taille de la salle (modifiable si aucun match en cours).
        JSpinner lignes = new JSpinner(new SpinnerNumberModel(params.getLignes(), 1, 20, 1));
        JSpinner colonnes = new JSpinner(new SpinnerNumberModel(params.getColonnes(), 1, 20, 1));
        boolean matchsEnCours = !service.getRencontresEnCours().isEmpty();
        if (matchsEnCours) {
            lignes.setEnabled(false);
            colonnes.setEnabled(false);
        }

        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, Theme.ESPACE_S, Theme.ESPACE_S));
        form.add(new JLabel("Lignes (salle)"));                  form.add(lignes);
        form.add(new JLabel("Colonnes (salle)"));                form.add(colonnes);
        form.add(new JLabel("Regle des points"));                 form.add(points);
        form.add(new JLabel("Duree estimee d'une rencontre (min)")); form.add(duree);
        form.add(new JLabel("Conseiller d'attendre sous (min)"));  form.add(seuil);
        form.add(new JLabel("Table 1 commence"));                 form.add(coin);

        String texteNote = matchsEnCours
                ? "La taille de la salle ne peut pas etre modifiee pendant qu'il y a des matchs en cours."
                : "Changer la taille de la salle recree les tables. Le reglage de points s'applique aux nouveaux matchs.";
        JLabel note = new JLabel("<html><i>" + texteNote + "</i></html>");
        note.setFont(Theme.PETIT);
        note.setForeground(Theme.TEXTE_DISCRET);
        JPanel conteneur = new JPanel(new BorderLayout(0, Theme.ESPACE_M));
        conteneur.add(form, BorderLayout.CENTER);
        conteneur.add(note, BorderLayout.SOUTH);

        int ok = JOptionPane.showConfirmDialog(parent, conteneur, "Parametres du tournoi",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            params.setPointEcart(points.getSelectedIndex() == 0);
            params.setDureeEstimeeMinutes((int) duree.getValue());
            params.setSeuilConseilAttenteMinutes((int) seuil.getValue());
            params.setCoinDepartTables(coinsValeurs[coin.getSelectedIndex()]);
            // Taille de salle : seulement si autorisé (aucun match en cours).
            if (!matchsEnCours) {
                int nl = (int) lignes.getValue();
                int nc = (int) colonnes.getValue();
                int nbTables = nl * nc;
                if (nbTables != service.getTables().size()) {
                    params.setLignes(nl);
                    params.setColonnes(nc);
                    service.configurerTables(nbTables);
                } else {
                    params.setLignes(nl);
                    params.setColonnes(nc);
                }
            }
            // Rafraichit l'affichage de la salle (la numerotation change).
            service.notifierTablesModifiees();
        }
    }

    private static void ouvrirAffichagePublic(ServiceTournoi service) {
        if (fenetrePublique != null && fenetrePublique.isDisplayable()) {
            fenetrePublique.toFront();
            fenetrePublique.requestFocus();
            return;
        }
        fenetrePublique = new FenetrePublique(service);
        fenetrePublique.setVisible(true);
    }

    /**
     * Fermeture : le tournoi et la base joueurs sont toujours enregistres, sans question.
     * On ne demande quelque chose qu'en cas d'echec de l'ecriture.
     */
    private static void fermer(JFrame frame) {
        try {
            sauvegardeAuto.sauvegarderMaintenant();
        } catch (IOException ex) {
            int quandMeme = JOptionPane.showConfirmDialog(frame,
                    "La sauvegarde a echoue :\n" + ex.getMessage()
                            + "\n\nQuitter quand meme (les dernieres modifications seront perdues) ?",
                    "Erreur de sauvegarde", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (quandMeme != JOptionPane.YES_OPTION) {
                return;
            }
        }
        frame.dispose();
        System.exit(0);
    }

    private static void appliquerLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // le look par defaut fera l'affaire
        }
        UIManager.put("Panel.background", Theme.FOND);
        UIManager.put("OptionPane.messageFont", Theme.CORPS);
        UIManager.put("Label.font", Theme.CORPS);
        UIManager.put("TextField.font", Theme.CORPS);
        UIManager.put("ComboBox.font", Theme.CORPS);
        UIManager.put("TabbedPane.selected", Theme.SURFACE);
    }
}
