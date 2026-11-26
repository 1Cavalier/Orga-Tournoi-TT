package org.club.tournoi.service;

import org.club.tournoi.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coeur metier du tournoi : la seule classe qui modifie l'etat.
 * <p>
 * C'est le "vrai" controleur qui manquait a l'ancienne version (ou {@code TournoiController}
 * n'etait qu'un porteur de listes et la logique vivait dans des {@code ActionListener}).
 * L'interface graphique appelle uniquement ces methodes ; elle ne touche jamais directement
 * au modele. Consequence directe : la generation automatique de rencontres (systeme suisse)
 * s'appuiera sur les memes methodes que les clics manuels, sans code duplique.
 * <p>
 * Chaque operation qui change l'etat notifie les {@link TournoiListener} abonnes, ce qui
 * rafraichit les vues concernees sans cascade manuelle.
 */
public class ServiceTournoi {

    private final EtatTournoi etat;
    private final List<TournoiListener> listeners = new ArrayList<>();

    /**
     * La base joueurs permanente du club. Le tournoi garde ses propres
     * copies (dans {@code etat.getJoueurs()}) avec le même id, ce qui permet
     * de synchroniser les modifications entre la base et le tournoi.
     */
    private BaseJoueurs baseJoueurs = new BaseJoueurs();

    public ServiceTournoi(EtatTournoi etat) {
        this.etat = etat;
    }

    public BaseJoueurs getBaseJoueurs() {
        return baseJoueurs;
    }

    public void setBaseJoueurs(BaseJoueurs baseJoueurs) {
        this.baseJoueurs = baseJoueurs == null ? new BaseJoueurs() : baseJoueurs;
    }

    // ------------------------------------------------------------------
    // Base joueurs permanente + pointage
    // ------------------------------------------------------------------

    /**
     * Ajoute un joueur à la BASE permanente (pas au tournoi). Détecte les
     * homonymes : si un joueur de même nom/prénom existe déjà, l'appelant
     * (l'UI) décide via {@code forcer}. Renvoie le joueur créé, ou null si
     * un homonyme existe et que {@code forcer} est false.
     */
    public Joueur ajouterJoueurBase(String nom, String prenom, Categorie categorie, boolean forcer) {
        if (!forcer && baseJoueurs.trouverHomonyme(nom, prenom) != null) {
            return null; // doublon détecté, on laisse l'UI demander confirmation
        }
        Joueur joueur = new Joueur(nom, prenom, categorie); // id unique auto
        baseJoueurs.ajouter(joueur);
        notifierJoueurs();
        return joueur;
    }

    /**
     * Définit (ou retire, avec {@link Symbole#AUCUN}) le symbole d'un joueur
     * présent au tournoi. Deux joueurs de même symbole devront être dans la
     * même équipe. On répercute aussi sur la base permanente (même id) pour
     * rester cohérent.
     */
    public void definirSymbole(Joueur joueur, Symbole symbole) {
        Symbole s = symbole == null ? Symbole.AUCUN : symbole;
        // Copie dans le tournoi
        for (Joueur jt : etat.getJoueurs()) {
            if (jt.getId().equals(joueur.getId())) jt.setSymbole(s);
        }
        // Fiche dans la base
        Joueur base = baseJoueurs.parId(joueur.getId());
        if (base != null) base.setSymbole(s);
        notifierJoueurs();
        notifierEquipes();
    }

    /** Un joueur de la base est-il présent (pointé) dans le tournoi ? */
    public boolean estDansTournoi(Joueur joueurBase) {
        return etat.getJoueurs().stream().anyMatch(j -> j.getId().equals(joueurBase.getId()));
    }

    /**
     * Pointe un joueur de la base dans le tournoi : on en ajoute une COPIE
     * (même id) à {@code etat.getJoueurs()}. Sans effet s'il est déjà présent.
     */
    public void pointerDansTournoi(Joueur joueurBase) {
        if (estDansTournoi(joueurBase)) return;
        Joueur copie = new Joueur(joueurBase.getId(), joueurBase.getNom(),
                joueurBase.getPrenom(), joueurBase.getCategorie());
        copie.setSymbole(joueurBase.getSymbole());
        etat.getJoueurs().add(copie);
        notifierJoueurs();
    }

    /**
     * Retire un joueur DU TOURNOI (pas de la base). Refuse s'il est engagé
     * dans une équipe (comme la suppression normale).
     */
    public void retirerDuTournoi(Joueur joueurTournoi) {
        Optional<Equipe> equipe = etat.getEquipes().stream()
                .filter(e -> e.getJoueurs().stream().anyMatch(j -> j.getId().equals(joueurTournoi.getId())))
                .findFirst();
        if (equipe.isPresent()) {
            throw new IllegalStateException(
                    "Ce joueur fait partie de l'equipe " + equipe.get().getNumero()
                    + ". Retirez-le d'abord de son equipe.");
        }
        etat.getJoueurs().removeIf(j -> j.getId().equals(joueurTournoi.getId()));
        notifierJoueurs();
    }

    /**
     * Modifie un joueur : met à jour la BASE et, s'il est présent, la copie
     * dans le TOURNOI (synchronisation). Se base sur l'id.
     */
    public void modifierJoueurBase(Joueur joueurBase, String nom, String prenom, Categorie categorie) {
        joueurBase.setNom(nom);
        joueurBase.setPrenom(prenom);
        joueurBase.setCategorie(categorie);
        // Répercute sur la copie dans le tournoi, si présente.
        for (Joueur jt : etat.getJoueurs()) {
            if (jt.getId().equals(joueurBase.getId())) {
                jt.setNom(nom);
                jt.setPrenom(prenom);
                jt.setCategorie(categorie);
            }
        }
        notifierJoueurs();
        notifierEquipes();
        notifierClassement();
    }

