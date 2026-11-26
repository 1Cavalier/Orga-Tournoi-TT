package org.club.tournoi.ui.theme;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Theme visuel centralise de l'application.
 * <p>
 * Toutes les couleurs, polices et marges vivent ici. Fini les {@code new Color(245,245,245)}
 * dissemines et dupliques dans chaque vue : un seul endroit a modifier pour changer l'allure
 * generale, et une coherence garantie entre les ecrans.
 * <p>
 * Direction visuelle : univers du tennis de table de club. Fond ardoise clair et calme,
 * vert "tapis de table" pour les surfaces principales, et l'orange de la balle comme unique
 * accent vif (boutons d'action, selection). L'accent est rare, c'est ce qui le rend lisible
 * sur un ecran projete pendant le tournoi.
 */
public final class Theme {

    private Theme() { }

    // === Palette ===
    /** Fond general, ardoise tres clair. */
    public static final Color FOND            = new Color(0xF2, 0xF4, 0xF7);
    /** Surfaces (panneaux, cartes). */
    public static final Color SURFACE         = new Color(0xFF, 0xFF, 0xFF);
    /** Vert "table de jeu", pour les entetes et bandeaux. */
    public static final Color VERT_TABLE      = new Color(0x1E, 0x5E, 0x4E);
    public static final Color VERT_TABLE_CLAIR= new Color(0x2E, 0x7D, 0x69);
    /** Accent balle : boutons d'action et selection. A utiliser avec parcimonie. */
    public static final Color ACCENT          = new Color(0xE8, 0x7A, 0x2C);
    public static final Color ACCENT_FONCE    = new Color(0xB8, 0x5A, 0x10);
    public static final Color ACCENT_SURVOL   = new Color(0xF0, 0x8E, 0x45);
    /** Texte principal, ardoise profond (jamais noir pur, plus doux a l'oeil). */
    public static final Color TEXTE           = new Color(0x22, 0x2A, 0x33);
    public static final Color TEXTE_DISCRET   = new Color(0x67, 0x72, 0x80);
    /** Lignes de separation fines. */
    public static final Color BORDURE         = new Color(0xDD, 0xE1, 0xE7);
    /** Alternance de lignes dans les tableaux. */
    public static final Color LIGNE_PAIRE     = new Color(0xF7, 0xF9, 0xFB);
    public static final Color LIGNE_IMPAIRE   = SURFACE;
    /** Selection dans les tableaux (teinte accent tres diluee). */
    public static final Color SELECTION       = new Color(0xFC, 0xE9, 0xD8);
    /** Etats. */
    public static final Color SUCCES          = new Color(0x2E, 0x7D, 0x32);
    public static final Color ERREUR          = new Color(0xC6, 0x28, 0x28);

    // Couleurs de niveau de force des joueurs, pour colorer les noms cote admin.
    // Choisies pour rester lisibles sur fond blanc : le "jaune" est un ocre fonce.
    /** Niveau 1 (plus forts) : rouge. */
    public static final Color NIVEAU_1 = new Color(0xC6, 0x28, 0x28);
    /** Niveau 2 : orange. */
    public static final Color NIVEAU_2 = new Color(0xD9, 0x6A, 0x0B);
    /** Niveau 3 : jaune ocre (assez fonce pour se voir sur blanc). */
    public static final Color NIVEAU_3 = new Color(0xB8, 0x8A, 0x00);
    /** Niveau 4 (plus faibles) : bleu. */
    public static final Color NIVEAU_4 = new Color(0x1B, 0x5E, 0xA8);

    /** Couleur associee a un niveau de force (1 a 4). Retombe sur le texte normal hors bornes. */
    public static Color couleurNiveau(int niveau) {
        return switch (niveau) {
            case 1 -> NIVEAU_1;
            case 2 -> NIVEAU_2;
            case 3 -> NIVEAU_3;
            case 4 -> NIVEAU_4;
            default -> TEXTE;
        };
    }

    // === Typographie ===
    private static final String FAMILLE = "Segoe UI";
    public static final Font TITRE       = police(Font.BOLD, 20);
    public static final Font SOUS_TITRE  = police(Font.BOLD, 15);
    public static final Font CORPS       = police(Font.PLAIN, 14);
    public static final Font CORPS_GRAS  = police(Font.BOLD, 14);
    public static final Font PETIT       = police(Font.PLAIN, 12);
    public static final Font PETIT_GRAS  = police(Font.BOLD, 12);
    public static final Font DONNEE      = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    // === Espacements (une echelle simple et coherente) ===
    public static final int ESPACE_XS = 4;
    public static final int ESPACE_S  = 8;
    public static final int ESPACE_M  = 16;
    public static final int ESPACE_L  = 24;

    private static Font police(int style, int taille) {
        return new Font(FAMILLE, style, taille);
    }

    // === Bordures reutilisables ===

    /** Marge interieure uniforme. */
    public static Border marge(int taille) {
        return BorderFactory.createEmptyBorder(taille, taille, taille, taille);
    }

    /** Carte : fond surface, coin arrondi visuel via bordure fine + marge. */
    public static Border carte() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE, 1, true),
                marge(ESPACE_M));
    }

    /** Titre de section discret, en vert table. */
    public static Border section(String titre) {
        var b = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(), titre);
        b.setTitleFont(SOUS_TITRE);
        b.setTitleColor(VERT_TABLE);
        return BorderFactory.createCompoundBorder(b, marge(ESPACE_S));
    }
}
