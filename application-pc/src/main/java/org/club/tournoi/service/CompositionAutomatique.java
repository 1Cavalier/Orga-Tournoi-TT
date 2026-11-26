package org.club.tournoi.service;

import org.club.tournoi.model.Categorie;
import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Joueur;

import java.util.*;

/**
 * Modes de composition automatique des equipes.
 * <p>
 * Principe directeur : <b>tout le monde joue</b>. On ne laisse jamais de joueur de cote (sauf
 * s'il y a moins de 3 joueurs en tout, auquel cas aucune equipe valable n'est possible). Pour
 * cela, on calcule d'abord un plan de tailles : autant d'equipes de 4 que possible, complete
 * par le nombre juste d'equipes de 3 (entre 0 et 3) pour absorber le reste.
 * <p>
 * Deux modes :
 * <ul>
 *   <li><b>Full aleatoire</b> : repartit tous les joueurs au hasard. Peut etre relance autant
 *       de fois qu'on veut (chaque appel avec une graine differente donne un autre melange).</li>
 *   <li><b>Rang 1</b> : place le joueur le plus fort en premier, puis distribue les autres en
 *       tenant compte du niveau (les forts en tete).</li>
 * </ul>
 * <p>
 * Regles :
 * <ul>
 *   <li>Uniquement des equipes de 3 ou 4, priorite aux equipes de 4 completes.</li>
 *   <li>Les joueurs d'un meme symbole restent ensemble tant que c'est possible. Si respecter un
 *       groupe empeche d'obtenir des equipes de 3-4, le groupe est scinde (les tailles priment,
 *       choix valide par le club) et une alerte le signale.</li>
 *   <li>Dans chaque equipe, joueurs ranges par force decroissante (poste 1 = plus fort).</li>
 * </ul>
 */
public final class CompositionAutomatique {

    private static final int TAILLE = Equipe.TAILLE_MAX; // 4
    private static final int TAILLE_MIN = 3;

    /** Proposition de composition : equipes formees, joueurs non places (rare), alertes. */
    public record Proposition(List<List<Joueur>> equipes, List<Joueur> nonPlaces, List<String> alertes) { }

    public enum Mode { ALEATOIRE, RANG_1 }

    private CompositionAutomatique() { }

    /** Variante pratique : graine tiree du temps (melange different a chaque appel). */
    public static Proposition composer(List<Joueur> disponibles, Mode mode) {
        return composer(disponibles, mode, System.nanoTime());
    }

