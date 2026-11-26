package org.club.tournoi.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Emplacement fixe des donnees de l'application (tournois et bases joueurs).
 * <p>
 * Avant, les dossiers {@code Base_Tournoi} et {@code Base_Joueur} etaient crees dans le dossier
 * de lancement : relancer l'application depuis un autre dossier (autre fenetre cmd, raccourci,
 * double-clic sur le jar) donnait l'impression que tout avait ete perdu. Les donnees vivent
 * maintenant dans un dossier unique, independant de l'endroit d'ou l'on lance :
 * <ul>
 *   <li>par defaut {@code <dossier utilisateur>/Tournoi-TT} (ex. {@code C:\Users\Nom\Tournoi-TT}) ;</li>
 *   <li>ou le dossier donne par la propriete systeme {@code -Dtournoi.donnees=...}
 *       (pratique pour une installation sur cle USB).</li>
 * </ul>
 * Ce dossier n'est jamais touche par une recompilation ({@code mvn clean}, {@code build/}).
 */
public final class DossierDonnees {

    /** Nom de la propriete systeme permettant de choisir un autre dossier. */
    public static final String PROPRIETE = "tournoi.donnees";
    public static final String SOUS_DOSSIER_TOURNOIS = "Base_Tournoi";
    public static final String SOUS_DOSSIER_JOUEURS = "Base_Joueur";

    private DossierDonnees() { }

    /** Dossier racine des donnees, selon la propriete systeme ou le dossier utilisateur. */
    public static Path racine() {
        String choisi = System.getProperty(PROPRIETE);
        if (choisi != null && !choisi.isBlank()) {
            return Paths.get(choisi).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home"), "Tournoi-TT");
    }

    /**
     * Recupere les donnees d'une ancienne installation : si le dossier de lancement contient
     * encore {@code Base_Tournoi} ou {@code Base_Joueur}, leurs fichiers .json sont copies dans
     * la racine (sans jamais ecraser un fichier deja present). Les originaux sont conserves.
     *
     * @return le nombre de fichiers copies
     */
    public static int migrerAnciensDossiers(Path ancienneRacine, Path racine) {
        int copies = 0;
        for (String sousDossier : new String[]{SOUS_DOSSIER_TOURNOIS, SOUS_DOSSIER_JOUEURS}) {
            Path source = ancienneRacine.resolve(sousDossier).toAbsolutePath().normalize();
            Path cible = racine.resolve(sousDossier).toAbsolutePath().normalize();
            if (!Files.isDirectory(source) || source.equals(cible)) {
                continue;
            }
            try (Stream<Path> fichiers = Files.list(source)) {
                Files.createDirectories(cible);
                for (Path f : (Iterable<Path>) fichiers.filter(p -> p.toString().endsWith(".json"))::iterator) {
                    Path destination = cible.resolve(f.getFileName());
                    if (!Files.exists(destination)) {
                        Files.copy(f, destination, StandardCopyOption.COPY_ATTRIBUTES);
                        copies++;
                    }
                }
            } catch (IOException e) {
                // Migration au mieux : en cas d'echec, les anciens fichiers restent ou ils sont.
            }
        }
        return copies;
    }
}
