package org.club.tournoi.service;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Rencontre;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Construit le classement ordonne des equipes.
 * <p>
 * L'ancien comparateur enchainait des {@code .reversed()} de facon ambigue (le
 * {@code reversed()} final ne s'appliquait qu'au dernier critere). Ici chaque critere
 * est explicite et l'ordre est clair :
 * <ol>
 *   <li>nombre de rencontres gagnees</li>
 *   <li>a egalite, position attribuee par un barrage de departage (si elle existe)</li>
 *   <li>puis nombre de matchs gagnes</li>
 *   <li>puis nombre de points marques</li>
 *   <li>enfin numero d'equipe (pour un ordre stable)</li>
 * </ol>
 */
public final class Classement {

    /** Une ligne de classement : le rang, l'equipe et ses stats. */
    public record Ligne(int rang, Equipe equipe, StatistiquesEquipe stats) { }

    private Classement() { }

    public static List<Ligne> calculer(List<Equipe> equipes, List<Rencontre> rencontres) {
        return calculer(equipes, rencontres, java.util.Map.of());
    }

    /**
     * Variante tenant compte des positions attribuees par les departages (barrages). Une position
     * de barrage prime, a l'interieur d'un meme nombre de rencontres gagnees, sur les matchs et les
     * points : c'est justement le role du barrage d'avoir tranche. {@code positionsBarrage} associe
     * le numero d'equipe a sa position finale imposee.
     */
    public static List<Ligne> calculer(List<Equipe> equipes, List<Rencontre> rencontres,
                                        java.util.Map<Integer, Integer> positionsBarrage) {
        Map<Equipe, StatistiquesEquipe> stats = new java.util.HashMap<>();
        for (Equipe e : equipes) {
            stats.put(e, CalculStatistiques.pour(e, rencontres));
        }

        Comparator<Equipe> comparateur = (e1, e2) -> {
            StatistiquesEquipe s1 = stats.get(e1);
            StatistiquesEquipe s2 = stats.get(e2);
            // 1) rencontres gagnees (critere principal du tournoi)
            int c = Integer.compare(s2.rencontresGagnees(), s1.rencontresGagnees());
            if (c != 0) return c;
            // 2) si les deux equipes ont une position de barrage, elle prime.
            Integer p1 = positionsBarrage.get(e1.getNumero());
            Integer p2 = positionsBarrage.get(e2.getNumero());
            if (p1 != null && p2 != null) {
                c = Integer.compare(p1, p2); // position plus petite = meilleure
                if (c != 0) return c;
            } else if (p1 != null) {
                return -1; // e1 a une place fixee par barrage, elle est prioritaire
            } else if (p2 != null) {
                return 1;
            }
            // 3) matchs gagnes, 4) points, 5) numero
            c = Integer.compare(s2.matchsGagnes(), s1.matchsGagnes());
            if (c != 0) return c;
            c = Integer.compare(s2.pointsPour(), s1.pointsPour());
            if (c != 0) return c;
            return Integer.compare(e1.getNumero(), e2.getNumero());
        };

        List<Equipe> triees = new ArrayList<>(equipes);
        triees.sort(comparateur);

        // Attribution des rangs. Deux equipes gardent le meme rang seulement si elles sont a
        // egalite ET qu'aucune position de barrage ne les distingue.
        List<Ligne> lignes = new ArrayList<>(triees.size());
        int rang = 1;
        for (int i = 0; i < triees.size(); i++) {
            Equipe e = triees.get(i);
            boolean memeRang = false;
            if (i > 0) {
                Equipe prec = triees.get(i - 1);
                boolean statsEgales = aEgalite(stats.get(prec), stats.get(e));
                Integer pPrec = positionsBarrage.get(prec.getNumero());
                Integer pCur = positionsBarrage.get(e.getNumero());
                boolean barrageDistingue = (pPrec != null || pCur != null);
                memeRang = statsEgales && !barrageDistingue;
            }
            if (!memeRang) rang = i + 1;
            lignes.add(new Ligne(rang, e, stats.get(e)));
        }
        return lignes;
    }

    private static boolean aEgalite(StatistiquesEquipe a, StatistiquesEquipe b) {
        return a.rencontresGagnees() == b.rencontresGagnees()
                && a.matchsGagnes() == b.matchsGagnes()
                && a.pointsPour() == b.pointsPour();
    }
}
