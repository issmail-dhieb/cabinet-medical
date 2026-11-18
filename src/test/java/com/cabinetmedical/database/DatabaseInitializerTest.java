// src/test/java/com/cabinetmedical/database/DatabaseInitializerTest.java
package com.cabinetmedical.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de la base de données sur un fichier TEMPORAIRE.
 * Le fichier de production cabinet_medical.db n'est jamais utilisé ici.
 */
class DatabaseInitializerTest {

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private DatabaseInitializer initialiseur;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = new DatabaseConnection(dossierTemporaire.resolve("test_cabinet.db"));
        initialiseur = new DatabaseInitializer(connexion);
        initialiseur.initialiser();
    }

    // ---------- Initialisation ----------

    @Test
    void initialiserCreeLeFichierEtLesQuatreTables() throws SQLException {
        assertTrue(Files.exists(connexion.getFichier()));
        assertEquals(List.of("medecins", "patients", "rendez_vous", "utilisateurs"),
                initialiseur.listerTables());
    }

    @Test
    void initialiserDeuxFoisConserveLesDonneesExistantes() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
        }
        initialiseur.initialiser();
        try (Connection c = connexion.getConnection()) {
            assertEquals(1, compter(c, "medecins"));
        }
    }

    @Test
    void cleEtrangereActiveSurChaqueConnexion() throws SQLException {
        for (int i = 0; i < 2; i++) {
            try (Connection c = connexion.getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    // ---------- Insertion et lecture ----------

    @Test
    void insertionEtLectureMedecinPatientEtRendezVous() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "PLANIFIE");

            String requete = """
                    SELECT r.id, m.nom AS nom_medecin, p.nom AS nom_patient,
                           r.date_heure, r.statut
                    FROM rendez_vous r
                    JOIN medecins m ON m.matricule = r.matricule_medecin
                    JOIN patients p ON p.numero_telephone = r.numero_telephone_patient
                    """;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(requete)) {
                assertTrue(rs.next());
                assertTrue(rs.getInt("id") >= 1);
                assertEquals("Benali", rs.getString("nom_medecin"));
                assertEquals("Trabelsi", rs.getString("nom_patient"));
                assertEquals("2030-05-10T09:30", rs.getString("date_heure"));
                assertEquals("PLANIFIE", rs.getString("statut"));
            }
        }
    }

    @Test
    void insertionEtLectureUtilisateur() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererUtilisateur(c, "admin");
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM utilisateurs")) {
                assertTrue(rs.next());
                assertEquals("admin", rs.getString("nom_utilisateur"));
                assertEquals("hash", rs.getString("mot_de_passe_hash"));
                assertEquals("sel", rs.getString("sel"));
                assertEquals(210000, rs.getInt("iterations"));
            }
        }
    }

    // ---------- Contraintes d'unicité ----------

    @Test
    void doublonMatriculeMedecinRefuse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            assertThrows(SQLException.class, () -> insererMedecin(c, "M001"));
            assertEquals(1, compter(c, "medecins"));
        }
    }

    @Test
    void doublonTelephonePatientRefuse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererPatient(c, "20123456");
            assertThrows(SQLException.class, () -> insererPatient(c, "20123456"));
            assertEquals(1, compter(c, "patients"));
        }
    }

    @Test
    void doublonNomUtilisateurRefuseMemeAvecAutreCasse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererUtilisateur(c, "admin");
            assertThrows(SQLException.class, () -> insererUtilisateur(c, "ADMIN"));
            assertEquals(1, compter(c, "utilisateurs"));
        }
    }

    // ---------- Clés étrangères et contraintes ----------

    @Test
    void rendezVousAvecMedecinInexistantRefuse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererPatient(c, "20123456");
            assertThrows(SQLException.class,
                    () -> insererRendezVous(c, "INCONNU", "20123456", "2030-05-10T09:30", "PLANIFIE"));
            assertEquals(0, compter(c, "rendez_vous"));
        }
    }

    @Test
    void rendezVousAvecPatientInexistantRefuse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            assertThrows(SQLException.class,
                    () -> insererRendezVous(c, "M001", "00000000", "2030-05-10T09:30", "PLANIFIE"));
            assertEquals(0, compter(c, "rendez_vous"));
        }
    }

    @Test
    void statutInvalideRefuse() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            assertThrows(SQLException.class,
                    () -> insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "INCONNU"));
            assertEquals(0, compter(c, "rendez_vous"));
        }
    }

    @Test
    void suppressionMedecinAvecRendezVousRefusee() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "ANNULE");
            assertThrows(SQLException.class,
                    () -> executer(c, "DELETE FROM medecins WHERE matricule = ?", "M001"));
            assertEquals(1, compter(c, "medecins"));
            assertEquals(1, compter(c, "rendez_vous"));
        }
    }

    @Test
    void suppressionPatientAvecRendezVousRefusee() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "EFFECTUE");
            assertThrows(SQLException.class,
                    () -> executer(c, "DELETE FROM patients WHERE numero_telephone = ?", "20123456"));
            assertEquals(1, compter(c, "patients"));
            assertEquals(1, compter(c, "rendez_vous"));
        }
    }

    // ---------- Conflits de créneaux ----------

    @Test
    void deuxRendezVousMemeMedecinMemeHeureRefuses() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererPatient(c, "20999999");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "PLANIFIE");
            assertThrows(SQLException.class,
                    () -> insererRendezVous(c, "M001", "20999999", "2030-05-10T09:30", "PLANIFIE"));
            assertEquals(1, compter(c, "rendez_vous"));
        }
    }

    @Test
    void deuxRendezVousMemePatientMemeHeureRefuses() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererMedecin(c, "M002");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "PLANIFIE");
            assertThrows(SQLException.class,
                    () -> insererRendezVous(c, "M002", "20123456", "2030-05-10T09:30", "PLANIFIE"));
            assertEquals(1, compter(c, "rendez_vous"));
        }
    }

    @Test
    void creneauAnnuleEstReutilisable() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "ANNULE");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "PLANIFIE");
            assertEquals(2, compter(c, "rendez_vous"));
        }
    }

    // ---------- Mise à jour du numéro de téléphone d'un patient ----------

    @Test
    void changementTelephonePatientSePropageAuxRendezVous() throws SQLException {
        try (Connection c = connexion.getConnection()) {
            insererMedecin(c, "M001");
            insererPatient(c, "20123456");
            insererRendezVous(c, "M001", "20123456", "2030-05-10T09:30", "PLANIFIE");

            executer(c, "UPDATE patients SET numero_telephone = ? WHERE numero_telephone = ?",
                    "20111111", "20123456");

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT numero_telephone_patient FROM rendez_vous")) {
                assertTrue(rs.next());
                assertEquals("20111111", rs.getString(1));
            }
        }
    }

    // ---------- Méthodes utilitaires ----------

    private void executer(Connection c, String sql, Object... parametres) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametres.length; i++) {
                ps.setObject(i + 1, parametres[i]);
            }
            ps.executeUpdate();
        }
    }

    private void insererMedecin(Connection c, String matricule) throws SQLException {
        executer(c,
                "INSERT INTO medecins (matricule, nom, prenom, specialite, telephone) VALUES (?, ?, ?, ?, ?)",
                matricule, "Benali", "Sami", "Cardiologie", "71000000");
    }

    private void insererPatient(Connection c, String numeroTelephone) throws SQLException {
        executer(c,
                "INSERT INTO patients (numero_telephone, nom, prenom, date_naissance) VALUES (?, ?, ?, ?)",
                numeroTelephone, "Trabelsi", "Leila", "1990-04-12");
    }

    private void insererRendezVous(Connection c, String matricule, String numeroTelephone,
                                   String dateHeure, String statut) throws SQLException {
        executer(c,
                "INSERT INTO rendez_vous (matricule_medecin, numero_telephone_patient, date_heure, statut) "
                        + "VALUES (?, ?, ?, ?)",
                matricule, numeroTelephone, dateHeure, statut);
    }

    private void insererUtilisateur(Connection c, String nomUtilisateur) throws SQLException {
        executer(c,
                "INSERT INTO utilisateurs (nom_utilisateur, mot_de_passe_hash, sel, iterations) "
                        + "VALUES (?, ?, ?, ?)",
                nomUtilisateur, "hash", "sel", 210000);
    }

    private int compter(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}