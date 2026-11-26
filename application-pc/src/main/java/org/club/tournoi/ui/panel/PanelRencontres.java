package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.EtatEquipe;
import org.club.tournoi.model.Rencontre;
import org.club.tournoi.model.Table;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Lancement des rencontres et saisie des scores.
 * <p>
 * A gauche : le formulaire de lancement plus les listes d'equipes en attente et de tables
 * libres. A droite : les rencontres en cours (double-clic pour saisir les scores). Toute la
 * logique (verifications, effets de bord) est dans le service ; ce panneau ne fait que
 * collecter la saisie et afficher. S'abonne au service pour se rafraichir.
 */
public class PanelRencontres extends JPanel implements TournoiListener {

    private final ServiceTournoi service;

    private final JComboBox<Equipe> comboA = new JComboBox<>();
    private final JComboBox<Equipe> comboB = new JComboBox<>();
    private final JComboBox<Table> comboTable = new JComboBox<>();
    private final JComboBox<Table> comboTable2 = new JComboBox<>();

    private final DefaultTableModel modeleEnCours;
    private final JTable tableEnCours;

    private final DefaultListModel<Equipe> modeleAttente = new DefaultListModel<>();
    private JList<Equipe> listeAttente;
    private final JLabel labelSuggestion = new JLabel();
    /** Derniere suggestion calculee, pour le bouton "Lancer la proposition". */
    private org.club.tournoi.service.MoteurSuisse.Suggestion derniereSuggestion;
    private JButton boutonDepartage;
    private JPanel cartes;
    private PanelDepartage panelDepartage;
    /** Boutons qui lancent de nouvelles rencontres : desactives en mode departage. */
    private final java.util.List<JButton> boutonsLancement = new java.util.ArrayList<>();
    private final DefaultListModel<Table> modeleTables = new DefaultListModel<>();

    public PanelRencontres(ServiceTournoi service) {
        this.service = service;
        service.ajouterListener(this);

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setBackground(Theme.FOND);
        setBorder(Theme.marge(Theme.ESPACE_M));

        // En-tete avec titre + bouton de bascule departage.
        JPanel entete = new JPanel(new BorderLayout());
        entete.setOpaque(false);
        entete.add(Composants.titre("Rencontres"), BorderLayout.WEST);
        boutonDepartage = Composants.boutonPrimaire("Preparer les departages");
        boutonDepartage.addActionListener(e -> basculerDepartage());
        entete.add(boutonDepartage, BorderLayout.EAST);
        add(entete, BorderLayout.NORTH);

        // --- Modele des rencontres en cours ---
        modeleEnCours = new DefaultTableModel(new Object[]{"Equipe A", "Equipe B", "Table", "Temps"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableEnCours = new JTable(modeleEnCours);
        Composants.styliser(tableEnCours);
        tableEnCours.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) saisirScore();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, colonneGauche(), colonneDroite());
        split.setResizeWeight(0.42);
        split.setBorder(null);
        split.setOpaque(false);

        // Cartes : vue normale (split) et vue departage.
        panelDepartage = new PanelDepartage(service);
        cartes = new JPanel(new CardLayout());
        cartes.setOpaque(false);
        cartes.add(split, "normal");
        cartes.add(panelDepartage, "departage");
        add(cartes, BorderLayout.CENTER);

        rafraichir();
        appliquerModeDepartage(); // reflete l'etat au demarrage (sauvegarde rechargee)

        // Timer d'affichage : met a jour la colonne "Temps" chaque seconde sans reconstruire la
        // table (preserve la selection en cours de l'utilisateur).
        javax.swing.Timer chrono = new javax.swing.Timer(1000, e -> majTemps());
        chrono.start();
    }

