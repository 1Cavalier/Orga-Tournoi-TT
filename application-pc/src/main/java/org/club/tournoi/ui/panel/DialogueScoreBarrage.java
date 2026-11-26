package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Barrage;
import org.club.tournoi.model.Match;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Saisie des scores d'un match de departage (barrage). Meme principe que le dialogue de score
 * d'une rencontre : les 5 matchs avec les noms des joueurs de chaque cote.
 */
public class DialogueScoreBarrage extends JDialog {

    private final Barrage barrage;
    private final JTextField[] champsA;
    private final JTextField[] champsB;
    private boolean valide = false;

    public DialogueScoreBarrage(Frame parent, Barrage barrage) {
        super(parent, "Score du departage", true);
        this.barrage = barrage;
        int n = barrage.getMatchs().size();
        champsA = new JTextField[n];
        champsB = new JTextField[n];

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        ((JComponent) getContentPane()).setBorder(Theme.marge(Theme.ESPACE_M));
        getContentPane().setBackground(Theme.FOND);

        add(Composants.titre("Departage : positions " + barrage.getPositionHaute()
                + "-" + barrage.getPositionBasse()), BorderLayout.NORTH);
        add(grille(), BorderLayout.CENTER);
        add(boutons(), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    private JComponent grille() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.FOND);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_XS, Theme.ESPACE_S, Theme.ESPACE_XS, Theme.ESPACE_S);

        int eqA = barrage.getEquipeA().getNumero();
        int eqB = barrage.getEquipeB().getNumero();
        g.gridy = 0;
        g.gridx = 0; g.anchor = GridBagConstraints.EAST;
        p.add(entete("Equipe " + eqA), g);
        g.gridx = 4; g.anchor = GridBagConstraints.WEST;
        p.add(entete("Equipe " + eqB), g);

        List<Match> matchs = barrage.getMatchs();
        for (int i = 0; i < matchs.size(); i++) {
            Match m = matchs.get(i);
            g.gridy = i + 1;
            g.gridx = 0; g.anchor = GridBagConstraints.EAST;
            p.add(noms(m, Match.Cote.A), g);
            champsA[i] = champ();
            champsB[i] = champ();
            g.anchor = GridBagConstraints.CENTER;
            g.gridx = 1; p.add(champsA[i], g);
            g.gridx = 2; JLabel t = new JLabel("-"); t.setForeground(Theme.TEXTE_DISCRET); p.add(t, g);
            g.gridx = 3; p.add(champsB[i], g);
            g.gridx = 4; g.anchor = GridBagConstraints.WEST;
            p.add(noms(m, Match.Cote.B), g);
        }
        return p;
    }

    private JLabel noms(Match m, Match.Cote cote) {
        var joueurs = m.getJoueurs(cote);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < joueurs.size(); k++) {
            if (k > 0) sb.append(" / ");
            sb.append(joueurs.get(k).getNomComplet());
        }
        JLabel l = new JLabel(sb.length() > 0 ? sb.toString() : "(a definir)");
        l.setFont(joueurs.size() > 1 ? Theme.CORPS : Theme.CORPS_GRAS);
        l.setForeground(joueurs.size() == 1
                ? Theme.couleurNiveau(joueurs.get(0).getCategorie().getNiveau()) : Theme.TEXTE);
        return l;
    }

    private JLabel entete(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.CORPS_GRAS);
        l.setForeground(Theme.VERT_TABLE);
        return l;
    }

    private JTextField champ() {
        JTextField c = new JTextField(3);
        c.setHorizontalAlignment(SwingConstants.CENTER);
        c.setFont(Theme.CORPS);
        return c;
    }

    private JComponent boutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_S, 0));
        p.setOpaque(false);
        JButton annuler = Composants.boutonSecondaire("Annuler");
        annuler.addActionListener(e -> { valide = false; dispose(); });
        JButton valider = Composants.boutonPrimaire("Valider");
        valider.addActionListener(e -> {
            if (lire()) { valide = true; dispose(); }
        });
        p.add(annuler);
        p.add(valider);
        return p;
    }

    private boolean lire() {
        try {
            for (int i = 0; i < champsA.length; i++) {
                Integer.parseInt(champsA[i].getText().trim());
                Integer.parseInt(champsB[i].getText().trim());
            }
            return true;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Saisissez tous les scores (nombres entiers).",
                    "Scores incomplets", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    public boolean estValide() { return valide; }

    public int[] getScoresA() {
        int[] s = new int[champsA.length];
        for (int i = 0; i < s.length; i++) s[i] = Integer.parseInt(champsA[i].getText().trim());
        return s;
    }

    public int[] getScoresB() {
        int[] s = new int[champsB.length];
        for (int i = 0; i < s.length; i++) s[i] = Integer.parseInt(champsB[i].getText().trim());
        return s;
    }
}
