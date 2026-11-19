// src/main/java/com/cabinetmedical/util/FormatUtil.java
package com.cabinetmedical.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Présentation des données pour l'utilisateur : numéros de téléphone, dates et heures
 * au format français. Cette classe ne dépend ni de la base de données ni de JavaFX.
 */
public final class FormatUtil {

    private static final DateTimeFormatter FORMAT_AFFICHAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // STRICT : une date qui n'existe pas (31/02/1990, 29/02/2023) est refusée au lieu d'être corrigée.
    // « uuuu » (et non « yyyy ») est obligatoire avec le mode STRICT.
    private static final DateTimeFormatter FORMAT_SAISIE =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private FormatUtil() {
        // Classe utilitaire : on ne crée pas d'instance.
    }

    /** Affiche 20123456 sous la forme « 20 123 456 ». Un autre texte est retourné tel quel. */
    public static String formaterTelephone(String telephone) {
        if (telephone != null && telephone.matches("\\d{8}")) {
            return telephone.substring(0, 2) + " " + telephone.substring(2, 5) + " " + telephone.substring(5);
        }
        return telephone;
    }

    /** Affiche une date au format jj/MM/aaaa. Une date null devient une chaîne vide. */
    public static String formaterDate(LocalDate date) {
        return date == null ? "" : date.format(FORMAT_AFFICHAGE);
    }

    /** Affiche une heure au format HH:mm (par exemple « 09:05 »). Une heure null devient une chaîne vide. */
    public static String formaterHeure(LocalTime heure) {
        return heure == null ? "" : heure.format(FORMAT_HEURE);
    }

    /** Affiche une date et une heure au format « 05/01/2030 09:05 ». Une valeur null devient une chaîne vide. */
    public static String formaterDateHeure(LocalDateTime dateHeure) {
        return dateHeure == null ? "" : dateHeure.format(FORMAT_DATE_HEURE);
    }

    /** Décrit un créneau dans une phrase : « le 05/01/2030 à 09:05 ». Une valeur null devient une chaîne vide. */
    public static String formaterCreneau(LocalDateTime dateHeure) {
        if (dateHeure == null) {
            return "";
        }
        return "le " + formaterDate(dateHeure.toLocalDate()) + " à " + formaterHeure(dateHeure.toLocalTime());
    }

    /**
     * Lit une date saisie au format jj/MM/aaaa (les zéros de début sont facultatifs,
     * les espaces autour sont ignorés).
     *
     * @return la date, ou null si le texte est vide, mal écrit ou ne correspond pas à un jour qui existe
     */
    public static LocalDate lireDate(String texte) {
        if (texte == null) {
            return null;
        }
        try {
            return LocalDate.parse(texte.strip(), FORMAT_SAISIE);
        } catch (DateTimeParseException e) {
            // Texte invalide : ce n'est pas une erreur du programme, l'appelant affiche un message.
            return null;
        }
    }
}