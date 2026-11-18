// src/test/java/com/cabinetmedical/BaseTemporaire.java
package com.cabinetmedical;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.database.DatabaseInitializer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Outils pour les tests : crée une base de données temporaire (jamais cabinet_medical.db)
 * et insère des rendez-vous directement en SQL, tant que RendezVousDAO n'existe pas.
 */
public final class BaseTemporaire {

    /** Numéro du patient de test créé automatiquement par insererRendezVous. */
    public static final String TELEPHONE_PATIENT = "20123456";

    private BaseTemporaire() {
        // Classe utilitaire : on ne crée pas d'instance.
    }

    /** Crée un fichier de base de données dans le dossier donné, avec toutes les tables. */
    public static DatabaseConnection creerBaseTemporaire(Path dossier) throws SQLException {
        DatabaseConnection connexion = new DatabaseConnection(dossier.resolve("test_cabinet.db"));
        new DatabaseInitializer(connexion).initialiser();
        return connexion;
    }

    /**
     * Insère un rendez-vous pour le médecin indiqué (qui doit déjà exister),
     * avec le patient de test (créé s'il n'existe pas).
     *
     * @param dateHeure au format yyyy-MM-dd'T'HH:mm, par exemple 2030-01-15T09:00
     * @param statut    PLANIFIE, EFFECTUE ou ANNULE
     */
    public static void insererRendezVous(DatabaseConnection connexion, String matricule,
                                         String dateHeure, String statut) throws SQLException {
        String requetePatient = "INSERT OR IGNORE INTO patients "
                + "(numero_telephone, nom, prenom, date_naissance) VALUES (?, 'Trabelsi', 'Sami', '1990-05-12')";
        try (Connection connection = connexion.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(requetePatient)) {
                statement.setString(1, TELEPHONE_PATIENT);
                statement.executeUpdate();
            }
            insererLigneRendezVous(connection, matricule, TELEPHONE_PATIENT, dateHeure, statut);
        }
    }

    /**
     * Insère un rendez-vous pour un médecin ET un patient qui existent déjà.
     *
     * @param dateHeure au format yyyy-MM-dd'T'HH:mm, par exemple 2030-01-15T09:00
     * @param statut    PLANIFIE, EFFECTUE ou ANNULE
     */
    public static void insererRendezVousPatient(DatabaseConnection connexion, String matricule,
                                                String numeroTelephonePatient, String dateHeure,
                                                String statut) throws SQLException {
        try (Connection connection = connexion.getConnection()) {
            insererLigneRendezVous(connection, matricule, numeroTelephonePatient, dateHeure, statut);
        }
    }

    private static void insererLigneRendezVous(Connection connection, String matricule,
                                               String numeroTelephonePatient, String dateHeure,
                                               String statut) throws SQLException {
        String requete = "INSERT INTO rendez_vous "
                + "(matricule_medecin, numero_telephone_patient, date_heure, statut) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, matricule);
            statement.setString(2, numeroTelephonePatient);
            statement.setString(3, dateHeure);
            statement.setString(4, statut);
            statement.executeUpdate();
        }
    }
}