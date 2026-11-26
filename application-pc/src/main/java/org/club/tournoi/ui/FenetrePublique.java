package org.club.tournoi.ui;

import org.club.tournoi.model.EtatEquipe;
import org.club.tournoi.model.Rencontre;
import org.club.tournoi.model.Table;
import org.club.tournoi.service.Classement;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.StatistiquesEquipe;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * Fenetre d'affichage public, a poser sur un second ecran (projecteur) pour les joueurs.
 * <p>
 * Mise en page : le classement occupe les deux tiers gauche, la liste des tables le tiers
 * droit. Tout est en gros caracteres pour rester lisible de loin. La fenetre s'abonne au
 * service : elle se met a jour automatiquement des qu'une rencontre est lancee ou terminee,
 * sans manipulation.
 * <p>
 * C'est une fenetre autonome (elle ne perturbe pas la fenetre principale de gestion) : on la
 * bascule en plein ecran d'un raccourci, et on la ferme quand on veut.
 */
public class FenetrePublique extends JFrame implements TournoiListener {

    private final ServiceTournoi service;
    private final JPanel corpsClassement = new JPanel();
    private final JPanel corpsTables = new JPanel();
    /** Nombre de premieres places a camoufler (flouter) ; 0 = aucun. */
    private int camouflerTopN = 0;
    private JTable tableClassement;
    private JScrollPane scrollClassement;
    private JScrollPane scrollTables;
    private javax.swing.Timer timerDefilement;
    private boolean defilementActif = false;

    public FenetrePublique(ServiceTournoi service) {
        super("Affichage public - Tournoi");
        this.service = service;
        service.ajouterListener(this);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.FOND);
        setLayout(new BorderLayout());

        add(barreOutils(), BorderLayout.NORTH);
        add(contenu(), BorderLayout.CENTER);

