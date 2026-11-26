package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un match de <b>departage</b> (barrage), joue pour attribuer des positions finales a l'interieur
 * d'un groupe d'equipes a egalite.
 * <p>
 * Contrairement a une {@link Rencontre} normale, un barrage <b>ne compte pas</b> dans les
 * statistiques du tournoi : il n'ajoute aucune rencontre gagnee et ne change pas le classement
 * suisse. Il sert uniquement a departager, en attribuant au gagnant la {@code positionHaute} et
 * au perdant la {@code positionBasse}. Il occupe une table et possede son propre chronometre,
 * comme une rencontre, mais reste un objet a part.
 * <p>
 * La grille de 5 matchs (4 simples + 1 double) est identique a celle d'une rencontre : on reutilise
 * {@link Rencontre#creer} en interne pour la construire.
 */
public class Barrage {

    private final String id;
    // Non finales : relinkees apres un chargement JSON (voir EtatTournoi#relierReferences).
    private Equipe equipeA;
    private Equipe equipeB;
    private final int positionHaute;   // position du gagnant
    private final int positionBasse;   // position du perdant
    private final int numeroTable;
    private int numeroTable2 = -1; // 2e table optionnelle (-1 = une seule table)
    private final List<Match> matchs;
    private long debutMillis;
    private boolean tirageAuSort = false;         // true = departage par tirage (pas de match joue)
    private Integer numeroGagnantTirage = null;   // numero de l'equipe gagnante si tirage au sort

    private Barrage(String id, Equipe equipeA, Equipe equipeB, int positionHaute, int positionBasse,
                    int numeroTable, List<Match> matchs) {
        this.id = id;
        this.equipeA = equipeA;
        this.equipeB = equipeB;
        this.positionHaute = positionHaute;
        this.positionBasse = positionBasse;
        this.numeroTable = numeroTable;
        this.matchs = matchs;
    }

    /**
     * Cree un barrage entre deux equipes pour les positions donnees, avec la meme grille de 5
     * matchs qu'une rencontre normale.
     * @param avecEcart mode de points applique aux matchs
     */
    public static Barrage creer(Equipe a, Equipe b, int positionHaute, int positionBasse,
                                int numeroTable, boolean avecEcart) {
        // Reutilise la grille d'une rencontre (rotation 0) pour construire les 5 matchs.
        Rencontre modele = Rencontre.creer(a, b, numeroTable, -1, 0, avecEcart);
        List<Match> copie = new ArrayList<>(modele.getMatchs());
        return new Barrage(UUID.randomUUID().toString(), a, b, positionHaute, positionBasse,
                numeroTable, copie);
    }

    /**
     * Cree un barrage deja resolu par tirage au sort (aucun match joue). Le gagnant prend la
     * position haute, le perdant la basse. Sert a garder une trace visible dans le suivi.
     */
    public static Barrage creerTirageAuSort(Equipe gagnant, Equipe perdant, int positionHaute,
                                            int positionBasse) {
        Barrage b = new Barrage(UUID.randomUUID().toString(), gagnant, perdant,
                positionHaute, positionBasse, -1, new ArrayList<>());
        b.tirageAuSort = true;
        b.numeroGagnantTirage = gagnant.getNumero();
        b.debutMillis = System.currentTimeMillis();
        return b;
    }

    public boolean estTirageAuSort() { return tirageAuSort; }
    public Integer getNumeroGagnantTirage() { return numeroGagnantTirage; }

    public String getId() { return id; }
    public Equipe getEquipeA() { return equipeA; }
    public Equipe getEquipeB() { return equipeB; }
    public int getPositionHaute() { return positionHaute; }
    public int getPositionBasse() { return positionBasse; }
    public int getNumeroTable() { return numeroTable; }
    public int getNumeroTable2() { return numeroTable2; }
    public void setNumeroTable2(int n) { this.numeroTable2 = n; }
    public boolean utiliseDeuxTables() { return numeroTable2 > 0; }
    public List<Match> getMatchs() { return matchs; }

    public void demarrer(long maintenantMillis) { this.debutMillis = maintenantMillis; }
    public long getDebutMillis() { return debutMillis; }
    public boolean estLance() { return debutMillis > 0; }

    public long secondesEcoulees(long maintenantMillis) {
        return debutMillis <= 0 ? 0 : Math.max(0, (maintenantMillis - debutMillis) / 1000);
    }

    /** Remplace equipes et joueurs par les instances canoniques (apres chargement). */
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

    public boolean impliqueEquipe(Equipe e) {
        return equipeA.equals(e) || equipeB.equals(e);
    }

    public boolean estTermine() {
        if (tirageAuSort) return true; // resolu par tirage, pas de match a jouer
        for (Match m : matchs) {
            if (!m.estTermine()) return false;
        }
        return !matchs.isEmpty();
    }

    /** Nombre de matchs gagnes par un cote (pour determiner le vainqueur du barrage). */
    public int matchsGagnes(Match.Cote cote) {
        int n = 0;
        for (Match m : matchs) {
            if (m.estTermine() && m.getGagnant() == cote) n++;
        }
        return n;
    }

    /** L'equipe gagnante du barrage (ou null si pas termine ou egalite impossible a 5 matchs). */
    public Equipe getGagnante() {
        if (tirageAuSort) {
            if (numeroGagnantTirage == null) return null;
            return equipeA.getNumero() == numeroGagnantTirage ? equipeA : equipeB;
        }
        if (!estTermine()) return null;
        int a = matchsGagnes(Match.Cote.A);
        int b = matchsGagnes(Match.Cote.B);
        if (a > b) return equipeA;
        if (b > a) return equipeB;
        return null;
    }

    /** L'equipe perdante du barrage (ou null si pas termine). */
    public Equipe getPerdante() {
        Equipe g = getGagnante();
        if (g == null) return null;
        return g.equals(equipeA) ? equipeB : equipeA;
    }
}
