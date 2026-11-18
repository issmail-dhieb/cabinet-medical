// src/main/java/com/cabinetmedical/model/StatutRendezVous.java
package com.cabinetmedical.model;

/**
 * Statut d'un rendez-vous.
 * Le nom de la constante (PLANIFIE, EFFECTUE, ANNULE) est la valeur stockée
 * dans la colonne rendez_vous.statut. Le libellé français est celui affiché
 * à l'utilisateur.
 */
public enum StatutRendezVous {

    PLANIFIE("Planifié"),
    EFFECTUE("Effectué"),
    ANNULE("Annulé");

    private final String libelle;

    StatutRendezVous(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé français avec accents, pour l'affichage. */
    public String getLibelle() {
        return libelle;
    }

    /** Un rendez-vous annulé n'est pas actif ; les autres le sont. */
    public boolean estActif() {
        return this != ANNULE;
    }

    /** Convertit la valeur stockée en base (PLANIFIE, EFFECTUE, ANNULE) en statut. */
    public static StatutRendezVous depuisCode(String code) {
        for (StatutRendezVous statut : values()) {
            if (statut.name().equals(code)) {
                return statut;
            }
        }
        throw new IllegalArgumentException("Statut de rendez-vous inconnu : " + code);
    }

    /** Affiche le libellé français (utile pour les listes déroulantes). */
    @Override
    public String toString() {
        return libelle;
    }
}