// src/main/java/com/cabinetmedical/controller/PatientController.java
package com.cabinetmedical.controller;

import com.cabinetmedical.model.Patient;
import com.cabinetmedical.service.PatientService;
import com.cabinetmedical.service.ServiceException;
import com.cabinetmedical.util.AlertUtil;
import com.cabinetmedical.util.FormatUtil;
import com.cabinetmedical.view.PatientView;
import java.time.LocalDate;
import java.util.List;

/**
 * Relie la section « Patients » à PatientService : recherche, ajout, modification
 * et suppression. Les règles métier (validation, doublons, historique) restent
 * dans le service ; ici on lit le formulaire, on convertit le texte de la date,
 * on appelle le service et on affiche le résultat ou le message d'erreur. Aucun SQL.
 */
public class PatientController {

    private static final String MESSAGE_FORMAT_DATE = "La date de naissance doit être écrite au format "
            + "JJ/MM/AAAA (par exemple 25/03/1990) et correspondre à un jour qui existe.";

    private final PatientService patientService;
    private final PatientView vue;

    // Patient affiché dans le formulaire en mode modification ; null = mode ajout.
    private Patient patientEnModification;

    public PatientController(PatientService patientService, PatientView vue) {
        this.patientService = patientService;
        this.vue = vue;
        brancherActions();
    }

    /** À appeler à chaque ouverture de la section : recherche vide, formulaire vide, liste à jour. */
    public void ouvrir() {
        patientEnModification = null;
        vue.reinitialiserRecherche();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        actualiserTableau();
    }

    private void brancherActions() {
        vue.setOnRechercheModifiee(this::actualiserTableau);
        vue.setOnSelection(this::selectionnerPatient);
        vue.setOnValidation(this::enregistrer);
        vue.getBoutonEnregistrer().setOnAction(evenement -> enregistrer());
        vue.getBoutonSupprimer().setOnAction(evenement -> supprimer());
        vue.getBoutonAnnuler().setOnAction(evenement -> nouveauPatient());
    }

    // ---------- Tableau et recherche ----------

    private void actualiserTableau() {
        String texte = vue.getTexteRecherche();
        boolean recherche = !texte.isBlank();
        try {
            List<Patient> patients = chercher(texte);
            int total = recherche ? patientService.listerTous().size() : patients.size();
            vue.afficherPatients(patients, recherche, total);
            if (patientEnModification != null) {
                vue.selectionner(patientEnModification.getNumeroTelephone());
            }
        } catch (ServiceException e) {
            vue.afficherListeIndisponible();
            vue.afficherErreur(e.getMessage());
        }
    }

    private List<Patient> chercher(String texte) throws ServiceException {
        return switch (vue.getCritere()) {
            case NOM -> patientService.chercherParNom(texte);
            case PRENOM -> patientService.chercherParPrenom(texte);
            case TELEPHONE -> patientService.chercherParNumeroTelephone(texte);
        };
    }

    // ---------- Formulaire ----------

    private void selectionnerPatient(Patient patient) {
        patientEnModification = patient;
        vue.effacerMessage();
        vue.passerEnModeModification(patient);
    }

    /** Revient au formulaire d'ajout : abandonne la modification en cours ou efface les champs. */
    private void nouveauPatient() {
        patientEnModification = null;
        vue.deselectionner();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        vue.focuserNom();
    }

    private void enregistrer() {
        vue.effacerMessage();
        String texteDate = vue.getDateNaissance();
        LocalDate dateNaissance = FormatUtil.lireDate(texteDate);
        if (dateNaissance == null && !texteDate.isBlank()) {
            // Texte de date illisible : le service ne reçoit que de vraies dates.
            vue.afficherErreur(MESSAGE_FORMAT_DATE);
            return;
        }
        // Une date vide reste null : c'est le service qui répond « La date de naissance est obligatoire. »
        Patient saisie = new Patient(vue.getTelephone(), vue.getNom(), vue.getPrenom(), dateNaissance);
        try {
            if (patientEnModification == null) {
                ajouter(saisie);
            } else {
                modifier(saisie);
            }
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }

    private void ajouter(Patient saisie) throws ServiceException {
        Patient ajoute = patientService.ajouter(saisie);
        vue.afficherSucces("Le patient " + ajoute.getNomComplet() + " ("
                + FormatUtil.formaterTelephone(ajoute.getNumeroTelephone()) + ") a été ajouté au registre.");
        vue.reinitialiserRecherche();
        vue.passerEnModeCreation();
        actualiserTableau();
        vue.faireDefiler(ajoute.getNumeroTelephone());
        vue.focuserNom();
    }

    private void modifier(Patient saisie) throws ServiceException {
        String ancienNumero = patientEnModification.getNumeroTelephone();
        Patient modifie = patientService.modifier(ancienNumero, saisie);
        patientEnModification = modifie;
        vue.passerEnModeModification(modifie);
        String texte = "Les informations de " + modifie.getNomComplet() + " ont été enregistrées.";
        if (!ancienNumero.equals(modifie.getNumeroTelephone())) {
            texte += " Ses éventuels rendez-vous suivent le nouveau numéro.";
        }
        vue.afficherSucces(texte);
        actualiserTableau();
    }

    private void supprimer() {
        if (patientEnModification == null) {
            return;
        }
        Patient patient = patientEnModification;
        String telephone = FormatUtil.formaterTelephone(patient.getNumeroTelephone());
        boolean confirme = AlertUtil.confirmer("Supprimer ce patient ?",
                "Le patient " + patient.getNomComplet() + " (téléphone " + telephone
                        + ") sera retiré définitivement du registre.",
                "Supprimer");
        if (!confirme) {
            return;
        }
        vue.effacerMessage();
        try {
            patientService.supprimer(patient.getNumeroTelephone());
            patientEnModification = null;
            vue.passerEnModeCreation();
            vue.afficherSucces("Le patient " + patient.getNomComplet() + " (" + telephone
                    + ") a été supprimé du registre.");
            actualiserTableau();
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }
}