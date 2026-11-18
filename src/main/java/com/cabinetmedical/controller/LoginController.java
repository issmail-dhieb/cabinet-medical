// src/main/java/com/cabinetmedical/controller/LoginController.java
package com.cabinetmedical.controller;

import com.cabinetmedical.model.Utilisateur;
import com.cabinetmedical.service.AuthService;
import com.cabinetmedical.service.ServiceException;
import com.cabinetmedical.util.AlertUtil;
import com.cabinetmedical.view.LoginView;
import com.cabinetmedical.view.SessionView;
import java.util.Optional;
import javafx.scene.Scene;

/**
 * Relie l'écran de connexion / de création du compte à AuthService.
 * Aucune règle métier ici : les contrôles sont faits par AuthService, ce contrôleur
 * affiche seulement ses messages. L'écran de session n'est ouvert que si AuthService
 * indique qu'un utilisateur est connecté.
 */
public class LoginController {

    private final AuthService authService;
    private final Scene scene;
    private final LoginView vue;

    public LoginController(AuthService authService, Scene scene, LoginView vue) {
        this.authService = authService;
        this.scene = scene;
        this.vue = vue;
        brancherActions();
    }

    /**
     * Choisit l'écran de départ : création du compte au premier lancement, sinon connexion.
     *
     * @return false si l'état des comptes n'a pas pu être lu (l'application doit alors se fermer)
     */
    public boolean demarrer() {
        try {
            if (authService.estPremierLancement()) {
                vue.afficherCreation();
            } else {
                vue.afficherConnexion();
            }
            return true;
        } catch (ServiceException e) {
            AlertUtil.afficherErreur("Démarrage impossible", e.getMessage());
            return false;
        }
    }

    private void brancherActions() {
        vue.getBoutonConnexion().setOnAction(evenement -> seConnecter());
        vue.getBoutonCreation().setOnAction(evenement -> creerAdministrateur());
    }

    private void seConnecter() {
        vue.effacerMessages();
        try {
            authService.connecter(vue.getNomConnexion(), vue.getMotDePasseConnexion());
            vue.viderChampsConnexion();
            ouvrirSession();
        } catch (ServiceException e) {
            vue.afficherErreurConnexion(e.getMessage());
            vue.viderMotDePasseConnexion();
            if (vue.getNomConnexion().isBlank()) {
                vue.focuserNomConnexion();
            } else {
                vue.focuserMotDePasseConnexion();
            }
        }
    }

    private void creerAdministrateur() {
        vue.effacerMessages();
        try {
            Utilisateur cree = authService.creerAdministrateur(
                    vue.getNomCreation(), vue.getMotDePasseCreation(), vue.getConfirmationCreation());
            vue.viderChampsCreation();
            vue.afficherConnexion();
            vue.preremplirNomConnexion(cree.getNomUtilisateur());
            vue.afficherInfoConnexion("Compte administrateur créé. Vous pouvez désormais vous connecter.");
            vue.focuserMotDePasseConnexion();
        } catch (ServiceException e) {
            vue.afficherErreurCreation(e.getMessage());
            vue.viderMotsDePasseCreation();
        }
    }

    private void ouvrirSession() {
        Optional<Utilisateur> connecte = authService.getUtilisateurConnecte();
        if (connecte.isEmpty()) {
            vue.afficherConnexion();
            scene.setRoot(vue.getRacine());
            return;
        }
        SessionView session = new SessionView(connecte.get().getNomUtilisateur());
        session.getBoutonDeconnexion().setOnAction(evenement -> seDeconnecter());
        scene.setRoot(session.getRacine());
    }

    private void seDeconnecter() {
        authService.deconnecter();
        vue.viderChampsConnexion();
        vue.afficherConnexion();
        vue.afficherInfoConnexion("Vous êtes déconnecté. Le cabinet garde vos secrets.");
        scene.setRoot(vue.getRacine());
    }
}