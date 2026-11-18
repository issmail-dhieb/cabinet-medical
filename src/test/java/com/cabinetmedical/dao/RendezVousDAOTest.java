// src/test/java/com/cabinetmedical/dao/RendezVousDAOTest.java
package com.cabinetmedical.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de RendezVousDAO sur une base TEMPORAIRE (jamais cabinet_medical.db).
 * Le DAO ne valide rien : les règles métier sont testées dans RendezVousServiceTest.
 */
class RendezVousDAOTest {

    private static final String MEDECIN_1 = "M001";
    private static final String MEDECIN_2 = "M002";
    private static final String PATIENT_1 = "20111111";
    private static final String PATIENT_2 = "21222222";

    private static final LocalDateTime JOUR_9H = LocalDateTime.of(2030, 1, 11, 9, 0);
    private static final LocalDateTime JOUR_10H = LocalDateTime.of(2030, 1, 11, 10, 0);
    private static final LocalDateTime JOUR_11H = LocalDateTime.of(2030, 1, 11, 11, 0);

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private RendezVousDAO dao;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        dao = new RendezVousDAO(connexion);

        MedecinDAO medecinDAO = new MedecinDAO(connexion);
        medecinDAO.ajouter(new Medecin(MEDECIN_1, "Benali", "Sami", "Cardiologie", "71000000"));
        medecinDAO.ajouter(new Medecin(MEDECIN_2, "Trabelsi", "Karim", "Pédiatrie", "72000000"));

