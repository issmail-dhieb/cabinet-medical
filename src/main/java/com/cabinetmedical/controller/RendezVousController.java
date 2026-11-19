// src/main/java/com/cabinetmedical/controller/RendezVousController.java
package com.cabinetmedical.controller;

import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;
import com.cabinetmedical.service.MedecinService;
import com.cabinetmedical.service.PatientService;
import com.cabinetmedical.service.RendezVousService;
import com.cabinetmedical.service.ServiceException;
import com.cabinetmedical.util.AlertUtil;
import com.cabinetmedical.util.FormatUtil;
import com.cabinetmedical.util.RechercheRendezVous;
import com.cabinetmedical.view.RendezVousView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Relie la section « Rendez-vous » aux services : filtres, planification, déplacement,
 * passage au statut effectué et annulation. Les règles métier (existence du médecin et du patient,
 * conflits, passé, transitions de statut) restent dans RendezVousService ; ici on lit le formulaire,
 * on appelle le service et on affiche le résultat ou le message d'erreur. Aucun SQL.
 * Aucun rendez-vous n'est supprimé : on peut seulement l'annuler (l'historique est conservé).
 */
public class RendezVousController {

    private static final String MESSAGE_MEDECIN_OBLIGATOIRE = "Le médecin est obligatoire.";
    private static final String MESSAGE_PATIENT_OBLIGATOIRE = "Le patient est obligatoire.";
    private static final String MESSAGE_DATE_HEURE_OBLIGATOIRE =
            "La date et l'heure du rendez-vous sont obligatoires.";

    private final RendezVousService rendezVousService;
    private final MedecinService medecinService;
    private final PatientService patientService;
    private final RendezVousView vue;

    // Rendez-vous affiché dans le formulaire en mode modification ; null = mode planification.
    private RendezVousDetail rendezVousEnModification;

    public RendezVousController(RendezVousService rendezVousService, MedecinService medecinService,
                                PatientService patientService, RendezVousView vue) {
        this.rendezVousService = rendezVousService;
        this.medecinService = medecinService;
        this.patientService = patientService;
        this.vue = vue;
        brancherActions();
    }

    /**
     * À appeler à chaque ouverture de la section : filtre « Planifié », recherche vide,
     * formulaire vide, listes de médecins et de patients et tableau à jour.
     */
    public void ouvrir() {
        rendezVousEnModification = null;
        vue.reinitialiserRecherche();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        chargerChoix();
        actualiserTableau();
    }

    private void brancherActions() {
        vue.setOnRechercheModifiee(this::actualiserTableau);
        vue.setOnSelection(this::selectionnerRendezVous);
        vue.getBoutonEnregistrer().setOnAction(evenement -> enregistrer());
        vue.getBoutonEffectue().setOnAction(evenement -> marquerEffectue());
        vue.getBoutonAnnulerRendezVous().setOnAction(evenement -> annuler());
        vue.getBoutonNouveau().setOnAction(evenement -> nouveauRendezVous());
    }

    // ---------- Listes de choix, tableau et filtres ----------

    private void chargerChoix() {
        try {
            List<Medecin> medecins = medecinService.listerTous();
            List<Patient> patients = patientService.listerTous();
            vue.definirMedecins(medecins);
            vue.definirPatients(patients);
            if (medecins.isEmpty() || patients.isEmpty()) {
                vue.afficherInfo("Pour planifier un rendez-vous, enregistrez d'abord au moins un médecin "
                        + "et un patient.");
            }
        } catch (ServiceException e) {
            vue.definirMedecins(List.of());
            vue.definirPatients(List.of());
            vue.afficherErreur(e.getMessage());
        }
    }

    private void actualiserTableau() {
        try {
            List<RendezVousDetail> tous = rendezVousService.listerTous();
            StatutRendezVous statut = vue.getStatutFiltre();
            String texte = vue.getTexteRecherche();
            boolean filtre = statut != null || !texte.isBlank();
            vue.afficherRendezVous(RechercheRendezVous.filtrer(tous, statut, texte), filtre, tous.size());
            if (rendezVousEnModification != null) {
                vue.selectionner(rendezVousEnModification.getRendezVous().getId());
            }
        } catch (ServiceException e) {
            vue.afficherListeIndisponible();
            vue.afficherErreur(e.getMessage());
        }
    }

    // ---------- Formulaire ----------

    private void selectionnerRendezVous(RendezVousDetail detail) {
        rendezVousEnModification = detail;
        vue.effacerMessage();
        vue.passerEnModeModification(detail);
    }

    /** Revient au formulaire de planification : abandonne la sélection en cours ou efface les champs. */
    private void nouveauRendezVous() {
        rendezVousEnModification = null;
        vue.deselectionner();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        vue.focuserMedecin();
    }

    private void enregistrer() {
        vue.effacerMessage();
        boolean planification = rendezVousEnModification == null;
        String champManquant = messageChampManquant(planification);
        if (champManquant != null) {
            vue.afficherErreur(champManquant);
            return;
        }
        LocalDateTime dateHeure = LocalDateTime.of(vue.getDate(), vue.getHeure());
        try {
            if (planification) {
                planifier(vue.getMedecin(), vue.getPatient(), dateHeure);
            } else {
                deplacer(dateHeure);
            }
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }

    /**
     * Vérifie seulement que les choix sont faits (on ne peut pas construire un rendez-vous sans date).
     * Toutes les autres règles (passé, pas de 15 minutes, conflits...) sont vérifiées par le service.
     */
    private String messageChampManquant(boolean planification) {
        if (planification && vue.getMedecin() == null) {
            return MESSAGE_MEDECIN_OBLIGATOIRE;
        }
        if (planification && vue.getPatient() == null) {
            return MESSAGE_PATIENT_OBLIGATOIRE;
        }
        if (vue.getDate() == null || vue.getHeure() == null) {
            return MESSAGE_DATE_HEURE_OBLIGATOIRE;
        }
        return null;
    }

    private void planifier(Medecin medecin, Patient patient, LocalDateTime dateHeure) throws ServiceException {
        RendezVous saisie = new RendezVous(medecin.getMatricule(), patient.getNumeroTelephone(), dateHeure);
        RendezVous planifie = rendezVousService.planifier(saisie);
        vue.reinitialiserRecherche();
        vue.passerEnModeCreation();
        vue.afficherSucces("Rendez-vous planifié : " + patient.getNomComplet() + " avec "
                + medecin.getNomComplet() + " " + FormatUtil.formaterCreneau(planifie.getDateHeure()) + ".");
        actualiserTableau();
        vue.faireDefiler(planifie.getId());
        vue.focuserMedecin();
    }

    private void deplacer(LocalDateTime nouvelleDateHeure) throws ServiceException {
        int id = rendezVousEnModification.getRendezVous().getId();
        rendezVousService.modifierDateHeure(id, nouvelleDateHeure);
        RendezVousDetail misAJour = recharger(id);
        rendezVousEnModification = misAJour;
        vue.passerEnModeModification(misAJour);
        vue.afficherSucces("Le rendez-vous de " + misAJour.getPatient().getNomComplet() + " a été déplacé "
                + FormatUtil.formaterCreneau(misAJour.getRendezVous().getDateHeure()) + ".");
        actualiserTableau();
    }

    private RendezVousDetail recharger(int id) throws ServiceException {
        Optional<RendezVousDetail> detail = rendezVousService.trouverParId(id);
        if (detail.isEmpty()) {
            throw new ServiceException("Aucun rendez-vous ne porte le numéro " + id + ".");
        }
        return detail.get();
    }

    // ---------- Changements de statut ----------

    private void marquerEffectue() {
        if (rendezVousEnModification == null) {
            return;
        }
        RendezVousDetail detail = rendezVousEnModification;
        boolean confirme = AlertUtil.confirmer("Marquer ce rendez-vous comme effectué ?",
                description(detail) + " Un rendez-vous effectué ne peut plus être modifié.",
                "Marquer comme effectué");
        if (!confirme) {
            return;
        }
        appliquerStatut(detail, StatutRendezVous.EFFECTUE, "Le rendez-vous de "
                + detail.getPatient().getNomComplet() + " a été marqué comme effectué. "
                + "Il reste consultable avec le filtre « Effectué ».");
    }

    private void annuler() {
        if (rendezVousEnModification == null) {
            return;
        }
        RendezVousDetail detail = rendezVousEnModification;
        boolean confirme = AlertUtil.confirmer("Annuler ce rendez-vous ?",
                description(detail) + " Il sera marqué comme annulé, définitivement, "
                        + "et restera visible dans l'historique. Choisissez « Annuler » pour le conserver.",
                "Annuler le rendez-vous");
        if (!confirme) {
            return;
        }
        appliquerStatut(detail, StatutRendezVous.ANNULE, "Le rendez-vous de "
                + detail.getPatient().getNomComplet() + " a été annulé. "
                + "Il reste consultable avec le filtre « Annulé ».");
    }

    private void appliquerStatut(RendezVousDetail detail, StatutRendezVous nouveauStatut, String messageSucces) {
        vue.effacerMessage();
        try {
            rendezVousService.modifierStatut(detail.getRendezVous().getId(), nouveauStatut);
            rendezVousEnModification = null;
            vue.deselectionner();
            vue.passerEnModeCreation();
            vue.afficherSucces(messageSucces);
            actualiserTableau();
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }

    private String description(RendezVousDetail detail) {
        return "Rendez-vous de " + detail.getPatient().getNomComplet() + " avec "
                + detail.getMedecin().getNomComplet() + " "
                + FormatUtil.formaterCreneau(detail.getRendezVous().getDateHeure()) + ".";
    }
}