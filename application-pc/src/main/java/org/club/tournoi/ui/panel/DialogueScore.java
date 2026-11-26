package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Match;
import org.club.tournoi.model.Rencontre;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Dialogue de saisie des 5 scores d'une rencontre.
 * <p>
 * Ne contient que la presentation et la collecte : la validation et l'enregistrement sont
 * delegues a {@link ServiceTournoi#enregistrerScores}. Si un score est invalide, le service
 * leve une exception et rien n'est applique (tout ou rien), ce qui evite les rencontres a
 * moitie enregistrees de l'ancienne version.
 */
public class DialogueScore extends JDialog {

    private final ServiceTournoi service;
    private final Rencontre rencontre;
    private final JTextField[] champsA;
    private final JTextField[] champsB;

    public DialogueScore(Frame parent, ServiceTournoi service, Rencontre rencontre) {
        super(parent, "Scores - Equipe " + rencontre.getEquipeA().getNumero()
                + " contre Equipe " + rencontre.getEquipeB().getNumero(), true);
        this.service = service;
        this.rencontre = rencontre;
        this.champsA = new JTextField[Rencontre.NB_MATCHS];
        this.champsB = new JTextField[Rencontre.NB_MATCHS];

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        ((JComponent) getContentPane()).setBorder(Theme.marge(Theme.ESPACE_M));

        add(grille(), BorderLayout.CENTER);
        add(boutons(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    private JComponent grille() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.SURFACE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_XS, Theme.ESPACE_S, Theme.ESPACE_XS, Theme.ESPACE_S);

        int eqA = rencontre.getEquipeA().getNumero();
        int eqB = rencontre.getEquipeB().getNumero();

        g.gridy = 0;
        g.gridx = 0; g.anchor = GridBagConstraints.EAST; p.add(entete("Equipe " + eqA), g);
        g.gridx = 1; g.anchor = GridBagConstraints.CENTER; p.add(entete(""), g);
        g.gridx = 2; p.add(entete(""), g);
        g.gridx = 3; p.add(entete(""), g);
        g.gridx = 4; g.anchor = GridBagConstraints.WEST; p.add(entete("Equipe " + eqB), g);

        var matchs = rencontre.getMatchs();
        for (int i = 0; i < matchs.size(); i++) {
            Match m = matchs.get(i);
            g.gridy = i + 1;

            // Nom(s) du cote A, aligne a droite (colle au champ de score).
            g.gridx = 0; g.anchor = GridBagConstraints.EAST;
            p.add(labelJoueurs(m, Match.Cote.A), g);

            champsA[i] = champScore();
            champsB[i] = champScore();
            g.anchor = GridBagConstraints.CENTER;
            g.gridx = 1; p.add(champsA[i], g);
            g.gridx = 2; JLabel vs = new JLabel("-"); vs.setForeground(Theme.TEXTE_DISCRET); p.add(vs, g);
            g.gridx = 3; p.add(champsB[i], g);

            // Nom(s) du cote B, aligne a gauche.
            g.gridx = 4; g.anchor = GridBagConstraints.WEST;
            p.add(labelJoueurs(m, Match.Cote.B), g);
        }
        return p;
    }

    /** Libelle des joueurs d'un cote pour un match : "Nom Prenom", ou deux noms pour un double. */
    private JComponent labelJoueurs(Match match, Match.Cote cote) {
        var joueurs = match.getJoueurs(cote);
        String texte;
        if (joueurs.isEmpty()) {
            texte = "(a definir)";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < joueurs.size(); k++) {
                if (k > 0) sb.append(" / ");
                sb.append(joueurs.get(k).getNomComplet());
            }
            texte = sb.toString();
        }
        JLabel l = new JLabel(texte);
        l.setFont(joueurs.size() > 1 ? Theme.CORPS : Theme.CORPS_GRAS);
        // Couleur de niveau si un seul joueur (simple) ; texte normal pour un double (2 niveaux).
        if (joueurs.size() == 1) {
            l.setForeground(Theme.couleurNiveau(joueurs.get(0).getCategorie().getNiveau()));
        } else {
            l.setForeground(Theme.TEXTE);
        }
        return l;
    }

    private JLabel entete(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.CORPS_GRAS);
        l.setForeground(Theme.VERT_TABLE);
        return l;
    }

    private JTextField champScore() {
        JTextField f = new JTextField(3);
        f.setHorizontalAlignment(SwingConstants.CENTER);
        f.setFont(Theme.DONNEE);
        return f;
    }

    private JComponent boutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.setOpaque(false);
        JButton valider = Composants.boutonPrimaire("Valider la rencontre");
        JButton annuler = Composants.boutonSecondaire("Annuler");
        valider.addActionListener(e -> valider());
        annuler.addActionListener(e -> dispose());
        p.add(annuler);
        p.add(valider);
        return p;
    }

    private void valider() {
        int n = Rencontre.NB_MATCHS;
        int[] scoresA = new int[n];
        int[] scoresB = new int[n];
        try {
            for (int i = 0; i < n; i++) {
                scoresA[i] = Integer.parseInt(champsA[i].getText().trim());
                scoresB[i] = Integer.parseInt(champsB[i].getText().trim());
            }
        } catch (NumberFormatException ex) {
            erreur("Chaque case doit contenir un score entier.");
            return;
        }

        try {
            service.enregistrerScores(rencontre, scoresA, scoresB);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            erreur(ex.getMessage());
            return;
        }
        dispose();
    }

    private void erreur(String m) {
        JOptionPane.showMessageDialog(this, m, "Score invalide", JOptionPane.ERROR_MESSAGE);
    }
}
