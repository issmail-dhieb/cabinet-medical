// src/main/java/com/cabinetmedical/view/SessionView.java
package com.cabinetmedical.view;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran TEMPORAIRE affiché après une connexion réussie (Phase 8).
 * Il montre l'utilisateur connecté et permet de se déconnecter.
 * La Phase 9 le remplacera par la vraie fenêtre principale (MainView).
 */
public class SessionView {

    private final StackPane racine = new StackPane();
    private final Button boutonDeconnexion = new Button("Se déconnecter");

    public SessionView(String nomUtilisateur) {
        Label titre = new Label("Session ouverte");
        titre.getStyleClass().add("titre-page");

        Region regle = new Region();
        regle.getStyleClass().add("regle");
        regle.setPrefHeight(1);
        regle.setMinHeight(1);
        regle.setMaxHeight(1);

        Label message = new Label("Connecté en tant que " + nomUtilisateur
                + ". La salle d'attente est vide : le tableau de bord arrive en Phase 9.");
        message.getStyleClass().add("sous-titre");
        message.setWrapText(true);
        message.setMinHeight(Region.USE_PREF_SIZE);

        VBox contenu = new VBox(16, titre, regle, message, boutonDeconnexion);
        contenu.setAlignment(Pos.TOP_LEFT);
        contenu.setMaxWidth(480);
        contenu.setMaxHeight(Region.USE_PREF_SIZE);

        racine.getStyleClass().add("zone-formulaire");
        racine.getChildren().add(contenu);
    }

    public Parent getRacine() {
        return racine;
    }

    public Button getBoutonDeconnexion() {
        return boutonDeconnexion;
    }
}