    /**
     * Supprime un joueur de la BASE. S'il est présent dans le tournoi, il en
     * est aussi retiré (l'UI aura demandé confirmation au préalable). Refuse
     * s'il est engagé dans une équipe.
     */
    public void supprimerJoueurBase(Joueur joueurBase) {
        Optional<Equipe> equipe = etat.getEquipes().stream()
                .filter(e -> e.getJoueurs().stream().anyMatch(j -> j.getId().equals(joueurBase.getId())))
                .findFirst();
        if (equipe.isPresent()) {
            throw new IllegalStateException(
                    "Ce joueur fait partie de l'equipe " + equipe.get().getNumero()
                    + ". Retirez-le d'abord de son equipe.");
        }
        etat.getJoueurs().removeIf(j -> j.getId().equals(joueurBase.getId()));
        baseJoueurs.supprimer(joueurBase);
        notifierJoueurs();
    }

    // ------------------------------------------------------------------
    // Observateurs
    // ------------------------------------------------------------------

    public void ajouterListener(TournoiListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void retirerListener(TournoiListener listener) {
        listeners.remove(listener);
    }

    private void notifierJoueurs()     { listeners.forEach(TournoiListener::joueursModifies); }
    private void notifierEquipes()     { listeners.forEach(TournoiListener::equipesModifiees); }
    private void notifierTables()      { listeners.forEach(TournoiListener::tablesModifiees); }

    /** Force un rafraichissement de l'affichage des tables (ex. apres changement de parametre). */
    public void notifierTablesModifiees() { notifierTables(); }
    private void notifierRencontres()  { listeners.forEach(TournoiListener::rencontresModifiees); }
    private void notifierClassement()  { listeners.forEach(TournoiListener::classementModifie); }

    // ------------------------------------------------------------------
    // Acces lecture
    // ------------------------------------------------------------------

    public EtatTournoi getEtat() {
        return etat;
    }

    /** Parametres generaux du tournoi (points, durees). */
    public ParametresTournoi getParametres() {
        return etat.getParametres();
    }

    // ------------------------------------------------------------------
    // Systeme suisse (moteur d'appariement)
    // ------------------------------------------------------------------

    /** Propose la prochaine rencontre a lancer (ou un conseil d'attente), sans rien appliquer. */
    public MoteurSuisse.Suggestion suggererProchaineRencontre() {
        if (estEnModeDepartage()) {
            return new MoteurSuisse.Suggestion(null, null, List.of(), false,
                    "Mode departage actif : plus de nouvelle rencontre suisse. Terminez les rencontres "
                    + "en cours puis lancez les departages.");
        }
        return new MoteurSuisse(this).suggerer(System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // Departages de fin de tournoi
    // ------------------------------------------------------------------

    /**
     * Active le mode "preparation des departages" : plus aucune nouvelle rencontre suisse n'est
     * proposee ou lancee (les rencontres en cours vont a leur terme normalement). On peut ensuite
     * consulter {@link #analyserDepartages()} et lancer les departages necessaires.
     */
    public void preparerDepartages() {
        etat.getParametres().setModeDepartage(true);
        notifierRencontres();
    }

    /** Quitte le mode departage (reprise possible d'un fonctionnement normal). */
    public void reprendreApresDepartages() {
        etat.getParametres().setModeDepartage(false);
        notifierRencontres();
    }

    public boolean estEnModeDepartage() {
        return etat.getParametres().isModeDepartage();
    }

    /** Analyse complete : departages a prevoir, supposes, et equipes sures de leur place. */
    public PreparationDepartages.Analyse analyserDepartages() {
        return PreparationDepartages.analyser(getEquipes(), etat.getRencontres());
    }

    /** Analyse le classement courant et renvoie les egalites qui touchent le podium (places 1-3). */
    public List<Departage.Egalite> egalitesPodium() {
        return Departage.egalitesPodium(classement());
    }

    /** Plans de departage (positionnement) pour chaque groupe a egalite du classement courant. */
    public List<Departage.Plan> plansDepartage() {
        return Departage.plans(classement(), etat.getPoseDofficeForce());
    }

    /**
     * Force, pour le groupe a {@code victoires} rencontres gagnees, l'equipe placee d'office (au
     * lieu de la meilleure automatique). Utile quand on veut manuellement decider qui garde la
     * place fixe d'un groupe impair.
     */
    public void forcerPoseDoffice(int victoires, Equipe equipe) {
        etat.getPoseDofficeForce().put(victoires, equipe.getNumero());
        notifierClassement();
    }

    /**
     * Indique si un groupe a egalite (a {@code victoires} rencontres gagnees) peut etre departage
     * maintenant, c.-a-d. qu'aucune equipe actuellement en match (rencontre normale) ne peut le
     * rejoindre. Une equipe en rencontre a {@code victoires - 1} peut monter a {@code victoires}
     * en gagnant : tant qu'une telle equipe joue, le groupe est bloque.
     */
    public boolean groupeDepartageableMaintenant(int victoires) {
        for (Rencontre r : getRencontresEnCours()) {
            // Chaque equipe d'une rencontre en cours a un certain nombre de victoires "acquises".
            int vA = rencontresGagneesDe(r.getEquipeA());
            int vB = rencontresGagneesDe(r.getEquipeB());
            // Le gagnant montera de +1. Si l'une peut atteindre exactement 'victoires', risque.
            if (vA + 1 == victoires || vB + 1 == victoires) {
                return false;
            }
            // Une equipe deja a 'victoires' et en train de jouer changerait aussi le groupe.
            if (vA == victoires || vB == victoires) {
                return false;
            }
        }
        return true;
    }

    private int rencontresGagneesDe(Equipe e) {
        return (int) etat.getRencontres().stream()
                .filter(Rencontre::estTerminee)
                .filter(r -> e.equals(r.getGagnante()))
                .count();
    }

    /** Les barrages (matchs de departage) en cours ou termines. */
    public List<Barrage> getBarrages() {
        return etat.getBarrages();
    }

    /** Position finale attribuee a une equipe par les departages, ou -1 si aucune. */
    public int positionFinale(Equipe equipe) {
        return etat.getPositionsFinales().getOrDefault(equipe.getNumero(), -1);
    }

    /**
     * Lance un match de departage (barrage) entre deux equipes, pour les positions donnees. Le
     * barrage occupe une table et demarre un chrono, mais ne compte pas dans les statistiques du
     * tournoi. Necessite le mode departage actif.
     */
    public Barrage lancerBarrage(Equipe a, Equipe b, int positionHaute, int positionBasse, Table table) {
        if (a.equals(b)) {
            throw new IllegalArgumentException("Une equipe ne peut pas se departager avec elle-meme.");
        }
        if (a.getEtat() != EtatEquipe.EN_ATTENTE || b.getEtat() != EtatEquipe.EN_ATTENTE) {
            throw new IllegalStateException("Les deux equipes doivent etre en attente.");
        }
        if (!table.estDisponible()) {
            throw new IllegalStateException("La table " + table.getNumero() + " n'est pas disponible.");
        }
        boolean avecEcart = etat.getParametres().isPointEcart();
        Barrage barrage = Barrage.creer(a, b, positionHaute, positionBasse, table.getNumero(), avecEcart);

        // Cherche une 2e table adjacente par numero (table +1 ou -1) si elle est libre, pour
        // etaler le barrage sur 2 tables comme une rencontre normale.
        Table voisine = tableParNumero(table.getNumero() + 1).filter(Table::estDisponible)
                .or(() -> tableParNumero(table.getNumero() - 1).filter(Table::estDisponible))
                .orElse(null);
        if (voisine != null) {
            barrage.setNumeroTable2(voisine.getNumero());
            voisine.setOccupee(true);
        }

        barrage.demarrer(System.currentTimeMillis());
        a.setEtat(EtatEquipe.EN_MATCH);
        b.setEtat(EtatEquipe.EN_MATCH);
        table.setOccupee(true);
        etat.getBarrages().add(barrage);
        notifierRencontres();
        notifierEquipes();
        notifierTables();
        return barrage;
    }

    /**
     * Interrompt un barrage en cours : libere la ou les tables, remet les equipes en attente et
     * retire le barrage. Aucune position n'est attribuee (le departage reste a jouer).
     */
    public void interrompreBarrage(Barrage barrage) {
        barrage.getEquipeA().setEtat(EtatEquipe.EN_ATTENTE);
        barrage.getEquipeB().setEtat(EtatEquipe.EN_ATTENTE);
        tableParNumero(barrage.getNumeroTable()).ifPresent(t -> t.setOccupee(false));
        if (barrage.utiliseDeuxTables()) {
            tableParNumero(barrage.getNumeroTable2()).ifPresent(t -> t.setOccupee(false));
        }
        etat.getBarrages().remove(barrage);
        notifierRencontres();
        notifierEquipes();
        notifierTables();
        notifierClassement();
    }

    /**
     * Enregistre les scores d'un barrage. Fige les positions finales (gagnant -> position haute,
     * perdant -> position basse), libere la table et remet les equipes en attente. N'affecte PAS
     * les statistiques du tournoi.
     */
    public void enregistrerScoresBarrage(Barrage barrage, int[] scoresA, int[] scoresB) {
        var matchs = barrage.getMatchs();
        if (scoresA.length != matchs.size() || scoresB.length != matchs.size()) {
            throw new IllegalArgumentException("Nombre de scores incorrect.");
        }
        // Memorise les rangs avant application, pour les fleches montee/descente.
        memoriserRangs();
        boolean avecEcart = etat.getParametres().isPointEcart();
        // Validation tout-ou-rien.
        for (int i = 0; i < matchs.size(); i++) {
            new MatchSimple("validation", null, null, avecEcart).enregistrerScore(scoresA[i], scoresB[i]);
        }
        for (int i = 0; i < matchs.size(); i++) {
            matchs.get(i).reinitialiser();
            matchs.get(i).enregistrerScore(scoresA[i], scoresB[i]);
        }

        Equipe gagnante = barrage.getGagnante();
        Equipe perdante = barrage.getPerdante();
        if (gagnante != null) {
            etat.getPositionsFinales().put(gagnante.getNumero(), barrage.getPositionHaute());
            etat.getPositionsFinales().put(perdante.getNumero(), barrage.getPositionBasse());
        }
        // Libere la ou les tables et remet les equipes en attente.
        barrage.getEquipeA().setEtat(EtatEquipe.EN_ATTENTE);
        barrage.getEquipeB().setEtat(EtatEquipe.EN_ATTENTE);
        tableParNumero(barrage.getNumeroTable()).ifPresent(t -> t.setOccupee(false));
        if (barrage.utiliseDeuxTables()) {
            tableParNumero(barrage.getNumeroTable2()).ifPresent(t -> t.setOccupee(false));
        }

        notifierRencontres();
        notifierEquipes();
        notifierTables();
        notifierClassement();
    }

    /** Fixe directement les positions (pour un tirage au sort, sans jouer de match). */
    public void fixerPositionsDepartage(Equipe haute, int positionHaute, Equipe basse, int positionBasse) {
        // Memorise les rangs avant, pour que les fleches montee/descente refletent l'inversion.
        memoriserRangs();
        // Garde une trace visible du tirage dans le suivi des barrages.
        Barrage tirage = Barrage.creerTirageAuSort(haute, basse, positionHaute, positionBasse);
        etat.getBarrages().add(tirage);
        etat.getPositionsFinales().put(haute.getNumero(), positionHaute);
        etat.getPositionsFinales().put(basse.getNumero(), positionBasse);
        notifierRencontres();
        notifierClassement();
    }

    /** Position finale d'office (equipe posee sans jouer), a enregistrer aussi. */
    public void fixerPositionDoffice(Equipe equipe, int position) {
        etat.getPositionsFinales().put(equipe.getNumero(), position);
        notifierClassement();
    }

    /**
     * Lance une rencontre de departage entre deux equipes a egalite. Contrairement a une rencontre
     * normale, le re-affrontement est autorise (les deux equipes se sont deja rencontrees pendant
     * le tournoi). Le resultat comptera comme une rencontre gagnee de plus pour la gagnante, ce qui
     * les separe au classement.
     *
     * @throws IllegalStateException si une equipe n'est pas disponible ou la table indisponible
     */
    public Rencontre lancerDepartage(Equipe equipeA, Equipe equipeB, List<Table> tables) {
        if (equipeA.equals(equipeB)) {
            throw new IllegalArgumentException("Une equipe ne peut pas se departager avec elle-meme.");
        }
        if (equipeA.getEtat() != EtatEquipe.EN_ATTENTE || equipeB.getEtat() != EtatEquipe.EN_ATTENTE) {
            throw new IllegalStateException("Les deux equipes doivent etre en attente pour un departage.");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("Il faut au moins une table.");
        }
        for (Table t : tables) {
            if (!t.estDisponible()) {
                throw new IllegalStateException("La table " + t.getNumero() + " n'est pas disponible.");
            }
        }
        int rotation = (int) etat.getRencontres().stream()
                .filter(r -> r.impliqueEquipe(equipeA))
                .count();
        boolean avecEcart = etat.getParametres().isPointEcart();
        int table1 = tables.get(0).getNumero();
        int table2 = tables.size() >= 2 ? tables.get(1).getNumero() : -1;

        Rencontre rencontre = Rencontre.creer(equipeA, equipeB, table1, table2, rotation, avecEcart);
        rencontre.demarrer(System.currentTimeMillis());
        equipeA.setEtat(EtatEquipe.EN_MATCH);
        equipeB.setEtat(EtatEquipe.EN_MATCH);
        for (Table t : tables) {
            t.setOccupee(true);
        }
        etat.getRencontres().add(rencontre);
        notifierRencontres();
        notifierEquipes();
        notifierTables();
        return rencontre;
    }

    /**
     * Choisit 1 ou 2 tables parmi les tables libres (triees par numero). Pour 2 tables, elles
     * doivent etre voisines (numeros consecutifs) ; a defaut, on se rabat sur une seule table.
     */
    private List<Table> choisirTablesAdjacentes(List<Table> libres, int nbTables) {
        if (nbTables <= 1 || libres.size() < 2) {
            return new ArrayList<>(libres.subList(0, Math.min(1, libres.size())));
        }
        for (int i = 0; i + 1 < libres.size(); i++) {
            if (libres.get(i + 1).getNumero() == libres.get(i).getNumero() + 1) {
                List<Table> paire = new ArrayList<>();
                paire.add(libres.get(i));
                paire.add(libres.get(i + 1));
                return paire;
            }
        }
        List<Table> une = new ArrayList<>();
        une.add(libres.get(0));
        return une;
    }

    /**
     * Premier lancement du tournoi facon suisse : apparie les equipes en attente deux a deux dans
     * l'ordre (1 vs 2, 3 vs 4, ...) et lance ces rencontres sur les tables libres. Choisit 1 ou 2
     * tables par rencontre selon la place. Ne fait rien si moins de deux equipes ou aucune table.
     *
     * @return le nombre de rencontres effectivement lancees
     */
    public int premierLancement() {
        if (estEnModeDepartage()) {
            return 0; // fige : aucune nouvelle rencontre suisse en mode departage
        }
        List<Equipe> attente = new ArrayList<>(etat.getEquipes().stream()
                .filter(e -> e.getEtat() == EtatEquipe.EN_ATTENTE)
                .sorted(Comparator.comparingInt(Equipe::getNumero))
                .toList());
        int lances = 0;
        while (attente.size() >= 2) {
            List<Table> libres = etat.getTables().stream()
                    .filter(Table::estDisponible)
                    .sorted(Comparator.comparingInt(Table::getNumero))
                    .toList();
            if (libres.isEmpty()) break;

            Equipe a = attente.remove(0);
            Equipe b = attente.remove(0);
            // Rencontres encore a lancer apres celle-ci (paires restantes dans la file).
            int rencontresRestantes = attente.size() / 2;
            // On garde 1 table pour chacune des rencontres restantes ; si le surplus permet de
            // doubler celle-ci (il faut 2 tables pour elle + 1 par rencontre restante), on double.
            int nbTables = (libres.size() >= 2 + rencontresRestantes) ? 2 : 1;
            List<Table> choisies = choisirTablesAdjacentes(libres, nbTables);
            lancerRencontre(a, b, new ArrayList<>(choisies));
            lances++;
        }
        return lances;
    }

    public List<Joueur> getJoueurs() {
        return etat.getJoueurs();
    }

    public List<Equipe> getEquipes() {
        return etat.getEquipes();
    }

    public List<Table> getTables() {
        return etat.getTables();
    }

    public List<Rencontre> getRencontres() {
        return etat.getRencontres();
    }

    /** Joueurs qui ne sont assignes a aucune equipe. */
    public List<Joueur> getJoueursSansEquipe() {
        List<Joueur> assignes = new ArrayList<>();
        for (Equipe e : etat.getEquipes()) {
            assignes.addAll(e.getJoueurs());
        }
        List<Joueur> libres = new ArrayList<>();
        for (Joueur j : etat.getJoueurs()) {
            if (!assignes.contains(j)) {
                libres.add(j);
            }
        }
        return libres;
    }

    public Optional<Equipe> equipeParNumero(int numero) {
        return etat.getEquipes().stream().filter(e -> e.getNumero() == numero).findFirst();
    }

    public Optional<Table> tableParNumero(int numero) {
        return etat.getTables().stream().filter(t -> t.getNumero() == numero).findFirst();
    }

    /** Rencontres encore en cours (score incomplet). */
    public List<Rencontre> getRencontresEnCours() {
        return etat.getRencontres().stream().filter(r -> !r.estTerminee()).toList();
    }

    /** Equipe qui occupe une table donnee, si elle est occupee. */
    public Optional<Rencontre> rencontreSurTable(int numeroTable) {
        return getRencontresEnCours().stream()
                .filter(r -> r.getNumeroTable() == numeroTable
                        || (r.utiliseDeuxTables() && r.getNumeroTable2() == numeroTable))
                .findFirst();
    }

    /** Barrage (match de departage) en cours sur une table donnee, s'il y en a un. */
    public Optional<Barrage> barrageSurTable(int numeroTable) {
        return etat.getBarrages().stream()
                .filter(b -> !b.estTermine()
                        && (b.getNumeroTable() == numeroTable
                            || (b.utiliseDeuxTables() && b.getNumeroTable2() == numeroTable)))
                .findFirst();
    }

    public StatistiquesEquipe statistiques(Equipe equipe) {
        return CalculStatistiques.pour(equipe, etat.getRencontres());
    }

    public List<Classement.Ligne> classement() {
        return Classement.calculer(etat.getEquipes(), etat.getRencontres(), etat.getPositionsFinales());
    }

    /**
     * Memorise le classement courant comme reference pour les fleches montee/descente. A appeler
     * juste AVANT d'enregistrer un score, pour comparer l'avant et l'apres.
     */
    public void memoriserRangs() {
        etat.getRangsPrecedents().clear();
        for (Classement.Ligne l : classement()) {
            etat.getRangsPrecedents().put(l.equipe().getNumero(), l.rang());
        }
    }

    /**
     * Variation de rang d'une equipe depuis le dernier {@link #memoriserRangs()} : positif si
     * l'equipe est montee (a gagne des places), negatif si elle est descendue, 0 si stable ou
     * pas de reference. Ex : rang precedent 5, rang actuel 3 -> +2.
     */
    public int variationRang(Equipe equipe) {
        Integer avant = etat.getRangsPrecedents().get(equipe.getNumero());
        if (avant == null) return 0;
        int actuel = classement().stream()
                .filter(l -> l.equipe().equals(equipe))
                .map(Classement.Ligne::rang)
                .findFirst().orElse(avant);
        return avant - actuel; // positif = montee
    }

    // ------------------------------------------------------------------
    // Joueurs
    // ------------------------------------------------------------------

    public Joueur ajouterJoueur(String nom, String prenom, Categorie categorie) {
        return ajouterJoueur(nom, prenom, categorie, Symbole.AUCUN);
    }

    public Joueur ajouterJoueur(String nom, String prenom, Categorie categorie, Symbole symbole) {
        Joueur joueur = new Joueur(nom, prenom, categorie);
        joueur.setSymbole(symbole);
        etat.getJoueurs().add(joueur);
        notifierJoueurs();
        return joueur;
    }

    public void modifierJoueur(Joueur joueur, String nom, String prenom, Categorie categorie) {
        modifierJoueur(joueur, nom, prenom, categorie, joueur.getSymbole());
    }

    public void modifierJoueur(Joueur joueur, String nom, String prenom, Categorie categorie, Symbole symbole) {
        joueur.setNom(nom);
        joueur.setPrenom(prenom);
        joueur.setCategorie(categorie);
        joueur.setSymbole(symbole);
        notifierJoueurs();
        notifierEquipes();
        notifierClassement();
    }

    /**
     * Supprime un joueur du tournoi.
     * @throws IllegalStateException si le joueur est encore affecte a une equipe : il faut
     *         d'abord l'en retirer (evite de vider une equipe par megarde).
     */
    public void supprimerJoueur(Joueur joueur) {
        Optional<Equipe> equipe = etat.getEquipes().stream()
                .filter(e -> e.getJoueurs().contains(joueur))
                .findFirst();
        if (equipe.isPresent()) {
            throw new IllegalStateException(
                    "Ce joueur fait partie de l'equipe " + equipe.get().getNumero()
                    + ". Retirez-le d'abord de son equipe avant de le supprimer.");
        }
        etat.getJoueurs().remove(joueur);
        notifierJoueurs();
    }

    // ------------------------------------------------------------------
    // Equipes
    // ------------------------------------------------------------------

    /**
     * Cree une equipe.
     * @throws IllegalArgumentException si le numero est deja pris
     */
    public Equipe ajouterEquipe(int numero) {
        if (equipeParNumero(numero).isPresent()) {
            throw new IllegalArgumentException("L'equipe " + numero + " existe deja.");
        }
        Equipe equipe = new Equipe(numero);
        etat.getEquipes().add(equipe);
        notifierEquipes();
        return equipe;
    }

    public void supprimerEquipe(Equipe equipe) {
        if (equipe.getEtat() == EtatEquipe.EN_MATCH) {
            throw new IllegalStateException("Impossible de supprimer une equipe en match.");
        }
        etat.getEquipes().remove(equipe);
        notifierEquipes();
        notifierJoueurs();
        notifierClassement();
    }

    /**
     * Supprime toutes les equipes qui ne sont pas en match (leurs joueurs redeviennent
     * disponibles). Les equipes en cours de rencontre sont laissees intactes.
     * @return le nombre d'equipes conservees car en match (0 si tout a ete supprime)
     */
    public int supprimerToutesEquipes() {
        int enMatch = 0;
        var iterateur = etat.getEquipes().iterator();
        while (iterateur.hasNext()) {
            Equipe e = iterateur.next();
            if (e.getEtat() == EtatEquipe.EN_MATCH) {
                enMatch++;
            } else {
                iterateur.remove();
            }
        }
        notifierEquipes();
        notifierJoueurs();
        notifierClassement();
        return enMatch;
    }

    /**
     * Affecte un joueur a une equipe (en le retirant de son equipe precedente).
     * @throws IllegalStateException si l'equipe est deja pleine
     */
    public void affecterJoueur(Joueur joueur, Equipe equipe) {
        if (equipe.estPleine()) {
            throw new IllegalStateException("L'equipe " + equipe.getNumero() + " est deja complete.");
        }
        etat.getEquipes().forEach(e -> e.retirerJoueur(joueur));
        equipe.ajouterJoueur(joueur);
        notifierEquipes();
        notifierJoueurs();
    }

    public void retirerJoueurDeEquipe(Joueur joueur, Equipe equipe) {
        equipe.retirerJoueur(joueur);
        notifierEquipes();
        notifierJoueurs();
    }

    // ------------------------------------------------------------------
    // Composition automatique (aleatoire / rang 1)
    // ------------------------------------------------------------------

    /**
     * Calcule une proposition de composition sans l'appliquer, pour pouvoir montrer d'eventuelles
     * alertes (groupes trop grands, joueurs non places) avant de valider. Se base sur les joueurs
     * qui ne sont pas deja affectes a une equipe.
     */
    public CompositionAutomatique.Proposition proposerComposition(CompositionAutomatique.Mode mode) {
        return CompositionAutomatique.composer(getJoueursSansEquipe(), mode, System.nanoTime());
    }

    /**
     * Applique une proposition : cree autant d'equipes que necessaire (numeros libres) et y
     * affecte les joueurs dans l'ordre fourni (deja trie par force). N'touche pas aux equipes
     * existantes ni aux rencontres. Notifie les vues une seule fois a la fin.
     */
    public void appliquerComposition(CompositionAutomatique.Proposition proposition) {
        int numero = 1;
        for (List<Joueur> membres : proposition.equipes()) {
            while (equipeParNumero(numero).isPresent()) {
                numero++;
            }
            Equipe equipe = new Equipe(numero);
            for (Joueur j : membres) {
                // Retire d'une eventuelle equipe (par securite) puis ajoute dans l'ordre.
                etat.getEquipes().forEach(e -> e.retirerJoueur(j));
                equipe.ajouterJoueur(j);
            }
            etat.getEquipes().add(equipe);
            numero++;
        }
        notifierEquipes();
        notifierJoueurs();
        notifierClassement();
    }

    /**
     * Mode rang 1 : calcule un plan pour completer les equipes existantes (celles dont le n&deg;1
     * a ete place a la main) avec les joueurs disponibles, puis creer de nouvelles equipes s'il
     * en reste. Ne modifie rien : renvoie le plan pour apercu/validation.
     */
    public CompletionRang1.Plan proposerCompletionRang1() {
        return CompletionRang1.completer(getEquipes(), getJoueursSansEquipe(), System.nanoTime());
    }

    /**
     * Applique un plan de completion rang 1 : ajoute les joueurs prevus aux equipes existantes
     * (aux postes suivants) et cree les nouvelles equipes. Notifie une fois a la fin.
     */
    public void appliquerCompletionRang1(CompletionRang1.Plan plan) {
        // Completer les equipes existantes.
        for (var entree : plan.ajouts().entrySet()) {
            Equipe equipe = entree.getKey();
            for (Joueur j : entree.getValue()) {
                etat.getEquipes().forEach(e -> e.retirerJoueur(j));
                equipe.ajouterJoueur(j);
            }
        }
        // Creer les nouvelles equipes.
        int numero = 1;
        for (List<Joueur> membres : plan.nouvellesEquipes()) {
            while (equipeParNumero(numero).isPresent()) {
                numero++;
            }
            Equipe equipe = new Equipe(numero);
            for (Joueur j : membres) {
                etat.getEquipes().forEach(e -> e.retirerJoueur(j));
                equipe.ajouterJoueur(j);
            }
            etat.getEquipes().add(equipe);
            numero++;
        }
        notifierEquipes();
        notifierJoueurs();
        notifierClassement();
    }

    /**
     * Affecte un joueur a un poste precis (ordre de jeu) d'une equipe. Le joueur est d'abord
     * retire de son equipe actuelle. Si le poste vise depasse le nombre de joueurs presents,
     * le joueur est simplement ajoute a la fin (on ne cree pas de trous). S'il existe deja un
     * joueur a ce poste, les deux sont echanges seulement si le joueur venait de la meme equipe,
     * sinon le nouveau prend la place et l'ancien est repousse.
     *
     * @param poste index de poste souhaite (0 = poste 1, 1 = poste 2, ...)
     * @throws IllegalStateException si l'equipe est pleine et que le joueur n'en fait pas deja partie
     */
    public void affecterJoueurAuPoste(Joueur joueur, Equipe equipe, int poste) {
        boolean dejaDedans = equipe.getJoueurs().contains(joueur);
        if (!dejaDedans && equipe.estPleine()) {
            throw new IllegalStateException("L'equipe " + equipe.getNumero() + " est deja complete.");
        }
        // Retire le joueur de partout (y compris de l'equipe cible pour le repositionner).
        etat.getEquipes().forEach(e -> e.retirerJoueur(joueur));

        List<Joueur> membres = equipe.getJoueurs();
        int cible = Math.max(0, Math.min(poste, membres.size()));
        membres.add(cible, joueur);

        notifierEquipes();
        notifierJoueurs();
    }

    public void echangerOrdreJoueurs(Equipe equipe, int i, int j) {
        equipe.echangerJoueurs(i, j);
        notifierEquipes();
    }

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------

    /** (Re)cree la salle avec {@code nb} tables numerotees de 1 a nb. */
    public void configurerTables(int nb) {
        etat.getTables().clear();
        for (int i = 1; i <= nb; i++) {
            etat.getTables().add(new Table(i));
        }
        notifierTables();
    }

    /**
     * Change le statut d'une table (Active, Retiree, Fil rouge, Robot). Une table occupee ne peut
     * pas changer de statut tant que sa rencontre n'est pas terminee.
     */
    public void changerStatutTable(Table table, org.club.tournoi.model.StatutTable statut) {
        if (table.isOccupee()) {
            throw new IllegalStateException("Impossible de changer le statut d'une table occupee.");
        }
        table.setStatut(statut);
        notifierTables();
    }

    // ------------------------------------------------------------------
    // Rencontres (creation + scores) - avec effets de bord centralises ici
    // ------------------------------------------------------------------

    /**
     * Verifie si deux equipes se sont deja affrontees (utile pour le systeme suisse
     * qui interdit les re-matchs).
     */
    public boolean dejaAffrontees(Equipe a, Equipe b) {
        return etat.getRencontres().stream()
                .anyMatch(r -> r.impliqueEquipe(a) && r.impliqueEquipe(b));
    }

    /**
     * Liste les equipes deja affrontees par une equipe (rencontres terminees ou en cours),
     * dans l'ordre chronologique. Utile pour anticiper les prochains appariements possibles.
     */
    public List<Equipe> adversairesDe(Equipe equipe) {
        List<Equipe> adversaires = new ArrayList<>();
        for (Rencontre r : etat.getRencontres()) {
            if (r.impliqueEquipe(equipe)) {
                Equipe autre = r.adversaireDe(equipe);
                if (autre != null && !adversaires.contains(autre)) {
                    adversaires.add(autre);
                }
            }
        }
        return adversaires;
    }

    /**
     * Lance une nouvelle rencontre. Regroupe ici tous les controles et les effets de bord
     * que l'ancien code eparpillait entre le constructeur de Rencontre et l'ActionListener.
     *
     * @throws IllegalArgumentException si les equipes/table sont invalides
     * @throws IllegalStateException si une equipe ou la table n'est pas disponible
     */
    public Rencontre lancerRencontre(Equipe equipeA, Equipe equipeB, Table table) {
        return lancerRencontre(equipeA, equipeB, List.of(table));
    }

    /**
     * Lance une rencontre sur une ou deux tables. Regroupe les controles et les effets de bord.
     * @param tables 1 ou 2 tables (la 2e est optionnelle, pour etaler les matchs)
     * @throws IllegalArgumentException si les equipes/tables sont invalides
     * @throws IllegalStateException si une equipe ou une table n'est pas disponible
     */
    public Rencontre lancerRencontre(Equipe equipeA, Equipe equipeB, List<Table> tables) {
        if (estEnModeDepartage()) {
            throw new IllegalStateException(
                    "Mode departage actif : impossible de lancer une nouvelle rencontre normale.");
        }
        if (equipeA.equals(equipeB)) {
            throw new IllegalArgumentException("Une equipe ne peut pas s'affronter elle-meme.");
        }
        if (equipeA.getEtat() != EtatEquipe.EN_ATTENTE || equipeB.getEtat() != EtatEquipe.EN_ATTENTE) {
            throw new IllegalStateException("Les deux equipes doivent etre en attente.");
        }
        if (dejaAffrontees(equipeA, equipeB)) {
            throw new IllegalStateException("Ces deux equipes se sont deja affrontees.");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("Il faut au moins une table.");
        }
        for (Table t : tables) {
            if (!t.estDisponible()) {
                throw new IllegalStateException("La table " + t.getNumero() + " n'est pas disponible.");
            }
        }

        // Rotation = nombre de rencontres deja jouees par l'equipe A (fait tourner roles/doubles).
        int rotation = (int) etat.getRencontres().stream()
                .filter(r -> r.impliqueEquipe(equipeA))
                .count();
        boolean avecEcart = etat.getParametres().isPointEcart();

        int table1 = tables.get(0).getNumero();
        int table2 = tables.size() >= 2 ? tables.get(1).getNumero() : -1;

        Rencontre rencontre = Rencontre.creer(equipeA, equipeB, table1, table2, rotation, avecEcart);
        rencontre.demarrer(System.currentTimeMillis());

        // Effets de bord, regroupes et coherents
        equipeA.setEtat(EtatEquipe.EN_MATCH);
        equipeB.setEtat(EtatEquipe.EN_MATCH);
        for (Table t : tables) {
            t.setOccupee(true);
        }
        etat.getRencontres().add(rencontre);

        notifierRencontres();
        notifierEquipes();
        notifierTables();
        return rencontre;
    }

    /**
     * Enregistre en une fois les 5 scores d'une rencontre, valide chaque match, puis
     * termine la rencontre (libere la table, repasse les equipes en attente).
     *
     * @param scoresA scores de l'equipe A pour les 5 matchs (index 0..4)
     * @param scoresB scores de l'equipe B pour les 5 matchs
     * @throws IllegalArgumentException si un score est invalide (aucun n'est applique)
     */
    public void enregistrerScores(Rencontre rencontre, int[] scoresA, int[] scoresB) {
        List<Match> matchs = rencontre.getMatchs();
        if (scoresA.length != matchs.size() || scoresB.length != matchs.size()) {
            throw new IllegalArgumentException("Il faut exactement " + matchs.size() + " scores par equipe.");
        }
        // Memorise les rangs avant application, pour les fleches montee/descente.
        memoriserRangs();

        // On valide tout d'abord sans rien appliquer : soit tout passe, soit rien.
        boolean avecEcart = etat.getParametres().isPointEcart();
        for (int i = 0; i < matchs.size(); i++) {
            new MatchSimple("validation", null, null, avecEcart).enregistrerScore(scoresA[i], scoresB[i]);
        }
        for (int i = 0; i < matchs.size(); i++) {
            matchs.get(i).reinitialiser();
            matchs.get(i).enregistrerScore(scoresA[i], scoresB[i]);
        }

        terminerRencontre(rencontre);
    }

    /**
     * Enregistre le score d'UN SEUL match d'une rencontre (saisie "au fil de l'eau",
     * utilisee par l'application capitaines). Applique exactement les memes regles de
     * validation que {@link #enregistrerScores}. Quand ce match est le dernier a etre
     * renseigne, la rencontre se cloture automatiquement (classement, tables, etats),
     * comme si les cinq scores avaient ete saisis d'un coup.
     *
     * @param rencontre  la rencontre concernee
     * @param indexMatch position du match dans la rencontre (0 = premier)
     * @param scoreA     score du cote A (equipe A de la rencontre)
     * @param scoreB     score du cote B (equipe B de la rencontre)
     * @throws IllegalArgumentException si l'index est hors bornes ou le score invalide
     * @throws IllegalStateException    si ce match est deja termine
     */
    public void enregistrerScoreMatch(Rencontre rencontre, int indexMatch, int scoreA, int scoreB) {
        List<Match> matchs = rencontre.getMatchs();
        if (indexMatch < 0 || indexMatch >= matchs.size()) {
            throw new IllegalArgumentException("Index de match invalide : " + indexMatch);
        }
        Match match = matchs.get(indexMatch);
        if (match.estTermine()) {
            throw new IllegalStateException("Ce match est deja termine.");
        }
        // Memorise les rangs avant application, pour les fleches montee/descente
        // (utile seulement quand ce match cloture la rencontre, sans effet sinon).
        memoriserRangs();

        // Valide AVANT d'appliquer (leve une exception si le score est invalide).
        match.enregistrerScore(scoreA, scoreB);

        // Tentative de cloture : ne fait rien tant que les 5 matchs ne sont pas joues.
        terminerRencontre(rencontre);

        // Rafraichit l'affichage meme si la rencontre n'est pas encore terminee.
        notifierRencontres();
        // Met a jour le classement des maintenant (apres chaque match valide),
        // pas seulement a la cloture de la rencontre.
        notifierClassement();
    }

    /**
     * Reinitialise UN match d'une rencontre encore en cours (score efface, match
     * "a rejouer"). Sert au juge-arbitre pour corriger un match saisi par erreur ou
     * en litige. Interdit si la rencontre est deja terminee (dans ce cas il faut
     * repasser par la saisie complete).
     */
    public void reinitialiserMatch(Rencontre rencontre, int indexMatch) {
        if (rencontre.estTerminee()) {
            throw new IllegalStateException(
                    "Rencontre terminee : corrigez plutot via la saisie complete des scores.");
        }
        List<Match> matchs = rencontre.getMatchs();
        if (indexMatch < 0 || indexMatch >= matchs.size()) {
            throw new IllegalArgumentException("Index de match invalide : " + indexMatch);
        }
        matchs.get(indexMatch).reinitialiser();
        notifierRencontres();
    }

    /**
     * Interrompt une rencontre en cours : elle est retiree, la table est liberee et les deux
     * equipes repassent en attente. Aucun score n'est enregistre, donc la rencontre ne compte
     * pas au classement. Utile en cas de probleme (erreur de composition, litige) pour pouvoir
     * modifier une equipe puis relancer.
     *
     * @throws IllegalStateException si la rencontre est deja terminee (on n'annule pas un
     *         resultat acquis ; il faudrait le corriger via la saisie de score)
     */
    public void interrompreRencontre(Rencontre rencontre) {
        if (rencontre.estTerminee()) {
            throw new IllegalStateException(
                    "Cette rencontre est terminee. Une rencontre interrompue est une rencontre encore en cours.");
        }
        long maintenant = System.currentTimeMillis();
        rencontre.getEquipeA().setEtat(EtatEquipe.EN_ATTENTE);
        rencontre.getEquipeA().setEnAttenteDepuisMillis(maintenant);
        rencontre.getEquipeB().setEtat(EtatEquipe.EN_ATTENTE);
        rencontre.getEquipeB().setEnAttenteDepuisMillis(maintenant);
        tableParNumero(rencontre.getNumeroTable()).ifPresent(t -> t.setOccupee(false));
        if (rencontre.utiliseDeuxTables()) {
            tableParNumero(rencontre.getNumeroTable2()).ifPresent(t -> t.setOccupee(false));
        }
        etat.getRencontres().remove(rencontre);

        notifierRencontres();
        notifierEquipes();
        notifierTables();
    }

    /** Cloture une rencontre dont tous les matchs sont joues. */
    private void terminerRencontre(Rencontre rencontre) {
        if (!rencontre.tousMatchsJoues()) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        rencontre.getEquipeA().setEtat(EtatEquipe.EN_ATTENTE);
        rencontre.getEquipeA().setEnAttenteDepuisMillis(maintenant);
        rencontre.getEquipeB().setEtat(EtatEquipe.EN_ATTENTE);
        rencontre.getEquipeB().setEnAttenteDepuisMillis(maintenant);
        tableParNumero(rencontre.getNumeroTable()).ifPresent(t -> t.setOccupee(false));
        if (rencontre.utiliseDeuxTables()) {
            tableParNumero(rencontre.getNumeroTable2()).ifPresent(t -> t.setOccupee(false));
        }

        notifierRencontres();
        notifierEquipes();
        notifierTables();
        notifierClassement();
    }
}
