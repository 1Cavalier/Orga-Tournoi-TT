package org.club.tournoi.ui.panel;

import org.club.tournoi.model.Barrage;
import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.EtatEquipe;
import org.club.tournoi.model.Table;
import org.club.tournoi.service.Classement;
import org.club.tournoi.service.Departage;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Affichage de preparation et de suivi des departages (onglet Rencontres, mode departage).
 * <p>
 * A gauche : le classement avec, pour chaque equipe, ses rencontres/matchs/points et son etat.
 * Les equipes dont la position est definitivement fixee sont en vert clair, celles en match en
 * orange, les autres en blanc. Chaque groupe a departager recoit une couleur d'accent, reprise
 * a droite, pour suivre visuellement les barrages.
 * <p>
 * A droite : pour chaque groupe a egalite, le plan (equipe posee d'office + paires a jouer avec
 * les positions en jeu) et un bouton pour lancer chaque barrage ; puis un mini-tableau de suivi
 * des barrages en cours et termines (equipes, position, temps, score).
 */
public class PanelDepartage extends JPanel {

    private final ServiceTournoi service;
    private final JPanel gauche = new JPanel();
    private final JPanel droite = new JPanel();
    private final JPanel rencontresEnCours = new JPanel();

    // Couleurs d'accent attribuees aux groupes de departage (cycle).
    private static final Color[] ACCENTS = {
            new Color(0x2E, 0x7D, 0x32), // vert
            new Color(0x15, 0x65, 0xC0), // bleu
            new Color(0xC6, 0x28, 0x28), // rouge
            new Color(0x6A, 0x1B, 0x9A), // violet
            new Color(0xE8, 0x7A, 0x2C), // orange
            new Color(0x00, 0x83, 0x8F), // cyan
    };
    private final Map<Integer, Color> couleurParGroupe = new HashMap<>();

    private static final Color COULEUR_FIXEE = new Color(0xDD, 0xF0, 0xDB);      // vert clair
    private static final Color COULEUR_EN_MATCH = new Color(0xFC, 0xE9, 0xD8);   // orange clair
    private static final Color COULEUR_EN_MATCH_FORT = new Color(0xC6, 0x28, 0x28); // rouge (rencontre)
    private static final Color COULEUR_BARRAGE = new Color(0xD3, 0x2F, 0x2F);    // rouge barrage
    private static final Color COULEUR_BARRAGE_FOND = new Color(0xFB, 0xE0, 0xE0); // rouge clair fond

