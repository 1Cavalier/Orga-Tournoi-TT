package org.club.tournoi.ui;

import org.club.tournoi.persistence.GestionnaireSauvegarde;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Ecran de demarrage : on choisit d'ouvrir une sauvegarde existante ou d'en creer une, puis
 * la taille de la salle. Regroupe en une seule fenetre ce que l'ancienne version faisait en
 * deux, et remonte proprement les erreurs de chargement (au lieu du {@code null} silencieux).
 */
public class DialogueDemarrage extends JDialog {

    /** Resultat de l'ecran de demarrage. */
    public record Resultat(Path fichier, boolean existant, int lignes, int colonnes,
                           boolean pointEcart, String coinTables,
                           Path baseJoueurs, boolean baseExistante,
                           org.club.tournoi.model.FormatTournoi format) { }

    private final GestionnaireSauvegarde gestionnaire;
    private Resultat resultat;

    private final JComboBox<Path> comboExistantes = new JComboBox<>();
    private final JTextField champNouveau = new JTextField(16);
    // Base joueurs permanente
    private final JComboBox<Path> comboBases = new JComboBox<>();
    private final JTextField champNouvelleBase = new JTextField(16);
    private final JRadioButton radioBaseExistante = new JRadioButton("Base existante");
    private final JRadioButton radioNouvelleBase = new JRadioButton("Nouvelle base");
    private final java.nio.file.Path dossierBases;
    private final JSpinner spinnerLignes = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
    private final JSpinner spinnerColonnes = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
    private final JRadioButton radioExistant = new JRadioButton("Ouvrir un tournoi existant");
    private final JRadioButton radioNouveau = new JRadioButton("Nouveau tournoi");
    private final JComboBox<String> comboPoints = new JComboBox<>(
            new String[]{"Avec 2 points d'ecart", "Sans point d'ecart (premier a 11)"});
    private final JComboBox<String> comboCoin = new JComboBox<>(
            new String[]{"En haut a gauche", "En haut a droite", "En bas a gauche", "En bas a droite"});
    private final JComboBox<org.club.tournoi.model.FormatTournoi> comboFormat =
            new JComboBox<>(org.club.tournoi.model.FormatTournoi.values());

    public DialogueDemarrage(GestionnaireSauvegarde gestionnaire, java.nio.file.Path dossierBases) {
        super((Frame) null, "Tournoi - Demarrage", true);
        this.gestionnaire = gestionnaire;
        this.dossierBases = dossierBases;

        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        ((JComponent) getContentPane()).setBorder(Theme.marge(Theme.ESPACE_L));
        getContentPane().setBackground(Theme.FOND);

        add(Composants.titre("Tournoi de tennis de table"), BorderLayout.NORTH);

        // Haut : deux cadres cote a cote (Base de donnees | Tournoi)
        JPanel haut = new JPanel(new GridLayout(1, 2, Theme.ESPACE_M, 0));
        haut.setOpaque(false);
        haut.add(encadre("Base de donnees", corpsBase()));
        haut.add(encadre("Tournoi", corps()));

        // Bas : autres informations (salle, format, points, numerotation)
        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setOpaque(false);
        haut.setAlignmentX(LEFT_ALIGNMENT);
        centre.add(haut);
        centre.add(Box.createVerticalStrut(Theme.ESPACE_M));
        JComponent infos = encadre("Autre information", corpsInfos());
        infos.setAlignmentX(LEFT_ALIGNMENT);
        centre.add(infos);
        add(centre, BorderLayout.CENTER);
        add(boutons(), BorderLayout.SOUTH);

        chargerSauvegardes();
        chargerBases();
        pack();
        setLocationRelativeTo(null);
    }

