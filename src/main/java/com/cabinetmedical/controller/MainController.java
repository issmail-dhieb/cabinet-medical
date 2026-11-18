// src/main/java/com/cabinetmedical/controller/MainController.java
package com.cabinetmedical.controller;

import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.Utilisateur;
import com.cabinetmedical.service.AuthService;
import com.cabinetmedical.service.MedecinService;
import com.cabinetmedical.service.PatientService;
import com.cabinetmedical.service.RendezVousService;
import com.cabinetmedical.service.ServiceException;
import com.cabinetmedical.view.MainView;
import com.cabinetmedical.view.MainView.Section;
import java.util.List;
import java.util.Optional;
import javafx.scene.Scene;

/**
 * Relie la fenêtre principale aux services : navigation entre les sections,
 * chiffres du tableau de bord et déconnexion. Aucune règle métier ni SQL ici.
 * La fenêtre ne s'ouvre que si AuthService indique qu'un utilisateur est connecté.
 */
public class MainController {

    private static final int PROCHAINS_RENDEZ_VOUS_AFFICHES = 5;

    private final AuthService authService;
    private final MedecinService medecinService;
    private final PatientService patientService;
    private final RendezVousService rendezVousService;
    private final Scene scene;
    private final MainView vue;
    private final Runnable apresDeconnexion;

    public MainController(AuthService authService, MedecinService medecinService,
                          PatientService patientService, RendezVousService rendezVousService,
                          Scene scene, MainView vue, Runnable apresDeconnexion) {
        this.authService = authService;
        this.medecinService = medecinService;
        this.patientService = patientService;
        this.rendezVousService = rendezVousService;
        this.scene = scene;
        this.vue = vue;
        this.apresDeconnexion = apresDeconnexion;
        brancherActions();
    }

    /** Ouvre la fenêtre principale sur le tableau de bord. Sans utilisateur connecté, retourne à la connexion. */
    public void ouvrir() {
        Optional<Utilisateur> connecte = authService.getUtilisateurConnecte();
        if (connecte.isEmpty()) {
            apresDeconnexion.run();
            return;
        }
        vue.definirUtilisateur(connecte.get().getNomUtilisateur());
        afficherSection(Section.TABLEAU_DE_BORD);
        scene.setRoot(vue.getRacine());
    }

    private void brancherActions() {
        for (Section section : Section.values()) {
            vue.getBoutonNavigation(section).setOnAction(evenement -> afficherSection(section));
        }
        vue.getBoutonDeconnexion().setOnAction(evenement -> seDeconnecter());
    }

    private void afficherSection(Section section) {
        vue.marquerSectionActive(section);
        if (section == Section.TABLEAU_DE_BORD) {
            chargerTableauDeBord();
        } else {
            vue.afficherSectionEnPreparation(section);
        }
    }

    private void chargerTableauDeBord() {
        try {
            int nombreMedecins = medecinService.listerTous().size();
            int nombrePatients = patientService.listerTous().size();
            List<RendezVousDetail> aVenir = rendezVousService.listerAVenir();
            int limite = Math.min(PROCHAINS_RENDEZ_VOUS_AFFICHES, aVenir.size());
            vue.afficherTableauDeBord(nombreMedecins, nombrePatients, aVenir.size(), aVenir.subList(0, limite));
        } catch (ServiceException e) {
            vue.afficherErreurTableauDeBord(e.getMessage());
        }
    }

    private void seDeconnecter() {
        authService.deconnecter();
        apresDeconnexion.run();
    }
}