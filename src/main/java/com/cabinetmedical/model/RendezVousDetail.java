// src/main/java/com/cabinetmedical/model/RendezVousDetail.java
package com.cabinetmedical.model;

/**
 * Un rendez-vous accompagné des informations complètes de son médecin et de son patient.
 * Cette classe sert uniquement à l'affichage (par exemple dans un tableau) :
 * pour enregistrer ou modifier un rendez-vous, on utilise RendezVous.
 */
public class RendezVousDetail {

    private final RendezVous rendezVous;
    private final Medecin medecin;
    private final Patient patient;

    public RendezVousDetail(RendezVous rendezVous, Medecin medecin, Patient patient) {
        this.rendezVous = rendezVous;
        this.medecin = medecin;
        this.patient = patient;
    }

    public RendezVous getRendezVous() {
        return rendezVous;
    }

    public Medecin getMedecin() {
        return medecin;
    }

    public Patient getPatient() {
        return patient;
    }

    @Override
    public String toString() {
        return "RendezVousDetail{rendezVous=" + rendezVous + ", medecin=" + medecin
                + ", patient=" + patient + "}";
    }
}