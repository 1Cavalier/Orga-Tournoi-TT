package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Categorie;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.model.Symbole;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.service.TournoiListener;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * Onglet Joueurs en deux colonnes.
 * <ul>
 *   <li><b>Gauche</b> — base du club : champ d'ajout, recherche (nom/prénom),
 *       tri par en-têtes de colonnes, case à cocher « Tournoi » pour pointer,
 *       double-clic pour modifier/supprimer.</li>
 *   <li><b>Droite</b> — présents au tournoi : double-clic pour donner/retirer
 *       un symbole (regroupement d'équipe), bouton pour retirer du tournoi.</li>
 * </ul>
 * Un {@link TableRowSorter} gère tri et filtre à gauche : on convertit donc
 * toujours l'index de vue en index de modèle avant de retrouver un joueur.
 */
public class PanelJoueurs extends JPanel implements TournoiListener {

    private final ServiceTournoi service;

    private final DefaultTableModel modeleBase;
    private final JTable tableBase;
    private final TableRowSorter<DefaultTableModel> trieur;
    private final JTextField champRecherche = new JTextField(14);

    private final DefaultTableModel modeleTournoi;
    private final JTable tableTournoi;

    private final JTextField champNom = new JTextField(9);
    private final JTextField champPrenom = new JTextField(9);
    private final JComboBox<Categorie> champCat = new JComboBox<>(Categorie.values());

    // Les joueurs affichés, dans l'ordre du modèle (index modèle -> joueur).
    private final java.util.List<Joueur> joueursBase = new java.util.ArrayList<>();
    private final java.util.List<Joueur> joueursTournoi = new java.util.ArrayList<>();

    public PanelJoueurs(ServiceTournoi service) {
        this.service = service;
        service.ajouterListener(this);

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setBackground(Theme.FOND);
        setBorder(Theme.marge(Theme.ESPACE_M));
        add(Composants.titre("Joueurs"), BorderLayout.NORTH);

        // ===== Colonne gauche : base du club =====
        modeleBase = new DefaultTableModel(new Object[]{"Nom", "Prenom", "Categorie", "Tournoi"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 3; }
            @Override public Class<?> getColumnClass(int c) { return c == 3 ? Boolean.class : String.class; }
        };
        tableBase = new JTable(modeleBase);
        Composants.styliser(tableBase);
        tableBase.getColumnModel().getColumn(3).setMaxWidth(80);
        RendererNiveau rn = new RendererNiveau();
        tableBase.getColumnModel().getColumn(0).setCellRenderer(rn);
        tableBase.getColumnModel().getColumn(1).setCellRenderer(rn);

        // Tri : nom/prénom alphabétique, catégorie par niveau (forts d'abord) puis libellé.
        trieur = new TableRowSorter<>(modeleBase);
        trieur.setComparator(2, (Comparator<String>) (a, b) -> {
            Categorie ca = categorieParLibelle(a), cb = categorieParLibelle(b);
            int na = ca == null ? 99 : ca.getNiveau();
            int nb = cb == null ? 99 : cb.getNiveau();
            if (na != nb) return Integer.compare(na, nb);       // niveau 1 (fort) d'abord
            return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
        });
        tableBase.setRowSorter(trieur);

        // Case à cocher = pointer / dépointer (convertir l'index !)
        modeleBase.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 3) {
                int modelRow = e.getFirstRow();
                if (modelRow >= 0 && modelRow < joueursBase.size()) {
                    boolean coche = Boolean.TRUE.equals(modeleBase.getValueAt(modelRow, 3));
                    Joueur j = joueursBase.get(modelRow);
                    if (coche) service.pointerDansTournoi(j);
                    else retirerDuTournoiSansConfirm(j);
                }
            }
        });
        // Double-clic = modifier / supprimer
        tableBase.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = tableBase.rowAtPoint(e.getPoint());
                    int col = tableBase.columnAtPoint(e.getPoint());
                    if (viewRow < 0 || col == 3) return;
                    int modelRow = tableBase.convertRowIndexToModel(viewRow);
                    if (modelRow >= 0 && modelRow < joueursBase.size()) {
                        modifierOuSupprimer(joueursBase.get(modelRow));
                    }
                }
            }
        });

        JScrollPane scrollBase = new JScrollPane(tableBase);
        scrollBase.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));

        JPanel gauche = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        gauche.setOpaque(false);
        JPanel hautG = new JPanel();
        hautG.setLayout(new BoxLayout(hautG, BoxLayout.Y_AXIS));
        hautG.setOpaque(false);
        hautG.add(enteteAjout());
        hautG.add(Box.createVerticalStrut(Theme.ESPACE_S));
        hautG.add(barreRecherche());
        gauche.add(hautG, BorderLayout.NORTH);
        gauche.add(scrollBase, BorderLayout.CENTER);
        JLabel aideG = new JLabel("Cochez « Tournoi » pour pointer · double-clic pour modifier/supprimer · en-tetes pour trier");
        aideG.setFont(Theme.PETIT); aideG.setForeground(Theme.TEXTE_DISCRET);
        gauche.add(aideG, BorderLayout.SOUTH);

        // ===== Colonne droite : présents au tournoi =====
        modeleTournoi = new DefaultTableModel(new Object[]{"Nom", "Prenom", "Categorie", "Symbole"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableTournoi = new JTable(modeleTournoi);
        Composants.styliser(tableTournoi);
        tableTournoi.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int r = tableTournoi.rowAtPoint(e.getPoint());
                    if (r >= 0 && r < joueursTournoi.size()) {
                        choisirSymbole(joueursTournoi.get(r));
                    }
                }
            }
        });
        JScrollPane scrollTournoi = new JScrollPane(tableTournoi);
        scrollTournoi.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        JPanel droite = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        droite.setOpaque(false);
        droite.add(Composants.sousTitre("Presents au tournoi"), BorderLayout.NORTH);
        droite.add(scrollTournoi, BorderLayout.CENTER);
        JButton retirer = Composants.boutonSecondaire("Retirer du tournoi");
        retirer.addActionListener(e -> retirerSelectionTournoi());
        JLabel aideD = new JLabel("Double-clic sur un joueur pour lui donner un symbole d'equipe");
        aideD.setFont(Theme.PETIT); aideD.setForeground(Theme.TEXTE_DISCRET);
        JPanel basD = new JPanel(new BorderLayout());
        basD.setOpaque(false);
        JPanel basDBtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        basDBtn.setOpaque(false); basDBtn.add(retirer);
        basD.add(basDBtn, BorderLayout.NORTH);
        basD.add(aideD, BorderLayout.SOUTH);
        droite.add(basD, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gauche, droite);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);

        rafraichir();
    }

    private JComponent enteteAjout() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        p.setOpaque(false);
        p.add(new JLabel("Nom"));    p.add(champNom);
        p.add(new JLabel("Prenom")); p.add(champPrenom);
        p.add(champCat);
        JButton ajouter = Composants.boutonPrimaire("Valider");
        ajouter.addActionListener(e -> ajouterABase());
        p.add(ajouter);
        return p;
    }

    private JComponent barreRecherche() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        p.setOpaque(false);
        p.add(new JLabel("Recherche"));
        p.add(champRecherche);
        champRecherche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrer(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrer(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrer(); }
        });
        return p;
    }

    /** Filtre sur nom + prénom uniquement (colonnes 0 et 1). */
    private void filtrer() {
        String txt = champRecherche.getText().trim();
        if (txt.isEmpty()) { trieur.setRowFilter(null); return; }
        String re = "(?i)" + java.util.regex.Pattern.quote(txt);
        trieur.setRowFilter(RowFilter.regexFilter(re, 0, 1));
    }

    private void ajouterABase() {
        String n = champNom.getText().trim();
        String pr = champPrenom.getText().trim();
        if (n.isEmpty() || pr.isEmpty()) { info("Le nom et le prenom sont obligatoires."); return; }
        Categorie cat = (Categorie) champCat.getSelectedItem();
        Joueur cree = service.ajouterJoueurBase(n, pr, cat, false);
        if (cree == null) {
            int rep = JOptionPane.showConfirmDialog(this,
                    "Un joueur nomme « " + pr + " " + n + " » existe deja dans la base.\n"
                            + "L'ajouter quand meme comme joueur distinct ?",
                    "Doublon detecte", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (rep != JOptionPane.YES_OPTION) return;
            cree = service.ajouterJoueurBase(n, pr, cat, true);
        }
        champNom.setText(""); champPrenom.setText(""); champNom.requestFocus();
        int auTournoi = JOptionPane.showConfirmDialog(this,
                "Ajouter « " + pr + " " + n + " » directement au tournoi en cours ?",
                "Ajouter au tournoi", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (auTournoi == JOptionPane.YES_OPTION) service.pointerDansTournoi(cree);
    }

    private void modifierOuSupprimer(Joueur joueur) {
        Object[] options = {"Modifier", "Supprimer", "Annuler"};
        int choix = JOptionPane.showOptionDialog(this,
                "Que faire avec « " + joueur.getPrenom() + " " + joueur.getNom() + " » ?",
                "Joueur", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choix == 0) modifier(joueur);
        else if (choix == 1) supprimerDeBase(joueur);
    }

    private void modifier(Joueur joueur) {
        JTextField nom = new JTextField(joueur.getNom(), 14);
        JTextField prenom = new JTextField(joueur.getPrenom(), 14);
        JComboBox<Categorie> categorie = new JComboBox<>(Categorie.values());
        categorie.setSelectedItem(joueur.getCategorie());
        JPanel form = new JPanel(new GridLayout(0, 2, Theme.ESPACE_S, Theme.ESPACE_S));
        form.add(new JLabel("Nom"));       form.add(nom);
        form.add(new JLabel("Prenom"));    form.add(prenom);
        form.add(new JLabel("Categorie")); form.add(categorie);
        int ok = JOptionPane.showConfirmDialog(this, form, "Modifier le joueur",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        String n = nom.getText().trim(), p = prenom.getText().trim();
        if (n.isEmpty() || p.isEmpty()) { info("Nom et prenom obligatoires."); return; }
        service.modifierJoueurBase(joueur, n, p, (Categorie) categorie.getSelectedItem());
    }

    private void supprimerDeBase(Joueur joueur) {
        String extra = service.estDansTournoi(joueur)
                ? "\n\nAttention : ce joueur est present dans le tournoi, il en sera aussi retire."
                : "";
        int ok = JOptionPane.showConfirmDialog(this,
                "Supprimer « " + joueur.getPrenom() + " " + joueur.getNom() + " » de la base du club ?" + extra,
                "Suppression", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try { service.supprimerJoueurBase(joueur); }
        catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Suppression impossible", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Pop-up de choix du symbole pour un joueur présent au tournoi. */
    private void choisirSymbole(Joueur joueur) {
        Symbole[] valeurs = Symbole.values();
        Symbole choix = (Symbole) JOptionPane.showInputDialog(this,
                "Symbole d'equipe pour « " + joueur.getPrenom() + " " + joueur.getNom() + " » :\n"
                        + "(les joueurs de meme symbole seront dans la meme equipe)",
                "Symbole", JOptionPane.PLAIN_MESSAGE, null, valeurs, joueur.getSymbole());
        if (choix == null) return; // annulé
        service.definirSymbole(joueur, choix);
    }

    private void retirerSelectionTournoi() {
        int r = tableTournoi.getSelectedRow();
        if (r < 0 || r >= joueursTournoi.size()) { info("Selectionnez un joueur a retirer."); return; }
        try { service.retirerDuTournoi(joueursTournoi.get(r)); }
        catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Retrait impossible", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void retirerDuTournoiSansConfirm(Joueur joueurBase) {
        try { service.retirerDuTournoi(joueurBase); }
        catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Retrait impossible", JOptionPane.WARNING_MESSAGE);
            rafraichir();
        }
    }

    private void info(String m) {
        JOptionPane.showMessageDialog(this, m, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private Categorie categorieParLibelle(String libelle) {
        for (Categorie c : Categorie.values()) if (c.getLibelle().equals(libelle)) return c;
        return null;
    }

    private void rafraichir() {
        // Gauche : base du club
        joueursBase.clear();
        modeleBase.setRowCount(0);
        for (Joueur j : service.getBaseJoueurs().getJoueurs()) {
            joueursBase.add(j);
            modeleBase.addRow(new Object[]{
                    j.getNom(), j.getPrenom(), j.getCategorie().getLibelle(), service.estDansTournoi(j)});
        }
        // Droite : présents au tournoi
        joueursTournoi.clear();
        modeleTournoi.setRowCount(0);
        for (Joueur j : service.getJoueurs()) {
            joueursTournoi.add(j);
            Symbole s = j.getSymbole();
            String sym = s.estDefini() ? s.getCaractere() + " " + s.getLibelle() : "";
            modeleTournoi.addRow(new Object[]{
                    j.getNom(), j.getPrenom(), j.getCategorie().getLibelle(), sym});
        }
    }

    @Override public void joueursModifies() { rafraichir(); }

    private class RendererNiveau extends Composants.RendererAlterne {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            int modelRow = t.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < joueursBase.size()) {
                Color c = Theme.couleurNiveau(joueursBase.get(modelRow).getCategorie().getNiveau());
                setForeground(c);
                setFont(Theme.CORPS_GRAS);
            }
            return this;
        }
    }
}
