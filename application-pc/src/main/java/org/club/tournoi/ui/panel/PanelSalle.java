package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Table;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Vue "salle" : les tables disposees en grille. Vert = libre, orange = occupee, grise =
 * desactivee. Un clic sur une table libre/desactivee bascule son activation (utile si une
 * table est indisponible). S'abonne au service.
 */
public class PanelSalle extends JPanel implements TournoiListener {

    private final ServiceTournoi service;
    private final JPanel grille;
    private BufferedImage imageTable;

    private int colonnes() { return Math.max(1, service.getParametres().getColonnes()); }

    public PanelSalle(ServiceTournoi service, int colonnes) {
        this.service = service;
        service.ajouterListener(this);

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setBackground(Theme.FOND);
        setBorder(Theme.marge(Theme.ESPACE_M));

        add(Composants.titre("Salle"), BorderLayout.NORTH);

        chargerImage();

        grille = new JPanel(new GridLayout(0, colonnes(), Theme.ESPACE_M, Theme.ESPACE_M));
        grille.setBackground(Theme.FOND);
        JScrollPane s = new JScrollPane(grille);
        s.setBorder(null);
        s.getViewport().setBackground(Theme.FOND);
        add(s, BorderLayout.CENTER);

        JLabel legende = new JLabel("Vert : libre    Orange : occupee    Grise : indisponible    "
                + "(clic sur une table pour changer son statut : Active, Retiree, Fil rouge, Robot)");
        legende.setFont(Theme.PETIT);
        legende.setForeground(Theme.TEXTE_DISCRET);
        add(legende, BorderLayout.SOUTH);

        rafraichir();
    }

    private void chargerImage() {
        try (InputStream in = getClass().getResourceAsStream("/images/table.png")) {
            if (in != null) {
                imageTable = ImageIO.read(in);
            }
        } catch (Exception ignore) {
            imageTable = null; // on retombe sur un rendu dessine
        }
    }

    private void rafraichir() {
        grille.removeAll();
        // Met à jour le nombre de colonnes de la grille (il peut avoir changé).
        ((GridLayout) grille.getLayout()).setColumns(colonnes());
        // Disposition en serpentin vertical. Le coin de depart (parametre) determine ou se trouve
        // la table 1 : haut-gauche, haut-droite, bas-gauche ou bas-droite. On calcule le serpentin
        // "standard" (depart haut-gauche) puis on applique un miroir horizontal et/ou vertical.
        List<Table> tables = new ArrayList<>(service.getTables());
        int n = tables.size();
        int c = colonnes();
        int lignes = (int) Math.ceil(n / (double) c);

        String coin = service.getParametres().getCoinDepartTables();
        boolean miroirH = coin.contains("DROITE"); // depart a droite -> miroir horizontal
        boolean miroirV = coin.contains("BAS");    // depart en bas -> miroir vertical

        for (int r = 0; r < lignes; r++) {
            for (int col = 0; col < c; col++) {
                // Coordonnees "source" dans le serpentin standard, apres miroir.
                int rr = miroirV ? (lignes - 1 - r) : r;
                int cc = miroirH ? (c - 1 - col) : col;
                int index;
                if (cc % 2 == 0) {
                    index = cc * lignes + rr;              // colonne paire : de haut en bas
                } else {
                    index = cc * lignes + (lignes - 1 - rr); // colonne impaire : de bas en haut
                }
                if (index >= 0 && index < n) {
                    grille.add(vignette(tables.get(index)));
                } else {
                    grille.add(caseVide());
                }
            }
        }
        grille.revalidate();
        grille.repaint();
    }

    /** Case vide pour combler la grille quand le nombre de tables n'est pas un multiple des colonnes. */
    private JComponent caseVide() {
        JPanel vide = new JPanel();
        vide.setOpaque(false);
        return vide;
    }

    private JComponent vignette(Table table) {
        JPanel carte = new JPanel(new BorderLayout(0, Theme.ESPACE_XS));
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(Theme.carte());
        carte.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titre = new JLabel("Table " + table.getNumero(), SwingConstants.CENTER);
        titre.setFont(Theme.CORPS_GRAS);
        titre.setForeground(Theme.TEXTE);
        carte.add(titre, BorderLayout.NORTH);

        JComponent visuel = visuelTable(table);
        carte.add(visuel, BorderLayout.CENTER);

        String etat;
        if (table.isOccupee()) {
            etat = "Occupee";
        } else if (table.getStatut().estJouable()) {
            etat = "Libre";
        } else {
            etat = table.getStatut().getLibelle();
        }
        JLabel statut = new JLabel(etat, SwingConstants.CENTER);
        statut.setFont(Theme.PETIT);
        statut.setForeground(couleurEtat(table));
        carte.add(statut, BorderLayout.SOUTH);

        carte.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                choisirStatut(table);
            }
        });
        return carte;
    }

    /** Petit menu pour choisir le statut d'une table (Active, Retiree, Fil rouge, Robot). */
    private void choisirStatut(Table table) {
        if (table.isOccupee()) {
            JOptionPane.showMessageDialog(this,
                    "Cette table est occupee par une rencontre en cours.",
                    "Table occupee", JOptionPane.WARNING_MESSAGE);
            return;
        }
        org.club.tournoi.model.StatutTable[] choix = org.club.tournoi.model.StatutTable.values();
        org.club.tournoi.model.StatutTable choisi = (org.club.tournoi.model.StatutTable)
                JOptionPane.showInputDialog(this,
                        "Statut de la table " + table.getNumero() + " :",
                        "Statut de table", JOptionPane.PLAIN_MESSAGE, null,
                        choix, table.getStatut());
        if (choisi != null) {
            try {
                service.changerStatutTable(table, choisi);
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Impossible", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private Color couleurEtat(Table t) {
        if (!t.getStatut().estJouable()) return Theme.TEXTE_DISCRET;
        return t.isOccupee() ? Theme.ACCENT : Theme.VERT_TABLE;
    }

    private JComponent visuelTable(Table table) {
        int w = 120, h = 70;
        if (imageTable != null) {
            Image img = imageTable.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            if (!table.isActive()) {
                img = GrayFilter.createDisabledImage(img);
            }
            return new JLabel(new ImageIcon(img));
        }
        // Rendu dessine si pas d'image : rectangle colore selon l'etat
        JPanel dessin = new JPanel();
        dessin.setPreferredSize(new Dimension(w, h));
        dessin.setBackground(couleurEtat(table));
        return dessin;
    }

    @Override public void tablesModifiees()     { rafraichir(); }
    @Override public void rencontresModifiees() { rafraichir(); }
}
