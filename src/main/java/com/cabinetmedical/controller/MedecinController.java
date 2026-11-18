// src/main/java/com/cabinetmedical/controller/MedecinController.java
package com.cabinetmedical.controller;

import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.service.MedecinService;
import com.cabinetmedical.service.ServiceException;
import com.cabinetmedical.util.AlertUtil;
import com.cabinetmedical.view.MedecinView;
import java.util.List;

/**
 * Relie la section « Médecins » à MedecinService : recherche, ajout, modification
 * et suppression. Les règles métier (validation, doublons, historique) restent
 * dans le service ; ici on lit le formulaire, on appelle le service et on affiche
 * le résultat ou le message d'erreur. Aucun SQL.
 */
public class MedecinController {

    private final MedecinService medecinService;
    private final MedecinView vue;

    // Médecin affiché dans le formulaire en mode modification ; null = mode ajout.
    private Medecin medecinEnModification;

    public MedecinController(MedecinService medecinService, MedecinView vue) {
        this.medecinService = medecinService;
        this.vue = vue;
        brancherActions();
    }

    /** À appeler à chaque ouverture de la section : recherche vide, formulaire vide, liste à jour. */
    public void ouvrir() {
        medecinEnModification = null;
        vue.reinitialiserRecherche();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        actualiserTableau();
    }

    private void brancherActions() {
        vue.setOnRechercheModifiee(this::actualiserTableau);
        vue.setOnSelection(this::selectionnerMedecin);
        vue.setOnValidation(this::enregistrer);
        vue.getBoutonEnregistrer().setOnAction(evenement -> enregistrer());
        vue.getBoutonSupprimer().setOnAction(evenement -> supprimer());
        vue.getBoutonAnnuler().setOnAction(evenement -> nouveauMedecin());
    }

    // ---------- Tableau et recherche ----------

    private void actualiserTableau() {
        String texte = vue.getTexteRecherche();
        boolean recherche = !texte.isBlank();
        try {
            List<Medecin> medecins = chercher(texte);
            int total = recherche ? medecinService.listerTous().size() : medecins.size();
            vue.afficherMedecins(medecins, recherche, total);
            if (medecinEnModification != null) {
                vue.selectionner(medecinEnModification.getMatricule());
            }
        } catch (ServiceException e) {
            vue.afficherListeIndisponible();
            vue.afficherErreur(e.getMessage());
        }
    }

    private List<Medecin> chercher(String texte) throws ServiceException {
        return switch (vue.getCritere()) {
            case MATRICULE -> medecinService.chercherParMatricule(texte);
            case NOM -> medecinService.chercherParNom(texte);
            case SPECIALITE -> medecinService.chercherParSpecialite(texte);
        };
    }

    // ---------- Formulaire ----------

    private void selectionnerMedecin(Medecin medecin) {
        medecinEnModification = medecin;
        vue.effacerMessage();
        vue.passerEnModeModification(medecin);
    }

    /** Revient au formulaire d'ajout : abandonne la modification en cours ou efface les champs. */
    private void nouveauMedecin() {
        medecinEnModification = null;
        vue.deselectionner();
        vue.effacerMessage();
        vue.passerEnModeCreation();
        vue.focuserMatricule();
    }

    private void enregistrer() {
        String matricule = medecinEnModification == null
                ? vue.getMatricule()
                : medecinEnModification.getMatricule();
        Medecin saisie = new Medecin(matricule, vue.getNom(), vue.getPrenom(),
                vue.getSpecialite(), vue.getTelephone());
        vue.effacerMessage();
        try {
            if (medecinEnModification == null) {
                ajouter(saisie);
            } else {
                modifier(saisie);
            }
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }

    private void ajouter(Medecin saisie) throws ServiceException {
        Medecin ajoute = medecinService.ajouter(saisie);
        vue.afficherSucces("Le médecin " + ajoute.getNomComplet()
                + " (" + ajoute.getMatricule() + ") a été ajouté au registre.");
        vue.reinitialiserRecherche();
        vue.passerEnModeCreation();
        actualiserTableau();
        vue.faireDefiler(ajoute.getMatricule());
        vue.focuserMatricule();
    }

    private void modifier(Medecin saisie) throws ServiceException {
        Medecin modifie = medecinService.modifier(saisie);
        medecinEnModification = modifie;
        vue.passerEnModeModification(modifie);
        vue.afficherSucces("Les informations de " + modifie.getNomComplet() + " ont été enregistrées.");
        actualiserTableau();
    }

    private void supprimer() {
        if (medecinEnModification == null) {
            return;
        }
        Medecin medecin = medecinEnModification;
        boolean confirme = AlertUtil.confirmer("Supprimer ce médecin ?",
                "Le médecin " + medecin.getNomComplet() + " (matricule " + medecin.getMatricule()
                        + ") sera retiré définitivement du registre.",
                "Supprimer");
        if (!confirme) {
            return;
        }
        vue.effacerMessage();
        try {
            medecinService.supprimer(medecin.getMatricule());
            medecinEnModification = null;
            vue.passerEnModeCreation();
            vue.afficherSucces("Le médecin " + medecin.getNomComplet()
                    + " (" + medecin.getMatricule() + ") a été supprimé du registre.");
            actualiserTableau();
        } catch (ServiceException e) {
            vue.afficherErreur(e.getMessage());
        }
    }
}