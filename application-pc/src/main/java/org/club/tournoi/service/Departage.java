package org.club.tournoi.service;

import org.club.tournoi.model.Equipe;

import java.util.*;

/**
 * Departages de fin de tournoi, vus comme un <b>dernier tour de positionnement</b>.
 * <p>
 * A la fin, chaque groupe d'equipes a egalite (meme nombre de rencontres gagnees) doit etre
 * departage pour attribuer les positions exactes, quelle que soit la place (pas seulement le
 * podium). La regle du club :
 * <ul>
 *   <li>on ordonne les equipes du groupe par matchs gagnes, puis par points (pour) ;</li>
 *   <li>on les apparie deux par deux depuis le haut ; chaque paire joue une rencontre normale et
 *       le gagnant prend la position haute ;</li>
 *   <li>si le groupe est impair, la <b>meilleure</b> equipe (en tete de l'ordre) est posee
 *       d'office a la position la plus haute, et le reste est apparie deux par deux ;</li>
 *   <li>si deux equipes sont parfaitement identiques (memes rencontres, matchs et points), elles
 *       ne peuvent pas etre ordonnees : c'est un tirage au sort.</li>
 * </ul>
 * Cette classe ne fait que de l'analyse : elle produit un {@link Plan} decrivant, pour chaque
 * groupe a egalite, qui est pose d'office et quelles paires doivent jouer. C'est le service /
 * l'interface qui lance les rencontres.
 */
public final class Departage {

    /** Nombre de places recompensees (podium). Conserve pour information/affichage. */
    public static final int PLACES_PRIX = 3;

    /**
     * Une paire a faire jouer pour departager deux equipes, avec les positions (rangs finaux)
     * en jeu : le gagnant prend {@code positionHaute}, le perdant {@code positionBasse}.
     */
    public record Paire(Equipe a, Equipe b, int positionHaute, int positionBasse, boolean tirageAuSort) { }

    /**
     * Le plan de departage d'un groupe d'equipes a egalite.
     * @param equipes les equipes du groupe, deja ordonnees (meilleure d'abord)
     * @param rencontresGagnees le nombre de rencontres gagnees commun au groupe
     * @param rangDebut rang (position) de la premiere equipe du groupe
     * @param poseeDoffice equipe posee d'office a la position haute (groupe impair), ou null
     * @param positionPosee position finale de l'equipe posee d'office (si presente)
     * @param paires les paires a faire jouer, de la position la plus haute a la plus basse
     */
    public record Plan(
            List<Equipe> equipes, int rencontresGagnees, int rangDebut,
            Equipe poseeDoffice, int positionPosee, List<Paire> paires) {

        public boolean necessiteDesMatchs() {
            return !paires.isEmpty();
        }
    }

    private Departage() { }

    /**
     * Analyse un classement et produit un plan de departage par groupe d'equipes a egalite de
     * rencontres gagnees (groupes de 2 equipes ou plus). Renvoie un plan par groupe, dans l'ordre
     * du classement.
     */
    public static List<Plan> plans(List<Classement.Ligne> classement) {
        return plans(classement, java.util.Map.of());
    }

    /**
     * Variante avec pose d'office force : {@code poseForce} associe, pour un groupe donne, son
     * nombre de rencontres gagnees au numero de l'equipe a placer d'office (au lieu de la meilleure
     * automatique). N'a d'effet que sur les groupes impairs.
     */
    public static List<Plan> plans(List<Classement.Ligne> classement, java.util.Map<Integer, Integer> poseForce) {
        List<Plan> resultat = new ArrayList<>();
        int i = 0;
        while (i < classement.size()) {
            int gagnees = classement.get(i).stats().rencontresGagnees();
            int j = i;
            while (j < classement.size()
                    && classement.get(j).stats().rencontresGagnees() == gagnees) {
                j++;
            }
            int taille = j - i;
            if (taille >= 2) {
                List<Classement.Ligne> groupe = classement.subList(i, j);
                Integer forcee = poseForce.get(gagnees);
                resultat.add(construirePlan(groupe, forcee));
            }
            i = j;
        }
        return resultat;
    }

