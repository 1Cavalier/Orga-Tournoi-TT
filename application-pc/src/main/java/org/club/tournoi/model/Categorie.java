package org.club.tournoi.model;

/**
 * Categories de joueurs du club, avec un niveau de force associe.
 * <p>
 * Le {@code niveau} va de 1 (les plus forts) a 4 (les plus faibles). Plusieurs categories
 * partagent le meme niveau : elles sont alors considerees d'une force equivalente. Ce niveau
 * sert au tri des joueurs et aux modes de composition automatique (rang 1, aleatoire), pour
 * repartir les forces dans les equipes.
 * <p>
 * L'ordre de declaration de l'enum n'est volontairement PAS modifie (il sert de reference
 * stable pour la persistance JSON) : le niveau est porte par un champ, pas par la position.
 */
public enum Categorie {
    // Niveau 4 (declaree en premier pour raison historique, mais c'est le champ qui compte)
    JEUNE_MERCREDI_1("Jeune Mercredi 1er groupe", 4),
    JEUNE_MERCREDI_2("Jeune Mercredi 2e groupe", 3),
    JEUNE_MERCREDI_3("Jeune Mercredi 3e groupe", 2),
    JEUNE_MARDI("Jeune Mardi", 3),
    JEUNE_COMPETITION("Jeune Competition", 1),
    JEUNE_LOISIR("Jeune Loisir", 4),
    ADULTE_LOISIR("Adulte Loisir", 3),
    ADULTE_ENTRAINEMENT("Adulte Entrainement", 2),
    ADULTE_COMPETITION("Adulte Competition", 1),
    PARENT_ACCOMPAGNANT("Parent Accompagnant", 4),
    INVITE_DEBUTANT("Invite Debutant", 4),
    INVITE_COMPETITION("Invite Competition", 1);

    /** Niveau le plus fort (valeur numerique la plus basse). */
    public static final int NIVEAU_MAX = 1;
    /** Niveau le plus faible (valeur numerique la plus haute). */
    public static final int NIVEAU_MIN = 4;

    private final String libelle;
    private final int niveau;

    Categorie(String libelle, int niveau) {
        this.libelle = libelle;
        this.niveau = niveau;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Niveau de force : 1 = plus fort, 4 = plus faible. */
    public int getNiveau() {
        return niveau;
    }

    @Override
    public String toString() {
        return libelle;
    }
}