    /** Entoure un contenu d'un cadre titre (comme sur la maquette). */
    private JComponent encadre(String titre, JComponent contenu) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDURE), titre));
        contenu.setOpaque(false);
        p.add(contenu, BorderLayout.CENTER);
        return p;
    }

    private JComponent corpsBase() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        ButtonGroup gb = new ButtonGroup();
        gb.add(radioBaseExistante);
        gb.add(radioNouvelleBase);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        p.add(Composants.sousTitre("Base joueurs du club"), g);
        g.gridy = 1; g.gridwidth = 2; p.add(radioBaseExistante, g);
        g.gridy = 2; g.gridwidth = 1; g.gridx = 0; p.add(new JLabel("Base"), g);
        g.gridx = 1; g.weightx = 1; p.add(comboBases, g); g.weightx = 0;
        comboBases.setRenderer(new javax.swing.plaf.basic.BasicComboBoxRenderer() {
            @Override public Component getListCellRendererComponent(JList list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Path chemin) setText(chemin.getFileName().toString());
                return this;
            }
        });
        g.gridx = 0; g.gridy = 3; g.gridwidth = 2; p.add(radioNouvelleBase, g);
        g.gridy = 4; g.gridwidth = 1; g.gridx = 0; p.add(new JLabel("Nom base"), g);
        g.gridx = 1; g.weightx = 1; p.add(champNouvelleBase, g); g.weightx = 0;

        Runnable maj = () -> {
            comboBases.setEnabled(radioBaseExistante.isSelected());
            champNouvelleBase.setEnabled(radioNouvelleBase.isSelected());
        };
        radioBaseExistante.addActionListener(e -> maj.run());
        radioNouvelleBase.addActionListener(e -> maj.run());
        return p;
    }

    private JComponent corps() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        ButtonGroup groupe = new ButtonGroup();
        groupe.add(radioExistant);
        groupe.add(radioNouveau);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; p.add(radioExistant, g);
        g.gridy = 1; g.gridwidth = 1; g.gridx = 0;
        p.add(new JLabel("Fichier"), g);
        g.gridx = 1; g.weightx = 1; p.add(comboExistantes, g); g.weightx = 0;
        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        JButton parcourir = Composants.boutonSecondaire("Parcourir...");
        parcourir.addActionListener(e -> parcourir());
        p.add(parcourir, g);
        g.fill = GridBagConstraints.HORIZONTAL;

        comboExistantes.setRenderer(new javax.swing.plaf.basic.BasicComboBoxRenderer() {
            @Override public Component getListCellRendererComponent(JList list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Path chemin) setText(chemin.getFileName().toString());
                return this;
            }
        });

        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; p.add(radioNouveau, g);
        g.gridy = 3; g.gridwidth = 1; g.gridx = 0; p.add(new JLabel("Nom"), g);
        g.gridx = 1; g.weightx = 1; p.add(champNouveau, g); g.weightx = 0;

        Runnable maj = () -> {
            comboExistantes.setEnabled(radioExistant.isSelected());
            champNouveau.setEnabled(radioNouveau.isSelected());
        };
        radioExistant.addActionListener(e -> maj.run());
        radioNouveau.addActionListener(e -> maj.run());
        return p;
    }

    /** Section du bas : taille de salle, format, regle des points, numerotation. */
    private JComponent corpsInfos() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        // Colonne gauche : taille de la salle
        g.gridx = 0; g.gridy = 0; p.add(Composants.sousTitre("Taille de la salle"), g);
        g.gridy = 1; p.add(new JLabel("Lignes"), g);
        g.gridx = 1; p.add(spinnerLignes, g);
        g.gridx = 0; g.gridy = 2; p.add(new JLabel("Colonnes"), g);
        g.gridx = 1; p.add(spinnerColonnes, g);
        g.gridx = 0; g.gridy = 3; p.add(new JLabel("Table 1 commence"), g);
        g.gridx = 1; p.add(comboCoin, g);

        // Colonne droite : format + points
        g.gridx = 2; g.gridy = 0; p.add(Composants.sousTitre("Format"), g);
        g.gridy = 1; p.add(new JLabel("Format des equipes"), g);
        g.gridx = 3; p.add(comboFormat, g);
        g.gridx = 2; g.gridy = 2; p.add(new JLabel("Point d'ecart"), g);
        g.gridx = 3; p.add(comboPoints, g);

        // Formats non disponibles : affichés mais non sélectionnables.
        comboFormat.setRenderer(new javax.swing.plaf.basic.BasicComboBoxRenderer() {
            @Override public Component getListCellRendererComponent(JList list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof org.club.tournoi.model.FormatTournoi f) {
                    setText(f.getLibelle());
                    if (!f.estDisponible()) setForeground(Theme.TEXTE_DISCRET);
                }
                return this;
            }
        });
        // Empêche de choisir un format non disponible.
        comboFormat.addActionListener(e -> {
            var f = (org.club.tournoi.model.FormatTournoi) comboFormat.getSelectedItem();
            if (f != null && !f.estDisponible()) {
                comboFormat.setSelectedItem(org.club.tournoi.model.FormatTournoi.EQUIPES_3_4);
            }
        });
        return p;
    }

    private JComponent boutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.setOpaque(false);
        JButton valider = Composants.boutonPrimaire("Demarrer");
        JButton quitter = Composants.boutonSecondaire("Quitter");
        valider.addActionListener(e -> valider());
        quitter.addActionListener(e -> { resultat = null; dispose(); });
        p.add(quitter);
        p.add(valider);
        return p;
    }

    /**
     * Ouvre un selecteur de fichier pour choisir un {@code .json} n'importe ou sur le disque.
     * Le fichier retenu est ajoute a la liste et selectionne ; le mode passe sur "ouvrir un
     * tournoi existant". Cela evite d'avoir a copier le fichier dans le dossier des sauvegardes.
     */
    private void parcourir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choisir un fichier de tournoi (.json)");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Fichiers de tournoi (*.json)", "json"));
        // Demarre dans le dossier des sauvegardes s'il existe, sinon le dossier courant.
        java.io.File depart = gestionnaire.getDossier().toFile();
        if (depart.isDirectory()) {
            chooser.setCurrentDirectory(depart);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path choisi = chooser.getSelectedFile().toPath();
            // Ajoute a la liste s'il n'y est pas deja, puis selectionne.
            boolean present = false;
            for (int i = 0; i < comboExistantes.getItemCount(); i++) {
                if (comboExistantes.getItemAt(i).equals(choisi)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                comboExistantes.addItem(choisi);
            }
            comboExistantes.setSelectedItem(choisi);
            radioExistant.setSelected(true);
            comboExistantes.setEnabled(true);
            champNouveau.setEnabled(false);
        }
    }

    private void chargerBases() {
        try {
            java.nio.file.Files.createDirectories(dossierBases);
            List<Path> bases = gestionnaire.listerBasesJoueurs(dossierBases);
            bases.forEach(comboBases::addItem);
            boolean yEnA = !bases.isEmpty();
            radioBaseExistante.setSelected(yEnA);
            radioNouvelleBase.setSelected(!yEnA);
            comboBases.setEnabled(yEnA);
            champNouvelleBase.setEnabled(!yEnA);
            if (!yEnA) champNouvelleBase.setText("base_du_club");
        } catch (IOException e) {
            radioNouvelleBase.setSelected(true);
            comboBases.setEnabled(false);
            champNouvelleBase.setText("base_du_club");
        }
    }

    private void chargerSauvegardes() {
        try {
            gestionnaire.initialiser();
            List<Path> sauvegardes = gestionnaire.listerSauvegardes();
            sauvegardes.forEach(comboExistantes::addItem);
            boolean yEnA = !sauvegardes.isEmpty();
            radioExistant.setSelected(yEnA);
            radioNouveau.setSelected(!yEnA);
            comboExistantes.setEnabled(yEnA);
            champNouveau.setEnabled(!yEnA);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Impossible de lire le dossier des sauvegardes : " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            radioNouveau.setSelected(true);
            comboExistantes.setEnabled(false);
        }
    }

    private void valider() {
        int lignes = (int) spinnerLignes.getValue();
        int colonnes = (int) spinnerColonnes.getValue();

        // --- Base joueurs ---
        Path baseChemin; boolean baseExistante;
        if (radioBaseExistante.isSelected()) {
            baseChemin = (Path) comboBases.getSelectedItem();
            if (baseChemin == null) {
                erreur("Choisissez une base joueurs existante ou creez-en une nouvelle.");
                return;
            }
            baseExistante = true;
        } else {
            String nomBase = champNouvelleBase.getText().trim();
            if (nomBase.isEmpty()) {
                erreur("Donnez un nom a la nouvelle base joueurs.");
                return;
            }
            String fichier = nomBase.endsWith(".json") ? nomBase : nomBase + ".json";
            baseChemin = dossierBases.resolve(fichier);
            baseExistante = false;
        }

        org.club.tournoi.model.FormatTournoi format =
                (org.club.tournoi.model.FormatTournoi) comboFormat.getSelectedItem();
        if (format == null || !format.estDisponible()) {
            format = org.club.tournoi.model.FormatTournoi.EQUIPES_3_4;
        }

        if (radioExistant.isSelected()) {
            Path fichier = (Path) comboExistantes.getSelectedItem();
            if (fichier == null) {
                erreur("Aucune sauvegarde a ouvrir. Creez plutot un nouveau tournoi.");
                return;
            }
            boolean pointEcart = comboPoints.getSelectedIndex() == 0;
            resultat = new Resultat(fichier, true, lignes, colonnes, pointEcart, coinChoisi(),
                    baseChemin, baseExistante, format);
        } else {
            String nom = champNouveau.getText().trim();
            if (nom.isEmpty()) {
                erreur("Donnez un nom au nouveau tournoi.");
                return;
            }
            // La sauvegarde etant automatique, un nom deja pris ecraserait l'ancien tournoi
            // des la premiere modification : on demande confirmation.
            if (java.nio.file.Files.exists(gestionnaire.cheminPour(nom))) {
                int choix = JOptionPane.showConfirmDialog(this,
                        "Un tournoi nomme \"" + nom + "\" existe deja.\n"
                                + "Le remplacer par un tournoi vide ?\n\n"
                                + "(Pour le reprendre, choisissez plutot \"Ouvrir un tournoi existant\".)",
                        "Tournoi existant", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choix != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            boolean pointEcart2 = comboPoints.getSelectedIndex() == 0;
            resultat = new Resultat(gestionnaire.cheminPour(nom), false, lignes, colonnes, pointEcart2,
                    coinChoisi(), baseChemin, baseExistante, format);
        }
        dispose();
    }

    private void erreur(String m) {
        JOptionPane.showMessageDialog(this, m, "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    /** Renvoie le choix de l'utilisateur, ou {@code null} s'il a quitte. */
    public Resultat getResultat() {
        return resultat;
    }

    private String coinChoisi() {
        String[] valeurs = {"HAUT_GAUCHE", "HAUT_DROITE", "BAS_GAUCHE", "BAS_DROITE"};
        int i = comboCoin.getSelectedIndex();
        return (i >= 0 && i < valeurs.length) ? valeurs[i] : "HAUT_GAUCHE";
    }
}
