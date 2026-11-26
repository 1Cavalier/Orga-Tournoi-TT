package org.club.tournoi.service;

import org.club.tournoi.model.Categorie;
import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.model.Symbole;

import java.util.*;

/**
 * Mode "rang 1" : completer des equipes dont la tete de serie (le joueur n&deg;1) a deja ete
 * placee a la main.
 * <p>
 * Deroule voulu par le club : l'organisateur cree quelques equipes et place lui-meme le joueur
 * fort qui sera le n&deg;1 de chacune. Ce mode complete alors ces equipes avec les joueurs
 * restants : les plus forts au poste 2, les moyens au poste 3, les plus faibles au poste 4, en
 * gardant les groupes de symboles ensemble. S'il reste des joueurs une fois ces equipes
 * completees, on en cree de nouvelles (logique "tout le monde joue" en equipes de 3-4), triees
 * par niveau et par symbole.
 * <p>
 * Ne touche pas a l'etat : renvoie une {@link Plan} decrivant quoi ajouter ou aux equipes
 * existantes, et quelles nouvelles equipes creer. C'est le service qui applique.
 */
public final class CompletionRang1 {

    private static final int TAILLE = Equipe.TAILLE_MAX; // 4

    /**
     * Plan de completion.
     * @param ajouts pour chaque equipe existante a completer : la liste ordonnee des joueurs a y
     *               ajouter (postes 2,3,4...).
     * @param nouvellesEquipes equipes entierement nouvelles a creer (chacune deja ordonnee).
     * @param alertes messages a montrer (groupe scinde, effectif impossible, etc.).
     */
    public record Plan(Map<Equipe, List<Joueur>> ajouts, List<List<Joueur>> nouvellesEquipes, List<String> alertes) { }

    private CompletionRang1() { }

    /**
     * @param equipesExistantes equipes deja creees (on ne complete que celles ayant au moins un
     *                          joueur, c.-a-d. un n&deg;1 place, et pas encore pleines).
     * @param disponibles joueurs sans equipe a repartir.
     */
    public static Plan completer(List<Equipe> equipesExistantes, List<Joueur> disponibles, long graine) {
        Random random = new Random(graine);
        List<String> alertes = new ArrayList<>();
        Map<Equipe, List<Joueur>> ajouts = new LinkedHashMap<>();

        // Equipes a completer : celles qui ont deja au moins un joueur (le n1) et de la place.
        List<Equipe> aCompleter = new ArrayList<>();
        for (Equipe e : equipesExistantes) {
            if (!e.getJoueurs().isEmpty() && e.getJoueurs().size() < TAILLE) {
                aCompleter.add(e);
                ajouts.put(e, new ArrayList<>());
            }
        }

        // Regrouper les joueurs disponibles par symbole (scinde les groupes > 4).
        GroupeSymbole.Resultat regroupement = GroupeSymbole.regrouper(disponibles, TAILLE);
        alertes.addAll(regroupement.alertes());
        List<GroupeSymbole.Bloc> blocs = new ArrayList<>(regroupement.blocs());

        // On place d'abord les gros blocs (groupes), puis les joueurs seuls, en remplissant les
        // equipes existantes par force decroissante (le plus fort disponible au poste 2, etc.).
        // Trier les blocs : gros d'abord, et a l'interieur, les plus forts d'abord.
        blocs.sort(Comparator
                .comparingInt((GroupeSymbole.Bloc b) -> b.taille()).reversed()
                .thenComparingInt(CompletionRang1::meilleurNiveau));

        List<Joueur> nonPlacesDansExistantes = new ArrayList<>();

        for (GroupeSymbole.Bloc bloc : blocs) {
            Equipe cible = equipeAvecPlace(aCompleter, ajouts, bloc.taille());
            if (cible != null) {
                ajouts.get(cible).addAll(bloc.joueurs());
            } else {
                // Pas de place pour ce bloc entier dans les equipes existantes : reporte.
                nonPlacesDansExistantes.addAll(bloc.joueurs());
            }
        }

        // Ordonner les ajouts de chaque equipe par force (poste 2 = plus fort du lot, etc.).
        for (List<Joueur> ajout : ajouts.values()) {
            ajout.sort(Comparator.comparingInt(j -> j.getCategorie().getNiveau()));
        }

        // S'il reste des joueurs, creer de nouvelles equipes avec la logique "tout le monde joue".
        List<List<Joueur>> nouvelles = new ArrayList<>();
        if (!nonPlacesDansExistantes.isEmpty()) {
            CompositionAutomatique.Proposition p = CompositionAutomatique.composer(
                    nonPlacesDansExistantes, CompositionAutomatique.Mode.RANG_1, random.nextLong());
            nouvelles.addAll(p.equipes());
            alertes.addAll(p.alertes());
            // Les joueurs vraiment non places (ex. reste indecomposable) sont signales.
            if (!p.nonPlaces().isEmpty()) {
                alertes.add(reste(p.nonPlaces()));
            }
        }

        return new Plan(ajouts, nouvelles, alertes);
    }

    private static Equipe equipeAvecPlace(List<Equipe> equipes, Map<Equipe, List<Joueur>> ajouts, int besoin) {
        // Cherche l'equipe la plus remplie ou le bloc tient encore (remplissage compact).
        Equipe meilleure = null;
        int meilleureOccupation = -1;
        for (Equipe e : equipes) {
            int occupe = e.getJoueurs().size() + ajouts.get(e).size();
            int reste = TAILLE - occupe;
            if (reste >= besoin && occupe > meilleureOccupation) {
                meilleure = e;
                meilleureOccupation = occupe;
            }
        }
        return meilleure;
    }

    private static int meilleurNiveau(GroupeSymbole.Bloc bloc) {
        return bloc.joueurs().stream().mapToInt(j -> j.getCategorie().getNiveau()).min().orElse(Categorie.NIVEAU_MIN);
    }

    private static String reste(List<Joueur> joueurs) {
        StringBuilder noms = new StringBuilder();
        for (Joueur j : joueurs) {
            if (noms.length() > 0) noms.append(", ");
            noms.append(j.getNomComplet());
        }
        return "Certains joueurs n'ont pas pu etre places automatiquement : " + noms
                + ". Ajustez a la main ou modifiez l'effectif.";
    }
}
