package org.club.tournoi.serveur;

import com.google.gson.Gson;
import org.club.tournoi.model.EtatEquipe;
import org.club.tournoi.model.Equipe;
import org.club.tournoi.model.Joueur;
import org.club.tournoi.model.Match;
import org.club.tournoi.model.Rencontre;
import org.club.tournoi.service.Classement;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.serveur.ComparateurSaisies.CleMatch;
import org.club.tournoi.serveur.ComparateurSaisies.Statut;
import org.club.tournoi.serveur.ComparateurSaisies.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construit "l'état public" : la photo, en lecture seule, de ce que les
 * téléphones des capitaines doivent voir. Le PC la produit et la pousse
 * sur le serveur ; les téléphones ne font que la lire.
 * <p>
 * Contenu par équipe : son état (en attente / en match), sa rencontre en
 * cours avec les 5 matchs et leur statut de saisie, et — si sa dernière
 * rencontre vient de se terminer — un <b>résultat</b> à afficher (écran de
 * fin). La vue porte aussi le <b>classement</b> général.
 * <p>
 * Cette classe ne modifie jamais le tournoi.
 */
public final class EtatPublic {

    public record JoueurVue(String nom) {}

    public record MatchVue(
            int index, String libelle,
            List<JoueurVue> coteA, List<JoueurVue> coteB,
            boolean termine, Integer scoreA, Integer scoreB,
            String statutSaisie
    ) {}

    /** Résultat d'une rencontre terminée, du point de vue de l'équipe. */
    public record ResultatVue(
            String idRencontre,     // pour que le téléphone sache s'il l'a déjà acquitté
            boolean gagne,          // cette équipe a-t-elle gagné ?
            boolean egalite,        // (théoriquement impossible à 5 matchs, par sûreté)
            int mesMatchs,          // matchs gagnés par cette équipe
            int sesMatchs,          // matchs gagnés par l'adversaire
            int mesPoints,          // total de points marqués par cette équipe
            int sesPoints,          // total de points de l'adversaire
            int numeroAdversaire
    ) {}

    public record EquipeVue(
            int numero, String etat, String etatLibelle,
            String idRencontre, String cote,
            Integer table1, Integer table2,
            Integer numeroAdversaire, String nomAdversaire,
            List<MatchVue> matchs,
            ResultatVue resultat        // non null seulement si une rencontre vient de finir
    ) {}

    /** Une ligne du classement, simplifiée pour l'affichage. */
    public record ClassementVue(
            int rang, int numeroEquipe,
            int rencontresGagnees, int matchsGagnes, int pointsPour,
            List<String> joueurs        // composition (Prénom Nom), dans l'ordre
    ) {}

    public record Vue(
            long horodatage,
            List<EquipeVue> equipes,
            List<ClassementVue> classement
    ) {}

    private EtatPublic() {}

    public static Vue construire(ServiceTournoi service) {
        return construire(service, Map.of());
    }

    public static Vue construire(ServiceTournoi service, Map<CleMatch, Verdict> verdicts) {
        List<EquipeVue> equipes = new ArrayList<>();

        for (Equipe eq : service.getEquipes()) {
            EtatEquipe etat = eq.getEtat();

            // Rencontre EN COURS de cette équipe (si elle joue).
            Rencontre enCours = service.getRencontresEnCours().stream()
                    .filter(r -> r.impliqueEquipe(eq))
                    .findFirst().orElse(null);

            if (etat == EtatEquipe.EN_MATCH && enCours != null) {
                equipes.add(equipeEnMatch(eq, enCours, verdicts));
            } else {
                // Pas en match : peut-être une rencontre VIENT de se terminer.
                ResultatVue resultat = dernierResultat(service, eq);
                equipes.add(new EquipeVue(
                        eq.getNumero(), etat.name(), etat.getLibelle(),
                        null, null, null, null, null, null,
                        List.of(), resultat
                ));
            }
        }

        // Classement général.
        List<ClassementVue> classement = new ArrayList<>();
        for (Classement.Ligne l : Classement.calculer(service.getEquipes(), service.getRencontres())) {
            List<String> joueurs = new ArrayList<>();
            for (Joueur j : l.equipe().getJoueurs()) {
                if (j != null) joueurs.add(j.getPrenom() + " " + j.getNom());
            }
            classement.add(new ClassementVue(
                    l.rang(), l.equipe().getNumero(),
                    l.stats().rencontresGagnees(), l.stats().matchsGagnes(), l.stats().pointsPour(),
                    joueurs
            ));
        }

        return new Vue(System.currentTimeMillis(), equipes, classement);
    }