    /** Rafraichit uniquement la colonne Temps des rencontres en cours. */
    private void majTemps() {
        var enCours = service.getRencontresEnCours();
        long maintenant = System.currentTimeMillis();
        for (int i = 0; i < enCours.size() && i < modeleEnCours.getRowCount(); i++) {
            modeleEnCours.setValueAt(formaterDuree(enCours.get(i).secondesEcoulees(maintenant)), i, 3);
        }
        // Rafraichit aussi l'affichage des temps d'attente (le renderer recalcule a chaque peinture).
        if (listeAttente != null) {
            listeAttente.repaint();
        }
        // En mode departage, rafraichir le panneau si une rencontre normale OU un barrage tourne
        // (pour les chronos a gauche et le mini-tableau a droite).
        if (panelDepartage != null && service.estEnModeDepartage()) {
            boolean quelqueChoseTourne = !service.getRencontresEnCours().isEmpty()
                    || service.getBarrages().stream().anyMatch(b -> !b.estTermine());
            if (quelqueChoseTourne) {
                panelDepartage.rafraichir();
            }
        }
    }

    /** Section systeme suisse : proposer une rencontre, premier lancement, et affichage du conseil. */
    private JComponent sectionSuisse() {
        JPanel bloc = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        bloc.setBackground(Theme.SURFACE);
        bloc.setBorder(Theme.carte());
        bloc.setAlignmentX(Component.LEFT_ALIGNMENT);

        bloc.add(Composants.sousTitre("Systeme suisse"), BorderLayout.NORTH);

        labelSuggestion.setFont(Theme.CORPS);
        labelSuggestion.setForeground(Theme.TEXTE);
        labelSuggestion.setText("<html>Cliquez \u00ab Proposer une rencontre \u00bb pour obtenir un appariement.</html>");
        bloc.add(labelSuggestion, BorderLayout.CENTER);

        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        boutons.setOpaque(false);
        JButton proposer = Composants.boutonPrimaire("Proposer une rencontre");
        JButton lancerProp = Composants.boutonSecondaire("Lancer la proposition");
        JButton premier = Composants.boutonSecondaire("Premier lancement");
        boutonsLancement.add(proposer);
        boutonsLancement.add(lancerProp);
        boutonsLancement.add(premier);
        proposer.addActionListener(e -> proposer());
        lancerProp.addActionListener(e -> lancerProposition());
        premier.addActionListener(e -> premierLancement());
        boutons.add(proposer);
        boutons.add(lancerProp);
        boutons.add(premier);
        bloc.add(boutons, BorderLayout.SOUTH);

        bloc.setMaximumSize(new Dimension(Integer.MAX_VALUE, bloc.getPreferredSize().height + 40));
        return bloc;
    }

    /** Passage definitif en mode departage (aucun retour possible). */
    private void basculerDepartage() {
        if (service.estEnModeDepartage()) {
            return; // deja en mode departage, definitif
        }
        long enCours = service.getRencontresEnCours().size();
        String message = "Passer en mode departage ?\n\n"
                + "Cette action est definitive : vous ne pourrez plus lancer de nouvelle rencontre "
                + "normale (mais vous pourrez terminer celles en cours et saisir leurs scores).";
        if (enCours > 0) {
            message += "\n\n" + enCours + " rencontre(s) sont encore en cours ; vous pourrez les "
                    + "terminer et lancer les barrages possibles au fur et a mesure.";
        }
        int ok = JOptionPane.showConfirmDialog(this, message, "Passer en mode departage (definitif)",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            service.preparerDepartages();
            appliquerModeDepartage();
        }
    }

    private void appliquerModeDepartage() {
        boolean mode = service.estEnModeDepartage();
        CardLayout cl = (CardLayout) cartes.getLayout();
        cl.show(cartes, mode ? "departage" : "normal");
        if (mode) {
            // Passage definitif : le bouton disparait une fois active.
            boutonDepartage.setVisible(false);
            for (JButton b : boutonsLancement) {
                b.setEnabled(false);
                b.setToolTipText("Indisponible en mode departage.");
            }
            panelDepartage.rafraichir();
        }
    }

    private void montrerAdversaires(Equipe equipe) {
        var adversaires = service.adversairesDe(equipe);
        var enAttente = service.getEquipes().stream()
                .filter(e -> e.getEtat() == org.club.tournoi.model.EtatEquipe.EN_ATTENTE && !e.equals(equipe))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Equipe ").append(equipe.getNumero()).append("\n\n");
        if (adversaires.isEmpty()) {
            sb.append("N'a encore affronte aucune equipe.\n");
        } else {
            sb.append("Deja affrontee (").append(adversaires.size()).append(") : ");
            sb.append(joindreNumeros(adversaires)).append("\n");
        }
        // Adversaires encore possibles = equipes en attente jamais rencontrees.
        var possibles = enAttente.stream()
                .filter(e -> !service.dejaAffrontees(equipe, e))
                .toList();
        sb.append("\nAdversaires possibles parmi les equipes en attente : ");
        sb.append(possibles.isEmpty() ? "aucune pour l'instant." : joindreNumeros(possibles));

        JOptionPane.showMessageDialog(this, sb.toString(),
                "Historique - Equipe " + equipe.getNumero(), JOptionPane.INFORMATION_MESSAGE);
    }