    public static Proposition composer(List<Joueur> disponibles, Mode mode, long graine) {
        Random random = new Random(graine);
        List<String> alertes = new ArrayList<>();

        int total = disponibles.size();
        if (total < TAILLE_MIN) {
            // Impossible de former ne serait-ce qu'une equipe de 3.
            List<Joueur> reste = new ArrayList<>(disponibles);
            if (total > 0) {
                alertes.add("Il n'y a que " + total + " joueur(s) disponible(s), il en faut au moins "
                        + TAILLE_MIN + " pour former une equipe. Aucune equipe n'a ete creee.");
            }
            return new Proposition(new ArrayList<>(), reste, alertes);
        }

        // 1) Plan de tailles : combien d'equipes de 4 et de 3 pour que tout le monde joue.
        int[] plan = planTailles(total);
        if (plan == null) {
            // Cas indecomposable (uniquement total == 5) : on ne peut pas faire QUE des equipes
            // de 3-4 avec 5 joueurs, et une equipe de 5 ne respecterait pas le format des
            // rencontres (4 simples + 1 double). On refuse donc proprement et on explique.
            alertes.add("Avec " + total + " joueurs disponibles, impossible de former uniquement des "
                    + "equipes de 3 ou 4 (5 = 3+2 ou 4+1, aucune ne convient). Ajoutez ou retirez un "
                    + "joueur (par exemple passer a 4, 6, 7 ou 8), ou composez a la main.");
            return new Proposition(new ArrayList<>(), new ArrayList<>(disponibles), alertes);
        }

        // 2) Regrouper par symbole (scinde deja les groupes > 4).
        GroupeSymbole.Resultat regroupement = GroupeSymbole.regrouper(disponibles, TAILLE);
        alertes.addAll(regroupement.alertes());
        List<GroupeSymbole.Bloc> blocs = new ArrayList<>(regroupement.blocs());

        // 3) Ordonner selon le mode.
        Collections.shuffle(blocs, random);
        if (mode == Mode.RANG_1) {
            blocs.sort(Comparator.comparingInt(CompositionAutomatique::meilleurNiveauDuBloc));
        }

        // 4) Preparer les contenants (equipes vides) aux tailles voulues, les grandes d'abord.
        List<Contenant> contenants = new ArrayList<>();
        for (int i = 0; i < plan[0]; i++) contenants.add(new Contenant(TAILLE));
        for (int i = 0; i < plan[1]; i++) contenants.add(new Contenant(TAILLE_MIN));

        // 5) Placer les blocs. Un bloc va dans le premier contenant ou il tient entierement.
        //    Si aucun ne peut l'accueillir en entier, on le scinde (les tailles priment).
        boolean groupeScinde = false;

        // Separer les blocs de groupe (>=2, indivisibles) des joueurs seuls.
        List<GroupeSymbole.Bloc> groupes = new ArrayList<>();
        List<Joueur> seuls = new ArrayList<>();
        for (GroupeSymbole.Bloc b : blocs) {
            if (b.taille() >= 2) groupes.add(b); else seuls.add(b.joueurs().get(0));
        }
        // Gros groupes d'abord (plus durs a caser), melange a taille egale pour l'aleatoire.
        groupes.sort(Comparator.comparingInt((GroupeSymbole.Bloc b) -> b.taille()).reversed());
        melangerGroupesAtailleEgale(groupes, random);

        // Placer les groupes de symboles (ils doivent rester ensemble).
        for (GroupeSymbole.Bloc bloc : groupes) {
            Contenant cible = contenantPour(contenants, bloc.taille());
            if (cible != null) {
                cible.ajouter(bloc.joueurs());
            } else {
                // Aucun contenant ne peut prendre le groupe entier : on le scinde (tailles priment).
                groupeScinde = true;
                seuls.addAll(bloc.joueurs());
            }
        }

        // Distribuer les joueurs seuls en SERPENTIN pour equilibrer les forces :
        // on les trie par force (plus fort d'abord) puis on les depose un par un en parcourant
        // les equipes dans un sens, puis dans l'autre a chaque vague. Ainsi les forts ne
        // s'entassent pas dans les memes equipes.
        seuls.sort(Comparator.comparingInt((Joueur j) -> j.getCategorie().getNiveau())
                .thenComparing(j -> alea(j, random))); // aleatoire a niveau egal
        distribuerEnSerpentin(seuls, contenants);

        if (groupeScinde) {
            alertes.add("Pour que toutes les equipes fassent 3 ou 4 joueurs, un groupe de symboles "
                    + "a du etre separe. Verifiez les equipes concernees et ajustez a la main si besoin.");
        }

        // 6) Construire les equipes finales, triees par force interne (poste 1 = plus fort).
        List<List<Joueur>> equipes = new ArrayList<>();
        for (Contenant c : contenants) {
            if (c.joueurs.size() >= TAILLE_MIN) {
                trierParForce(c.joueurs);
                equipes.add(c.joueurs);
            }
        }
        // Equipes les plus fortes en tete.
        equipes.sort(Comparator.comparingDouble(CompositionAutomatique::niveauMoyen));

        // Verification : normalement plus personne n'est laisse de cote.
        List<Joueur> nonPlaces = new ArrayList<>();
        int placesTotal = equipes.stream().mapToInt(List::size).sum();
        if (placesTotal < total) {
            // Filet de securite (ne devrait pas arriver avec un plan correct).
            Set<Joueur> places = new HashSet<>();
            equipes.forEach(places::addAll);
            for (Joueur j : disponibles) {
                if (!places.contains(j)) nonPlaces.add(j);
            }
        }

        return new Proposition(equipes, nonPlaces, alertes);
    }

    /**
     * Calcule combien d'equipes de 4 et de 3 forment exactement {@code total} joueurs.
     * <p>
     * On cherche a, b >= 0 tels que a*4 + b*3 = total, en maximisant les equipes de 4 et en
     * gardant b (equipes de 3) entre 0 et 3. C'est possible pour tout {@code total} >= 3 sauf
     * {@code total == 5}, seul cas non decomposable en paquets de 3 et 4 : il est signale a part
     * (renvoie {@code null}) pour que l'appelant applique une regle de repli.
     *
     * @return tableau {nbEquipesDe4, nbEquipesDe3}, ou {@code null} si indecomposable (total==5)
     */
    static int[] planTailles(int total) {
        // Cherche la decomposition avec le plus d'equipes de 4 possible.
        for (int a = total / TAILLE; a >= 0; a--) {
            int reste = total - a * TAILLE;
            if (reste % TAILLE_MIN == 0) {
                return new int[]{a, reste / TAILLE_MIN};
            }
        }
        return null; // uniquement total == 5 pour total >= 3
    }

