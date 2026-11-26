package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Joueur;
import org.club.tournoi.service.CompositionAutomatique;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Apercu d'une proposition de composition automatique avant de l'appliquer.
 * <p>
 * Montre les equipes proposees (avec l'ordre des postes) et les eventuelles alertes. Pour le
 * mode aleatoire, un bouton "Relancer" recalcule une nouvelle proposition : on peut re-melanger
 * autant de fois qu'on veut jusqu'a en obtenir une qui convient, puis "Valider". Le mode rang 1
 * etant deterministe (memes joueurs -> meme resultat), le bouton Relancer y est masque.
 */
public class DialogueApercuComposition extends JDialog {

    private final ServiceTournoi service;
    private final CompositionAutomatique.Mode mode;
    private CompositionAutomatique.Proposition proposition;
    private boolean valide = false;

    private final JPanel corps = new JPanel();

    public DialogueApercuComposition(Frame parent, ServiceTournoi service,
            CompositionAutomatique.Mode mode, CompositionAutomatique.Proposition proposition) {
        super(parent, "Apercu de la composition", true);
        this.service = service;
        this.mode = mode;
        this.proposition = proposition;

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        ((JComponent) getContentPane()).setBorder(Theme.marge(Theme.ESPACE_M));
        getContentPane().setBackground(Theme.FOND);

        String titre = mode == CompositionAutomatique.Mode.RANG_1
                ? "Composition par rang 1" : "Composition aleatoire";
        add(Composants.titre(titre), BorderLayout.NORTH);

        corps.setLayout(new BoxLayout(corps, BoxLayout.Y_AXIS));
        corps.setBackground(Theme.FOND);
        JScrollPane scroll = new JScrollPane(corps);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(560, 420));
        add(scroll, BorderLayout.CENTER);

        add(boutons(), BorderLayout.SOUTH);

        rendre();
        pack();
        setLocationRelativeTo(parent);
    }

    private JComponent boutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_S, 0));
        p.setOpaque(false);

        JButton annuler = Composants.boutonSecondaire("Annuler");
        annuler.addActionListener(e -> { valide = false; dispose(); });
        p.add(annuler);

        // Relancer : seulement pour le mode aleatoire (le rang 1 est deterministe).
        if (mode == CompositionAutomatique.Mode.ALEATOIRE) {
            JButton relancer = Composants.boutonSecondaire("Relancer");
            relancer.addActionListener(e -> {
                proposition = service.proposerComposition(mode);
                rendre();
            });
            p.add(relancer);
        }

        JButton valider = Composants.boutonPrimaire("Valider cette composition");
        valider.addActionListener(e -> { valide = true; dispose(); });
        p.add(valider);
        return p;
    }

    private void rendre() {
        corps.removeAll();

        // Alertes eventuelles en tete.
        if (!proposition.alertes().isEmpty()) {
            JPanel bloc = new JPanel();
            bloc.setLayout(new BoxLayout(bloc, BoxLayout.Y_AXIS));
            bloc.setBackground(new Color(0xFC, 0xF3, 0xE6));
            bloc.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.ACCENT, 1, true),
                    Theme.marge(Theme.ESPACE_S)));
            bloc.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (String alerte : proposition.alertes()) {
                JLabel l = new JLabel("<html>&#9888; " + echapper(alerte) + "</html>");
                l.setFont(Theme.PETIT);
                l.setForeground(new Color(0x8A, 0x4B, 0x08));
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                bloc.add(l);
            }
            bloc.setMaximumSize(new Dimension(Integer.MAX_VALUE, bloc.getPreferredSize().height));
            corps.add(bloc);
            corps.add(Box.createVerticalStrut(Theme.ESPACE_M));
        }

        int numero = 1;
        for (List<Joueur> equipe : proposition.equipes()) {
            corps.add(carteEquipe(numero++, equipe));
            corps.add(Box.createVerticalStrut(Theme.ESPACE_S));
        }

        JLabel resume = new JLabel(proposition.equipes().size() + " equipe(s), "
                + proposition.equipes().stream().mapToInt(List::size).sum() + " joueur(s) places.");
        resume.setFont(Theme.PETIT);
        resume.setForeground(Theme.TEXTE_DISCRET);
        resume.setAlignmentX(Component.LEFT_ALIGNMENT);
        corps.add(resume);

        corps.add(Box.createVerticalGlue());
        corps.revalidate();
        corps.repaint();
    }

    private JComponent carteEquipe(int numero, List<Joueur> equipe) {
        JPanel carte = new JPanel(new BorderLayout(0, Theme.ESPACE_XS));
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(Theme.carte());
        carte.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titre = new JLabel("Equipe " + numero + "  (" + equipe.size() + " joueurs)");
        titre.setFont(Theme.SOUS_TITRE);
        titre.setForeground(Theme.VERT_TABLE);
        carte.add(titre, BorderLayout.NORTH);

        JPanel liste = new JPanel();
        liste.setOpaque(false);
        liste.setLayout(new BoxLayout(liste, BoxLayout.Y_AXIS));
        int poste = 1;
        for (Joueur j : equipe) {
            String sym = j.getSymbole().estDefini() ? "  " + j.getSymbole().getCaractere() : "";
            JLabel l = new JLabel(poste++ + ". " + j.getNomComplet()
                    + "  [" + j.getCategorie().getLibelle() + "]" + sym);
            l.setFont(Theme.CORPS);
            l.setForeground(Theme.TEXTE);
            liste.add(l);
        }
        carte.add(liste, BorderLayout.CENTER);
        carte.setMaximumSize(new Dimension(Integer.MAX_VALUE, carte.getPreferredSize().height));
        return carte;
    }

    private String echapper(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean estValide() {
        return valide;
    }

    public CompositionAutomatique.Proposition getProposition() {
        return proposition;
    }
}