        // Retire l'abonnement quand la fenetre se ferme (evite de garder un observateur mort).
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                FenetrePublique.this.service.retirerListener(FenetrePublique.this);
            }
        });

        setSize(1280, 800);
        setLocationRelativeTo(null);
        rafraichir();
    }

    private JComponent barreOutils() {
        JPanel barre = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_S, Theme.ESPACE_S));
        barre.setBackground(Theme.FOND);
        JButton pleinEcran = Composants.boutonSecondaire("Plein ecran (F11)");
        pleinEcran.addActionListener(e -> basculerPleinEcran());

        JButton camoufler = Composants.boutonSecondaire("Camoufler le top...");
        camoufler.addActionListener(e -> choisirCamouflage());

        JButton defiler = Composants.boutonSecondaire("Defilement auto");
        defiler.addActionListener(e -> basculerDefilement());

        barre.add(camoufler);
        barre.add(defiler);
        barre.add(pleinEcran);

        // Raccourci clavier F11 pour basculer le plein ecran, Echap pour en sortir.
        JRootPane root = getRootPane();
        root.registerKeyboardAction(e -> basculerPleinEcran(),
                KeyStroke.getKeyStroke("F11"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> sortirPleinEcran(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return barre;
    }

    private JComponent contenu() {
        // Classement (2/3) a gauche, tables (1/3) a droite, proportions fixees par poids.
        corpsClassement.setLayout(new BorderLayout());
        corpsClassement.setBackground(Theme.FOND);
        corpsClassement.setBorder(Theme.marge(Theme.ESPACE_L));

        corpsTables.setLayout(new BoxLayout(corpsTables, BoxLayout.Y_AXIS));
        corpsTables.setBackground(Theme.FOND);

        JScrollPane scrollTables = new JScrollPane(corpsTables);
        this.scrollTables = scrollTables;
        scrollTables.setBorder(null);
        scrollTables.getViewport().setBackground(Theme.FOND);
        scrollTables.getVerticalScrollBar().setUnitIncrement(20);

        JPanel bloctables = new JPanel(new BorderLayout(0, Theme.ESPACE_M));
        bloctables.setBackground(Theme.FOND);
        bloctables.setBorder(Theme.marge(Theme.ESPACE_L));
        JLabel titreT = new JLabel("Tables");
        titreT.setFont(Theme.TITRE.deriveFont(28f));
        titreT.setForeground(Theme.VERT_TABLE);
        bloctables.add(titreT, BorderLayout.NORTH);
        bloctables.add(scrollTables, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, corpsClassement, bloctables);
        split.setResizeWeight(0.66);       // 2/3 pour le classement
        split.setDividerLocation(0.66);
        split.setEnabled(false);           // proportion figee (pas de poignee a bouger)
        split.setDividerSize(2);
        split.setBorder(null);
        return split;
    }

    // ------------------------------------------------------------------
    // Plein ecran
    // ------------------------------------------------------------------

    private void basculerPleinEcran() {
        GraphicsDevice ecran = getGraphicsConfiguration().getDevice();
        if (ecran.getFullScreenWindow() == this) {
            sortirPleinEcran();
        } else {
            dispose();
            setUndecorated(true);
            ecran.setFullScreenWindow(this);
            setVisible(true);
        }
    }

    private void sortirPleinEcran() {
        GraphicsDevice ecran = getGraphicsConfiguration().getDevice();
        if (ecran.getFullScreenWindow() == this) {
            ecran.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setVisible(true);
        }
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    private void rafraichir() {
        rendreClassement();
        rendreTables();
        revalidate();
        repaint();
    }

    private void rendreClassement() {
        corpsClassement.removeAll();

        JLabel titre = new JLabel("Classement");
        titre.setFont(Theme.TITRE.deriveFont(28f));
        titre.setForeground(Theme.TEXTE);
        corpsClassement.add(titre, BorderLayout.NORTH);

        String[] colonnes = {"Position", "Evol.", "Equipe", "Joueurs",
                "Rencontres gagnees", "Matchs gagnes", "Points gagnes"};
        javax.swing.table.DefaultTableModel modele =
                new javax.swing.table.DefaultTableModel(colonnes, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        // Equipes actuellement en departage (barrage) pour la couleur.
        java.util.Set<Integer> enBarrage = new java.util.HashSet<>();
        for (var b : service.getBarrages()) {
            if (!b.estTermine()) {
                enBarrage.add(b.getEquipeA().getNumero());
                enBarrage.add(b.getEquipeB().getNumero());
            }
        }
        // Equipes concernees par un departage a jouer (groupe a egalite non regle), avec la couleur
        // d'accent du groupe et les bornes (premiere/derniere equipe du groupe) pour l'encadrement.
        java.util.Set<Integer> enDepartage = new java.util.HashSet<>();
        java.util.Set<Integer> fixees = new java.util.HashSet<>();
        java.util.Map<Integer, Color> couleurGroupe = new java.util.HashMap<>();
        java.util.Set<Integer> hautGroupe = new java.util.HashSet<>();  // 1re equipe d'un groupe
        java.util.Set<Integer> basGroupe = new java.util.HashSet<>();   // derniere equipe d'un groupe
        if (service.estEnModeDepartage()) {
            java.util.Set<Integer> dansEgalite = new java.util.HashSet<>();
            int idx = 0;
            for (var plan : service.plansDepartage()) {
                Color acc = ACCENTS_GROUPE[idx % ACCENTS_GROUPE.length];
                idx++;
                boolean regle = plan.equipes().stream().allMatch(e -> service.positionFinale(e) > 0);
                var eqs = plan.equipes();
                for (int k = 0; k < eqs.size(); k++) {
                    int num = eqs.get(k).getNumero();
                    dansEgalite.add(num);
                    if (!regle && service.positionFinale(eqs.get(k)) < 0) {
                        enDepartage.add(num);
                        couleurGroupe.put(num, acc);
                        if (k == 0) hautGroupe.add(num);
                        if (k == eqs.size() - 1) basGroupe.add(num);
                    }
                }
            }
            for (var l : service.classement()) {
                int num = l.equipe().getNumero();
                boolean posFixee = service.positionFinale(l.equipe()) > 0;
                boolean horsEgalite = !dansEgalite.contains(num);
                if (posFixee || horsEgalite) fixees.add(num);
            }
        }

        for (Classement.Ligne ligne : service.classement()) {
            StatistiquesEquipe st = ligne.stats();
            int evol = service.variationRang(ligne.equipe());
            String fleche = evol > 0 ? "\u2191 " + evol : (evol < 0 ? "\u2193 " + evol : "=");
            modele.addRow(new Object[]{
                    ligne.rang(),
                    fleche,
                    "Equipe " + ligne.equipe().getNumero(),
                    nomsJoueurs(ligne.equipe()),
                    st.rencontresGagnees(),
                    st.matchsGagnes(),
                    st.pointsPour()
            });
        }

        int topCamoufle = camouflerTopN; // 0 = rien de camoufle
        JTable table = new JTable(modele) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        Composants.styliser(table);
        table.setFont(Theme.CORPS.deriveFont(20f));
        table.setRowHeight(110);
        table.getTableHeader().setFont(Theme.CORPS_GRAS.deriveFont(18f));

        EtatsDepartage etats = new EtatsDepartage(enBarrage, enDepartage, fixees, modele,
                couleurGroupe, hautGroupe, basGroupe);

        // Renderer joueurs (multiligne) avec camouflage et fond colore.
        table.getColumnModel().getColumn(3).setCellRenderer(
                new RendererJoueursPublic(topCamoufle, etats));
        table.getColumnModel().getColumn(3).setPreferredWidth(280);

        // Colonne evolution : fleche coloree + fond departage.
        table.getColumnModel().getColumn(1).setCellRenderer(new RendererEvolution(topCamoufle, etats));
        table.getColumnModel().getColumn(1).setPreferredWidth(70);

        // Colonnes numeriques + position : camouflage + fond.
        for (int c : new int[]{0, 4, 5, 6}) {
            table.getColumnModel().getColumn(c).setCellRenderer(new RendererPublic(topCamoufle, etats));
        }
        // Colonne Equipe : camouflee aussi si demande, sinon numero visible + fond.
        table.getColumnModel().getColumn(2).setCellRenderer(new RendererEquipe(topCamoufle, etats));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        corpsClassement.add(scroll, BorderLayout.CENTER);
        this.tableClassement = table;
        this.scrollClassement = scroll;
    }

    /** Noms des joueurs empiles (un par ligne). */
    private String nomsJoueurs(org.club.tournoi.model.Equipe equipe) {
        if (equipe.getJoueurs().isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (org.club.tournoi.model.Joueur j : equipe.getJoueurs()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(j.getNomComplet());
        }
        return sb.toString();
    }

    /** Les 6 couleurs d'accent par groupe, identiques a l'ecran admin. */
    private static final Color[] ACCENTS_GROUPE = {
            new Color(0x2E, 0x7D, 0x32), // vert
            new Color(0x15, 0x65, 0xC0), // bleu
            new Color(0xC6, 0x28, 0x28), // rouge
            new Color(0x6A, 0x1B, 0x9A), // violet
            new Color(0xE8, 0x7A, 0x2C), // orange
            new Color(0x00, 0x83, 0x8F), // cyan
    };

    /** Regroupe les infos d'etat des equipes pour colorer et encadrer le classement public. */
    private static class EtatsDepartage {
        final java.util.Set<Integer> enBarrage, enDepartage, fixees;
        final javax.swing.table.DefaultTableModel modele;
        final java.util.Map<Integer, Color> couleurGroupe;
        final java.util.Set<Integer> hautGroupe, basGroupe;
        EtatsDepartage(java.util.Set<Integer> b, java.util.Set<Integer> d, java.util.Set<Integer> f,
                       javax.swing.table.DefaultTableModel m, java.util.Map<Integer, Color> cg,
                       java.util.Set<Integer> hg, java.util.Set<Integer> bg) {
            enBarrage = b; enDepartage = d; fixees = f; modele = m;
            couleurGroupe = cg; hautGroupe = hg; basGroupe = bg;
        }
        Color fond(int row) {
            int num = numeroEquipe(modele, row);
            if (fixees.contains(num)) return new Color(0xDD, 0xF0, 0xDB); // vert : place definitive
            Color acc = couleurGroupe.get(num);
            if (acc != null) return teinteClaire(acc);                    // fond leger de la couleur du groupe
            if (enBarrage.contains(num)) return new Color(0xFB, 0xE0, 0xE0);
            return row % 2 == 0 ? Theme.LIGNE_IMPAIRE : Theme.LIGNE_PAIRE;
        }
        /** Bordure coloree encadrant le groupe : trait en haut de la 1re ligne, en bas de la derniere. */
        javax.swing.border.Border bordure(int row, boolean premiereColonne, boolean derniereColonne) {
            int num = numeroEquipe(modele, row);
            Color acc = couleurGroupe.get(num);
            if (acc == null) return BorderFactory.createEmptyBorder();
            int haut = hautGroupe.contains(num) ? 3 : 0;
            int bas = basGroupe.contains(num) ? 3 : 0;
            int gauche = premiereColonne ? 3 : 0;
            int droite = derniereColonne ? 3 : 0;
            return BorderFactory.createMatteBorder(haut, gauche, bas, droite, acc);
        }
        private static Color teinteClaire(Color c) {
            // Melange avec du blanc pour un fond pastel lisible derriere du texte.
            int r = (c.getRed() + 255 * 4) / 5;
            int g = (c.getGreen() + 255 * 4) / 5;
            int b = (c.getBlue() + 255 * 4) / 5;
            return new Color(r, g, b);
        }
    }

    /** Rendu multiligne des noms (gros caracteres), camouflable, avec fond colore. */
    private static class RendererJoueursPublic extends JTextArea
            implements javax.swing.table.TableCellRenderer {
        private final int topCamoufle;
        private final EtatsDepartage etats;
        RendererJoueursPublic(int topCamoufle, EtatsDepartage etats) {
            this.topCamoufle = topCamoufle; this.etats = etats;
            setOpaque(true);
            setFont(Theme.CORPS.deriveFont(18f));
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean focus, int row, int col) {
            boolean camoufle = row < topCamoufle;
            setText(camoufle ? "\u2022 \u2022 \u2022" : (v == null ? "" : v.toString()));
            setBackground(etats.fond(row));
            setForeground(camoufle ? new Color(0xBB, 0xBB, 0xBB) : Theme.TEXTE);
            setBorder(BorderFactory.createCompoundBorder(etats.bordure(row, false, false),
                    BorderFactory.createEmptyBorder(Theme.ESPACE_S, Theme.ESPACE_M, Theme.ESPACE_S, Theme.ESPACE_M)));
            return this;
        }
    }

    /** Colonne evolution : fleche verte (montee) / rouge (descente), fond colore, camouflable. */
    private static class RendererEvolution extends javax.swing.table.DefaultTableCellRenderer {
        private final int topCamoufle;
        private final EtatsDepartage etats;
        RendererEvolution(int topCamoufle, EtatsDepartage etats) {
            this.topCamoufle = topCamoufle; this.etats = etats;
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.CORPS_GRAS.deriveFont(22f));
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            boolean camoufle = row < topCamoufle;
            String s = v == null ? "=" : v.toString();
            if (camoufle) { setText("\u2022"); setForeground(new Color(0xBB, 0xBB, 0xBB)); }
            else {
                setText(s);
                if (s.startsWith("\u2191")) setForeground(new Color(0x2E, 0x7D, 0x32));
                else if (s.startsWith("\u2193")) setForeground(new Color(0xC6, 0x28, 0x28));
                else setForeground(Theme.TEXTE_DISCRET);
            }
            setBackground(etats.fond(row));
            setBorder(etats.bordure(row, false, false));
            return this;
        }
    }

    /** Colonnes numeriques : camouflage + fond departage. */
    private static class RendererPublic extends javax.swing.table.DefaultTableCellRenderer {
        private final int topCamoufle;
        private final EtatsDepartage etats;
        RendererPublic(int topCamoufle, EtatsDepartage etats) {
            this.topCamoufle = topCamoufle; this.etats = etats;
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.CORPS.deriveFont(22f));
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            boolean camoufle = row < topCamoufle;
            setText(camoufle ? "\u2022" : (v == null ? "" : v.toString()));
            setForeground(camoufle ? new Color(0xBB, 0xBB, 0xBB) : Theme.TEXTE);
            setBackground(etats.fond(row));
            // Colonne 0 = premiere (Position), colonne 6 = derniere (Points).
            setBorder(etats.bordure(row, col == 0, col == 6));
            return this;
        }
    }

    /** Colonne Equipe : camouflable (numero cache si demande) + fond colore. */
    private static class RendererEquipe extends javax.swing.table.DefaultTableCellRenderer {
        private final int topCamoufle;
        private final EtatsDepartage etats;
        RendererEquipe(int topCamoufle, EtatsDepartage etats) {
            this.topCamoufle = topCamoufle; this.etats = etats;
            setFont(Theme.CORPS_GRAS.deriveFont(20f));
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            boolean camoufle = row < topCamoufle;
            setText(camoufle ? "\u2022 \u2022 \u2022" : (v == null ? "" : v.toString()));
            setForeground(camoufle ? new Color(0xBB, 0xBB, 0xBB) : Theme.TEXTE);
            setBackground(etats.fond(row));
            setBorder(etats.bordure(row, false, false));
            return this;
        }
    }

    private static int numeroEquipe(javax.swing.table.DefaultTableModel modele, int row) {
        Object val = modele.getValueAt(row, 2);
        if (val instanceof String s && s.startsWith("Equipe ")) {
            try { return Integer.parseInt(s.substring(7).trim()); } catch (NumberFormatException ignore) {}
        }
        return -1;
    }

    private void choisirCamouflage() {
        String[] options = {"Aucun", "Top 1", "Top 2", "Top 3", "Top 4", "Top 5"};
        String choix = (String) JOptionPane.showInputDialog(this,
                "Camoufler (flouter) les premieres places pour garder le suspense :",
                "Camoufler le podium", JOptionPane.PLAIN_MESSAGE, null, options, options[camouflerTopN]);
        if (choix != null) {
            camouflerTopN = java.util.Arrays.asList(options).indexOf(choix);
            rafraichir();
        }
    }

    // ------------------------------------------------------------------
    // Defilement automatique lent (haut -> bas -> haut) avec pauses.
    // ------------------------------------------------------------------

    private void basculerDefilement() {
        defilementActif = !defilementActif;
        if (defilementActif) demarrerDefilement();
        else if (timerDefilement != null) timerDefilement.stop();
    }

    private void demarrerDefilement() {
        if (scrollClassement == null) return;
        final int[] sens = {1};
        final int[] pause = {0};
        timerDefilement = new javax.swing.Timer(40, e -> {
            if (!defilementActif) return;
            JScrollBar barre = scrollClassement.getVerticalScrollBar();
            if (pause[0] > 0) { pause[0]--; return; }
            int val = barre.getValue();
            int max = barre.getMaximum() - barre.getVisibleAmount();
            if (max <= 0) return;
            val += sens[0];
            if (val >= max) { val = max; sens[0] = -1; pause[0] = 75; }
            else if (val <= 0) { val = 0; sens[0] = 1; pause[0] = 75; }
            barre.setValue(val);
            // Defilement synchronise de la liste des tables (meme progression relative).
            if (scrollTables != null) {
                JScrollBar bt = scrollTables.getVerticalScrollBar();
                int maxT = bt.getMaximum() - bt.getVisibleAmount();
                if (maxT > 0) {
                    double ratio = max > 0 ? (double) val / max : 0;
                    bt.setValue((int) Math.round(ratio * maxT));
                }
            }
        });
        timerDefilement.start();
    }

    private void rendreTables() {
        corpsTables.removeAll();
        for (Table t : service.getTables()) {
            corpsTables.add(carteTable(t));
            corpsTables.add(Box.createVerticalStrut(Theme.ESPACE_S));
        }
        corpsTables.add(Box.createVerticalGlue());
    }

    private JComponent carteTable(Table table) {
        Optional<Rencontre> rencontre = service.rencontreSurTable(table.getNumero());
        Optional<org.club.tournoi.model.Barrage> barrage = service.barrageSurTable(table.getNumero());

        JPanel carte = new JPanel(new BorderLayout(Theme.ESPACE_M, 0));
        carte.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        carte.setBackground(Theme.SURFACE);

        Color accentEtat;
        String texteEtat;
        if (!table.isActive()) {
            accentEtat = Theme.TEXTE_DISCRET;
            texteEtat = "Desactivee";
        } else if (barrage.isPresent()) {
            accentEtat = new Color(0xD3, 0x2F, 0x2F); // rouge barrage
            texteEtat = "Match de barrage";
        } else if (rencontre.isPresent()) {
            accentEtat = Theme.ACCENT;
            texteEtat = "En cours";
        } else {
            accentEtat = Theme.VERT_TABLE;
            texteEtat = "Libre";
        }

        JPanel bande = new JPanel();
        bande.setPreferredSize(new Dimension(8, 0));
        bande.setBackground(accentEtat);
        carte.add(bande, BorderLayout.WEST);

        JPanel centre = new JPanel();
        centre.setOpaque(false);
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBorder(Theme.marge(Theme.ESPACE_S));

        JLabel num = new JLabel("Table " + table.getNumero());
        num.setFont(Theme.SOUS_TITRE.deriveFont(20f));
        num.setForeground(Theme.TEXTE);
        num.setAlignmentX(Component.LEFT_ALIGNMENT);
        centre.add(num);

        if (barrage.isPresent()) {
            var b = barrage.get();
            JLabel titre = new JLabel("MATCH DE BARRAGE");
            titre.setFont(Theme.CORPS_GRAS.deriveFont(15f));
            titre.setForeground(new Color(0xD3, 0x2F, 0x2F));
            titre.setAlignmentX(Component.LEFT_ALIGNMENT);
            centre.add(titre);
            JLabel duel = new JLabel("Equipe " + b.getEquipeA().getNumero()
                    + " contre Equipe " + b.getEquipeB().getNumero()
                    + " - Place de " + b.getPositionHaute() + " a " + b.getPositionBasse());
            duel.setFont(Theme.CORPS.deriveFont(16f));
            duel.setForeground(new Color(0xD3, 0x2F, 0x2F));
            duel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centre.add(duel);
        } else if (rencontre.isPresent()) {
            Rencontre r = rencontre.get();
            JLabel duel = new JLabel("Equipe " + r.getEquipeA().getNumero()
                    + "  vs  Equipe " + r.getEquipeB().getNumero());
            duel.setFont(Theme.CORPS.deriveFont(18f));
            duel.setForeground(Theme.ACCENT);
            duel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centre.add(duel);
        } else {
            JLabel etat = new JLabel(texteEtat);
            etat.setFont(Theme.CORPS.deriveFont(16f));
            etat.setForeground(accentEtat);
            etat.setAlignmentX(Component.LEFT_ALIGNMENT);
            centre.add(etat);
        }

        carte.add(centre, BorderLayout.CENTER);
        carte.setBorder(BorderFactory.createLineBorder(
                barrage.isPresent() ? new Color(0xD3, 0x2F, 0x2F) : Theme.BORDURE,
                barrage.isPresent() ? 2 : 1));
        return carte;
    }

    @Override public void equipesModifiees()    { rafraichir(); }
    @Override public void tablesModifiees()      { rafraichir(); }
    @Override public void rencontresModifiees()  { rafraichir(); }
    @Override public void classementModifie()    { rafraichir(); }
}
