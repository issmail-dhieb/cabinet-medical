// src/test/java/com/cabinetmedical/dao/PatientDAOTest.java
package com.cabinetmedical.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de PatientDAO sur une base TEMPORAIRE (jamais cabinet_medical.db).
 * Le DAO ne valide rien : la validation est testée dans PatientServiceTest.
 */
class PatientDAOTest {

    private static final LocalDate NAISSANCE = LocalDate.of(1990, 5, 12);

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private PatientDAO dao;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        dao = new PatientDAO(connexion);
    }

    // ---------- Ajout, lecture, modification, suppression ----------

    @Test
    void ajouterPuisTrouverParNumeroTelephone() throws SQLException {
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));

        Optional<Patient> trouve = dao.trouverParNumeroTelephone("20111111");

        assertTrue(trouve.isPresent());
        assertEquals("20111111", trouve.get().getNumeroTelephone());
        assertEquals("Trabelsi", trouve.get().getNom());
        assertEquals("Sami", trouve.get().getPrenom());
        assertEquals(NAISSANCE, trouve.get().getDateNaissance());
    }

    @Test
    void trouverParNumeroInexistantRetourneVide() throws SQLException {
        assertTrue(dao.trouverParNumeroTelephone("20999999").isEmpty());
    }

    @Test
    void ajouterNumeroEnDoubleLeveUneException() throws SQLException {
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));

        assertThrows(SQLException.class, () -> dao.ajouter(patient("20111111", "Benali", "Karim")));
        assertEquals(1, dao.listerTous().size());
    }

    @Test
    void modifierMetAJourLesChampsSansChangerLeNumero() throws SQLException {
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));

        boolean modifie = dao.modifier("20111111",
                new Patient("20111111", "Benali", "Karim", LocalDate.of(1985, 3, 1)));

        assertTrue(modifie);
        Patient relu = dao.trouverParNumeroTelephone("20111111").orElseThrow();
        assertEquals("Benali", relu.getNom());
        assertEquals("Karim", relu.getPrenom());
        assertEquals(LocalDate.of(1985, 3, 1), relu.getDateNaissance());
    }

    @Test
    void modifierChangeLeNumeroEtMetAJourLesRendezVous() throws SQLException {
        new MedecinDAO(connexion).ajouter(new Medecin("M001", "Benali", "Sami", "Cardiologie", "71000000"));
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));
        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:00", "PLANIFIE");

        boolean modifie = dao.modifier("20111111", patient("20999999", "Trabelsi", "Sami"));

        assertTrue(modifie);
        assertTrue(dao.trouverParNumeroTelephone("20111111").isEmpty());
        assertTrue(dao.trouverParNumeroTelephone("20999999").isPresent());
        assertEquals(0, dao.compterRendezVous("20111111"));
        assertEquals(1, dao.compterRendezVous("20999999"));
    }

    @Test
    void modifierVersUnNumeroDejaPrisLeveUneException() throws SQLException {
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));
        dao.ajouter(patient("21222222", "Benali", "Karim"));

        assertThrows(SQLException.class,
                () -> dao.modifier("20111111", patient("21222222", "Trabelsi", "Sami")));
        assertEquals("Trabelsi", dao.trouverParNumeroTelephone("20111111").orElseThrow().getNom());
        assertEquals("Benali", dao.trouverParNumeroTelephone("21222222").orElseThrow().getNom());
    }

    @Test
    void modifierPatientInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.modifier("20999999", patient("20999999", "Trabelsi", "Sami")));
    }

    @Test
    void supprimerPatientSansRendezVous() throws SQLException {
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));

        assertTrue(dao.supprimer("20111111"));
        assertTrue(dao.trouverParNumeroTelephone("20111111").isEmpty());
    }

    @Test
    void supprimerPatientInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.supprimer("20999999"));
    }

    @Test
    void supprimerPatientAvecRendezVousEstBloqueParLaBase() throws SQLException {
        new MedecinDAO(connexion).ajouter(new Medecin("M001", "Benali", "Sami", "Cardiologie", "71000000"));
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));
        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:00", "ANNULE");

        assertThrows(SQLException.class, () -> dao.supprimer("20111111"));
        assertTrue(dao.trouverParNumeroTelephone("20111111").isPresent());
    }

    @Test
    void compterRendezVousInclutLesRendezVousAnnules() throws SQLException {
        new MedecinDAO(connexion).ajouter(new Medecin("M001", "Benali", "Sami", "Cardiologie", "71000000"));
        dao.ajouter(patient("20111111", "Trabelsi", "Sami"));
        dao.ajouter(patient("21222222", "Benali", "Karim"));
        assertEquals(0, dao.compterRendezVous("20111111"));

        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:00", "ANNULE");
        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:15", "PLANIFIE");

        assertEquals(2, dao.compterRendezVous("20111111"));
        assertEquals(0, dao.compterRendezVous("21222222"));
    }

    // ---------- Liste et tri ----------

    @Test
    void listerTousTrieParNomPuisPrenomEnFrancais() throws SQLException {
        dao.ajouter(patient("20111111", "Zouari", "Sami"));
        dao.ajouter(patient("21222222", "Ben Ali", "Leila"));
        dao.ajouter(patient("22333333", "Éloi", "Karim"));

        List<String> noms = dao.listerTous().stream().map(Patient::getNom).toList();

        assertEquals(List.of("Ben Ali", "Éloi", "Zouari"), noms);
    }

    // ---------- Recherches ----------

    @Test
    void chercherParNomIgnoreCasseEtAccents() throws SQLException {
        ajouterTroisPatients();

        assertEquals(List.of("20111111"), numeros(dao.chercherParNom("ben sal")));
        assertEquals(List.of("20111111"), numeros(dao.chercherParNom("BEN SAL")));
        assertEquals(List.of("21222222"), numeros(dao.chercherParNom("trab")));
        assertTrue(dao.chercherParNom("xyz").isEmpty());
    }

    @Test
    void chercherParNomEtParPrenomSontIndependants() throws SQLException {
        ajouterTroisPatients();

        assertTrue(dao.chercherParNom("eloise").isEmpty());
        assertTrue(dao.chercherParPrenom("ben").isEmpty());
    }

    @Test
    void chercherParPrenomIgnoreCasseEtAccents() throws SQLException {
        ajouterTroisPatients();

        assertEquals(List.of("20111111"), numeros(dao.chercherParPrenom("eloise")));
        assertEquals(List.of("20111111"), numeros(dao.chercherParPrenom("ÉLOÏSE")));
        assertEquals(List.of("21222222"), numeros(dao.chercherParPrenom("kar")));
    }

    @Test
    void chercherParNumeroTelephoneEstPartielEtIgnoreEspacesEtPrefixe() throws SQLException {
        ajouterTroisPatients();

        assertEquals(List.of("20111111"), numeros(dao.chercherParNumeroTelephone("2011")));
        assertEquals(List.of("21222222"), numeros(dao.chercherParNumeroTelephone("+216 21 222")));
        assertEquals(List.of("55123456"), numeros(dao.chercherParNumeroTelephone("  55 123 ")));
        assertTrue(dao.chercherParNumeroTelephone("99").isEmpty());
    }

    @Test
    void rechercheVideRetourneTousLesPatients() throws SQLException {
        ajouterTroisPatients();

        assertEquals(3, dao.chercherParNom("").size());
        assertEquals(3, dao.chercherParPrenom("   ").size());
        assertEquals(3, dao.chercherParNumeroTelephone(null).size());
    }

    // ---------- Donnée illisible ----------

    @Test
    void dateDeNaissanceIllisibleEnBaseLeveUneSQLException() throws SQLException {
        String requete = "INSERT INTO patients (numero_telephone, nom, prenom, date_naissance) "
                + "VALUES ('20111111', 'Trabelsi', 'Sami', 'pas-une-date')";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.executeUpdate();
        }

        assertThrows(SQLException.class, () -> dao.listerTous());
    }

    // ---------- Méthodes utilitaires ----------

    private void ajouterTroisPatients() throws SQLException {
        dao.ajouter(patient("20111111", "Ben Salah", "Éloïse"));
        dao.ajouter(patient("21222222", "Trabelsi", "Karim"));
        dao.ajouter(patient("55123456", "Zouari", "Leila"));
    }

    private static Patient patient(String numero, String nom, String prenom) {
        return new Patient(numero, nom, prenom, NAISSANCE);
    }

    private static List<String> numeros(List<Patient> patients) {
        return patients.stream().map(Patient::getNumeroTelephone).toList();
    }
}