    /** Un contenant = une equipe en cours de remplissage, avec une capacite cible. */
    private static final class Contenant {
        final int capacite;
        final List<Joueur> joueurs = new ArrayList<>();
        Contenant(int capacite) { this.capacite = capacite; }
        int placeRestante() { return capacite - joueurs.size(); }
        void ajouter(List<Joueur> js) { joueurs.addAll(js); }
    }

    /** Premier contenant capable d'accueillir {@code besoin} joueurs de plus (le plus rempli d'abord). */
    private static Contenant contenantPour(List<Contenant> contenants, int besoin) {
        Contenant meilleur = null;
        int meilleureOccupation = -1;
        for (Contenant c : contenants) {
            if (c.placeRestante() >= besoin && c.joueurs.size() > meilleureOccupation) {
                meilleur = c;
                meilleureOccupation = c.joueurs.size();
            }
        }
        return meilleur;
    }

    private static void melangerGroupesAtailleEgale(List<GroupeSymbole.Bloc> blocs, Random random) {
        // Melange les groupes de meme taille entre eux, pour garder de l'aleatoire sans
        // changer l'ordre par taille (les gros groupes restant traites en premier).
        int i = 0;
        while (i < blocs.size()) {
            int j = i;
            while (j < blocs.size() && blocs.get(j).taille() == blocs.get(i).taille()) j++;
            List<GroupeSymbole.Bloc> tranche = new ArrayList<>(blocs.subList(i, j));
            Collections.shuffle(tranche, random);
            for (int k = i; k < j; k++) blocs.set(k, tranche.get(k - i));
            i = j;
        }
    }

    /** Cle aleatoire stable par joueur, pour departager a niveau egal sans biais d'ordre. */
    private static int alea(Joueur j, Random random) {
        return random.nextInt();
    }

    /**
     * Distribue les joueurs (deja tries du plus fort au plus faible) sur les contenants en
     * "serpentin" : vague 1 dans l'ordre 0,1,2,3 ; vague 2 en sens inverse 3,2,1,0 ; etc. On saute
     * les contenants pleins. Cela repartit les forces au lieu de les entasser dans les memes
     * equipes.
     */
    private static void distribuerEnSerpentin(List<Joueur> joueursTries, List<Contenant> contenants) {
        int nb = contenants.size();
        if (nb == 0) return;

        boolean sensAvant = true;
        int pos = 0;

        for (Joueur joueur : joueursTries) {
            // Trouve le prochain contenant avec de la place en suivant le serpentin.
            int gardeFou = 0;
            while (contenants.get(pos).placeRestante() <= 0 && gardeFou <= nb) {
                int[] r = prochainePosition(pos, sensAvant, nb);
                pos = r[0]; sensAvant = r[1] == 1;
                gardeFou++;
            }
            if (contenants.get(pos).placeRestante() <= 0) {
                Contenant c = contenantPour(contenants, 1); // filet de securite
                if (c != null) c.ajouter(List.of(joueur));
                continue;
            }
            contenants.get(pos).ajouter(List.of(joueur));
            int[] r = prochainePosition(pos, sensAvant, nb);
            pos = r[0]; sensAvant = r[1] == 1;
        }
    }

    /**
     * Calcule la position suivante d'un parcours serpentin sur {@code nb} elements.
     * Aux bords, on ne sort pas : on reste sur le bord et on inverse le sens (effet aller-retour).
     * @return {nouvellePosition, nouveauSens(1=avant,0=arriere)}
     */
    private static int[] prochainePosition(int pos, boolean sensAvant, int nb) {
        if (nb == 1) return new int[]{0, sensAvant ? 1 : 0};
        if (sensAvant) {
            if (pos + 1 < nb) return new int[]{pos + 1, 1};
            return new int[]{pos, 0};        // bord droit : on inverse, on reste
        } else {
            if (pos - 1 >= 0) return new int[]{pos - 1, 0};
            return new int[]{pos, 1};        // bord gauche : on inverse, on reste
        }
    }

    private static void trierParForce(List<Joueur> equipe) {
        equipe.sort(Comparator.comparingInt(j -> j.getCategorie().getNiveau()));
    }

    private static int meilleurNiveauDuBloc(GroupeSymbole.Bloc bloc) {
        return bloc.joueurs().stream()
                .mapToInt(j -> j.getCategorie().getNiveau())
                .min().orElse(Categorie.NIVEAU_MIN);
    }

    private static double niveauMoyen(List<Joueur> equipe) {
        return equipe.stream()
                .mapToInt(j -> j.getCategorie().getNiveau())
                .average().orElse(Categorie.NIVEAU_MIN);
    }
}
