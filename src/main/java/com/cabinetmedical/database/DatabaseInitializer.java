// src/main/java/com/cabinetmedical/database/DatabaseInitializer.java
package com.cabinetmedical.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Crée les tables de la base de données si elles n'existent pas encore.
 * Ne supprime et ne recrée jamais des tables existantes : les données
 * restent disponibles après chaque redémarrage.
 */
public class DatabaseInitializer {

    private static final String CREATION_MEDECINS = """
            CREATE TABLE IF NOT EXISTS medecins (
                matricule  TEXT NOT NULL PRIMARY KEY,
                nom        TEXT NOT NULL,
                prenom     TEXT NOT NULL,
                specialite TEXT NOT NULL,
                telephone  TEXT NOT NULL
            )
            """;

    private static final String CREATION_PATIENTS = """
            CREATE TABLE IF NOT EXISTS patients (
                numero_telephone TEXT NOT NULL PRIMARY KEY,
                nom              TEXT NOT NULL,
                prenom           TEXT NOT NULL,
                date_naissance   TEXT NOT NULL
            )
            """;

    private static final String CREATION_RENDEZ_VOUS = """
            CREATE TABLE IF NOT EXISTS rendez_vous (
                id                       INTEGER PRIMARY KEY AUTOINCREMENT,
                matricule_medecin        TEXT NOT NULL,
                numero_telephone_patient TEXT NOT NULL,
                date_heure               TEXT NOT NULL,
                statut                   TEXT NOT NULL DEFAULT 'PLANIFIE'
                    CHECK (statut IN ('PLANIFIE', 'EFFECTUE', 'ANNULE')),
                FOREIGN KEY (matricule_medecin) REFERENCES medecins (matricule)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY (numero_telephone_patient) REFERENCES patients (numero_telephone)
                    ON DELETE RESTRICT ON UPDATE CASCADE
            )
            """;

    // Un médecin ne peut pas avoir deux rendez-vous non annulés à la même date et heure.
    private static final String INDEX_CONFLIT_MEDECIN = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_rdv_medecin_creneau
                ON rendez_vous (matricule_medecin, date_heure)
                WHERE statut <> 'ANNULE'
            """;

    // Un patient ne peut pas avoir deux rendez-vous non annulés à la même date et heure.
    private static final String INDEX_CONFLIT_PATIENT = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_rdv_patient_creneau
                ON rendez_vous (numero_telephone_patient, date_heure)
                WHERE statut <> 'ANNULE'
            """;

    private static final String CREATION_UTILISATEURS = """
            CREATE TABLE IF NOT EXISTS utilisateurs (
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                nom_utilisateur   TEXT NOT NULL UNIQUE COLLATE NOCASE,
                mot_de_passe_hash TEXT NOT NULL,
                sel               TEXT NOT NULL,
                iterations        INTEGER NOT NULL
            )
            """;

    private static final List<String> REQUETES = List.of(
            CREATION_MEDECINS,
            CREATION_PATIENTS,
            CREATION_RENDEZ_VOUS,
            INDEX_CONFLIT_MEDECIN,
            INDEX_CONFLIT_PATIENT,
            CREATION_UTILISATEURS);

    private final DatabaseConnection connexion;

    public DatabaseInitializer(DatabaseConnection connexion) {
        this.connexion = connexion;
    }

    /** Crée le fichier et les tables manquantes, dans une seule transaction. */
    public void initialiser() throws SQLException {
        try (Connection connection = connexion.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String requete : REQUETES) {
                    statement.execute(requete);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Retourne les noms des tables de l'application, triés par ordre alphabétique. */
    public List<String> listerTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        String requete = "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name";
        try (Connection connection = connexion.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultat = statement.executeQuery(requete)) {
            while (resultat.next()) {
                String nom = resultat.getString("name");
                if (!nom.startsWith("sqlite_")) {
                    tables.add(nom);
                }
            }
        }
        return tables;
    }
}