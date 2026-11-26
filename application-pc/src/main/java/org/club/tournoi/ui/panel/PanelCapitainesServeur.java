package org.club.tournoi.ui.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.club.tournoi.serveur.AgentSynchro;
import org.club.tournoi.serveur.ClientServeur;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.ui.theme.Composants;
import org.club.tournoi.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Onglet "Capitaines" : depuis ici, le juge-arbitre lance ou arrête la
 * synchronisation avec le serveur et voit si elle fonctionne.
 * <p>
 * Contenu : configuration (URL + secret, mémorisés entre deux sessions via
 * {@link java.util.prefs.Preferences}), boutons Démarrer / Arrêter, ligne
 * de statut mise à jour à chaque cycle, et liste des litiges à trancher
 * (désaccord entre les deux capitaines sur un score).
 */
public class PanelCapitainesServeur extends JPanel {

    private final ServiceTournoi service;
    private final Gson gson;
    private final Preferences prefs =
            Preferences.userRoot().node("org/club/tournoi/serveur");

    private final JTextField champUrl = new JTextField(28);
    private final JPasswordField champSecret = new JPasswordField(20);
    private final JButton boutonDemarrer = Composants.boutonPrimaire("Démarrer la publication");
    private final JButton boutonArreter = Composants.boutonSecondaire("Arrêter");
    private final JLabel labelStatut = new JLabel("Publication arrêtée.");
    private final JLabel labelPoint = new JLabel("\u25CF"); // pastille ronde

    /** Zone où s'affichent les litiges à traiter. */
    private final JPanel zoneLitiges = new JPanel();
    private final JLabel titreLitiges = new JLabel("Litiges à traiter");

    private AgentSynchro agent;

    public PanelCapitainesServeur(ServiceTournoi service) {
        this.service = service;
        this.gson = new GsonBuilder().create();
        construire();
        chargerPrefs();
        majBoutons(false);
    }

