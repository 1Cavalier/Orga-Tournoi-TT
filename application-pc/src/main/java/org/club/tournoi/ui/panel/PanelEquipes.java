package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.EtatEquipe;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Composition des equipes, repensee autour d'un geste simple : on selectionne un poste
 * dans une equipe (case cliquable), puis on clique un joueur disponible pour l'y placer.
 * <p>
 * A gauche : les joueurs sans equipe. A droite : les equipes sous forme de cartes, chaque
 * carte montrant ses postes numerotes 1 a {@value org.club.tournoi.model.Equipe#TAILLE_MAX}.
 * Un poste occupe se libere d'un clic. Aucune boite de dialogue pour la manipulation
 * courante : tout se fait par selection directe.
 * <p>
 * La barre du haut prevoit la place des futurs modes de remplissage automatique (rang 1,
 * aleatoire) et du choix de format ; ces controles sont presents mais signales comme a venir.
 */
public class PanelEquipes extends JPanel implements TournoiListener {

    private final ServiceTournoi service;

    private final JPanel listeJoueurs = new JPanel();
    private final JPanel grilleEquipes = new JPanel();

    /** Poste actuellement selectionne (equipe + index), ou null si aucun. */
    private Equipe equipeCible;
    private int posteCible = -1;

    /** Critere de tri de la liste des joueurs disponibles. */
    private enum Tri { NOM, NIVEAU }
    private Tri tri = Tri.NIVEAU;

    private final JLabel indication = new JLabel();

    public PanelEquipes(ServiceTournoi service) {
        this.service = service;
        service.ajouterListener(this);

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setBackground(Theme.FOND);
        setBorder(Theme.marge(Theme.ESPACE_M));

        add(barreHaut(), BorderLayout.NORTH);
        add(corps(), BorderLayout.CENTER);

        rafraichir();
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    private JComponent barreHaut() {
        JPanel haut = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        haut.setOpaque(false);

        haut.add(Composants.titre("Equipes"), BorderLayout.NORTH);

        JPanel barre = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        barre.setOpaque(false);

        JButton nouvelle = Composants.boutonPrimaire("Nouvelle equipe");
        nouvelle.addActionListener(e -> nouvelleEquipe());
        barre.add(nouvelle);

        // Boutons de remplissage automatique.
        JButton rang1 = Composants.boutonSecondaire("Completer (rang 1)");
        JButton alea = Composants.boutonSecondaire("Remplir : aleatoire");
        rang1.setToolTipText("Complete les equipes dont vous avez place le joueur n\u00b01");
        rang1.addActionListener(e -> completerRang1());
        alea.addActionListener(e -> remplirAuto(org.club.tournoi.service.CompositionAutomatique.Mode.ALEATOIRE));
        barre.add(rang1);
        barre.add(alea);

        JButton toutSupprimer = Composants.boutonSecondaire("Supprimer toutes les equipes");
        toutSupprimer.addActionListener(e -> supprimerToutesEquipes());
        barre.add(toutSupprimer);

        haut.add(barre, BorderLayout.CENTER);

        indication.setFont(Theme.CORPS);
        indication.setForeground(Theme.TEXTE_DISCRET);
        indication.setBorder(BorderFactory.createEmptyBorder(Theme.ESPACE_S, 0, 0, 0));
        haut.add(indication, BorderLayout.SOUTH);

        return haut;
    }

    private JComponent corps() {
        // Colonne gauche : joueurs disponibles
        listeJoueurs.setLayout(new BoxLayout(listeJoueurs, BoxLayout.Y_AXIS));
        listeJoueurs.setBackground(Theme.SURFACE);

        JScrollPane scrollJoueurs = new JScrollPane(listeJoueurs);
        scrollJoueurs.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        scrollJoueurs.getVerticalScrollBar().setUnitIncrement(16);

        JPanel gauche = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        gauche.setOpaque(false);

        JPanel enteteGauche = new JPanel(new BorderLayout());
        enteteGauche.setOpaque(false);
        enteteGauche.add(Composants.sousTitre("Joueurs disponibles"), BorderLayout.WEST);
        enteteGauche.add(barreTri(), BorderLayout.EAST);
        gauche.add(enteteGauche, BorderLayout.NORTH);
        gauche.add(scrollJoueurs, BorderLayout.CENTER);

        // Colonne droite : cartes d'equipes
        grilleEquipes.setLayout(new GridLayout(0, 2, Theme.ESPACE_M, Theme.ESPACE_M));
        grilleEquipes.setOpaque(false);

        JScrollPane scrollEquipes = new JScrollPane(grilleEquipes);
        scrollEquipes.setBorder(null);
        scrollEquipes.getViewport().setBackground(Theme.FOND);
        scrollEquipes.getVerticalScrollBar().setUnitIncrement(16);

        JPanel droite = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        droite.setOpaque(false);
        droite.add(Composants.sousTitre("Equipes"), BorderLayout.NORTH);
        droite.add(scrollEquipes, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gauche, droite);
        split.setResizeWeight(0.32);
        split.setBorder(null);
        split.setOpaque(false);
        return split;
    }

    // ------------------------------------------------------------------
    // Interaction : selection d'un poste puis d'un joueur
    // ------------------------------------------------------------------

    private void selectionnerPoste(Equipe equipe, int poste) {
        // Un clic sur le poste deja selectionne le deselectionne.
        if (equipe.equals(equipeCible) && poste == posteCible) {
            viderSelection();
        } else {
            equipeCible = equipe;
            posteCible = poste;
            majIndication();
        }
        rafraichir();
    }

    private void viderSelection() {
        equipeCible = null;
        posteCible = -1;
        majIndication();
    }

    private void majIndication() {
        if (equipeCible == null) {
            indication.setText("Astuce : cliquez un poste d'equipe, puis un joueur disponible pour l'y placer.");
        } else {
            indication.setText("Poste " + (posteCible + 1) + " de l'equipe " + equipeCible.getNumero()
                    + " selectionne — cliquez un joueur disponible (ou le meme poste pour annuler).");
        }
    }

    private void placerJoueur(Joueur joueur) {
        if (equipeCible == null) {
            info("Choisissez d'abord un poste dans une equipe, a droite.");
            return;
        }
        try {
            service.affecterJoueurAuPoste(joueur, equipeCible, posteCible);
            viderSelection();
        } catch (IllegalStateException ex) {
            erreur(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Actions equipes
    // ------------------------------------------------------------------

    /**
     * Lance un mode de composition automatique. Calcule d'abord une proposition, montre les
     * eventuelles alertes (groupes trop grands, joueurs non places), demande confirmation, puis
     * applique. Ne touche pas aux equipes deja formees : on previent l'utilisateur si besoin.
     */
    /**
     * Lance un mode de composition automatique. Ouvre un apercu des equipes proposees ; pour le
     * mode aleatoire, un bouton "Relancer" permet de re-melanger autant de fois qu'on veut avant
     * de valider. "Valider" applique la proposition affichee ; les equipes existantes sont
     * conservees.
     */
    private void remplirAuto(org.club.tournoi.service.CompositionAutomatique.Mode mode) {
        if (service.getJoueursSansEquipe().isEmpty()) {
            info("Aucun joueur disponible a repartir. Tous sont deja dans une equipe.");
            return;
        }
        var proposition = service.proposerComposition(mode);
        if (proposition.equipes().isEmpty()) {
            // Aucune equipe possible : montrer l'explication precise si elle existe (ex. N=5),
            // sinon un message generique.
            if (!proposition.alertes().isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", proposition.alertes()),
                        "Composition impossible", JOptionPane.WARNING_MESSAGE);
            } else {
                info("Pas assez de joueurs disponibles pour former une equipe (il en faut au moins 3).");
            }
            return;
        }

        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        DialogueApercuComposition dlg = new DialogueApercuComposition(parent, service, mode, proposition);
        dlg.setVisible(true);
        if (dlg.estValide()) {
            viderSelection();
            service.appliquerComposition(dlg.getProposition());
        }
    }

    /**
     * Mode rang 1 : complete les equipes dont l'utilisateur a place le joueur n&deg;1. Verifie
     * qu'il y a au moins une equipe amorcee et des joueurs a repartir, montre un recapitulatif
     * (avec alertes), puis applique.
     */
    private void completerRang1() {
        boolean auMoinsUnN1 = service.getEquipes().stream()
                .anyMatch(e -> !e.getJoueurs().isEmpty() && e.getJoueurs().size() < org.club.tournoi.model.Equipe.TAILLE_MAX);
        if (!auMoinsUnN1) {
            info("Le mode rang 1 complete les equipes dont vous avez deja place le joueur n\u00b01.\n"
                    + "Creez d'abord une ou plusieurs equipes et placez-y leur tete de serie (poste 1), "
                    + "puis relancez.");
            return;
        }
        if (service.getJoueursSansEquipe().isEmpty()) {
            info("Aucun joueur disponible a repartir. Tous sont deja affectes.");
            return;
        }

        var plan = service.proposerCompletionRang1();

        int nbAjouts = plan.ajouts().values().stream().mapToInt(java.util.List::size).sum();
        int nbNouvelles = plan.nouvellesEquipes().size();

        StringBuilder message = new StringBuilder();
        message.append("Completion des equipes par rang 1 :\n");
        message.append("  - ").append(nbAjouts).append(" joueur(s) ajoute(s) aux equipes existantes\n");
        if (nbNouvelles > 0) {
            message.append("  - ").append(nbNouvelles).append(" nouvelle(s) equipe(s) creee(s) pour les joueurs restants\n");
        }
        if (!plan.alertes().isEmpty()) {
            message.append("\nA noter :\n");
            for (String a : plan.alertes()) {
                message.append("  - ").append(a).append("\n");
            }
        }
        message.append("\nAppliquer ?");

        int ok = JOptionPane.showConfirmDialog(this, message.toString(), "Completion rang 1",
                JOptionPane.OK_CANCEL_OPTION,
                plan.alertes().isEmpty() ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            viderSelection();
            service.appliquerCompletionRang1(plan);
        }
    }

    private void supprimerToutesEquipes() {
        if (service.getEquipes().isEmpty()) {
            info("Il n'y a aucune equipe a supprimer.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "Supprimer toutes les equipes ?\nTous les joueurs redeviendront disponibles.\n"
                        + "(Les equipes en cours de rencontre seront conservees.)",
                "Tout supprimer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        viderSelection();
        int enMatch = service.supprimerToutesEquipes();
        if (enMatch > 0) {
            info(enMatch + " equipe(s) en cours de rencontre ont ete conservees. "
                    + "Terminez ou interrompez leur rencontre pour pouvoir les supprimer.");
        }
    }

    private void nouvelleEquipe() {
        // Numero propose : le plus petit entier libre a partir de 1.
        int propose = 1;
        while (service.equipeParNumero(propose).isPresent()) {
            propose++;
        }
        String saisie = JOptionPane.showInputDialog(this, "Numero de la nouvelle equipe :",
                String.valueOf(propose));
        if (saisie == null) return;
        try {
            service.ajouterEquipe(Integer.parseInt(saisie.trim()));
        } catch (NumberFormatException ex) {
            erreur("Numero d'equipe invalide.");
        } catch (IllegalArgumentException ex) {
            erreur(ex.getMessage());
        }
    }

    private void supprimerEquipe(Equipe equipe) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Supprimer l'equipe " + equipe.getNumero() + " ? Ses joueurs redeviendront disponibles.",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            try {
                if (equipe.equals(equipeCible)) viderSelection();
                service.supprimerEquipe(equipe);
            } catch (IllegalStateException ex) {
                erreur(ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    private void rafraichir() {
        majIndication();
        rendreJoueurs();
        rendreEquipes();
        revalidate();
        repaint();
    }

    private void rendreJoueurs() {
        listeJoueurs.removeAll();
        List<Joueur> dispo = new java.util.ArrayList<>(service.getJoueursSansEquipe());
        // Tri selon le critere choisi.
        if (tri == Tri.NOM) {
            dispo.sort(java.util.Comparator
                    .comparing((Joueur j) -> j.getNom(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(j -> j.getPrenom(), String.CASE_INSENSITIVE_ORDER));
        } else {
            // Par niveau (1 = plus fort d'abord), puis par nom a niveau egal.
            dispo.sort(java.util.Comparator
                    .comparingInt((Joueur j) -> j.getCategorie().getNiveau())
                    .thenComparing(j -> j.getNom(), String.CASE_INSENSITIVE_ORDER));
        }
        if (dispo.isEmpty()) {
            JLabel vide = new JLabel("Tous les joueurs sont affectes.");
            vide.setFont(Theme.CORPS);
            vide.setForeground(Theme.TEXTE_DISCRET);
            vide.setBorder(Theme.marge(Theme.ESPACE_M));
            vide.setAlignmentX(Component.LEFT_ALIGNMENT);
            listeJoueurs.add(vide);
        } else {
            for (Joueur j : dispo) {
                listeJoueurs.add(carteJoueur(j));
                listeJoueurs.add(Box.createVerticalStrut(1));
            }
        }
        listeJoueurs.add(Box.createVerticalGlue());
    }

    /** Deux boutons pour trier la liste des joueurs par nom ou par niveau. */
    private JComponent barreTri() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_XS, 0));
        p.setOpaque(false);
        JLabel label = new JLabel("Trier :");
        label.setFont(Theme.PETIT);
        label.setForeground(Theme.TEXTE_DISCRET);
        JButton parNom = new JButton("Nom");
        JButton parNiveau = new JButton("Niveau");
        for (JButton b : new JButton[]{parNom, parNiveau}) {
            b.setFont(Theme.PETIT);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createEmptyBorder(2, Theme.ESPACE_S, 2, Theme.ESPACE_S));
        }
        Runnable style = () -> {
            parNom.setBackground(tri == Tri.NOM ? Theme.ACCENT : Theme.SURFACE);
            parNom.setForeground(tri == Tri.NOM ? Color.WHITE : Theme.TEXTE_DISCRET);
            parNiveau.setBackground(tri == Tri.NIVEAU ? Theme.ACCENT : Theme.SURFACE);
            parNiveau.setForeground(tri == Tri.NIVEAU ? Color.WHITE : Theme.TEXTE_DISCRET);
        };
        parNom.addActionListener(e -> { tri = Tri.NOM; style.run(); rendreJoueurs(); listeJoueurs.revalidate(); listeJoueurs.repaint(); });
        parNiveau.addActionListener(e -> { tri = Tri.NIVEAU; style.run(); rendreJoueurs(); listeJoueurs.revalidate(); listeJoueurs.repaint(); });
        style.run();
        p.add(label);
        p.add(parNom);
        p.add(parNiveau);
        return p;
    }

    /** Ligne cliquable representant un joueur disponible. */
    private JComponent carteJoueur(Joueur joueur) {
        JPanel ligne = new JPanel(new BorderLayout());
        ligne.setBackground(Theme.SURFACE);
        ligne.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDURE),
                BorderFactory.createEmptyBorder(Theme.ESPACE_S, Theme.ESPACE_M, Theme.ESPACE_S, Theme.ESPACE_M)));
        ligne.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        ligne.setAlignmentX(Component.LEFT_ALIGNMENT);
        ligne.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nom = new JLabel(joueur.getNomComplet());
        nom.setFont(Theme.CORPS_GRAS);
        nom.setForeground(Theme.couleurNiveau(joueur.getCategorie().getNiveau()));

        JLabel cat = new JLabel(joueur.getCategorie().getLibelle());
        cat.setFont(Theme.PETIT);
        cat.setForeground(Theme.TEXTE_DISCRET);

        JPanel textes = new JPanel();
        textes.setOpaque(false);
        textes.setLayout(new BoxLayout(textes, BoxLayout.Y_AXIS));
        textes.add(nom);
        textes.add(cat);
        ligne.add(textes, BorderLayout.CENTER);

        // Symbole de groupe, s'il en a un, affiche a droite.
        if (joueur.getSymbole().estDefini()) {
            JLabel sym = new JLabel(joueur.getSymbole().getCaractere());
            sym.setFont(Theme.SOUS_TITRE);
            sym.setForeground(Theme.VERT_TABLE);
            sym.setToolTipText("Groupe " + joueur.getSymbole().getLibelle());
            sym.setBorder(BorderFactory.createEmptyBorder(0, Theme.ESPACE_S, 0, 0));
            ligne.add(sym, BorderLayout.EAST);
        }

        // Survol : teinte la ligne si un poste attend un joueur.
        ligne.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                ligne.setBackground(equipeCible != null ? Theme.SELECTION : Theme.LIGNE_PAIRE);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                ligne.setBackground(Theme.SURFACE);
            }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                placerJoueur(joueur);
            }
        });
        return ligne;
    }

    private void rendreEquipes() {
        grilleEquipes.removeAll();
        if (service.getEquipes().isEmpty()) {
            JLabel vide = new JLabel("Aucune equipe. Cliquez \"Nouvelle equipe\" pour commencer.");
            vide.setFont(Theme.CORPS);
            vide.setForeground(Theme.TEXTE_DISCRET);
            grilleEquipes.add(vide);
            return;
        }
        for (Equipe e : service.getEquipes()) {
            grilleEquipes.add(carteEquipe(e));
        }
    }

    private JComponent carteEquipe(Equipe equipe) {
        JPanel carte = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(Theme.carte());

        // En-tete : nom + etat + suppression
        JPanel tete = new JPanel(new BorderLayout());
        tete.setOpaque(false);
        JLabel nom = new JLabel("Equipe " + equipe.getNumero());
        nom.setFont(Theme.SOUS_TITRE);
        nom.setForeground(Theme.VERT_TABLE);
        tete.add(nom, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_S, 0));
        actions.setOpaque(false);
        JLabel etat = new JLabel(equipe.getEtat().getLibelle());
        etat.setFont(Theme.PETIT);
        etat.setForeground(equipe.getEtat() == EtatEquipe.EN_MATCH ? Theme.ACCENT : Theme.TEXTE_DISCRET);
        actions.add(etat);

        JButton suppr = new JButton("Supprimer");
        suppr.setFont(Theme.PETIT);
        suppr.setForeground(Theme.ERREUR);
        suppr.setContentAreaFilled(false);
        suppr.setBorder(BorderFactory.createEmptyBorder(2, Theme.ESPACE_S, 2, Theme.ESPACE_S));
        suppr.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        suppr.setEnabled(equipe.getEtat() != EtatEquipe.EN_MATCH);
        suppr.addActionListener(ev -> supprimerEquipe(equipe));
        actions.add(suppr);

        tete.add(actions, BorderLayout.EAST);
        carte.add(tete, BorderLayout.NORTH);

        // Postes 1..TAILLE_MAX
        JPanel postes = new JPanel();
        postes.setOpaque(false);
        postes.setLayout(new BoxLayout(postes, BoxLayout.Y_AXIS));
        for (int i = 0; i < Equipe.TAILLE_MAX; i++) {
            postes.add(rangPoste(equipe, i));
            if (i < Equipe.TAILLE_MAX - 1) postes.add(Box.createVerticalStrut(Theme.ESPACE_XS));
        }
        carte.add(postes, BorderLayout.CENTER);
        return carte;
    }

    /** Une case de poste : numero + occupant (ou vide), cliquable. */
    private JComponent rangPoste(Equipe equipe, int index) {
        Joueur occupant = equipe.joueurAu(index);
        boolean selectionne = equipe.equals(equipeCible) && index == posteCible;

        JPanel rang = new JPanel(new BorderLayout(Theme.ESPACE_S, 0));
        rang.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        rang.setBackground(selectionne ? Theme.SELECTION : Theme.FOND);
        rang.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selectionne ? Theme.ACCENT : Theme.BORDURE, selectionne ? 2 : 1, true),
                BorderFactory.createEmptyBorder(Theme.ESPACE_XS, Theme.ESPACE_S, Theme.ESPACE_XS, Theme.ESPACE_S)));
        rang.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel numero = new JLabel(String.valueOf(index + 1));
        numero.setFont(Theme.CORPS_GRAS);
        numero.setForeground(selectionne ? Theme.ACCENT : Theme.TEXTE_DISCRET);
        numero.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Theme.ESPACE_S));
        numero.setPreferredSize(new Dimension(18, 20));
        rang.add(numero, BorderLayout.WEST);

        if (occupant != null) {
            String texteSymbole = occupant.getSymbole().estDefini()
                    ? "  " + occupant.getSymbole().getCaractere() : "";
            JLabel nom = new JLabel(occupant.getNomComplet() + texteSymbole);
            nom.setFont(Theme.CORPS_GRAS);
            nom.setForeground(Theme.couleurNiveau(occupant.getCategorie().getNiveau()));
            if (occupant.getSymbole().estDefini()) {
                nom.setToolTipText("Groupe " + occupant.getSymbole().getLibelle());
            }
            rang.add(nom, BorderLayout.CENTER);

            JButton retirer = new JButton("x");
            retirer.setFont(Theme.CORPS_GRAS);
            retirer.setForeground(Theme.TEXTE_DISCRET);
            retirer.setContentAreaFilled(false);
            retirer.setBorder(BorderFactory.createEmptyBorder(0, Theme.ESPACE_S, 0, 0));
            retirer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            retirer.setToolTipText("Retirer ce joueur");
            retirer.addActionListener(ev -> {
                if (equipe.equals(equipeCible)) viderSelection();
                service.retirerJoueurDeEquipe(occupant, equipe);
            });
            rang.add(retirer, BorderLayout.EAST);
        } else {
            JLabel vide = new JLabel(selectionne ? "Cliquez un joueur a gauche" : "Poste libre");
            vide.setFont(Theme.PETIT);
            vide.setForeground(selectionne ? Theme.ACCENT : Theme.TEXTE_DISCRET);
            rang.add(vide, BorderLayout.CENTER);
        }

        rang.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                selectionnerPoste(equipe, index);
            }
        });
        return rang;
    }

    private void info(String m) {
        JOptionPane.showMessageDialog(this, m, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void erreur(String m) {
        JOptionPane.showMessageDialog(this, m, "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    @Override public void joueursModifies() { rafraichir(); }
    @Override public void equipesModifiees() { rafraichir(); }
}
