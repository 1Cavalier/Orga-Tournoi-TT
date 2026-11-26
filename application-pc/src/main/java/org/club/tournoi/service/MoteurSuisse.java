package org.club.tournoi.service;

import org.club.tournoi.model.*;

import java.util.*;

/**
 * Moteur d'appariement facon "systeme suisse", sans tours fixes.
 * <p>
 * A la demande (typiquement quand une table se libere), le moteur propose la meilleure
 * rencontre a lancer, selon ces priorites :
 * <ol>
 *   <li>on part de l'equipe <b>en attente depuis le plus longtemps</b> (a egalite, celle qui a
 *       joue le moins de rencontres) ;</li>
 *   <li>on lui cherche un adversaire de <b>score le plus proche</b> (nombre de rencontres
 *       gagnees), <b>jamais deja affronte</b>, en privilegiant la aussi l'anciennete d'attente
 *       et le faible nombre de matchs ;</li>
 *   <li>on choisit d'occuper <b>1 ou 2 tables</b> : deux tables si le nombre d'equipes en attente
 *       est faible par rapport aux tables libres (pour finir plus vite et ne pas laisser de
 *       tables vides), une seule sinon (pour faire tourner un maximum d'equipes en parallele).</li>
 * </ol>
 * Le moteur peut aussi <b>conseiller d'attendre</b> : si aucune paire ideale n'est dispo mais
 * qu'une rencontre en cours doit se terminer tres bientot (selon la duree estimee), il le signale.
 * <p>
 * Ne modifie rien : il calcule une {@link Suggestion}. C'est le service qui l'applique via
 * {@code lancerRencontre}.
 */
public final class MoteurSuisse {

    /** Suggestion du moteur : soit une rencontre a lancer, soit un conseil d'attente, soit rien. */
    public record Suggestion(
            Equipe equipeA, Equipe equipeB,
            List<Table> tables,          // 1 ou 2 tables proposees
            boolean conseilAttente,      // true si mieux vaut attendre
            String message) {

        public boolean estLancable() {
            return equipeA != null && equipeB != null && tables != null && !tables.isEmpty();
        }
    }

    private final ServiceTournoi service;

    public MoteurSuisse(ServiceTournoi service) {
        this.service = service;
    }

    /**
     * Calcule la prochaine rencontre suggeree, ou un conseil d'attente, a l'instant donne.
     */
    public Suggestion suggerer(long maintenantMillis) {
        List<Table> tablesLibres = service.getTables().stream()
                .filter(Table::estDisponible)
                .sorted(Comparator.comparingInt(Table::getNumero))
                .toList();

        List<Equipe> enAttente = new ArrayList<>(service.getEquipes().stream()
                .filter(e -> e.getEtat() == EtatEquipe.EN_ATTENTE)
                .toList());

        if (tablesLibres.isEmpty()) {
            return rien("Aucune table disponible pour le moment.");
        }
        if (enAttente.size() < 2) {
            return rien("Il faut au moins deux equipes en attente pour lancer une rencontre.");
        }

        // Ordonne les equipes en attente par priorite : plus longue attente, puis moins de matchs.
        enAttente.sort(comparateurPriorite());

        // Cherche la meilleure paire (equipe prioritaire vs adversaire de score proche non affronte).
        Paire paire = trouverMeilleurePaire(enAttente);

        if (paire == null) {
            // Aucune paire possible (tout le monde s'est deja affronte, ou impossible d'apparier).
            // Peut-etre qu'une rencontre se termine bientot : conseiller d'attendre.
            String conseil = conseilSiRencontreBientotFinie(maintenantMillis);
            if (conseil != null) {
                return new Suggestion(null, null, List.of(), true, conseil);
            }
            return rien("Aucun appariement possible sans re-affrontement pour l'instant.");
        }

        // Choix du nombre de tables : 1 ou 2.
        int nbTables = choisirNombreTables(enAttente.size(), tablesLibres.size());
        List<Table> tables = choisirTablesAdjacentes(tablesLibres, nbTables);

        // Conseil d'attente : si une rencontre finit tres bientot, on peut suggerer de patienter
        // (surtout s'il ne reste qu'une paire "imparfaite" a lancer). On l'ajoute en info, sans
        // bloquer : la suggestion reste lancable.
        String info = "Equipe " + paire.a.getNumero() + " contre equipe " + paire.b.getNumero()
                + " sur " + (tables.size() == 2 ? "2 tables" : "1 table") + ".";
        String conseil = conseilSiRencontreBientotFinie(maintenantMillis);
        boolean attente = false;
        if (conseil != null && paire.ecartScore > 0) {
            // On propose quand meme, mais on signale qu'attendre donnerait peut-etre mieux.
            info += " " + conseil;
            attente = true;
        }

        return new Suggestion(paire.a, paire.b, new ArrayList<>(tables), attente, info);
    }

    // ------------------------------------------------------------------

    private Comparator<Equipe> comparateurPriorite() {
        return (e1, e2) -> {
            // Attente la plus longue d'abord : plus petit "depuis" = attend depuis plus longtemps.
            long t1 = e1.getEnAttenteDepuisMillis();
            long t2 = e2.getEnAttenteDepuisMillis();
            // Les equipes jamais mises en attente (0) sont considerees comme attendant depuis le debut.
            long ref1 = t1 == 0 ? Long.MIN_VALUE : t1;
            long ref2 = t2 == 0 ? Long.MIN_VALUE : t2;
            int c = Long.compare(ref1, ref2);
            if (c != 0) return c;
            // Puis moins de rencontres jouees d'abord.
            c = Integer.compare(rencontresJouees(e1), rencontresJouees(e2));
            if (c != 0) return c;
            return Integer.compare(e1.getNumero(), e2.getNumero());
        };
    }

