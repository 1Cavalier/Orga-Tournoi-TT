package org.club.tournoi.persistence;

import com.google.gson.*;
import org.club.tournoi.model.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sauvegarde et chargement de l'etat du tournoi au format JSON (lisible, robuste,
 * 100% hors ligne). Remplace la serialisation binaire {@code .ser} de l'ancienne
 * version, qui cassait au moindre changement de classe et ne conservait ni les tables
 * ni les rencontres.
 * <p>
 * Le seul point technique est le polymorphisme de {@link Match} : comme c'est une
 * interface avec deux implementations, on enregistre un adaptateur qui ecrit le type
 * concret dans le JSON et sait le relire.
 * <p>
 * Toutes les ecritures sont atomiques (fichier temporaire puis renommage) : une coupure de
 * courant ou un arret brutal pendant une sauvegarde laisse intact le fichier precedent.
 */
public class GestionnaireSauvegarde {

    private static final String EXTENSION = ".json";
    private final Gson gson;
    private final Path dossier;

    public GestionnaireSauvegarde(Path dossier) {
        this.dossier = dossier;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Match.class, new AdaptateurMatch())
                .create();
    }

    public Path getDossier() {
        return dossier;
    }

    /** Cree le dossier de sauvegarde s'il n'existe pas encore. */
    public void initialiser() throws IOException {
        Files.createDirectories(dossier);
    }

    /** Liste les fichiers de sauvegarde disponibles, le plus recemment modifie en premier. */
    public List<Path> listerSauvegardes() throws IOException {
        return listerJson(dossier);
    }

    public Path cheminPour(String nom) {
        String fichier = nom.endsWith(EXTENSION) ? nom : nom + EXTENSION;
        return dossier.resolve(fichier);
    }

    public void sauvegarder(EtatTournoi etat, Path fichier) throws IOException {
        ecrireAtomique(fichier, etat);
    }

    /**
     * Ecrit un objet en JSON sans jamais laisser de fichier a moitie ecrit : on ecrit d'abord
     * dans un fichier temporaire du meme dossier, puis on le renomme a la place de l'ancien.
     */
    private void ecrireAtomique(Path fichier, Object contenu) throws IOException {
        Path cible = fichier.toAbsolutePath();
        Files.createDirectories(cible.getParent());
        Path temporaire = cible.resolveSibling(cible.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(temporaire, StandardCharsets.UTF_8)) {
            gson.toJson(contenu, w);
        }
        try {
            Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Systeme de fichiers sans renommage atomique : remplacement simple.
            Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Fichiers .json d'un dossier, le plus recemment modifie en premier. */
    private static List<Path> listerJson(Path dossierJson) throws IOException {
        if (!Files.isDirectory(dossierJson)) {
            return List.of();
        }
        try (Stream<Path> flux = Files.list(dossierJson)) {
            return flux.filter(p -> p.toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(GestionnaireSauvegarde::dateModification).reversed()
                            .thenComparing(Comparator.naturalOrder()))
                    .toList();
        }
    }

    private static FileTime dateModification(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /**
     * Charge un etat depuis un fichier JSON.
     * @throws IOException si le fichier est illisible ou corrompu (l'appelant decide quoi faire,
     *                     au lieu de recevoir un {@code null} silencieux comme avant)
     */
    public EtatTournoi charger(Path fichier) throws IOException {
        try (Reader r = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            EtatTournoi etat = gson.fromJson(r, EtatTournoi.class);
            if (etat == null) {
                throw new IOException("Fichier de sauvegarde vide ou illisible : " + fichier);
            }
            // Retablit le partage des objets (equipes, joueurs) perdu par la deserialisation.
            etat.relierReferences();
            return etat;
        } catch (JsonParseException e) {
            throw new IOException("Sauvegarde corrompue : " + fichier, e);
        }
    }

    // ---- Base joueurs permanente (fichier séparé) -----------------

    /** Sauvegarde la base joueurs permanente dans son propre fichier JSON. */
    public void sauvegarderBaseJoueurs(BaseJoueurs base, Path fichier) throws IOException {
        ecrireAtomique(fichier, base);
    }

    /**
     * Charge une base joueurs depuis un fichier JSON. Si le fichier n'existe
     * pas encore, renvoie une base vide (premier démarrage).
     */
    public BaseJoueurs chargerBaseJoueurs(Path fichier) throws IOException {
        if (!Files.exists(fichier)) {
            return new BaseJoueurs();
        }
        try (Reader r = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            BaseJoueurs base = gson.fromJson(r, BaseJoueurs.class);
            return base == null ? new BaseJoueurs() : base;
        } catch (JsonParseException e) {
            throw new IOException("Base joueurs corrompue : " + fichier, e);
        }
    }

    /** Liste les bases joueurs disponibles, la plus recemment modifiee en premier. */
    public List<Path> listerBasesJoueurs(Path dossierBases) throws IOException {
        return listerJson(dossierBases);
    }

    /**
     * Adaptateur Gson pour l'interface {@link Match}. Ecrit le nom du type concret a cote
     * des donnees, ce qui permet de reconstruire la bonne classe au chargement.
     */
    private static class AdaptateurMatch implements JsonSerializer<Match>, JsonDeserializer<Match> {

        private static final String CHAMP_TYPE = "__type";

        @Override
        public JsonElement serialize(Match match, Type type, JsonSerializationContext ctx) {
            JsonObject obj = (JsonObject) ctx.serialize(match, match.getClass());
            obj.addProperty(CHAMP_TYPE, match.getClass().getSimpleName());
            return obj;
        }

        @Override
        public Match deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) {
            JsonObject obj = element.getAsJsonObject();
            JsonElement typeElem = obj.get(CHAMP_TYPE);
            String nomType = typeElem != null ? typeElem.getAsString() : "MatchSimple";
            Class<? extends Match> cible = switch (nomType) {
                case "MatchDouble" -> MatchDouble.class;
                default -> MatchSimple.class;
            };
            return ctx.deserialize(obj, cible);
        }
    }
}
