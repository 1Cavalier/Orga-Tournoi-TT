package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une rencontre entre deux equipes sur une table : 4 matchs simples + 1 match double.
 * <p>
 * Difference majeure avec l'ancienne version : le constructeur <b>ne provoque aucun
 * effet de bord</b>. Il ne change plus l'etat des equipes ni n'occupe la table.
 * Toute cette coordination (verifier la disponibilite, occuper la table, marquer les
 * equipes EN_MATCH) est desormais la responsabilite du service, ce qui rend la classe
 * testable et utilisable par la generation automatique.
 */
public class Rencontre {

    /** Nombre de matchs par rencontre (4 simples + 1 double), identique en equipe de 3 ou 4. */
    public static final int NB_MATCHS = 5;

    private final String id;
    // Non finales : remplacees par les instances canoniques apres un chargement JSON
    // (voir relierReferences).
    private Equipe equipeA;
    private Equipe equipeB;
    private final int numeroTable;
    /** Seconde table si la rencontre s'etale sur deux tables (-1 si une seule). */
    private final int numeroTable2;
    private final List<Match> matchs;
    /** Horodatage de lancement (millisecondes epoch), 0 si pas encore lancee. */
    private long debutMillis;

    private Rencontre(String id, Equipe equipeA, Equipe equipeB, int numeroTable, int numeroTable2, List<Match> matchs) {
        this.id = id;
        this.equipeA = equipeA;
        this.equipeB = equipeB;
        this.numeroTable = numeroTable;
        this.numeroTable2 = numeroTable2;
        this.matchs = matchs;
    }

    /**
     * Fabrique la rencontre et sa grille de 5 matchs, sans effet de bord.
     * <p>
     * Gere les equipes de 4 (1v1, 2v2, 3v3, 4v4, puis double des joueurs 1-2) comme les equipes
     * de 3. Pour une equipe de 3, un joueur joue deux simples et les deux autres un seul, ces
     * deux-la formant le double : le role du "joueur aux deux simples" tourne selon
     * {@code rotation}, ce qui fait varier les affrontements et les paires de double au fil du
     * tournoi. Le mode de points (avec/sans ecart) est recopie sur chaque match.
     *
     * @param rotation compteur croissant fourni par le service (une valeur differente a chaque
     *                 rencontre d'une meme equipe) pour faire tourner roles et doubles
     * @param avecEcart mode de points applique a tous les matchs de la rencontre
     */
    public static Rencontre creer(Equipe equipeA, Equipe equipeB, int numeroTable, int numeroTable2,
                                  int rotation, boolean avecEcart) {
        List<Match> matchs;
        int tailleA = equipeA.getJoueurs().size();
        int tailleB = equipeB.getJoueurs().size();
        if (tailleA <= 3 || tailleB <= 3) {
            matchs = grilleTrois(equipeA, equipeB, rotation, avecEcart);
        } else {
            matchs = grilleQuatre(equipeA, equipeB, rotation, avecEcart);
        }
        return new Rencontre(UUID.randomUUID().toString(), equipeA, equipeB, numeroTable, numeroTable2, matchs);
    }

    /** Surcharge simple : une seule table, rotation 0, avec ecart (compat / usage basique). */
    public static Rencontre creer(Equipe equipeA, Equipe equipeB, int numeroTable) {
        return creer(equipeA, equipeB, numeroTable, -1, 0, true);
    }

    /** Grille classique equipe de 4 : 1v1, 2v2, 3v3, 4v4 puis double des joueurs 1 et 2. */
    private static List<Match> grilleQuatre(Equipe a, Equipe b, int rotation, boolean avecEcart) {
        List<Match> matchs = new ArrayList<>(NB_MATCHS);
        for (int i = 0; i < 4; i++) {
            matchs.add(new MatchSimple("Simple " + (i + 1), a.joueurAu(i), b.joueurAu(i), avecEcart));
        }
        // Paire de double qui varie selon la rotation (0:1-2, 1:1-3, 2:2-3, 3:3-4, ...).
        int[][] paires = {{0, 1}, {0, 2}, {1, 2}, {2, 3}, {0, 3}, {1, 3}};
        int[] p = paires[Math.floorMod(rotation, paires.length)];
        matchs.add(new MatchDouble(a.joueurAu(p[0]), a.joueurAu(p[1]),
                b.joueurAu(p[0]), b.joueurAu(p[1]), avecEcart));
        return matchs;
    }

