package org.club.tournoi.service;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Rencontre;

import java.util.*;

/**
 * Prepare les departages de fin de tournoi en distinguant ce qui est certain de ce qui depend
 * encore des rencontres en cours.
 * <p>
 * Deux notions :
 * <ul>
 *   <li><b>Departages a prevoir</b> : egalites sur le podium (places 1 a 3) calculees a partir des
 *       seules rencontres <i>terminees</i>, en supposant qu'aucune rencontre en cours ne change le
 *       classement final. Ce sont les egalites "sures" a l'instant present.</li>
 *   <li><b>Departages supposes</b> : egalites qui pourraient apparaitre selon l'issue des
 *       rencontres <i>en cours</i>. On explore les deux resultats possibles (equipe A ou B gagne)
 *       de chaque rencontre en cours et on releve toutes les configurations d'egalite de podium
 *       qui peuvent survenir, en plus de celles deja "a prevoir".</li>
 * </ul>
 * Le seul critere d'egalite etant le nombre de rencontres gagnees (regle du club), on raisonne
 * uniquement la-dessus : victoires actuelles + victoires hypothetiques.
 */
public final class PreparationDepartages {

    /** Une equipe sure de sa place finale = presente dans aucune egalite (certaine ni supposee). */
    public record Analyse(
            List<Departage.Egalite> aPrevoir,     // egalites certaines (rencontres finies)
            List<EgaliteSupposee> supposees,      // egalites possibles selon rencontres en cours
            Set<Equipe> equipesSures) {           // equipes dont la place ne peut plus bouger
    }

    /** Une egalite possible selon certaines issues, avec le nombre de victoires concerne. */
    public record EgaliteSupposee(List<Equipe> equipes, int rencontresGagnees, int rangDebut, int rangFin) { }

    /** Au-dela de ce nombre de rencontres en cours, on ne developpe pas toutes les combinaisons. */
    private static final int MAX_RENCONTRES_SIMULEES = 12;

    private PreparationDepartages() { }

    public static Analyse analyser(List<Equipe> equipes, List<Rencontre> rencontres) {
        // 1) Victoires actuelles (rencontres terminees) par equipe.
        Map<Equipe, Integer> victoiresBase = new HashMap<>();
        for (Equipe e : equipes) victoiresBase.put(e, 0);
        List<Rencontre> enCours = new ArrayList<>();
        for (Rencontre r : rencontres) {
            if (r.estTerminee()) {
                Equipe g = r.getGagnante();
                if (g != null) victoiresBase.merge(g, 1, Integer::sum);
            } else {
                enCours.add(r);
            }
        }

        // 2) Departages "a prevoir" : egalites de podium sur la base actuelle.
        List<Departage.Egalite> aPrevoir = egalitesPodiumDepuisVictoires(equipes, victoiresBase);

        // 3) Departages "supposes" : on explore les issues des rencontres en cours.
        //    Chaque rencontre en cours a 2 issues (A ou B gagne). On enumere les combinaisons
        //    (bornees) et on releve toutes les egalites de podium possibles.
        Set<String> dejaVues = new HashSet<>();
        List<EgaliteSupposee> supposees = new ArrayList<>();
        // On marque les egalites "a prevoir" comme deja connues pour ne pas les redonner en suppose.
        for (Departage.Egalite eg : aPrevoir) {
            dejaVues.add(cle(eg.equipes(), eg.rencontresGagnees()));
        }

        int n = enCours.size();
        if (n > 0 && n <= MAX_RENCONTRES_SIMULEES) {
            int combinaisons = 1 << n; // 2^n
            for (int masque = 0; masque < combinaisons; masque++) {
                Map<Equipe, Integer> victoires = new HashMap<>(victoiresBase);
                for (int i = 0; i < n; i++) {
                    Rencontre r = enCours.get(i);
                    boolean aGagne = (masque & (1 << i)) != 0;
                    Equipe gagnant = aGagne ? r.getEquipeA() : r.getEquipeB();
                    victoires.merge(gagnant, 1, Integer::sum);
                }
                List<Departage.Egalite> egs = egalitesPodiumDepuisVictoires(equipes, victoires);
                for (Departage.Egalite eg : egs) {
                    String cle = cle(eg.equipes(), eg.rencontresGagnees());
                    if (dejaVues.add(cle)) {
                        supposees.add(new EgaliteSupposee(eg.equipes(), eg.rencontresGagnees(),
                                eg.rangDebut(), eg.rangFin()));
                    }
                }
            }
        }

        // 4) Equipes sures de leur place = celles qui n'apparaissent dans AUCUNE egalite
        //    (ni a prevoir, ni supposee).
        Set<Equipe> dansEgalite = new HashSet<>();
        for (Departage.Egalite eg : aPrevoir) dansEgalite.addAll(eg.equipes());
        for (EgaliteSupposee eg : supposees) dansEgalite.addAll(eg.equipes());
        Set<Equipe> sures = new LinkedHashSet<>();
        for (Equipe e : equipes) {
            if (!dansEgalite.contains(e)) sures.add(e);
        }

        return new Analyse(aPrevoir, supposees, sures);
    }

    /**
     * Calcule les egalites de podium a partir d'une table de victoires (rencontres gagnees) par
     * equipe. Classe les equipes par victoires decroissantes, attribue des rangs, et regroupe les
     * equipes a egalite touchant les places 1 a 3.
     */
    private static List<Departage.Egalite> egalitesPodiumDepuisVictoires(
            List<Equipe> equipes, Map<Equipe, Integer> victoires) {
        // Trie les equipes par victoires decroissantes (puis numero pour la stabilite).
        List<Equipe> tri = new ArrayList<>(equipes);
        tri.sort((a, b) -> {
            int c = Integer.compare(victoires.getOrDefault(b, 0), victoires.getOrDefault(a, 0));
            if (c != 0) return c;
            return Integer.compare(a.getNumero(), b.getNumero());
        });

        List<Departage.Egalite> resultat = new ArrayList<>();
        int i = 0;
        int rang = 1;
        while (i < tri.size()) {
            int v = victoires.getOrDefault(tri.get(i), 0);
            int j = i;
            while (j < tri.size() && victoires.getOrDefault(tri.get(j), 0) == v) j++;
            int taille = j - i;
            int rangDebut = rang;
            int rangFin = rang + taille - 1;
            if (taille >= 2 && rangDebut <= Departage.PLACES_PRIX) {
                List<Equipe> eqs = new ArrayList<>(tri.subList(i, j));
                resultat.add(new Departage.Egalite(eqs, v, rangDebut, rangFin));
            }
            rang += taille;
            i = j;
        }
        return resultat;
    }

    /** Cle d'identite d'une egalite (equipes + victoires), pour dedoublonner. */
    private static String cle(List<Equipe> equipes, int victoires) {
        List<Integer> nums = new ArrayList<>();
        for (Equipe e : equipes) nums.add(e.getNumero());
        Collections.sort(nums);
        return victoires + ":" + nums;
    }
}