    private void construire() {
        setBackground(Theme.FOND);
        setBorder(BorderFactory.createEmptyBorder(
                Theme.ESPACE_L, Theme.ESPACE_L, Theme.ESPACE_L, Theme.ESPACE_L));
        setLayout(new BorderLayout(Theme.ESPACE_M, Theme.ESPACE_M));

        // --- Titre ---
        JLabel titre = Composants.titre("Application capitaines (serveur web)");
        add(titre, BorderLayout.NORTH);

        // --- Carte de configuration ---
        JPanel carte = new JPanel(new GridBagLayout());
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDURE),
                BorderFactory.createEmptyBorder(Theme.ESPACE_L, Theme.ESPACE_L, Theme.ESPACE_L, Theme.ESPACE_L)));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S, Theme.ESPACE_S);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        carte.add(etiquette("Adresse du serveur :"), c);
        c.gridx = 1;
        champUrl.setToolTipText("Ex. https://votre-site.fr/tournoi");
        carte.add(champUrl, c);

        c.gridx = 0; c.gridy = 1;
        carte.add(etiquette("Secret partagé :"), c);
        c.gridx = 1;
        carte.add(champSecret, c);

        c.gridx = 1; c.gridy = 2;
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        boutons.setBackground(Theme.SURFACE);
        boutons.add(boutonDemarrer);
        boutons.add(boutonArreter);
        carte.add(boutons, c);

        // --- Ligne de statut ---
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        JPanel ligneStatut = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, Theme.ESPACE_S));
        ligneStatut.setBackground(Theme.SURFACE);
        labelPoint.setForeground(Theme.TEXTE_DISCRET);
        labelStatut.setFont(Theme.CORPS);
        ligneStatut.add(labelPoint);
        ligneStatut.add(labelStatut);
        carte.add(ligneStatut, c);

        // --- Aide ---
        c.gridy = 4;
        JLabel aide = new JLabel("<html><body style='width:420px'>"
                + "Une fois démarrée, la publication envoie l'état des rencontres "
                + "au serveur toutes les quelques secondes. Les capitaines voient "
                + "alors leur table, leur adversaire et les 5 matchs sur leur "
                + "téléphone. Laissez cet onglet actif pendant le tournoi.</body></html>");
        aide.setFont(Theme.PETIT);
        aide.setForeground(Theme.TEXTE_DISCRET);
        carte.add(aide, c);

        JPanel enveloppe = new JPanel(new BorderLayout(0, Theme.ESPACE_M));
        enveloppe.setBackground(Theme.FOND);
        JPanel hautConfig = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hautConfig.setBackground(Theme.FOND);
        hautConfig.add(carte);
        enveloppe.add(hautConfig, BorderLayout.NORTH);

        // --- Zone "Litiges à traiter" ---
        JPanel blocLitiges = new JPanel(new BorderLayout(0, Theme.ESPACE_S));
        blocLitiges.setBackground(Theme.FOND);
        titreLitiges.setFont(Theme.SOUS_TITRE);
        titreLitiges.setForeground(Theme.TEXTE);
        titreLitiges.setBorder(BorderFactory.createEmptyBorder(Theme.ESPACE_M, 0, 0, 0));
        blocLitiges.add(titreLitiges, BorderLayout.NORTH);

        zoneLitiges.setLayout(new BoxLayout(zoneLitiges, BoxLayout.Y_AXIS));
        zoneLitiges.setBackground(Theme.FOND);
        JScrollPane scroll = new JScrollPane(zoneLitiges);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        blocLitiges.add(scroll, BorderLayout.CENTER);

        enveloppe.add(blocLitiges, BorderLayout.CENTER);
        add(enveloppe, BorderLayout.CENTER);

        majZoneLitiges(java.util.List.of()); // état initial : aucun litige

        // --- Actions ---
        boutonDemarrer.addActionListener(e -> demarrer());
        boutonArreter.addActionListener(e -> arreter());
    }

    /** Reconstruit la zone des litiges à partir de la liste reçue de l'agent. */
    private void majZoneLitiges(java.util.List<AgentSynchro.LitigeInfo> litiges) {
        zoneLitiges.removeAll();
        if (litiges.isEmpty()) {
            titreLitiges.setText("Litiges à traiter");
            JLabel vide = new JLabel("Aucun litige. Tout est en ordre.");
            vide.setFont(Theme.CORPS);
            vide.setForeground(Theme.TEXTE_DISCRET);
            vide.setBorder(BorderFactory.createEmptyBorder(Theme.ESPACE_S, Theme.ESPACE_S, 0, 0));
            vide.setAlignmentX(LEFT_ALIGNMENT);
            zoneLitiges.add(vide);
        } else {
            titreLitiges.setText("Litiges à traiter (" + litiges.size() + ")");
            for (AgentSynchro.LitigeInfo lit : litiges) {
                zoneLitiges.add(carteLitige(lit));
                zoneLitiges.add(Box.createVerticalStrut(Theme.ESPACE_S));
            }
        }
        zoneLitiges.revalidate();
        zoneLitiges.repaint();
    }

    /** Une carte pour un litige : détails des deux saisies + 2 boutons. */
    private JPanel carteLitige(AgentSynchro.LitigeInfo lit) {
        JPanel carte = new JPanel();
        carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
        carte.setBackground(Theme.SURFACE);
        carte.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ERREUR),
                BorderFactory.createEmptyBorder(Theme.ESPACE_M, Theme.ESPACE_M, Theme.ESPACE_M, Theme.ESPACE_M)));
        carte.setAlignmentX(LEFT_ALIGNMENT);
        carte.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        String tables = (lit.table2() != null)
                ? ("Tables " + lit.table1() + " et " + lit.table2())
                : ("Table " + lit.table1());
        JLabel entete = new JLabel(lit.libelleMatch() + " — Équipe n°" + lit.numeroEquipeA()
                + " contre Équipe n°" + lit.numeroEquipeB() + "  (" + tables + ")");
        entete.setFont(Theme.CORPS_GRAS);
        entete.setForeground(Theme.TEXTE);
        entete.setAlignmentX(LEFT_ALIGNMENT);
        carte.add(entete);
        carte.add(Box.createVerticalStrut(Theme.ESPACE_S));

        // Ce qu'a saisi chaque capitaine (point de vue équipe A / équipe B)
        JLabel saisieA = new JLabel("Capitaine équipe A a saisi : "
                + lit.capAscoreA() + " – " + lit.capAscoreB()
                + "   (A – B)");
        JLabel saisieB = new JLabel("Capitaine équipe B a saisi : "
                + lit.capBscoreA() + " – " + lit.capBscoreB()
                + "   (A – B)");
        saisieA.setFont(Theme.CORPS);
        saisieB.setFont(Theme.CORPS);
        saisieA.setForeground(Theme.TEXTE);
        saisieB.setForeground(Theme.TEXTE);
        saisieA.setAlignmentX(LEFT_ALIGNMENT);
        saisieB.setAlignmentX(LEFT_ALIGNMENT);
        carte.add(saisieA);
        carte.add(saisieB);
        carte.add(Box.createVerticalStrut(Theme.ESPACE_M));

        // Boutons
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.ESPACE_S, 0));
        boutons.setBackground(Theme.SURFACE);
        boutons.setAlignmentX(LEFT_ALIGNMENT);
        boutons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        JButton bTrancher = Composants.boutonPrimaire("Trancher moi-même");
        JButton bResaisir = Composants.boutonSecondaire("Faire resaisir");
        bTrancher.addActionListener(e -> dialogueTrancher(lit));
        bResaisir.addActionListener(e -> confirmerResaisir(lit));
        boutons.add(bTrancher);
        boutons.add(bResaisir);
        carte.add(boutons);

        return carte;
    }

    /** Ouvre une petite fenêtre pour saisir le score qui fait autorité. */
    private void dialogueTrancher(AgentSynchro.LitigeInfo lit) {
        if (agent == null) return;
        JTextField champA = new JTextField(4);
        JTextField champB = new JTextField(4);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.ESPACE_S, Theme.ESPACE_S));
        p.add(new JLabel("Équipe n°" + lit.numeroEquipeA() + " :"));
        p.add(champA);
        p.add(new JLabel("  –  Équipe n°" + lit.numeroEquipeB() + " :"));
        p.add(champB);

        int rep = JOptionPane.showConfirmDialog(this, p,
                "Score qui fait autorité — " + lit.libelleMatch(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (rep != JOptionPane.OK_OPTION) return;

        try {
            int a = Integer.parseInt(champA.getText().trim());
            int b = Integer.parseInt(champB.getText().trim());
            agent.trancherLitige(lit.idRencontre(), lit.indexMatch(), a, b);
            JOptionPane.showMessageDialog(this,
                    "Score enregistré. Les téléphones se mettront à jour dans quelques secondes.",
                    "Litige tranché", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Entrez deux nombres valides.",
                    "Score invalide", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Score refusé : " + ex.getMessage(),
                    "Impossible d'enregistrer", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Demande confirmation puis efface les saisies pour faire resaisir. */
    private void confirmerResaisir(AgentSynchro.LitigeInfo lit) {
        if (agent == null) return;
        int rep = JOptionPane.showConfirmDialog(this,
                "Effacer les deux saisies de « " + lit.libelleMatch()
                        + " » ? Les capitaines devront resaisir ce match.",
                "Faire resaisir", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (rep != JOptionPane.OK_OPTION) return;
        agent.faireResaisir(lit.idRencontre(), lit.indexMatch());
    }

    private JLabel etiquette(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Theme.CORPS_GRAS);
        l.setForeground(Theme.TEXTE);
        return l;
    }

    private void demarrer() {
        String url = champUrl.getText().trim();
        String secret = new String(champSecret.getPassword());
        if (url.isEmpty() || secret.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Renseignez l'adresse du serveur et le secret partagé.",
                    "Configuration incomplète", JOptionPane.WARNING_MESSAGE);
            return;
        }
        enregistrerPrefs(url, secret);

        ClientServeur client = new ClientServeur(url, secret);
        agent = new AgentSynchro(service, client, gson, 3);
        agent.setSurStatut(this::afficherStatut);
        agent.setSurLitiges(this::majZoneLitiges);
        agent.demarrer();
        majBoutons(true);
    }

    private void arreter() {
        if (agent != null) {
            agent.arreter();
            agent = null;
        }
        majBoutons(false);
        afficherStatut("Publication arrêtée.");
    }

    private void afficherStatut(String msg) {
        labelStatut.setText(msg);
        boolean ok = msg.startsWith("Publié");
        boolean erreur = msg.startsWith("Échec") || msg.startsWith("Erreur");
        labelPoint.setForeground(ok ? Theme.SUCCES : (erreur ? Theme.ERREUR : Theme.TEXTE_DISCRET));
    }

    private void majBoutons(boolean actif) {
        boutonDemarrer.setEnabled(!actif);
        boutonArreter.setEnabled(actif);
        champUrl.setEnabled(!actif);
        champSecret.setEnabled(!actif);
    }

    // --- Mémorisation URL + secret entre deux lancements ---
    private void chargerPrefs() {
        champUrl.setText(prefs.get("url", "https://votre-site.fr/tournoi"));
        champSecret.setText(prefs.get("secret", ""));
    }
    private void enregistrerPrefs(String url, String secret) {
        prefs.put("url", url);
        prefs.put("secret", secret);
    }
}
