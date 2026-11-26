package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.service.Classement;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.StatistiquesEquipe;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Classement (vue admin), trie selon la regle du club : rencontres gagnees, puis matchs
 * gagnes, puis points gagnes. Colonnes affichees : position, numero d'equipe, noms des
 * joueurs (empiles), rencontres gagnees, matchs gagnes, points gagnes, et le total de
 * matchs joues (colonne propre a la vue admin). S'abonne au service et se recalcule seul.
 */
public class PanelClassement extends JPanel implements TournoiListener {

    private final ServiceTournoi service;
    private final DefaultTableModel modele;
    private final JTable table;

    private static final int COL_JOUEURS = 2;

    public PanelClassement(ServiceTournoi service) {
        this.service = service;
        service.ajouterListener(this);

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setBackground(Theme.FOND);
        setBorder(Theme.marge(Theme.ESPACE_M));

        add(Composants.titre("Classement"), BorderLayout.NORTH);

        modele = new DefaultTableModel(new Object[]{
                "Position", "Equipe", "Joueurs",
                "Rencontres gagnees", "Matchs gagnes", "Points gagnes", "Matchs joues"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(modele);
        Composants.styliser(table);
        table.setRowHeight(88); // place pour 4 noms empiles

        // Colonne des joueurs : rendu multiligne (un nom par ligne).
        table.getColumnModel().getColumn(COL_JOUEURS).setCellRenderer(new RendererJoueurs());
        // Colonnes numeriques centrees.
        for (int c : new int[]{0, 3, 4, 5, 6}) {
            var r = new Composants.RendererAlterne();
            r.setHorizontalAlignment(SwingConstants.CENTER);
            table.getColumnModel().getColumn(c).setCellRenderer(r);
        }
        // Largeurs indicatives.
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(COL_JOUEURS).setPreferredWidth(240);

        JScrollPane s = new JScrollPane(table);
        s.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        add(s, BorderLayout.CENTER);

        rafraichir();
    }

    private void rafraichir() {
        modele.setRowCount(0);
        for (Classement.Ligne ligne : service.classement()) {
            StatistiquesEquipe st = ligne.stats();
            modele.addRow(new Object[]{
                    ligne.rang(),
                    "Equipe " + ligne.equipe().getNumero(),
                    nomsJoueurs(ligne.equipe()),
                    st.rencontresGagnees(),
                    st.matchsGagnes(),
                    st.pointsPour(),
                    st.matchsJoues()
            });
        }
    }

    /** Noms des joueurs de l'equipe, un par ligne (ou un tiret si l'equipe est vide). */
    private String nomsJoueurs(Equipe equipe) {
        if (equipe.getJoueurs().isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Joueur j : equipe.getJoueurs()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(j.getNomComplet());
        }
        return sb.toString();
    }

    @Override public void classementModifie() { rafraichir(); }
    @Override public void equipesModifiees()  { rafraichir(); }
    @Override public void joueursModifies()   { rafraichir(); }

    /** Affiche plusieurs noms empiles dans une cellule, avec l'alternance de couleurs du theme. */
    private static class RendererJoueurs extends JTextArea
            implements javax.swing.table.TableCellRenderer {
        RendererJoueurs() {
            setOpaque(true);
            setFont(Theme.CORPS);
            setBorder(BorderFactory.createEmptyBorder(Theme.ESPACE_XS, Theme.ESPACE_S, Theme.ESPACE_XS, Theme.ESPACE_S));
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean focus, int row, int col) {
            setText(v == null ? "" : v.toString());
            setBackground(sel ? Theme.SELECTION : (row % 2 == 0 ? Theme.LIGNE_IMPAIRE : Theme.LIGNE_PAIRE));
            setForeground(Theme.TEXTE);
            return this;
        }
    }
}