    private static EquipeVue equipeEnMatch(Equipe eq, Rencontre rencontre, Map<CleMatch, Verdict> verdicts) {
        Equipe adversaire = rencontre.adversaireDe(eq);
        boolean equipeEstA = rencontre.getEquipeA().equals(eq);
        String cote = equipeEstA ? "A" : "B";

        List<MatchVue> matchs = new ArrayList<>();
        List<Match> liste = rencontre.getMatchs();
        for (int i = 0; i < liste.size(); i++) {
            Match m = liste.get(i);
            String statut;
            if (m.estTermine()) {
                statut = "ACCORD";
            } else {
                Verdict v = verdicts.get(new CleMatch(rencontre.getId(), i));
                statut = (v == null) ? Statut.AUCUNE.name() : v.statut().name();
            }
            matchs.add(new MatchVue(
                    i, m.getLibelle(),
                    noms(m.getJoueurs(Match.Cote.A)), noms(m.getJoueurs(Match.Cote.B)),
                    m.estTermine(),
                    m.estTermine() ? m.getScore(Match.Cote.A) : null,
                    m.estTermine() ? m.getScore(Match.Cote.B) : null,
                    statut
            ));
        }

        Integer table2 = rencontre.utiliseDeuxTables() ? rencontre.getNumeroTable2() : null;

        return new EquipeVue(
                eq.getNumero(), "EN_MATCH", eq.getEtat().getLibelle(),
                rencontre.getId(), cote,
                rencontre.getNumeroTable(), table2,
                adversaire != null ? adversaire.getNumero() : null,
                adversaire != null ? ("Équipe n°" + adversaire.getNumero()) : null,
                matchs,
                null
        );
    }

    /**
     * Cherche la dernière rencontre TERMINÉE de l'équipe et en fait un
     * résultat à afficher. On ne remonte que la plus récente rencontre
     * terminée ; le téléphone décide s'il l'a déjà acquittée (via l'id).
     */
    private static ResultatVue dernierResultat(ServiceTournoi service, Equipe eq) {
        Rencontre derniere = null;
        for (Rencontre r : service.getRencontres()) {
            if (r.impliqueEquipe(eq) && r.estTerminee()) {
                derniere = r; // la liste est dans l'ordre de création ; on garde la dernière
            }
        }
        if (derniere == null) return null;

        boolean estA = derniere.getEquipeA().equals(eq);
        int mesMatchs = derniere.matchsGagnes(estA ? Match.Cote.A : Match.Cote.B);
        int sesMatchs = derniere.matchsGagnes(estA ? Match.Cote.B : Match.Cote.A);
        int mesPoints = 0, sesPoints = 0;
        for (Match m : derniere.getMatchs()) {
            if (!m.estTermine()) continue;
            mesPoints += m.getScore(estA ? Match.Cote.A : Match.Cote.B);
            sesPoints += m.getScore(estA ? Match.Cote.B : Match.Cote.A);
        }
        Equipe adv = derniere.adversaireDe(eq);
        Equipe gagnante = derniere.getGagnante();
        boolean egalite = (gagnante == null);
        boolean gagne = (gagnante != null && gagnante.equals(eq));

        return new ResultatVue(
                derniere.getId(), gagne, egalite,
                mesMatchs, sesMatchs, mesPoints, sesPoints,
                adv != null ? adv.getNumero() : -1
        );
    }

    public static String versJson(Gson gson, Vue vue) {
        return gson.toJson(vue);
    }

    private static List<JoueurVue> noms(List<Joueur> joueurs) {
        List<JoueurVue> out = new ArrayList<>();
        if (joueurs != null) {
            for (Joueur j : joueurs) {
                if (j != null) out.add(new JoueurVue(j.getNomComplet()));
            }
        }
        return out;
    }
}