    private static final class Paire {
        final Equipe a, b;
        final int ecartScore;
        Paire(Equipe a, Equipe b, int ecartScore) { this.a = a; this.b = b; this.ecartScore = ecartScore; }
    }

    /**
     * Pour l'equipe la plus prioritaire, trouve l'adversaire de score le plus proche, non deja
     * affronte. Si la premiere equipe ne peut affronter personne, on passe a la suivante.
     */
    private Paire trouverMeilleurePaire(List<Equipe> enAttentePrioritaire) {
        for (Equipe a : enAttentePrioritaire) {
            int scoreA = rencontresGagnees(a);
            Equipe meilleur = null;
            int meilleurEcart = Integer.MAX_VALUE;
            // Les adversaires candidats sont deja tries par priorite (ordre de la liste).
            for (Equipe b : enAttentePrioritaire) {
                if (b.equals(a)) continue;
                if (service.dejaAffrontees(a, b)) continue;
                int ecart = Math.abs(scoreA - rencontresGagnees(b));
                if (ecart < meilleurEcart) {
                    meilleurEcart = ecart;
                    meilleur = b;
                }
            }
            if (meilleur != null) {
                return new Paire(a, meilleur, meilleurEcart);
            }
        }
        return null;
    }

    /** Choisit 1 ou 2 tables libres ; pour 2 tables, elles doivent avoir des numeros consecutifs. */
    private List<Table> choisirTablesAdjacentes(List<Table> tablesLibres, int nbTables) {
        if (nbTables <= 1 || tablesLibres.size() < 2) {
            return new java.util.ArrayList<>(tablesLibres.subList(0, Math.min(1, tablesLibres.size())));
        }
        // tablesLibres est triee par numero croissant. Cherche une paire de numeros consecutifs
        // (les 2 tables doivent se suivre : ex. 2 et 3).
        for (int i = 0; i + 1 < tablesLibres.size(); i++) {
            Table a = tablesLibres.get(i);
            Table b = tablesLibres.get(i + 1);
            if (b.getNumero() == a.getNumero() + 1) {
                List<Table> paire = new java.util.ArrayList<>();
                paire.add(a);
                paire.add(b);
                return paire;
            }
        }
        // Pas de voisines libres : on se rabat sur une seule table.
        List<Table> une = new java.util.ArrayList<>();
        une.add(tablesLibres.get(0));
        return une;
    }

    /**
     * 2 tables si peu d'equipes en attente par rapport aux tables (on peut se permettre d'etaler
     * une rencontre sur 2 tables sans laisser d'equipes sur le banc) ; 1 sinon.
     * <p>
     * Regle voulue : on privilegie d'abord de lancer un maximum de rencontres (pour que le moins
     * d'equipes possible reste sur le banc), puis on donne une 2e table a une rencontre tant qu'il
     * reste assez de tables pour toutes les rencontres encore lancables.
     * <p>
     * Concretement, pour la rencontre qu'on s'apprete a lancer : le nombre de rencontres qu'on
     * pourra encore former avec les equipes en attente (celle-ci comprise) est
     * {@code paires = equipesEnAttente / 2}. Il faut au minimum {@code paires} tables (une par
     * rencontre). S'il reste au moins une table en plus de ce minimum, on peut doubler cette
     * rencontre. Exemple : 7 equipes, 10 tables &rarr; paires = 3, minimum 3 tables, il en reste 7,
     * donc on double (et chacune des 3 rencontres sera doublee a tour de role).
     */
    private int choisirNombreTables(int equipesEnAttente, int tablesLibres) {
        if (tablesLibres < 2) return 1;
        int paires = Math.max(1, equipesEnAttente / 2); // rencontres encore possibles
        // Tables necessaires pour lancer toutes ces rencontres sur au moins 1 table chacune.
        int minimumPourToutes = paires;
        // Peut-on donner 2 tables a celle-ci en gardant 1 table pour chacune des autres ?
        // Cette rencontre prend 2, les (paires - 1) autres prennent 1 chacune.
        int besoinSiOnDouble = 2 + (paires - 1);
        if (tablesLibres >= besoinSiOnDouble) {
            return 2;
        }
        return 1;
    }

    /**
     * Renvoie un message de conseil si une rencontre en cours doit se terminer dans moins que le
     * seuil configure, sinon null.
     */
    private String conseilSiRencontreBientotFinie(long maintenantMillis) {
        var params = service.getParametres();
        int dureeEstimeeSec = params.getDureeEstimeeMinutes() * 60;
        int seuilSec = params.getSeuilConseilAttenteMinutes() * 60;
        if (seuilSec <= 0) return null;

        Long minRestant = null;
        for (Rencontre r : service.getRencontresEnCours()) {
            long ecoule = r.secondesEcoulees(maintenantMillis);
            long restant = dureeEstimeeSec - ecoule;
            if (restant >= 0 && restant <= seuilSec) {
                if (minRestant == null || restant < minRestant) {
                    minRestant = restant;
                }
            }
        }
        if (minRestant == null) return null;
        long minutes = Math.max(1, Math.round(minRestant / 60.0));
        return "Une table devrait se liberer dans ~" + minutes + " min : vous pouvez attendre "
                + "pour un meilleur appariement.";
    }

    private int rencontresJouees(Equipe e) {
        return (int) service.getRencontres().stream()
                .filter(r -> r.impliqueEquipe(e) && r.estTerminee())
                .count();
    }

    private int rencontresGagnees(Equipe e) {
        return (int) service.getRencontres().stream()
                .filter(Rencontre::estTerminee)
                .filter(r -> e.equals(r.getGagnante()))
                .count();
    }

    private Suggestion rien(String message) {
        return new Suggestion(null, null, List.of(), false, message);
    }
}