    private String joindreNumeros(java.util.List<Equipe> equipes) {
        StringBuilder sb = new StringBuilder();
        for (Equipe e : equipes) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Equipe ").append(e.getNumero());
        }
        return sb.toString();
    }

    private void proposer() {
        var sug = service.suggererProchaineRencontre();
        derniereSuggestion = sug;
        if (sug.estLancable()) {
            String tables = sug.tables().size() == 2
                    ? "tables " + sug.tables().get(0).getNumero() + " et " + sug.tables().get(1).getNumero()
                    : "table " + sug.tables().get(0).getNumero();
            StringBuilder html = new StringBuilder("<html><b>Equipe ")
                    .append(sug.equipeA().getNumero()).append("</b> contre <b>Equipe ")
                    .append(sug.equipeB().getNumero()).append("</b><br>sur ").append(tables).append(".");
            if (sug.conseilAttente()) {
                html.append("<br><span style='color:#B8; '>\u26A0 ").append(sug.message()).append("</span>");
            }
            html.append("</html>");
            labelSuggestion.setText(html.toString());
        } else {
            labelSuggestion.setText("<html>" + sug.message() + "</html>");
        }
    }

    private void lancerProposition() {
        if (derniereSuggestion == null || !derniereSuggestion.estLancable()) {
            info("Proposez d'abord une rencontre (bouton \u00ab Proposer une rencontre \u00bb).");
            return;
        }
        try {
            service.lancerRencontre(derniereSuggestion.equipeA(), derniereSuggestion.equipeB(),
                    derniereSuggestion.tables());
            derniereSuggestion = null;
            labelSuggestion.setText("<html>Rencontre lancee. Cliquez \u00ab Proposer \u00bb pour la suivante.</html>");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            erreur(ex.getMessage());
        }
    }

    private void premierLancement() {
        if (!service.getRencontresEnCours().isEmpty()) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Des rencontres sont deja en cours. Lancer quand meme les premieres rencontres "
                    + "pour les equipes encore en attente ?", "Premier lancement",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        int lances = service.premierLancement();
        if (lances == 0) {
            info("Aucune rencontre lancee (il faut au moins deux equipes en attente et une table libre).");
        } else {
            labelSuggestion.setText("<html>" + lances + " rencontre(s) lancee(s).</html>");
        }
    }

    private JComponent colonneGauche() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        // --- Section systeme suisse ---
        col.add(sectionSuisse());
        col.add(Box.createVerticalStrut(Theme.ESPACE_M));

        // Formulaire
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.SURFACE);
        form.setBorder(Theme.carte());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Equipe A"), g);
        g.gridx = 1; g.weightx = 1; form.add(comboA, g); g.weightx = 0;
        g.gridx = 0; g.gridy = 1; form.add(new JLabel("Equipe B"), g);
        g.gridx = 1; g.weightx = 1; form.add(comboB, g); g.weightx = 0;
        g.gridx = 0; g.gridy = 2; form.add(new JLabel("Table"), g);
        g.gridx = 1; g.weightx = 1; form.add(comboTable, g); g.weightx = 0;
        g.gridx = 0; g.gridy = 3; form.add(new JLabel("Table 2 (facultatif)"), g);
        g.gridx = 1; g.weightx = 1; form.add(comboTable2, g); g.weightx = 0;
        // Affiche "— aucune —" pour l'option vide (null) de la 2e table.
        comboTable2.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) setText("\u2014 aucune \u2014");
                return this;
            }
        });

        JButton lancer = Composants.boutonPrimaire("Lancer la rencontre");
        boutonsLancement.add(lancer);
        lancer.addActionListener(e -> lancer());
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(lancer, g);

        JPanel formWrap = new JPanel(new BorderLayout());
        formWrap.setOpaque(false);
        formWrap.add(Composants.sousTitre("Nouvelle rencontre"), BorderLayout.NORTH);
        formWrap.add(form, BorderLayout.CENTER);
        formWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(formWrap);
        col.add(Box.createVerticalStrut(Theme.ESPACE_M));

        // Listes attente / tables
        listeAttente = new JList<>(modeleAttente);
        JList<Table> listeTables = new JList<>(modeleTables);
        listeAttente.setFont(Theme.CORPS);
        listeTables.setFont(Theme.CORPS);
        // Double-clic sur une equipe en attente : montre les equipes deja affrontees.
        listeAttente.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Equipe eq = listeAttente.getSelectedValue();
                    if (eq != null) montrerAdversaires(eq);
                }
            }
        });
        // Renderer : affiche "Equipe N — mm:ss" avec le temps d'attente courant.
        listeAttente.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Equipe eq) {
                    long depuis = eq.getEnAttenteDepuisMillis();
                    String t = depuis == 0 ? "--:--" : formaterDuree(Math.max(0, (System.currentTimeMillis() - depuis) / 1000));
                    setText("Equipe " + eq.getNumero() + "   " + t);
                }
                return this;
            }
        });
        listeAttente.setToolTipText("Double-cliquez une equipe pour voir ses adversaires deja rencontres");

        JPanel listes = new JPanel(new GridLayout(1, 2, Theme.ESPACE_M, 0));
        listes.setOpaque(false);
        listes.add(encadre("Equipes en attente", listeAttente));
        listes.add(encadre("Tables libres", listeTables));
        listes.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(listes);

        return col;
    }

    private JComponent encadre(String titre, JComponent contenu) {
        JPanel p = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        p.setOpaque(false);
        p.add(Composants.sousTitre(titre), BorderLayout.NORTH);
        JScrollPane s = new JScrollPane(contenu);
        s.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        s.setPreferredSize(new Dimension(180, 150));
        p.add(s, BorderLayout.CENTER);
        return p;
    }

    private JComponent colonneDroite() {
        JPanel col = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        col.setOpaque(false);
        col.add(Composants.sousTitre("Rencontres en cours  (double-clic pour saisir les scores)"), BorderLayout.NORTH);
        JScrollPane s = new JScrollPane(tableEnCours);
        s.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        col.add(s, BorderLayout.CENTER);

        JPanel barre = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, Theme.ESPACE_S));
        barre.setOpaque(false);
        JButton saisir = Composants.boutonSecondaire("Saisir les scores");
        JButton interrompre = Composants.boutonSecondaire("Interrompre la rencontre");
        saisir.addActionListener(e -> saisirScore());
        interrompre.addActionListener(e -> interrompre());
        barre.add(saisir);
        barre.add(interrompre);
        col.add(barre, BorderLayout.SOUTH);
        return col;
    }

    private void interrompre() {
        int r = tableEnCours.getSelectedRow();
        List<Rencontre> enCours = service.getRencontresEnCours();
        if (r < 0 || r >= enCours.size()) {
            info("Selectionnez d'abord une rencontre en cours dans le tableau.");
            return;
        }
        Rencontre rencontre = enCours.get(r);
        int ok = JOptionPane.showConfirmDialog(this,
                "Interrompre la rencontre Equipe " + rencontre.getEquipeA().getNumero()
                        + " contre Equipe " + rencontre.getEquipeB().getNumero() + " ?\n"
                        + "Aucun score ne sera enregistre, la table sera liberee et les deux equipes\n"
                        + "repasseront en attente. Elles pourront se reaffronter ensuite.",
                "Interrompre la rencontre", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            try {
                service.interrompreRencontre(rencontre);
            } catch (IllegalStateException ex) {
                erreur(ex.getMessage());
            }
        }
    }

    private void lancer() {
        Equipe a = (Equipe) comboA.getSelectedItem();
        Equipe b = (Equipe) comboB.getSelectedItem();
        Table t = (Table) comboTable.getSelectedItem();
        Table t2 = (Table) comboTable2.getSelectedItem();
        if (a == null || b == null || t == null) {
            info("Choisissez deux equipes et une table.");
            return;
        }
        if (t2 != null && t2.equals(t)) {
            info("La deuxieme table doit etre differente de la premiere.");
            return;
        }
        try {
            if (t2 != null) {
                service.lancerRencontre(a, b, java.util.List.of(t, t2));
            } else {
                service.lancerRencontre(a, b, t);
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            erreur(ex.getMessage());
        }
    }

    private void saisirScore() {
        int r = tableEnCours.getSelectedRow();
        List<Rencontre> enCours = service.getRencontresEnCours();
        if (r < 0 || r >= enCours.size()) return;
        Rencontre rencontre = enCours.get(r);
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        new DialogueScore(parent, service, rencontre).setVisible(true);
    }

    private void info(String m) {
        JOptionPane.showMessageDialog(this, m, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void erreur(String m) {
        JOptionPane.showMessageDialog(this, m, "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    private void rafraichir() {
        // Si le mode departage est actif, mettre a jour son affichage.
        if (panelDepartage != null && service.estEnModeDepartage()) {
            panelDepartage.rafraichir();
        }
        // Combos equipes en attente
        Equipe selA = (Equipe) comboA.getSelectedItem();
        Equipe selB = (Equipe) comboB.getSelectedItem();
        comboA.removeAllItems();
        comboB.removeAllItems();
        modeleAttente.clear();
        // Equipes en attente, triees : celle qui attend depuis le plus longtemps en tete.
        java.util.List<Equipe> attente = new java.util.ArrayList<>();
        for (Equipe e : service.getEquipes()) {
            if (e.getEtat() == EtatEquipe.EN_ATTENTE) {
                attente.add(e);
                comboA.addItem(e);
                comboB.addItem(e);
            }
        }
        attente.sort((e1, e2) -> {
            long t1 = e1.getEnAttenteDepuisMillis();
            long t2 = e2.getEnAttenteDepuisMillis();
            long ref1 = t1 == 0 ? Long.MIN_VALUE : t1; // jamais mis en attente = depuis le debut
            long ref2 = t2 == 0 ? Long.MIN_VALUE : t2;
            int c = Long.compare(ref1, ref2); // plus petit instant = attend depuis plus longtemps
            if (c != 0) return c;
            return Integer.compare(e1.getNumero(), e2.getNumero());
        });
        for (Equipe e : attente) {
            modeleAttente.addElement(e);
        }
        if (selA != null) comboA.setSelectedItem(selA);
        if (selB != null) comboB.setSelectedItem(selB);

        // Combo + liste tables libres
        Table selT = (Table) comboTable.getSelectedItem();
        Table selT2 = (Table) comboTable2.getSelectedItem();
        comboTable.removeAllItems();
        comboTable2.removeAllItems();
        comboTable2.addItem(null); // option "aucune 2e table"
        modeleTables.clear();
        for (Table t : service.getTables()) {
            if (t.estDisponible()) {
                comboTable.addItem(t);
                comboTable2.addItem(t);
                modeleTables.addElement(t);
            }
        }
        if (selT != null) comboTable.setSelectedItem(selT);
        if (selT2 != null) comboTable2.setSelectedItem(selT2);

        // Rencontres en cours
        modeleEnCours.setRowCount(0);
        long maintenant = System.currentTimeMillis();
        for (Rencontre rc : service.getRencontresEnCours()) {
            String tables = "Table " + rc.getNumeroTable()
                    + (rc.utiliseDeuxTables() ? " + " + rc.getNumeroTable2() : "");
            modeleEnCours.addRow(new Object[]{
                    "Equipe " + rc.getEquipeA().getNumero(),
                    "Equipe " + rc.getEquipeB().getNumero(),
                    tables,
                    formaterDuree(rc.secondesEcoulees(maintenant))
            });
        }
    }

    /** Formate une duree en secondes sous forme mm:ss. */
    private String formaterDuree(long secondes) {
        long m = secondes / 60;
        long s = secondes % 60;
        return String.format("%d:%02d", m, s);
    }

    @Override public void equipesModifiees()    { rafraichir(); }
    @Override public void tablesModifiees()      { rafraichir(); }
    @Override public void rencontresModifiees()  { rafraichir(); }
}
