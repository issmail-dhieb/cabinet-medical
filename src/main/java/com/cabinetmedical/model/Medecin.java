// src/main/java/com/cabinetmedical/model/Medecin.java
package com.cabinetmedical.model;

import java.util.Objects;

/**
 * Un médecin du cabinet. Correspond à la table medecins.
 * Le matricule est l'identifiant unique.
 */
public class Medecin {

    private String matricule;
    private String nom;
    private String prenom;
    private String specialite;
    private String telephone;

    public Medecin(String matricule, String nom, String prenom, String specialite, String telephone) {
        this.matricule = matricule;
        this.nom = nom;
        this.prenom = prenom;
        this.specialite = specialite;
        this.telephone = telephone;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
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

    public String getSpecialite() {
        return specialite;
    }

    public void setSpecialite(String specialite) {
        this.specialite = specialite;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    /** Prénom suivi du nom, par exemple pour l'affichage dans un rendez-vous. */
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    /** Deux médecins sont identiques s'ils ont le même matricule. */
    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || getClass() != autre.getClass()) {
            return false;
        }
        Medecin medecin = (Medecin) autre;
        return Objects.equals(matricule, medecin.matricule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricule);
    }

    @Override
    public String toString() {
        return "Medecin{matricule='" + matricule + "', nom='" + nom + "', prenom='" + prenom
                + "', specialite='" + specialite + "', telephone='" + telephone + "'}";
    }
}