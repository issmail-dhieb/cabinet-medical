// src/main/java/com/cabinetmedical/model/RendezVous.java
package com.cabinetmedical.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Un rendez-vous entre un médecin et un patient. Correspond à la table rendez_vous.
 * Le médecin et le patient sont référencés par leur identifiant (matricule et
 * numéro de téléphone), comme dans la base de données.
 * L'identifiant 0 signifie que le rendez-vous n'est pas encore enregistré.
 */
public class RendezVous {

    /**
     * Format du texte stocké dans la colonne date_heure.
     * À utiliser dans les DAO pour écrire et lire la base, afin que toutes les
     * dates aient exactement le même format (les index d'unicité comparent du texte).
     */
    public static final DateTimeFormatter FORMAT_STOCKAGE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private int id;
    private String matriculeMedecin;
    private String numeroTelephonePatient;
    private LocalDateTime dateHeure;
    private StatutRendezVous statut;

    /** Nouveau rendez-vous, pas encore enregistré : id = 0 et statut PLANIFIE. */
    public RendezVous(String matriculeMedecin, String numeroTelephonePatient, LocalDateTime dateHeure) {
        this(0, matriculeMedecin, numeroTelephonePatient, dateHeure, StatutRendezVous.PLANIFIE);
    }

    /** Rendez-vous complet, par exemple lu depuis la base de données. */
    public RendezVous(int id, String matriculeMedecin, String numeroTelephonePatient,
                      LocalDateTime dateHeure, StatutRendezVous statut) {
        this.id = id;
        this.matriculeMedecin = matriculeMedecin;
        this.numeroTelephonePatient = numeroTelephonePatient;
        this.dateHeure = normaliser(dateHeure);
        this.statut = statut;
    }

    /** Supprime les secondes : la base ne conserve que les minutes. */
    private static LocalDateTime normaliser(LocalDateTime dateHeure) {
        if (dateHeure == null) {
            return null;
        }
        return dateHeure.truncatedTo(ChronoUnit.MINUTES);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMatriculeMedecin() {
        return matriculeMedecin;
    }

    public void setMatriculeMedecin(String matriculeMedecin) {
        this.matriculeMedecin = matriculeMedecin;
    }

    public String getNumeroTelephonePatient() {
        return numeroTelephonePatient;
    }

    public void setNumeroTelephonePatient(String numeroTelephonePatient) {
        this.numeroTelephonePatient = numeroTelephonePatient;
    }

    public LocalDateTime getDateHeure() {
        return dateHeure;
    }

    public void setDateHeure(LocalDateTime dateHeure) {
        this.dateHeure = normaliser(dateHeure);
    }

    public StatutRendezVous getStatut() {
        return statut;
    }

    public void setStatut(StatutRendezVous statut) {
        this.statut = statut;
    }

    /** Un rendez-vous annulé n'est pas actif. */
    public boolean estActif() {
        return statut != null && statut.estActif();
    }

    @Override
    public String toString() {
        return "RendezVous{id=" + id + ", matriculeMedecin='" + matriculeMedecin
                + "', numeroTelephonePatient='" + numeroTelephonePatient
                + "', dateHeure=" + dateHeure + ", statut=" + statut.name() + "}";
    }
}