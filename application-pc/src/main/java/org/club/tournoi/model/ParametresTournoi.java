package org.club.tournoi.model;

/**
 * Parametres generaux du tournoi, fixes a l'initialisation et modifiables en cours.
 * <p>
 * Regroupe les reglages qui s'appliquent a l'ensemble du tournoi :
 * <ul>
 *   <li>{@code pointEcart} : les matchs se jouent-ils avec 2 points d'ecart obligatoires
 *       (jusqu'a 11-9, puis prolongation type 12-10, 13-11...) ou au premier a 11 sans ecart
 *       (11-10 possible) ;</li>
 *   <li>{@code dureeEstimeeMinutes} : duree theorique d'une rencontre, utilisee pour estimer le
 *       temps restant et conseiller d'attendre qu'une table se libere ;</li>
 *   <li>{@code seuilConseilAttenteMinutes} : si une rencontre doit se terminer dans moins que ce
 *       seuil, l'algo suisse conseille d'attendre plutot que de lancer un appariement moins bon.</li>
 * </ul>
 * Cet objet est persiste avec l'etat du tournoi.
 */
public class ParametresTournoi {

    private boolean pointEcart = true;             // par defaut : avec 2 points d'ecart
    private int dureeEstimeeMinutes = 15;          // duree theorique d'une rencontre
    private int seuilConseilAttenteMinutes = 3;    // conseil d'attente sous ce seuil
    private boolean modeDepartage = false;         // true = on prepare les departages (fige le suisse)
    private String coinDepartTables = "HAUT_GAUCHE"; // coin de depart de la numerotation en serpentin
    private FormatTournoi format = FormatTournoi.EQUIPES_3_4; // format du tournoi
    private int lignes = 3;    // taille de la salle (lignes)
    private int colonnes = 4;  // taille de la salle (colonnes)

    public int getLignes() { return lignes < 1 ? 1 : lignes; }
    public void setLignes(int lignes) { this.lignes = Math.max(1, lignes); }
    public int getColonnes() { return colonnes < 1 ? 1 : colonnes; }
    public void setColonnes(int colonnes) { this.colonnes = Math.max(1, colonnes); }

    public FormatTournoi getFormat() {
        return format == null ? FormatTournoi.EQUIPES_3_4 : format;
    }

    public void setFormat(FormatTournoi format) {
        this.format = format == null ? FormatTournoi.EQUIPES_3_4 : format;
    }

    /** Coin ou commence la numerotation des tables (serpentin). Jamais null. */
    public String getCoinDepartTables() {
        return coinDepartTables == null ? "HAUT_GAUCHE" : coinDepartTables;
    }

    public void setCoinDepartTables(String coin) {
        this.coinDepartTables = coin == null ? "HAUT_GAUCHE" : coin;
    }

    public boolean isModeDepartage() {
        return modeDepartage;
    }

    public void setModeDepartage(boolean modeDepartage) {
        this.modeDepartage = modeDepartage;
    }

    public boolean isPointEcart() {
        return pointEcart;
    }

    public void setPointEcart(boolean pointEcart) {
        this.pointEcart = pointEcart;
    }

    public int getDureeEstimeeMinutes() {
        return dureeEstimeeMinutes;
    }

    public void setDureeEstimeeMinutes(int dureeEstimeeMinutes) {
        this.dureeEstimeeMinutes = Math.max(1, dureeEstimeeMinutes);
    }

    public int getSeuilConseilAttenteMinutes() {
        return seuilConseilAttenteMinutes;
    }

    public void setSeuilConseilAttenteMinutes(int seuilConseilAttenteMinutes) {
        this.seuilConseilAttenteMinutes = Math.max(0, seuilConseilAttenteMinutes);
    }
}
