package org.club.tournoi.model;

/**
 * Base commune aux matchs simple et double : porte les scores et la validation.
 * <p>
 * La validation est <b>unique</b> pour les deux types (corrige une incoherence de l'ancienne
 * version) et depend du mode de points du tournoi :
 * <ul>
 *   <li><b>avec ecart</b> : il faut atteindre au moins 11 ET mener de 2 points. Au-dela de
 *       10-10 la manche se prolonge (12-10, 13-11, 14-12...) sans plafond fixe ;</li>
 *   <li><b>sans ecart</b> : premier a 11, l'ecart d'un point suffit (11-10 est valide), le
 *       score gagnant est exactement 11.</li>
 * </ul>
 * Le mode est fige a la creation du match (recopie des parametres du tournoi au moment ou la
 * rencontre est formee), ce qui garde {@link Match#enregistrerScore} sans parametre supplementaire.
 */
abstract class MatchAbstrait implements Match {

    /** Score de reference d'une manche. */
    public static final int SCORE_CIBLE = 11;

    private final boolean avecEcart;
    private int scoreA;
    private int scoreB;
    private boolean termine;

    protected MatchAbstrait(boolean avecEcart) {
        this.avecEcart = avecEcart;
    }

    public boolean isAvecEcart() {
        return avecEcart;
    }

    @Override
    public int getScore(Cote cote) {
        return cote == Cote.A ? scoreA : scoreB;
    }

    @Override
    public boolean estTermine() {
        return termine;
    }

    @Override
    public void enregistrerScore(int scoreA, int scoreB) {
        if (termine) {
            throw new IllegalStateException("Ce match est deja termine.");
        }
        valider(scoreA, scoreB);
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.termine = true;
    }

    @Override
    public void reinitialiser() {
        this.scoreA = 0;
        this.scoreB = 0;
        this.termine = false;
    }

    /**
     * Remplace chaque joueur aligne par son instance canonique (meme id). Utilise apres un
     * chargement JSON, ou Gson cree des copies independantes des joueurs du tournoi.
     */
    abstract void relierJoueurs(java.util.function.UnaryOperator<Joueur> canonique);

    /** Regles de score selon le mode (avec ou sans point d'ecart). */
    protected void valider(int a, int b) {
        if (a < 0 || b < 0) {
            throw new IllegalArgumentException("Les scores ne peuvent pas etre negatifs.");
        }
        if (a == b) {
            throw new IllegalArgumentException("Un match ne peut pas se terminer sur une egalite.");
        }
        int gagnant = Math.max(a, b);
        int perdant = Math.min(a, b);

        if (gagnant < SCORE_CIBLE) {
            throw new IllegalArgumentException("Le gagnant doit atteindre " + SCORE_CIBLE + " points.");
        }

        if (avecEcart) {
            // Il faut mener de 2 points. Tant qu'on est a 11 face a 9 ou moins, ok.
            // Au-dela (prolongation), le gagnant peut depasser 11 mais toujours avec 2 d'ecart.
            if (perdant <= SCORE_CIBLE - 2) {
                // gagnant doit valoir exactement 11 (pas de points superflus avant prolongation)
                if (gagnant != SCORE_CIBLE) {
                    throw new IllegalArgumentException(
                            "Sans prolongation, le gagnant marque exactement " + SCORE_CIBLE + " points.");
                }
            } else {
                // Prolongation : gagnant = perdant + 2 exactement.
                if (gagnant - perdant != 2) {
                    throw new IllegalArgumentException(
                            "En prolongation, il faut exactement 2 points d'ecart (ex. 12-10, 13-11).");
                }
            }
        } else {
            // Sans ecart : le gagnant marque exactement 11, le perdant entre 0 et 10.
            if (gagnant != SCORE_CIBLE) {
                throw new IllegalArgumentException(
                        "Sans point d'ecart, le gagnant marque exactement " + SCORE_CIBLE + " points.");
            }
            if (perdant > SCORE_CIBLE - 1) {
                throw new IllegalArgumentException("Score final invalide.");
            }
        }
    }
}