    /**
     * Grille equipe de 3 : un joueur (l'index {@code deux = rotation % 3}) joue deux simples,
     * les deux autres un seul, et ces deux-la forment le double. On apparie les 4 simples au
     * mieux : le joueur aux deux simples affronte les deux "autres" adverses, et les deux
     * joueurs a un simple affrontent le joueur adverse aux deux simples.
     */
    private static List<Match> grilleTrois(Equipe a, Equipe b, int rotation, boolean avecEcart) {
        int da = Math.floorMod(rotation, 3);       // cote A : index du joueur aux 2 simples
        int db = Math.floorMod(rotation + 1, 3);   // cote B : decale, pour varier les affrontements
        int[] autresA = autres(da);                // les deux joueurs de A a un seul simple
        int[] autresB = autres(db);

        List<Match> matchs = new ArrayList<>(NB_MATCHS);
        // Le joueur aux 2 simples de A affronte les 2 "autres" de B.
        matchs.add(new MatchSimple("Simple 1", a.joueurAu(da), b.joueurAu(autresB[0]), avecEcart));
        matchs.add(new MatchSimple("Simple 2", a.joueurAu(da), b.joueurAu(autresB[1]), avecEcart));
        // Les 2 "autres" de A affrontent le joueur aux 2 simples de B.
        matchs.add(new MatchSimple("Simple 3", a.joueurAu(autresA[0]), b.joueurAu(db), avecEcart));
        matchs.add(new MatchSimple("Simple 4", a.joueurAu(autresA[1]), b.joueurAu(db), avecEcart));
        // Double : les 2 "autres" de chaque cote (ceux qui n'ont fait qu'un simple).
        matchs.add(new MatchDouble(a.joueurAu(autresA[0]), a.joueurAu(autresA[1]),
                b.joueurAu(autresB[0]), b.joueurAu(autresB[1]), avecEcart));
        return matchs;
    }

    /** Les deux indices de {0,1,2} differents de {@code exclu}. */
    private static int[] autres(int exclu) {
        int[] res = new int[2];
        int k = 0;
        for (int i = 0; i < 3; i++) {
            if (i != exclu) res[k++] = i;
        }
        return res;
    }

    public String getId() {
        return id;
    }

    public Equipe getEquipeA() {
        return equipeA;
    }

    public Equipe getEquipeB() {
        return equipeB;
    }

    public int getNumeroTable() {
        return numeroTable;
    }

    /** Seconde table (-1 si la rencontre tient sur une seule table). */
    public int getNumeroTable2() {
        return numeroTable2;
    }

    public boolean utiliseDeuxTables() {
        return numeroTable2 > 0;
    }

    /** Marque l'instant de lancement (demarre le chrono). */
    public void demarrer(long maintenantMillis) {
        this.debutMillis = maintenantMillis;
    }

    public long getDebutMillis() {
        return debutMillis;
    }

    public boolean estLancee() {
        return debutMillis > 0;
    }

    /** Temps ecoule depuis le lancement, en secondes (0 si pas lancee). */
    public long secondesEcoulees(long maintenantMillis) {
        return debutMillis <= 0 ? 0 : Math.max(0, (maintenantMillis - debutMillis) / 1000);
    }

    public List<Match> getMatchs() {
        return matchs;
    }

    /** Nombre de matchs gagnes par le cote demande. */
    public int matchsGagnes(Match.Cote cote) {
        return (int) matchs.stream()
                .filter(Match::estTermine)
                .filter(m -> m.getGagnant() == cote)
                .count();
    }

    /** Tous les matchs ont-ils un score enregistre ? */
    public boolean tousMatchsJoues() {
        return matchs.stream().allMatch(Match::estTermine);
    }

    public boolean estTerminee() {
        return tousMatchsJoues();
    }

    /**
     * Equipe gagnante une fois tous les matchs joues, ou {@code null} si la rencontre
     * n'est pas finie. Sur 5 matchs il ne peut pas y avoir d'egalite, mais on la traite
     * defensivement en renvoyant {@code null}.
     */
    public Equipe getGagnante() {
        if (!tousMatchsJoues()) {
            return null;
        }
        int a = matchsGagnes(Match.Cote.A);
        int b = matchsGagnes(Match.Cote.B);
        if (a == b) {
            return null;
        }
        return a > b ? equipeA : equipeB;
    }

    /** Equipe adverse de celle passee en argument, ou {@code null} si elle n'en fait pas partie. */
    public Equipe adversaireDe(Equipe equipe) {
        if (equipe.equals(equipeA)) return equipeB;
        if (equipe.equals(equipeB)) return equipeA;
        return null;
    }

    /**
     * Remplace les equipes et les joueurs references par les instances canoniques du tournoi.
     * Appele uniquement par {@link EtatTournoi#relierReferences()} apres un chargement.
     */
    void relierReferences(java.util.function.UnaryOperator<Equipe> equipeCanonique,
                          java.util.function.UnaryOperator<Joueur> joueurCanonique) {
        equipeA = equipeCanonique.apply(equipeA);
        equipeB = equipeCanonique.apply(equipeB);
        for (Match m : matchs == null ? List.<Match>of() : matchs) {
            if (m instanceof MatchAbstrait ma) {
                ma.relierJoueurs(joueurCanonique);
            }
        }
    }

    public boolean impliqueEquipe(Equipe equipe) {
        return equipe.equals(equipeA) || equipe.equals(equipeB);
    }
}
