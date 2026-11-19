// src/main/java/com/cabinetmedical/util/AlertUtil.java
package com.cabinetmedical.util;

import java.net.URL;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

/**
 * Boîtes de dialogue communes à l'application.
 * Les messages affichés doivent déjà être compréhensibles par l'utilisateur
 * (par exemple le message d'une ServiceException).
 * Les boîtes utilisent la même feuille de style que le reste de l'application.
 */
public final class AlertUtil {

    private static final String FEUILLE_DE_STYLE = "/css/style.css";

    private AlertUtil() {
    }

    /** Affiche une boîte d'erreur et attend que l'utilisateur la ferme. */
    public static void afficherErreur(String titre, String message) {
        Alert alerte = new Alert(AlertType.ERROR);
        alerte.setTitle("Erreur");
        alerte.setHeaderText(titre);
        alerte.setContentText(message);
        appliquerStyle(alerte);
        alerte.showAndWait();
    }

    /**
     * Demande une confirmation avant une action destructive.
     * Le bouton par défaut (Entrée) est « Annuler » : une validation trop rapide ne supprime rien.
     *
     * @param libelleConfirmation texte du bouton qui confirme l'action, par exemple « Supprimer »
     * @return vrai seulement si l'utilisateur a cliqué sur le bouton de confirmation
     */
    public static boolean confirmer(String titre, String message, String libelleConfirmation) {
        ButtonType confirmer = new ButtonType(libelleConfirmation, ButtonData.OK_DONE);
        ButtonType annuler = new ButtonType("Annuler", ButtonData.CANCEL_CLOSE);
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, confirmer, annuler);
        alerte.setTitle("Confirmation");
        alerte.setHeaderText(titre);
        appliquerStyle(alerte);

        Button boutonConfirmer = (Button) alerte.getDialogPane().lookupButton(confirmer);
        Button boutonAnnuler = (Button) alerte.getDialogPane().lookupButton(annuler);
        boutonConfirmer.setDefaultButton(false);
        boutonAnnuler.setDefaultButton(true);

        Optional<ButtonType> choix = alerte.showAndWait();
        return choix.isPresent() && choix.get() == confirmer;
    }

    /** Applique la feuille de style de l'application à la boîte (sans effet si elle est introuvable). */
    private static void appliquerStyle(Alert alerte) {
        URL feuille = AlertUtil.class.getResource(FEUILLE_DE_STYLE);
        if (feuille != null) {
            alerte.getDialogPane().getStylesheets().add(feuille.toExternalForm());
        }
    }
}