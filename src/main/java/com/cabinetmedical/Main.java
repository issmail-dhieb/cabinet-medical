// src/main/java/com/cabinetmedical/Main.java
package com.cabinetmedical;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.database.DatabaseInitializer;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/**
 * Point d'entrée de l'application.
 * Phase 2 : en plus de la fenêtre simple, initialise la base de données
 * et affiche son emplacement pour vérifier qu'elle est bien créée.
 * (Cet affichage temporaire sera remplacé dans les phases d'interface.)
 */
public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage stage) {
        Label titre = new Label("Cabinet Médical");
        titre.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label sousTitre = new Label("Projet initialisé : la fenêtre JavaFX s'affiche correctement.");

        Label versions = new Label(
                "Java " + System.getProperty("java.version")
                        + "  |  JavaFX " + System.getProperty("javafx.version"));

        DatabaseConnection connexion = new DatabaseConnection();
        Label etatBase = creerLabelCentre(initialiserBaseDeDonnees(connexion));
        Label cheminBase = creerLabelCentre("Fichier : " + connexion.getFichier());

        VBox racine = new VBox(12, titre, sousTitre, versions, etatBase, cheminBase);
        racine.setAlignment(Pos.CENTER);
        racine.setPadding(new Insets(24));

        Scene scene = new Scene(racine, 800, 500);

        stage.setTitle("Cabinet Médical");
        stage.setMinWidth(600);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }

    private String initialiserBaseDeDonnees(DatabaseConnection connexion) {
        DatabaseInitializer initialiseur = new DatabaseInitializer(connexion);
        try {
            initialiseur.initialiser();
            return "Base de données prête. Tables : " + String.join(", ", initialiseur.listerTables());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Impossible d'initialiser la base de données", e);
            return "Erreur : la base de données n'a pas pu être initialisée.";
        }
    }

    private Label creerLabelCentre(String texte) {
        Label label = new Label(texte);
        label.setWrapText(true);
        label.setMaxWidth(700);
        label.setTextAlignment(TextAlignment.CENTER);
        return label;
    }

    public static void main(String[] args) {
        launch(args);
    }
}