        PatientDAO patientDAO = new PatientDAO(connexion);
        patientDAO.ajouter(new Patient(PATIENT_1, "Ben Salah", "Éloïse", LocalDate.of(1990, 5, 12)));
        patientDAO.ajouter(new Patient(PATIENT_2, "Zouari", "Leila", LocalDate.of(1985, 3, 1)));
    }

    // ---------- Ajout et lecture ----------

    @Test
    void ajouterPuisTrouverParId() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertTrue(id > 0);
        RendezVous relu = dao.trouverParId(id).orElseThrow();
        assertEquals(id, relu.getId());
        assertEquals(MEDECIN_1, relu.getMatriculeMedecin());
        assertEquals(PATIENT_1, relu.getNumeroTelephonePatient());
        assertEquals(JOUR_9H, relu.getDateHeure());
        assertEquals(StatutRendezVous.PLANIFIE, relu.getStatut());
    }

    @Test
    void ajouterDonneDesIdentifiantsDifferents() throws SQLException {
        int premier = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));
        int second = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_10H));

        assertTrue(second != premier);
    }

    @Test
    void trouverParIdInexistantRetourneVide() throws SQLException {
        assertTrue(dao.trouverParId(999).isEmpty());
    }

    @Test
    void ajouterRefuseUnMedecinInexistant() throws SQLException {
        assertThrows(SQLException.class,
                () -> dao.ajouter(new RendezVous("M999", PATIENT_1, JOUR_9H)));
        assertTrue(dao.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnPatientInexistant() throws SQLException {
        assertThrows(SQLException.class,
                () -> dao.ajouter(new RendezVous(MEDECIN_1, "29999999", JOUR_9H)));
        assertTrue(dao.listerTous().isEmpty());
    }

    // ---------- Créneaux (index d'unicité de la base) ----------

    @Test
    void ajouterRefuseLeMemeCreneauPourLeMemeMedecin() throws SQLException {
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertThrows(SQLException.class,
                () -> dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_2, JOUR_9H)));
        assertEquals(1, dao.listerTous().size());
    }

    @Test
    void ajouterRefuseLeMemeCreneauPourLeMemePatient() throws SQLException {
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertThrows(SQLException.class,
                () -> dao.ajouter(new RendezVous(MEDECIN_2, PATIENT_1, JOUR_9H)));
        assertEquals(1, dao.listerTous().size());
    }

    @Test
    void ajouterAccepteLeCreneauDUnRendezVousAnnule() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));
        dao.modifierStatut(id, StatutRendezVous.ANNULE);

        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertEquals(2, dao.listerTous().size());
    }

    // ---------- Modification ----------

    @Test
    void modifierDateHeureMetAJourLeRendezVous() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        boolean modifie = dao.modifierDateHeure(id, JOUR_11H);

        assertTrue(modifie);
        RendezVous relu = dao.trouverParId(id).orElseThrow();
        assertEquals(JOUR_11H, relu.getDateHeure());
        assertEquals(MEDECIN_1, relu.getMatriculeMedecin());
        assertEquals(StatutRendezVous.PLANIFIE, relu.getStatut());
    }

    @Test
    void modifierDateHeureInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.modifierDateHeure(999, JOUR_9H));
    }

    @Test
    void modifierDateHeureVersUnCreneauPrisLeveUneException() throws SQLException {
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_2, JOUR_10H));

        assertThrows(SQLException.class, () -> dao.modifierDateHeure(id, JOUR_9H));
        assertEquals(JOUR_10H, dao.trouverParId(id).orElseThrow().getDateHeure());
    }

    @Test
    void modifierStatutMetAJourLeRendezVous() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertTrue(dao.modifierStatut(id, StatutRendezVous.EFFECTUE));
        assertEquals(StatutRendezVous.EFFECTUE, dao.trouverParId(id).orElseThrow().getStatut());

        assertTrue(dao.modifierStatut(id, StatutRendezVous.ANNULE));
        assertEquals(StatutRendezVous.ANNULE, dao.trouverParId(id).orElseThrow().getStatut());
    }

    @Test
    void modifierStatutInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.modifierStatut(999, StatutRendezVous.ANNULE));
    }

    // ---------- Listes ----------

    @Test
    void listerTousTrieParDateHeureEtJointMedecinEtPatient() throws SQLException {
        dao.ajouter(new RendezVous(MEDECIN_2, PATIENT_2, JOUR_11H));
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_2, JOUR_10H));

        List<RendezVousDetail> details = dao.listerTous();

        assertEquals(List.of(JOUR_9H, JOUR_10H, JOUR_11H),
                details.stream().map(d -> d.getRendezVous().getDateHeure()).toList());
        RendezVousDetail premier = details.get(0);
        assertEquals("Benali", premier.getMedecin().getNom());
        assertEquals("Sami", premier.getMedecin().getPrenom());
        assertEquals("Cardiologie", premier.getMedecin().getSpecialite());
        assertEquals("71000000", premier.getMedecin().getTelephone());
        assertEquals("Ben Salah", premier.getPatient().getNom());
        assertEquals("Éloïse", premier.getPatient().getPrenom());
        assertEquals(LocalDate.of(1990, 5, 12), premier.getPatient().getDateNaissance());
        assertEquals("Trabelsi", details.get(2).getMedecin().getNom());
        assertEquals("Zouari", details.get(2).getPatient().getNom());
    }

    @Test
    void listerTousConserveLesRendezVousAnnulesEtEffectues() throws SQLException {
        int annule = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));
        int effectue = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_10H));
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_11H));
        dao.modifierStatut(annule, StatutRendezVous.ANNULE);
        dao.modifierStatut(effectue, StatutRendezVous.EFFECTUE);

        List<StatutRendezVous> statuts = dao.listerTous().stream()
                .map(d -> d.getRendezVous().getStatut()).toList();

        assertEquals(List.of(StatutRendezVous.ANNULE, StatutRendezVous.EFFECTUE,
                StatutRendezVous.PLANIFIE), statuts);
    }

    @Test
    void listerAVenirGardeLesRendezVousPlanifiesDepuisMaintenant() throws SQLException {
        LocalDateTime maintenant = LocalDateTime.of(2030, 1, 11, 10, 0);
        dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 9, 0)));
        int dixHeures = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 10, 0)));
        int onzeHeures = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 11, 0)));
        int annule = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 12, 0)));
        int effectue = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 13, 0)));
        dao.modifierStatut(annule, StatutRendezVous.ANNULE);
        dao.modifierStatut(effectue, StatutRendezVous.EFFECTUE);

        List<Integer> identifiants = dao.listerAVenir(maintenant).stream()
                .map(d -> d.getRendezVous().getId()).toList();

        assertEquals(List.of(dixHeures, onzeHeures), identifiants);
    }

    @Test
    void trouverDetailParIdJointMedecinEtPatient() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_2, PATIENT_2, JOUR_9H));

        Optional<RendezVousDetail> detail = dao.trouverDetailParId(id);

        assertTrue(detail.isPresent());
        assertEquals(id, detail.get().getRendezVous().getId());
        assertEquals("Trabelsi", detail.get().getMedecin().getNom());
        assertEquals("Zouari", detail.get().getPatient().getNom());
        assertTrue(dao.trouverDetailParId(999).isEmpty());
    }

    // ---------- Détection des conflits ----------

    @Test
    void existeConflitMedecinIgnoreLesRendezVousAnnulesEtLIdExclu() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertTrue(dao.existeConflitMedecin(MEDECIN_1, JOUR_9H, 0));
        assertFalse(dao.existeConflitMedecin(MEDECIN_1, JOUR_9H, id));
        assertFalse(dao.existeConflitMedecin(MEDECIN_1, JOUR_10H, 0));
        assertFalse(dao.existeConflitMedecin(MEDECIN_2, JOUR_9H, 0));

        dao.modifierStatut(id, StatutRendezVous.ANNULE);
        assertFalse(dao.existeConflitMedecin(MEDECIN_1, JOUR_9H, 0));
    }

    @Test
    void existeConflitPatientIgnoreLesRendezVousAnnulesEtLIdExclu() throws SQLException {
        int id = dao.ajouter(new RendezVous(MEDECIN_1, PATIENT_1, JOUR_9H));

        assertTrue(dao.existeConflitPatient(PATIENT_1, JOUR_9H, 0));
        assertFalse(dao.existeConflitPatient(PATIENT_1, JOUR_9H, id));
        assertFalse(dao.existeConflitPatient(PATIENT_1, JOUR_10H, 0));
        assertFalse(dao.existeConflitPatient(PATIENT_2, JOUR_9H, 0));

        dao.modifierStatut(id, StatutRendezVous.ANNULE);
        assertFalse(dao.existeConflitPatient(PATIENT_1, JOUR_9H, 0));
    }

    // ---------- Donnée illisible ----------

    @Test
    void dateIllisibleEnBaseLeveUneSQLException() throws SQLException {
        String requete = "INSERT INTO rendez_vous "
                + "(matricule_medecin, numero_telephone_patient, date_heure, statut) "
                + "VALUES ('M001', '20111111', 'pas-une-date', 'PLANIFIE')";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.executeUpdate();
        }

        assertThrows(SQLException.class, () -> dao.listerTous());
        assertThrows(SQLException.class, () -> dao.trouverParId(1));
    }
}