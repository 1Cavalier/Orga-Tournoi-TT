package org.club.tournoi.ui.theme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Fabrique de composants Swing pre-stylises selon le {@link Theme}.
 * <p>
 * Regroupe ici le look des widgets recurrents (boutons, titres, tableaux) pour ne pas
 * repeter le meme code de style dans chaque panneau.
 */
public final class Composants {

    private Composants() { }

    /** Bouton d'action principal (accent balle). A reserver a l'action dominante d'un ecran. */
    public static JButton boutonPrimaire(String texte) {
        JButton b = new JButton(texte);
        b.setFont(Theme.CORPS_GRAS);
        b.setForeground(Color.WHITE);
        b.setBackground(Theme.ACCENT);
        b.setFocusPainted(false);
        // Sous le Look&Feel Windows, un bouton ne peint son fond que si on desactive
        // le remplissage fourni par le L&F et qu'on le rend opaque nous-memes.
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(Theme.ESPACE_S, Theme.ESPACE_M, Theme.ESPACE_S, Theme.ESPACE_M));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.getModel().addChangeListener(e -> {
            ButtonModel m = b.getModel();
            b.setBackground(m.isRollover() ? Theme.ACCENT_SURVOL : Theme.ACCENT);
        });
        return b;
    }

    /** Bouton secondaire (contour vert table), pour les actions non dominantes. */
    public static JButton boutonSecondaire(String texte) {
        JButton b = new JButton(texte);
        b.setFont(Theme.CORPS);
        b.setForeground(Theme.VERT_TABLE);
        b.setBackground(Theme.SURFACE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.VERT_TABLE, 1, true),
                BorderFactory.createEmptyBorder(Theme.ESPACE_S, Theme.ESPACE_M, Theme.ESPACE_S, Theme.ESPACE_M)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JLabel titre(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.TITRE);
        l.setForeground(Theme.TEXTE);
        return l;
    }

    public static JLabel sousTitre(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.SOUS_TITRE);
        l.setForeground(Theme.VERT_TABLE);
        return l;
    }

    /** Applique le style de tableau du theme (alternance de lignes, entete vert, selection accent). */
    public static void styliser(JTable table) {
        table.setFont(Theme.CORPS);
        table.setForeground(Theme.TEXTE);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setGridColor(Theme.BORDURE);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXTE);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader entete = table.getTableHeader();
        entete.setReorderingAllowed(false);
        entete.setPreferredSize(new Dimension(entete.getWidth(), 34));
        // Le L&F Windows peint l'entete avec son propre renderer et ignore setBackground.
        // On impose donc notre propre renderer d'entete (fond vert, texte blanc).
        entete.setDefaultRenderer(new RendererEntete());

        table.setDefaultRenderer(Object.class, new RendererAlterne());
    }

    /** Renderer d'entete impose (le L&F systeme ne respecte pas les couleurs sinon). */
    public static class RendererEntete extends DefaultTableCellRenderer {
        RendererEntete() {
            setHorizontalAlignment(SwingConstants.LEFT);
            setOpaque(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(Theme.CORPS_GRAS);
            setBackground(Theme.VERT_TABLE);
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.VERT_TABLE_CLAIR),
                    BorderFactory.createEmptyBorder(Theme.ESPACE_XS, Theme.ESPACE_S, Theme.ESPACE_XS, Theme.ESPACE_S)));
            return this;
        }
    }

    /** Renderer qui alterne la couleur des lignes pour la lisibilite. */
    public static class RendererAlterne extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Theme.LIGNE_IMPAIRE : Theme.LIGNE_PAIRE);
                c.setForeground(Theme.TEXTE);
            }
            setBorder(BorderFactory.createEmptyBorder(0, Theme.ESPACE_S, 0, Theme.ESPACE_S));
            return c;
        }
    }
}
