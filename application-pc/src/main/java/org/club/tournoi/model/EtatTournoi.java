package org.club.tournoi.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Etat complet d'un tournoi a un instant donne.
 * <p>
 * C'est cet objet qui est persiste (JSON). Contrairement a l'ancienne {@code BaseTournoi}
 * qui ne sauvegardait que joueurs + equipes, on conserve aussi les <b>tables</b> et les
 * <b>rencontres</b> : un tournoi interrompu peut donc etre repris exactement dans l'etat
 * ou il etait.
 */
public class EtatTournoi {

    private final List<Joueur> joueurs = new ArrayList<>();
    private final List<Equipe> equipes = new ArrayList<>();
    private final List<Table> tables = new ArrayList<>();
    private final List<Rencontre> rencontres = new ArrayList<>();
    private final List<Barrage> barrages = new ArrayList<>();
    /** Positions finales attribuees par les departages : numero d'equipe -> position finale. */
    private final Map<Integer, Integer> positionsFinales = new HashMap<>();
    /** Pose d'office force : nb de rencontres gagnees du groupe -> numero d'equipe choisie. */
    private final Map<Integer, Integer> poseDofficeForce = new HashMap<>();
    /** Rang de chaque equipe au dernier enregistrement de score (pour les fleches montee/descente). */
    private final Map<Integer, Integer> rangsPrecedents = new HashMap<>();
    private ParametresTournoi parametres = new ParametresTournoi();

    public Map<Integer, Integer> getRangsPrecedents() {
        return rangsPrecedents;
    }

    public List<Barrage> getBarrages() {
        return barrages;
    }

    public Map<Integer, Integer> getPositionsFinales() {
        return positionsFinales;
    }

    public Map<Integer, Integer> getPoseDofficeForce() {
        return poseDofficeForce;
    }

    public List<Joueur> getJoueurs() {
        return joueurs;
    }

    /** Parametres generaux (jamais null : recree par defaut si absent d'une vieille sauvegarde). */
    public ParametresTournoi getParametres() {
        if (parametres == null) {
            parametres = new ParametresTournoi();
        }
        return parametres;
    }

    public void setParametres(ParametresTournoi parametres) {
        this.parametres = parametres;
    }

    public List<Equipe> getEquipes() {
        return equipes;
    }

    public List<Table> getTables() {
        return tables;
    }

    public List<Rencontre> getRencontres() {
        return rencontres;
    }

    /**
     * Reconstruit le partage des references apres un chargement JSON.
     * <p>
     * Gson cree un objet distinct pour chaque occurrence : l'equipe n°1 de la liste des equipes
     * et l'equipe n°1 d'une rencontre deviennent deux objets differents (egaux au sens de
     * {@code equals}, mais independants). Sans ce relinkage, terminer une rencontre reprise
     * apres chargement modifiait la copie, et l'equipe de la liste restait "En match" pour
     * toujours. On remplace donc chaque reference par l'instance canonique :
     * joueurs par id, equipes par numero.
     */
    public void relierReferences() {
        Map<String, Joueur> joueursParId = new HashMap<>();
        for (Joueur j : joueurs) {
            joueursParId.putIfAbsent(j.getId(), j);
        }
        // Un joueur inconnu de la liste (vieille sauvegarde) est conserve tel quel.
        java.util.function.UnaryOperator<Joueur> joueurCanonique =
                j -> j == null ? null : joueursParId.computeIfAbsent(j.getId(), id -> j);

        Map<Integer, Equipe> equipesParNumero = new HashMap<>();
        for (Equipe e : equipes) {
            e.getJoueurs().replaceAll(joueurCanonique);
            equipesParNumero.putIfAbsent(e.getNumero(), e);
        }
        // Une equipe supprimee depuis reste referencee par ses anciennes rencontres : on garde la copie.
        java.util.function.UnaryOperator<Equipe> equipeCanonique =
                e -> e == null ? null : equipesParNumero.getOrDefault(e.getNumero(), e);

        for (Rencontre r : rencontres) {
            r.relierReferences(equipeCanonique, joueurCanonique);
        }
        for (Barrage b : barrages) {
            b.relierReferences(equipeCanonique, joueurCanonique);
        }
    }
}
