// src/main/java/com/cabinetmedical/model/Patient.java
package com.cabinetmedical.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un patient du cabinet. Correspond à la table patients.
 * Le numéro de téléphone est l'identifiant unique.
 */
public class Patient {

    private String numeroTelephone;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;

    public Patient(String numeroTelephone, String nom, String prenom, LocalDate dateNaissance) {
        this.numeroTelephone = numeroTelephone;
        this.nom = nom;
        this.prenom = prenom;
        this.dateNaissance = dateNaissance;
    }

    public String getNumeroTelephone() {
        return numeroTelephone;
    }

    public void setNumeroTelephone(String numeroTelephone) {
        this.numeroTelephone = numeroTelephone;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    /** Prénom suivi du nom, par exemple pour l'affichage dans un rendez-vous. */
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    /** Deux patients sont identiques s'ils ont le même numéro de téléphone. */
    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        Patient patient = (Patient) autre;
        return Objects.equals(numeroTelephone, patient.numeroTelephone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numeroTelephone);
    }

    @Override
    public String toString() {
        return "Patient{numeroTelephone='" + numeroTelephone + "', nom='" + nom + "', prenom='" + prenom
                + "', dateNaissance=" + dateNaissance + "}";
    }
}