    /** Construit le plan d'un groupe (deja borne) d'equipes a egalite de rencontres gagnees. */
    private static Plan construirePlan(List<Classement.Ligne> groupe, Integer numeroPoseForce) {
        int rangDebut = groupe.get(0).rang();
        int gagnees = groupe.get(0).stats().rencontresGagnees();

        // Ordonne le groupe par matchs gagnes puis points (meilleur d'abord).
        List<Classement.Ligne> ordre = new ArrayList<>(groupe);
        ordre.sort((x, y) -> {
            int c = Integer.compare(y.stats().matchsGagnes(), x.stats().matchsGagnes());
            if (c != 0) return c;
            c = Integer.compare(y.stats().pointsPour(), x.stats().pointsPour());
            if (c != 0) return c;
            return Integer.compare(x.equipe().getNumero(), y.equipe().getNumero());
        });

        // Si une equipe est forcee comme posee d'office (groupe impair), on la place en tete.
        if (numeroPoseForce != null && ordre.size() % 2 == 1) {
            for (int k = 0; k < ordre.size(); k++) {
                if (ordre.get(k).equipe().getNumero() == numeroPoseForce) {
                    Classement.Ligne choisie = ordre.remove(k);
                    ordre.add(0, choisie);
                    break;
                }
            }
        }

        List<Equipe> equipesOrdonnees = new ArrayList<>();
        for (Classement.Ligne l : ordre) equipesOrdonnees.add(l.equipe());

        List<Paire> paires = new ArrayList<>();
        Equipe posee = null;
        int positionPosee = -1;
        int position = rangDebut;

        int debut = 0;
        // Groupe impair : la meilleure equipe (ou celle forcee) est posee d'office en haut.
        if (ordre.size() % 2 == 1) {
            posee = ordre.get(0).equipe();
            positionPosee = position;
            position++;
            debut = 1;
        }

        // Apparie le reste deux par deux, de la position la plus haute a la plus basse.
        for (int k = debut; k + 1 < ordre.size(); k += 2) {
            Classement.Ligne h = ordre.get(k);
            Classement.Ligne b = ordre.get(k + 1);
            boolean tirage = estParfaitementEgal(h, b);
            paires.add(new Paire(h.equipe(), b.equipe(), position, position + 1, tirage));
            position += 2;
        }

        return new Plan(equipesOrdonnees, gagnees, rangDebut, posee, positionPosee, paires);
    }

    /** Deux equipes parfaitement egales : memes rencontres, memes matchs, memes points. */
    private static boolean estParfaitementEgal(Classement.Ligne a, Classement.Ligne b) {
        return a.stats().rencontresGagnees() == b.stats().rencontresGagnees()
                && a.stats().matchsGagnes() == b.stats().matchsGagnes()
                && a.stats().pointsPour() == b.stats().pointsPour();
    }

    // ------------------------------------------------------------------
    // Compat : ancienne notion d'"egalite de podium" (encore utilisee par la preparation).
    // ------------------------------------------------------------------

    /** Une situation d'egalite simple (groupe d'equipes a egalite de rencontres gagnees). */
    public record Egalite(List<Equipe> equipes, int rencontresGagnees, int rangDebut, int rangFin) {
        public boolean estDepartageable() {
            return equipes.size() == 2;
        }
        public boolean concernePodium() {
            return rangDebut <= PLACES_PRIX;
        }
    }

    /** Renvoie tous les groupes a egalite (2 equipes ou plus), quel que soit le rang. */
    public static List<Egalite> egalitesPodium(List<Classement.Ligne> classement) {
        List<Egalite> resultat = new ArrayList<>();
        int i = 0;
        while (i < classement.size()) {
            int gagnees = classement.get(i).stats().rencontresGagnees();
            int j = i;
            while (j < classement.size()
                    && classement.get(j).stats().rencontresGagnees() == gagnees) {
                j++;
            }
            int taille = j - i;
            if (taille >= 2) {
                int rangDebut = classement.get(i).rang();
                List<Equipe> equipes = new ArrayList<>();
                for (int k = i; k < j; k++) equipes.add(classement.get(k).equipe());
                resultat.add(new Egalite(equipes, gagnees, rangDebut, rangDebut + taille - 1));
            }
            i = j;
        }
        return resultat;
    }
}
