package org.club.tournoi.serveur;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.club.tournoi.model.Rencontre;
import org.club.tournoi.service.ServiceTournoi;
import org.club.tournoi.serveur.ComparateurSaisies.CleMatch;
import org.club.tournoi.serveur.ComparateurSaisies.Saisie;
import org.club.tournoi.serveur.ComparateurSaisies.Statut;
import org.club.tournoi.serveur.ComparateurSaisies.Verdict;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * L'agent de synchronisation : le "chef d'orchestre" côté PC.
 * <p>
 * À chaque cycle (toutes les quelques secondes) :
 * <ol>
 *   <li>récupère les saisies des capitaines depuis le serveur ;</li>
 *   <li>les compare deux à deux ({@link ComparateurSaisies}) ;</li>
 *   <li>pour chaque match en ACCORD, enregistre le score dans le logiciel
 *       (sur l'EDT, via {@code enregistrerScoreMatch}) puis purge ces
 *       saisies sur le serveur ;</li>
 *   <li>republie l'état enrichi (chaque match porte son statut de saisie).</li>
 * </ol>
 * Les matchs en LITIGE sont laissés tels quels : ils s'affichent en rouge
 * côté capitaine et attendent l'intervention du juge-arbitre (onglet
 * "Capitaines" : trancher ou faire resaisir).
 * <p>
 * <b>Sécurité des threads.</b> Toute lecture/écriture du modèle
 * ({@code construire}, {@code enregistrerScoreMatch}) se fait sur l'EDT
 * via {@link SwingUtilities#invokeAndWait}. Les appels réseau se font sur
 * le thread de l'agent.
 */
public final class AgentSynchro {

    private final ServiceTournoi service;
    private final ClientServeur client;
    private final Gson gson;
    private final long periodeSecondes;

    private ScheduledExecutorService planificateur;
    private final AtomicReference<String> dernierStatut = new AtomicReference<>("Arrêté");
    private Consumer<String> surStatut;

    /**
     * Info d'un litige, prête à afficher pour le juge-arbitre : la rencontre,
     * le match, les libellés d'équipes/joueurs et ce qu'a saisi chaque
     * capitaine. Les scores sont "point de vue équipe A" (aScoreA = points
     * de l'équipe A selon le capitaine A, etc.).
     */
    public record LitigeInfo(
            String idRencontre, int indexMatch,
            String libelleMatch,
            int numeroEquipeA, int numeroEquipeB,
            Integer table1, Integer table2,
            int capAscoreA, int capAscoreB,   // saisie du capitaine A
            int capBscoreA, int capBscoreB    // saisie du capitaine B
    ) {}

    /** Appelé (sur l'EDT) à chaque cycle avec la liste des litiges en cours. */
    private Consumer<List<LitigeInfo>> surLitiges;

    public AgentSynchro(ServiceTournoi service, ClientServeur client, Gson gson, long periodeSecondes) {
        this.service = service;
        this.client = client;
        this.gson = gson;
        this.periodeSecondes = Math.max(2, periodeSecondes);
    }

    public void setSurStatut(Consumer<String> surStatut) { this.surStatut = surStatut; }
    public void setSurLitiges(Consumer<List<LitigeInfo>> surLitiges) { this.surLitiges = surLitiges; }
    public String getDernierStatut() { return dernierStatut.get(); }

    public synchronized boolean estActif() {
        return planificateur != null && !planificateur.isShutdown();
    }

    public synchronized void demarrer() {
        if (estActif()) return;
        planificateur = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-synchro-tournoi");
            t.setDaemon(true);
            return t;
        });
        planificateur.scheduleWithFixedDelay(this::cycle, 0, periodeSecondes, TimeUnit.SECONDS);
        publierStatut("Démarré (cycle toutes les " + periodeSecondes + " s)");
    }

    public synchronized void arreter() {
        if (planificateur != null) {
            planificateur.shutdownNow();
            planificateur = null;
        }
        publierStatut("Arrêté");
    }

    // ---- Un cycle complet -----------------------------------------
    private void cycle() {
        try {
            // 1. Récupérer les saisies depuis le serveur.
            List<Saisie> saisies = recupererSaisies();

            // 2. Comparer (logique pure).
            Map<CleMatch, Verdict> verdicts = ComparateurSaisies.analyser(saisies);

            // 3. Appliquer les ACCORDS dans le logiciel (sur l'EDT), collecter
            //    les clés à purger sur le serveur.
            List<CleMatch> aPurger = appliquerAccords(verdicts);

            // 4. Purger sur le serveur les saisies des matchs enregistrés.
            for (CleMatch cle : aPurger) {
                purger(cle);
            }

            // 5. Reconstruire la vue (avec statuts) sur l'EDT, puis publier.
            final EtatPublic.Vue[] boite = new EtatPublic.Vue[1];
            surEdt(() -> boite[0] = EtatPublic.construire(service, verdicts));
            String json = EtatPublic.versJson(gson, boite[0]);
            ClientServeur.Reponse rep = client.ecrire("etat", json);

            int nbAccords = (int) verdicts.values().stream().filter(v -> v.statut() == Statut.ACCORD).count();
            int nbLitiges = (int) verdicts.values().stream().filter(v -> v.statut() == Statut.LITIGE).count();

            // 6. Construire la liste des litiges (sur l'EDT) et l'envoyer à l'UI.
            publierLitiges(verdicts);

            if (rep.ok()) {
                String extra = "";
                if (nbAccords > 0) extra += " · " + nbAccords + " enregistré(s)";
                if (nbLitiges > 0) extra += " · " + nbLitiges + " litige(s)";
                publierStatut("Publié à " + heure() + extra);
            } else {
                publierStatut("Échec publication (HTTP " + rep.code() + ") à " + heure());
            }
        } catch (Exception e) {
            publierStatut("Erreur : " + e.getMessage());
        }
    }

    /** Récupère et parse les saisies depuis l'action pull_saisies. */
    private List<Saisie> recupererSaisies() throws Exception {
        ClientServeur.Reponse rep = client.pullSaisies();
        List<Saisie> out = new ArrayList<>();
        if (!rep.ok()) return out;
        JsonObject racine = JsonParser.parseString(rep.corps()).getAsJsonObject();
        if (!racine.has("saisies") || racine.get("saisies").isJsonNull()) return out;
        JsonArray arr = racine.getAsJsonArray("saisies");
        for (var el : arr) {
            // Une ligne mal formée est ignorée : elle ne doit pas empêcher de traiter les autres.
            try {
                JsonObject o = el.getAsJsonObject();
                out.add(new Saisie(
                        o.get("id_rencontre").getAsString(),
                        o.get("index_match").getAsInt(),
                        o.get("cote_capitaine").getAsString(),
                        o.get("score_a").getAsInt(),
                        o.get("score_b").getAsInt()
                ));
            } catch (RuntimeException ignoree) {
                // saisie illisible : on passe à la suivante
            }
        }
        return out;
    }

    /**
     * Applique dans le logiciel les matchs en ACCORD (sur l'EDT). Renvoie la
     * liste des matchs effectivement enregistrés (à purger sur le serveur).
     */
    private List<CleMatch> appliquerAccords(Map<CleMatch, Verdict> verdicts) throws Exception {
        List<CleMatch> purges = new ArrayList<>();
        for (Map.Entry<CleMatch, Verdict> e : verdicts.entrySet()) {
            Verdict v = e.getValue();
            if (v.statut() != Statut.ACCORD) continue;
            CleMatch cle = e.getKey();
            final boolean[] enregistre = { false };
            surEdt(() -> {
                Rencontre r = trouverRencontre(cle.idRencontre());
                if (r == null) { enregistre[0] = true; return; } // rencontre disparue : purge quand même
                // Index hors grille (saisie erronée) : on purge, sinon l'exception
                // interromprait chaque cycle et bloquerait toute la publication.
                if (cle.indexMatch() < 0 || cle.indexMatch() >= r.getMatchs().size()) {
                    enregistre[0] = true; return;
                }
                // Si le match est déjà terminé (déjà enregistré à un cycle
                // précédent), on purge simplement.
                if (r.getMatchs().get(cle.indexMatch()).estTermine()) {
                    enregistre[0] = true; return;
                }
                try {
                    service.enregistrerScoreMatch(r, cle.indexMatch(), v.scoreA(), v.scoreB());
                    enregistre[0] = true;
                } catch (Exception ex) {
                    // Score refusé par le logiciel (ne devrait pas arriver si les
                    // deux capitaines sont d'accord sur un score valide). On NE
                    // purge pas : le JA verra l'anomalie. On journalise le statut.
                    enregistre[0] = false;
                    publierStatut("Score refusé (" + cle.idRencontre() + " match " + (cle.indexMatch()+1)
                            + ") : " + ex.getMessage());
                }
            });
            if (enregistre[0]) purges.add(cle);
        }
        return purges;
    }

    private void purger(CleMatch cle) {
        try { client.purgerSaisies(cle.idRencontre(), cle.indexMatch()); }
        catch (Exception ignore) { /* on retentera au prochain cycle */ }
    }

    private Rencontre trouverRencontre(String id) {
        return service.getRencontresEnCours().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst().orElse(null);
    }

    // ---- Litiges : exposition à l'UI + résolution par le JA -------

    /** Construit la liste des litiges (sur l'EDT) et la transmet à l'UI. */
    private void publierLitiges(Map<CleMatch, Verdict> verdicts) throws Exception {
        if (surLitiges == null) return;
        final List<LitigeInfo> liste = new ArrayList<>();
        surEdt(() -> {
            for (Map.Entry<CleMatch, Verdict> e : verdicts.entrySet()) {
                Verdict v = e.getValue();
                if (v.statut() != Statut.LITIGE) continue;
                CleMatch cle = e.getKey();
                Rencontre r = trouverRencontre(cle.idRencontre());
                if (r == null) continue;
                int idx = cle.indexMatch();
                if (idx < 0 || idx >= r.getMatchs().size()) continue;
                String libelle = r.getMatchs().get(idx).getLibelle();
                liste.add(new LitigeInfo(
                        cle.idRencontre(), idx, libelle,
                        r.getEquipeA().getNumero(), r.getEquipeB().getNumero(),
                        r.getNumeroTable(),
                        r.utiliseDeuxTables() ? r.getNumeroTable2() : null,
                        v.aScoreA(), v.aScoreB(),
                        v.bScoreA(), v.bScoreB()
                ));
            }
        });
        SwingUtilities.invokeLater(() -> surLitiges.accept(liste));
    }

    /**
     * Le juge-arbitre tranche un litige : il fixe le score (point de vue
     * équipe A), qui est enregistré dans le logiciel et fait autorité. Les
     * saisies capitaines de ce match sont purgées. Si c'était le 5e match,
     * la rencontre se clôt automatiquement.
     * <p>À appeler depuis l'UI (EDT).
     */
    public void trancherLitige(String idRencontre, int indexMatch, int scoreA, int scoreB) {
        Rencontre r = trouverRencontre(idRencontre);
        if (r == null) throw new IllegalStateException("Rencontre introuvable (déjà terminée ?).");
        if (indexMatch < 0 || indexMatch >= r.getMatchs().size()) {
            throw new IllegalArgumentException("Index de match invalide : " + indexMatch);
        }
        if (!r.getMatchs().get(indexMatch).estTermine()) {
            service.enregistrerScoreMatch(r, indexMatch, scoreA, scoreB);
        }
        // Purge les saisies sur le serveur (sur le thread réseau, pas l'EDT).
        final CleMatch cle = new CleMatch(idRencontre, indexMatch);
        new Thread(() -> purger(cle), "purge-litige").start();
    }

    /**
     * Le juge-arbitre demande aux deux capitaines de resaisir : on efface
     * simplement les saisies du match, qui repasse "à jouer" partout.
     * <p>À appeler depuis l'UI (EDT).
     */
    public void faireResaisir(String idRencontre, int indexMatch) {
        final CleMatch cle = new CleMatch(idRencontre, indexMatch);
        new Thread(() -> purger(cle), "resaisir-litige").start();
    }

    // ---- Utilitaires EDT / statut ----------------------------------
    private void surEdt(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeAndWait(action);
    }

    private void publierStatut(String msg) {
        dernierStatut.set(msg);
        if (surStatut != null) SwingUtilities.invokeLater(() -> surStatut.accept(msg));
    }

    private static String heure() {
        return java.time.LocalTime.now().withNano(0).toString();
    }
}
