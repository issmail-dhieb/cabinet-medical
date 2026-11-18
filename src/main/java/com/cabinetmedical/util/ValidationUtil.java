// src/main/java/com/cabinetmedical/util/ValidationUtil.java
package com.cabinetmedical.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Outils de nettoyage et de validation de texte, partagés par les services.
 * Cette classe ne dépend ni de la base de données ni de JavaFX.
 */
public final class ValidationUtil {

    public static final int LONGUEUR_MAX_MATRICULE = 20;

    private static final String PREFIXE_TUNISIE = "+216";

    // Matricule (déjà en majuscules) : lettres sans accent, chiffres, tiret, tiret bas.
    private static final Pattern MOTIF_MATRICULE = Pattern.compile("[A-Z0-9_-]+");

    // Nom, prénom, spécialité : commence par une lettre, puis lettres, espaces, tirets, apostrophes.
    private static final Pattern MOTIF_TEXTE_NOM = Pattern.compile("\\p{L}[\\p{L} '’-]*");

    private static final Pattern MOTIF_TELEPHONE = Pattern.compile("\\d{8}");

    private static final Pattern MARQUES_ACCENTS = Pattern.compile("\\p{M}+");

    private ValidationUtil() {
        // Classe utilitaire : on ne crée pas d'instance.
    }

    /** Supprime les espaces au début et à la fin et réduit les espaces multiples à un seul. */
    public static String nettoyerTexte(String texte) {
        if (texte == null) {
            return "";
        }
        return texte.strip().replaceAll("\\s+", " ");
    }

    /** Supprime les espaces autour du matricule et le met en majuscules. */
    public static String normaliserMatricule(String matricule) {
        if (matricule == null) {
            return "";
        }
        return matricule.strip().toUpperCase(Locale.ROOT);
    }

    /** Vérifie un matricule déjà normalisé (voir normaliserMatricule). */
    public static boolean estMatriculeValide(String matricule) {
        return matricule != null
                && matricule.length() <= LONGUEUR_MAX_MATRICULE
                && MOTIF_MATRICULE.matcher(matricule).matches();
    }

    /** Vérifie un texte déjà nettoyé (voir nettoyerTexte) : nom, prénom ou spécialité. */
    public static boolean estNomValide(String texte) {
        return texte != null && MOTIF_TEXTE_NOM.matcher(texte).matches();
    }

    /**
     * Normalise un numéro de téléphone : retire les espaces et le préfixe +216.
     *
     * @return les 8 chiffres du numéro, ou null si le numéro est invalide
     */
    public static String normaliserTelephone(String telephone) {
        if (telephone == null) {
            return null;
        }
        String numero = telephone.replaceAll("\\s+", "");
        if (numero.startsWith(PREFIXE_TUNISIE)) {
            numero = numero.substring(PREFIXE_TUNISIE.length());
        }
        return MOTIF_TELEPHONE.matcher(numero).matches() ? numero : null;
    }

    /**
     * Prépare un texte pour une recherche : sans accents, en minuscules,
     * sans espaces en trop. Un texte null devient une chaîne vide.
     */
    public static String normaliserPourRecherche(String texte) {
        if (texte == null) {
            return "";
        }
        String decompose = Normalizer.normalize(texte, Normalizer.Form.NFD);
        String sansAccents = MARQUES_ACCENTS.matcher(decompose).replaceAll("");
        return sansAccents.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}