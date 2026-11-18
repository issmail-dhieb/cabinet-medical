// src/main/java/com/cabinetmedical/Main.java
package com.cabinetmedical;

import com.cabinetmedical.controller.LoginController;
import com.cabinetmedical.dao.UtilisateurDAO;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.database.DatabaseInitializer;
import com.cabinetmedical.service.AuthService;
import com.cabinetmedical.util.AlertUtil;
import com.cabinetmedical.view.LoginView;
import java.net.URL;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Point d'entrée de l'application.
 * Phase 8 : initialise la base de données, crée l'unique AuthService partagé,
 * puis affiche l'écran de connexion (ou de création du compte au premier lancement).
 */
public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String FEUILLE_DE_STYLE = "/css/style.css";

    @Override
    public void start(Stage stage) {
        DatabaseConnection connexion = new DatabaseConnection();
        if (!initialiserBaseDeDonnees(connexion)) {
            Platform.exit();
            return;
        }

        AuthService authService = new AuthService(new UtilisateurDAO(connexion));
        LoginView vueConnexion = new LoginView();
        Scene scene = new Scene(vueConnexion.getRacine(), 1000, 650);
        ajouterFeuilleDeStyle(scene);

        LoginController controleur = new LoginController(authService, scene, vueConnexion);
        if (!controleur.demarrer()) {
            Platform.exit();
            return;
        }

        stage.setTitle("Cabinet médical");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    private boolean initialiserBaseDeDonnees(DatabaseConnection connexion) {
        try {
            new DatabaseInitializer(connexion).initialiser();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'initialiser la base de données", e);
            AlertUtil.afficherErreur("Base de données indisponible",
                    "La base de données n'a pas pu être ouverte.\nFichier : " + connexion.getFichier());
            return false;
        }
    }

    private void ajouterFeuilleDeStyle(Scene scene) {
        URL feuille = Main.class.getResource(FEUILLE_DE_STYLE);
        if (feuille == null) {
            LOGGER.log(Level.WARNING, "Feuille de style introuvable : {0}", FEUILLE_DE_STYLE);
        } else {
            scene.getStylesheets().add(feuille.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}