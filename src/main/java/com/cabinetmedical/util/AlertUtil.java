// src/main/java/com/cabinetmedical/util/AlertUtil.java
package com.cabinetmedical.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Boîtes de dialogue communes à l'application.
 * Les messages affichés doivent déjà être compréhensibles par l'utilisateur
 * (par exemple le message d'une ServiceException).
 */
public final class AlertUtil {

    private AlertUtil() {
    }

    /** Affiche une boîte d'erreur et attend que l'utilisateur la ferme. */
    public static void afficherErreur(String titre, String message) {
        Alert alerte = new Alert(AlertType.ERROR);
        alerte.setTitle("Erreur");
        alerte.setHeaderText(titre);
        alerte.setContentText(message);
        alerte.showAndWait();
    }
}