    public PanelDepartage(ServiceTournoi service) {
        this.service = service;
        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));
        setOpaque(false);

        gauche.setLayout(new BoxLayout(gauche, BoxLayout.Y_AXIS));
        gauche.setOpaque(false);
        droite.setLayout(new BoxLayout(droite, BoxLayout.Y_AXIS));
        droite.setOpaque(false);
        rencontresEnCours.setLayout(new BoxLayout(rencontresEnCours, BoxLayout.Y_AXIS));
        rencontresEnCours.setOpaque(false);

        // Colonne gauche : en haut les rencontres encore en cours (avant departage), en bas le
        // classement avec les etats.
        JSplitPane gaucheSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                encadrer("Rencontres avant departage", rencontresEnCours),
                encadrer("Classement et etats", gauche));
        gaucheSplit.setResizeWeight(0.3);
        gaucheSplit.setBorder(null);
        gaucheSplit.setOpaque(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                gaucheSplit,
                encadrer("Departages", droite));
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);
    }

    private JComponent encadrer(String titre, JComponent contenu) {
        JPanel wrap = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        wrap.setOpaque(false);
        wrap.add(Composants.sousTitre(titre), BorderLayout.NORTH);
        JScrollPane sc = new JScrollPane(contenu);
        sc.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        sc.getVerticalScrollBar().setUnitIncrement(16);
        wrap.add(sc, BorderLayout.CENTER);
        return wrap;
    }

    /** Recalcule et redessine tout le panneau. */
    public void rafraichir() {
        // Attribue une couleur a chaque groupe a egalite (par rang de debut).
        couleurParGroupe.clear();
        var plans = service.plansDepartage();
        int idx = 0;
        for (var plan : plans) {
            couleurParGroupe.put(plan.rangDebut(), ACCENTS[idx % ACCENTS.length]);
            idx++;
        }
        rendreRencontresEnCours();
        rendreGauche();
        rendreDroite();
        revalidate();
        repaint();
    }

    /** Liste les rencontres normales encore en cours, avec chrono et bouton de saisie de score. */
    private void rendreRencontresEnCours() {
        rencontresEnCours.removeAll();
        var enCours = service.getRencontresEnCours();
        if (enCours.isEmpty()) {
            rencontresEnCours.add(note("Aucune rencontre en cours. Tous les matchs sont termines."));
        } else {
            long maintenant = System.currentTimeMillis();
            for (var r : enCours) {
                JPanel l = new JPanel(new BorderLayout(Theme.ESPACE_S, 0));
                l.setBackground(Theme.SURFACE);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 1, 0, COULEUR_EN_MATCH_FORT),
                        Theme.marge(Theme.ESPACE_S)));
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

                String tables = "Table " + r.getNumeroTable()
                        + (r.utiliseDeuxTables() ? " + " + r.getNumeroTable2() : "");
                JLabel txt = new JLabel("Eq " + r.getEquipeA().getNumero() + " vs Eq "
                        + r.getEquipeB().getNumero() + "   " + tables + "   "
                        + formaterDuree(r.secondesEcoulees(maintenant)));
                txt.setFont(Theme.CORPS);
                txt.setForeground(Theme.TEXTE);
                l.add(txt, BorderLayout.CENTER);

                JButton score = Composants.boutonSecondaire("Score");
                score.addActionListener(e -> saisirScoreRencontre(r));
                l.add(score, BorderLayout.EAST);
                rencontresEnCours.add(l);
                rencontresEnCours.add(Box.createVerticalStrut(2));
            }
        }
        rencontresEnCours.add(Box.createVerticalGlue());
    }

    private void saisirScoreRencontre(org.club.tournoi.model.Rencontre r) {
        DialogueScore dlg = new DialogueScore(
                (Frame) SwingUtilities.getWindowAncestor(this), service, r);
        dlg.setVisible(true);
        rafraichir();
    }

    // ------------------------------------------------------------------ gauche

    private void rendreGauche() {
        gauche.removeAll();
        gauche.add(ligne("#", "Equipe", "Renc.", "Matchs", "Points", "Etat", Etiquette.ENTETE, null));

        // Equipes encore dans un groupe a departager (sans position finale fixee).
        java.util.Set<Equipe> enEgalite = new java.util.HashSet<>();
        Map<Equipe, Color> accentEquipe = new HashMap<>();
        for (var plan : service.plansDepartage()) {
            Color acc = couleurParGroupe.get(plan.rangDebut());
            for (Equipe e : plan.equipes()) {
                // Une equipe deja fixee (position finale attribuee) n'est plus "a departager".
                if (service.positionFinale(e) < 0) {
                    enEgalite.add(e);
                    accentEquipe.put(e, acc);
                }
            }
        }

        // Equipes actuellement dans un barrage (pour l'affichage rouge "Match barrage").
        java.util.Set<Equipe> enBarrage = new java.util.HashSet<>();
        for (var b : service.getBarrages()) {
            if (!b.estTermine()) {
                enBarrage.add(b.getEquipeA());
                enBarrage.add(b.getEquipeB());
            }
        }

        for (Classement.Ligne l : service.classement()) {
            Equipe eq = l.equipe();
            boolean fixee = service.positionFinale(eq) > 0;
            String etat;
            Etiquette et;
            if (enBarrage.contains(eq)) {
                etat = "Match barrage";
                et = Etiquette.BARRAGE;
            } else if (eq.getEtat() == EtatEquipe.EN_MATCH) {
                etat = "En match";
                et = Etiquette.EN_MATCH;
            } else if (fixee) {
                etat = "Termine";
                et = Etiquette.FIXEE;
            } else if (!enEgalite.contains(eq)) {
                etat = "Place";
                et = Etiquette.FIXEE;
            } else {
                etat = "A departager";
                et = Etiquette.STANDARD;
            }
            // Position affichee : position finale si fixee, sinon rang du classement.
            int posFinale = service.positionFinale(eq);
            String rang = posFinale > 0 ? String.valueOf(posFinale) : String.valueOf(l.rang());
            gauche.add(ligne(rang, "Equipe " + eq.getNumero(),
                    String.valueOf(l.stats().rencontresGagnees()),
                    String.valueOf(l.stats().matchsGagnes()),
                    String.valueOf(l.stats().pointsPour()),
                    etat, et, accentEquipe.get(eq)));
        }
        gauche.add(Box.createVerticalGlue());

        JPanel legende = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_M, 2));
        legende.setOpaque(false);
        legende.setAlignmentX(Component.LEFT_ALIGNMENT);
        legende.add(pastille("Place fixee", COULEUR_FIXEE));
        legende.add(pastille("En match", COULEUR_EN_MATCH));
        legende.add(pastille("Match barrage", COULEUR_BARRAGE_FOND));
        legende.add(pastille("A departager", Theme.SURFACE));
        gauche.add(legende);
    }

    private enum Etiquette { ENTETE, EN_MATCH, BARRAGE, FIXEE, STANDARD }

    private JComponent ligne(String rang, String equipe, String renc, String matchs, String points,
                             String etat, Etiquette etiquette, Color accent) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(true);
        Color fond = switch (etiquette) {
            case ENTETE -> Theme.VERT_TABLE;
            case EN_MATCH -> COULEUR_EN_MATCH;
            case BARRAGE -> COULEUR_BARRAGE_FOND;
            case FIXEE -> COULEUR_FIXEE;
            case STANDARD -> Theme.SURFACE;
        };
        p.setBackground(fond);
        // Bord gauche colore = groupe de departage de l'equipe.
        int bordGauche = accent != null ? 4 : 0;
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, bordGauche, 1, 0, accent != null ? accent : Theme.BORDURE),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        boolean entete = etiquette == Etiquette.ENTETE;
        String[] valeurs = {rang, equipe, renc, matchs, points, etat};
        double[] poids = {0.6, 2.0, 1.0, 1.0, 1.0, 1.6};
        for (int i = 0; i < valeurs.length; i++) {
            g.gridx = i; g.weightx = poids[i];
            JLabel l = new JLabel(valeurs[i]);
            boolean colEtat = (i == valeurs.length - 1);
            // La colonne "etat" passe en rouge gras pour un match barrage.
            if (colEtat && etiquette == Etiquette.BARRAGE) {
                l.setFont(Theme.CORPS_GRAS);
                l.setForeground(COULEUR_BARRAGE);
            } else {
                l.setFont(entete ? Theme.PETIT_GRAS : Theme.CORPS);
                l.setForeground(entete ? Color.WHITE : Theme.TEXTE);
            }
            p.add(l, g);
        }
        return p;
    }

    private JComponent pastille(String texte, Color couleur) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel carre = new JLabel("  ");
        carre.setOpaque(true);
        carre.setBackground(couleur);
        carre.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        JLabel l = new JLabel(texte);
        l.setFont(Theme.PETIT);
        l.setForeground(Theme.TEXTE_DISCRET);
        p.add(carre);
        p.add(l);
        return p;
    }

    // ------------------------------------------------------------------ droite

    private void rendreDroite() {
        droite.removeAll();

        var plans = service.plansDepartage();
        // Plans (hors groupes deja entierement departages).
        boolean auMoins = false;
        for (var plan : plans) {
            // Si toutes les equipes du groupe ont deja une position finale, le groupe est regle.
            boolean regle = plan.equipes().stream().allMatch(e -> service.positionFinale(e) > 0);
            if (regle) continue;
            auMoins = true;
            // Ce groupe est-il departageable maintenant ? Bloque si une equipe en match peut le
            // rejoindre (a son niveau de victoires).
            boolean bloque = !service.groupeDepartageableMaintenant(plan.rencontresGagnees());
            droite.add(cartePlan(plan, bloque));
            droite.add(Box.createVerticalStrut(Theme.ESPACE_S));
        }
        if (!auMoins) {
            droite.add(note("Aucun departage a jouer : le classement est definitif."));
        }

        // Mini-tableau de suivi des barrages.
        if (!service.getBarrages().isEmpty()) {
            droite.add(Box.createVerticalStrut(Theme.ESPACE_M));
            JLabel titre = new JLabel("Suivi des matchs de departage");
            titre.setFont(Theme.CORPS_GRAS);
            titre.setForeground(Theme.TEXTE);
            titre.setAlignmentX(Component.LEFT_ALIGNMENT);
            titre.setBorder(Theme.marge(Theme.ESPACE_S));
            droite.add(titre);
            for (Barrage b : service.getBarrages()) {
                droite.add(ligneBarrage(b));
                droite.add(Box.createVerticalStrut(2));
            }
        }
        droite.add(Box.createVerticalGlue());
    }

    private JComponent cartePlan(Departage.Plan plan, boolean bloque) {
        Color accent = couleurParGroupe.getOrDefault(plan.rangDebut(), Theme.ACCENT);
        JPanel carte = new JPanel();
        carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDURE, 1),
                        Theme.marge(Theme.ESPACE_S))));
        carte.setAlignmentX(Component.LEFT_ALIGNMENT);

        StringBuilder noms = new StringBuilder();
        for (Equipe e : plan.equipes()) {
            if (noms.length() > 0) noms.append(", ");
            noms.append(e.getNumero());
        }
        JLabel titre = new JLabel("Egalite a " + plan.equipes().size() + " (equipes " + noms
                + ") - " + plan.rencontresGagnees() + " rencontres gagnees");
        titre.setFont(Theme.CORPS_GRAS);
        titre.setForeground(accent);
        titre.setAlignmentX(Component.LEFT_ALIGNMENT);
        carte.add(titre);

        if (bloque) {
            JLabel av = new JLabel("<html>En attente : une rencontre en cours peut encore amener "
                    + "une equipe dans ce groupe. Departage bloque pour l'instant.</html>");
            av.setFont(Theme.PETIT);
            av.setForeground(Theme.ACCENT_FONCE);
            av.setAlignmentX(Component.LEFT_ALIGNMENT);
            carte.add(av);
        }

        if (plan.poseeDoffice() != null) {
            int posee = service.positionFinale(plan.poseeDoffice());
            boolean dejaFixe = posee > 0;
            JPanel l = new JPanel(new BorderLayout(Theme.ESPACE_S, 0));
            l.setOpaque(false);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            JLabel txt = new JLabel("Position " + plan.positionPosee() + " : Equipe "
                    + plan.poseeDoffice().getNumero() + " (meilleure, placee d'office)");
            txt.setFont(Theme.CORPS);
            txt.setForeground(Theme.TEXTE);
            l.add(txt, BorderLayout.CENTER);
            if (!dejaFixe) {
                JButton valider = Composants.boutonSecondaire("Valider la place");
                valider.addActionListener(e -> {
                    service.fixerPositionDoffice(plan.poseeDoffice(), plan.positionPosee());
                    rafraichir();
                });
                l.add(valider, BorderLayout.EAST);
            }
            carte.add(Box.createVerticalStrut(Theme.ESPACE_XS));
            carte.add(l);
        }

        for (var paire : plan.paires()) {
            carte.add(Box.createVerticalStrut(Theme.ESPACE_XS));
            carte.add(lignePaire(plan, paire, bloque));
        }
        return carte;
    }

    private JComponent lignePaire(Departage.Plan plan, Departage.Paire paire, boolean bloque) {
        JPanel p = new JPanel(new BorderLayout(Theme.ESPACE_S, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        boolean dejaJoue = service.positionFinale(paire.a()) > 0 && service.positionFinale(paire.b()) > 0;
        // Un barrage est-il en cours pour cette paire ? (equipes en match dans un barrage non termine)
        boolean barrageEnCours = service.getBarrages().stream().anyMatch(bar ->
                !bar.estTermine() && bar.impliqueEquipe(paire.a()) && bar.impliqueEquipe(paire.b()));

        String desc = "Positions " + paire.positionHaute() + "-" + paire.positionBasse()
                + " : Equipe " + paire.a().getNumero() + " vs Equipe " + paire.b().getNumero();
        JLabel l = new JLabel(desc);
        l.setFont(Theme.CORPS);
        l.setForeground(Theme.TEXTE);
        p.add(l, BorderLayout.CENTER);

        if (dejaJoue) {
            JLabel ok = new JLabel("Termine");
            ok.setFont(Theme.PETIT);
            ok.setForeground(Theme.SUCCES);
            p.add(ok, BorderLayout.EAST);
            return p;
        }
        if (barrageEnCours) {
            JLabel enCours = new JLabel("Match en cours");
            enCours.setFont(Theme.PETIT_GRAS);
            enCours.setForeground(new Color(0xD3, 0x2F, 0x2F));
            p.add(enCours, BorderLayout.EAST);
            return p;
        }

        // Deux boutons : le meilleur choix (orange) + "Autre option" (menu).
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.ESPACE_XS, 0));
        actions.setOpaque(false);

        boolean pret = pretPourBarrage(paire.a(), paire.b());
        // Meilleur choix : tirage au sort si egalite parfaite, sinon rencontre.
        boolean meilleurEstTirage = paire.tirageAuSort();
        JButton principal = Composants.boutonPrimaire(meilleurEstTirage ? "Tirage au sort" : "Lancer la rencontre");
        principal.setEnabled(!bloque && (meilleurEstTirage || pret));
        if (bloque) {
            principal.setToolTipText("Une rencontre en cours peut encore rejoindre ce groupe.");
        } else if (!meilleurEstTirage && !pret) {
            principal.setToolTipText("Les deux equipes doivent etre en attente et une table libre.");
        }
        principal.addActionListener(e -> {
            if (meilleurEstTirage) tirageAuSort(paire);
            else lancerBarrage(paire);
        });
        actions.add(principal);

        // Autre option : menu (rencontre / tirage / changer le pose d'office si groupe impair).
        JButton autre = Composants.boutonSecondaire("Autre option");
        autre.setEnabled(!bloque);
        autre.addActionListener(e -> ouvrirAutreOption(plan, paire, autre, meilleurEstTirage));
        actions.add(autre);

        p.add(actions, BorderLayout.EAST);
        return p;
    }

    /** Menu "Autre option" d'une paire : forcer rencontre/tirage, ou changer le pose d'office. */
    private void ouvrirAutreOption(Departage.Plan plan, Departage.Paire paire, JComponent ancre,
                                   boolean meilleurEstTirage) {
        JPopupMenu menu = new JPopupMenu();

        // L'option inverse du meilleur choix.
        if (meilleurEstTirage) {
            JMenuItem rencontre = new JMenuItem("Faire une rencontre a la place");
            rencontre.setEnabled(pretPourBarrage(paire.a(), paire.b()));
            rencontre.addActionListener(e -> lancerBarrage(paire));
            menu.add(rencontre);
        } else {
            JMenuItem tirage = new JMenuItem("Tirage au sort a la place");
            tirage.addActionListener(e -> tirageAuSort(paire));
            menu.add(tirage);
        }

        // Groupe impair : possibilite de changer l'equipe posee d'office.
        if (plan.poseeDoffice() != null) {
            menu.addSeparator();
            JMenuItem changer = new JMenuItem("Changer l'equipe placee d'office...");
            changer.addActionListener(e -> changerPoseDoffice(plan));
            menu.add(changer);
        }

        menu.show(ancre, 0, ancre.getHeight());
    }

    /**
     * Change l'equipe placee d'office dans un groupe impair. L'equipe choisie prend la position
     * haute du groupe ; les autres seront re-appariees automatiquement au prochain rafraichissement.
     */
    private void changerPoseDoffice(Departage.Plan plan) {
        Equipe[] eqs = plan.equipes().toArray(new Equipe[0]);
        JComboBox<Equipe> combo = new JComboBox<>(eqs);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Equipe e) setText("Equipe " + e.getNumero());
                return this;
            }
        });
        if (plan.poseeDoffice() != null) combo.setSelectedItem(plan.poseeDoffice());
        int ok = JOptionPane.showConfirmDialog(this, combo,
                "Quelle equipe placer d'office en position " + plan.positionPosee() + " ?",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        Equipe choisie = (Equipe) combo.getSelectedItem();
        if (choisie != null) {
            service.forcerPoseDoffice(plan.rencontresGagnees(), choisie);
            rafraichir();
        }
    }

    /** Ligne du mini-tableau de suivi d'un barrage. */
    private JComponent ligneBarrage(Barrage b) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(true);
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDURE));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        String match = "Eq " + b.getEquipeA().getNumero() + " vs Eq " + b.getEquipeB().getNumero();
        String place = "pl. " + b.getPositionHaute() + "-" + b.getPositionBasse();
        String etat;
        if (b.estTirageAuSort()) {
            Equipe gagn = b.getGagnante();
            etat = "Tirage au sort - gagnant Eq " + (gagn != null ? gagn.getNumero() : "?");
        } else if (b.estTermine()) {
            Equipe gagn = b.getGagnante();
            etat = "Gagnant : Eq " + (gagn != null ? gagn.getNumero() : "?")
                    + " (" + b.matchsGagnes(org.club.tournoi.model.Match.Cote.A) + "-"
                    + b.matchsGagnes(org.club.tournoi.model.Match.Cote.B) + ")";
        } else {
            etat = formaterDuree(b.secondesEcoulees(System.currentTimeMillis()));
        }

        String[] vals = {match, place, etat};
        double[] poids = {2.0, 1.0, 2.0};
        for (int i = 0; i < vals.length; i++) {
            g.gridx = i; g.weightx = poids[i];
            JLabel l = new JLabel(vals[i]);
            l.setFont(Theme.CORPS);
            l.setForeground(b.estTermine() ? Theme.SUCCES : Theme.TEXTE);
            p.add(l, g);
        }

        if (!b.estTermine()) {
            JButton saisir = Composants.boutonSecondaire("Score");
            saisir.addActionListener(e -> saisirScoreBarrage(b));
            g.gridx = vals.length; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            p.add(saisir, g);

            JButton interrompre = Composants.boutonSecondaire("Interrompre");
            interrompre.setToolTipText("Annuler ce barrage : libere les tables, le departage reste a jouer.");
            interrompre.addActionListener(e -> interrompreBarrage(b));
            g.gridx = vals.length + 1;
            p.add(interrompre, g);
        }
        return p;
    }

    private void interrompreBarrage(Barrage b) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Interrompre le barrage Equipe " + b.getEquipeA().getNumero()
                        + " vs Equipe " + b.getEquipeB().getNumero() + " ?\n"
                        + "Les tables sont liberees et le departage reste a jouer.",
                "Interrompre le barrage", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            service.interrompreBarrage(b);
            rafraichir();
        }
    }

    // ------------------------------------------------------------------ actions

    private boolean pretPourBarrage(Equipe a, Equipe b) {
        boolean eq = a.getEtat() == EtatEquipe.EN_ATTENTE && b.getEtat() == EtatEquipe.EN_ATTENTE;
        boolean table = service.getTables().stream().anyMatch(Table::estDisponible);
        return eq && table;
    }

    private void lancerBarrage(Departage.Paire paire) {
        var libre = service.getTables().stream().filter(Table::estDisponible).findFirst();
        if (libre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucune table libre.", "Impossible",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            service.lancerBarrage(paire.a(), paire.b(), paire.positionHaute(), paire.positionBasse(),
                    libre.get());
            rafraichir();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Impossible", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saisirScoreBarrage(Barrage b) {
        DialogueScoreBarrage dlg = new DialogueScoreBarrage(
                (Frame) SwingUtilities.getWindowAncestor(this), b);
        dlg.setVisible(true);
        if (dlg.estValide()) {
            try {
                service.enregistrerScoresBarrage(b, dlg.getScoresA(), dlg.getScoresB());
                rafraichir();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Score invalide",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void tirageAuSort(Departage.Paire paire) {
        Equipe a = paire.a(), b = paire.b();
        Equipe gagnant = Math.random() < 0.5 ? a : b;
        Equipe perdant = gagnant.equals(a) ? b : a;
        int ok = JOptionPane.showConfirmDialog(this,
                "Egalite parfaite (memes rencontres, matchs et points).\n\n"
                        + "Tirage au sort :\n"
                        + "  Position " + paire.positionHaute() + " : Equipe " + gagnant.getNumero() + "\n"
                        + "  Position " + paire.positionBasse() + " : Equipe " + perdant.getNumero() + "\n\n"
                        + "Valider ce tirage ?",
                "Tirage au sort", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            service.fixerPositionsDepartage(gagnant, paire.positionHaute(), perdant, paire.positionBasse());
            rafraichir();
        }
    }

    /** Variante manuelle : options selon la taille du groupe. */
    private String formaterDuree(long secondes) {
        return String.format("%d:%02d", secondes / 60, secondes % 60);
    }

    private JComponent note(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.PETIT);
        l.setForeground(Theme.TEXTE_DISCRET);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(Theme.marge(Theme.ESPACE_S));
        return l;
    }
}
