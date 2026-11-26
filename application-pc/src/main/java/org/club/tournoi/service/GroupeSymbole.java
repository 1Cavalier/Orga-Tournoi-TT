package org.club.tournoi.service;

import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.model.Symbole;

import java.util.*;

/**
 * Regroupe les joueurs partageant un meme symbole et decoupe les groupes trop grands en
 * sous-groupes compatibles avec la taille d'une equipe.
 * <p>
 * Regle : deux joueurs de meme symbole doivent finir dans la meme equipe. Mais une equipe
 * ne depasse pas {@link Equipe#TAILLE_MAX} joueurs. Si un symbole reunit plus de joueurs que
 * cette limite, on ne peut pas tous les mettre ensemble : on decoupe alors en sous-groupes
 * les plus equilibres possibles (par exemple 5 &rarr; 3 + 2, 6 &rarr; 3 + 3, 7 &rarr; 4 + 3),
 * en privilegiant des paquets d'au moins 2 pour ne pas isoler un joueur inutilement.
 * <p>
 * Cette classe ne fait que du calcul, sans effet de bord : elle prepare le travail des modes
 * de composition automatique.
 */
public final class GroupeSymbole {

    /** Un sous-groupe de joueurs a placer ensemble dans une meme equipe. */
    public record Bloc(Symbole symbole, List<Joueur> joueurs) {
        public int taille() { return joueurs.size(); }
    }

    /** Resultat du regroupement : les blocs a placer et les alertes eventuelles. */
    public record Resultat(List<Bloc> blocs, List<String> alertes) { }

    private GroupeSymbole() { }

    /**
     * Construit les blocs a partir d'une liste de joueurs.
     * <p>
     * Les joueurs sans symbole ({@link Symbole#AUCUN}) deviennent chacun un bloc de taille 1
     * (ils sont libres de tout groupage). Les autres sont regroupes par symbole, puis
     * decoupes si necessaire. Un message d'alerte est genere pour chaque symbole qui a du
     * etre scinde, avec la liste des joueurs concernes.
     */
    public static Resultat regrouper(List<Joueur> joueurs, int tailleEquipe) {
        Map<Symbole, List<Joueur>> parSymbole = new LinkedHashMap<>();
        List<Joueur> libres = new ArrayList<>();

        for (Joueur j : joueurs) {
            if (j.getSymbole().estDefini()) {
                parSymbole.computeIfAbsent(j.getSymbole(), s -> new ArrayList<>()).add(j);
            } else {
                libres.add(j);
            }
        }

        List<Bloc> blocs = new ArrayList<>();
        List<String> alertes = new ArrayList<>();

        for (Map.Entry<Symbole, List<Joueur>> e : parSymbole.entrySet()) {
            Symbole sym = e.getKey();
            List<Joueur> membres = e.getValue();
            if (membres.size() <= tailleEquipe) {
                blocs.add(new Bloc(sym, new ArrayList<>(membres)));
            } else {
                List<List<Joueur>> paquets = decouper(membres, tailleEquipe);
                StringBuilder noms = new StringBuilder();
                for (Joueur j : membres) {
                    if (noms.length() > 0) noms.append(", ");
                    noms.append(j.getNomComplet());
                }
                alertes.add("Le groupe " + sym.getLibelle() + " (" + sym.getCaractere() + ") compte "
                        + membres.size() + " joueurs, trop pour une seule equipe de " + tailleEquipe
                        + ". Il a ete reparti en " + paquets.size() + " sous-groupes. Joueurs : " + noms + ".");
                for (List<Joueur> paquet : paquets) {
                    blocs.add(new Bloc(sym, paquet));
                }
            }
        }

        // Les joueurs libres : un bloc de taille 1 chacun.
        for (Joueur j : libres) {
            blocs.add(new Bloc(Symbole.AUCUN, new ArrayList<>(List.of(j))));
        }

        return new Resultat(blocs, alertes);
    }

    /**
     * Decoupe une liste en paquets de taille <= max, aussi equilibres que possible et d'au
     * moins 2 quand c'est faisable (evite d'isoler un joueur seul si un partage plus equilibre
     * existe). Exemple pour max=4 : 5 -> [3,2], 6 -> [3,3], 7 -> [4,3], 8 -> [4,4], 9 -> [3,3,3].
     */
    static List<List<Joueur>> decouper(List<Joueur> joueurs, int max) {
        int total = joueurs.size();
        int nbPaquets = (int) Math.ceil((double) total / max);
        // Tailles equilibrees : on repartit total sur nbPaquets paquets.
        int base = total / nbPaquets;
        int reste = total % nbPaquets;

        List<List<Joueur>> paquets = new ArrayList<>();
        int index = 0;
        for (int p = 0; p < nbPaquets; p++) {
            int taille = base + (p < reste ? 1 : 0);
            paquets.add(new ArrayList<>(joueurs.subList(index, index + taille)));
            index += taille;
        }
        return paquets;
    }
}
