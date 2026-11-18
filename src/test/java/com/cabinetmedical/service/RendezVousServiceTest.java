// src/test/java/com/cabinetmedical/service/RendezVousServiceTest.java
package com.cabinetmedical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.dao.MedecinDAO;
import com.cabinetmedical.dao.PatientDAO;
import com.cabinetmedical.dao.RendezVousDAO;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de RendezVousService sur une base TEMPORAIRE (jamais cabinet_medical.db).
 * L'heure actuelle est fixée au 10/01/2030 à 10:00 grâce à une horloge de test.
 */
class RendezVousServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2030, 1, 10, 10, 0);

    private static final String MEDECIN_1 = "M001";
    private static final String MEDECIN_2 = "M002";
    private static final String PATIENT_1 = "20111111";
    private static final String PATIENT_2 = "21222222";

    private static final LocalDateTime DEMAIN_9H = LocalDateTime.of(2030, 1, 11, 9, 0);
    private static final LocalDateTime DEMAIN_10H = LocalDateTime.of(2030, 1, 11, 10, 0);
    private static final LocalDateTime DEMAIN_11H = LocalDateTime.of(2030, 1, 11, 11, 0);

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private RendezVousDAO rendezVousDAO;
    private RendezVousService service;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        MedecinDAO medecinDAO = new MedecinDAO(connexion);
        PatientDAO patientDAO = new PatientDAO(connexion);
        rendezVousDAO = new RendezVousDAO(connexion);

        medecinDAO.ajouter(new Medecin(MEDECIN_1, "Benali", "Sami", "Cardiologie", "71000000"));
        medecinDAO.ajouter(new Medecin(MEDECIN_2, "Trabelsi", "Karim", "Pédiatrie", "72000000"));
        patientDAO.ajouter(new Patient(PATIENT_1, "Ben Salah", "Éloïse", LocalDate.of(1990, 5, 12)));
        patientDAO.ajouter(new Patient(PATIENT_2, "Zouari", "Leila", LocalDate.of(1985, 3, 1)));

        service = new RendezVousService(rendezVousDAO, medecinDAO, patientDAO, horlogeFixee());
    }

    // ---------- Planification ----------

    @Test
    void planifierEnregistreUnRendezVousPlanifie() throws Exception {
        RendezVous enregistre = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        assertTrue(enregistre.getId() > 0);
        assertEquals(StatutRendezVous.PLANIFIE, enregistre.getStatut());
        List<RendezVousDetail> tous = service.listerTous();
        assertEquals(1, tous.size());
        assertEquals(enregistre.getId(), tous.get(0).getRendezVous().getId());
        assertEquals(DEMAIN_9H, tous.get(0).getRendezVous().getDateHeure());
        assertEquals("Benali", tous.get(0).getMedecin().getNom());
        assertEquals("Ben Salah", tous.get(0).getPatient().getNom());
    }

    @Test
    void planifierNormaliseLeMatriculeEtLeTelephone() throws Exception {
        RendezVous enregistre = planifier(" m001 ", " +216 20 111 111 ", DEMAIN_9H);

        assertEquals(MEDECIN_1, enregistre.getMatriculeMedecin());
        assertEquals(PATIENT_1, enregistre.getNumeroTelephonePatient());
    }

    @Test
    void planifierIgnoreLIdentifiantEtLeStatutDeLaSaisie() throws Exception {
        RendezVous saisie = new RendezVous(77, MEDECIN_1, PATIENT_1, DEMAIN_9H, StatutRendezVous.ANNULE);

        RendezVous enregistre = service.planifier(saisie);

        assertEquals(StatutRendezVous.PLANIFIE, enregistre.getStatut());
        RendezVous relu = rendezVousDAO.trouverParId(enregistre.getId()).orElseThrow();
        assertEquals(StatutRendezVous.PLANIFIE, relu.getStatut());
        assertEquals(1, service.listerTous().size());
    }

    @Test
    void planifierRefuseUneSaisieNull() {
        assertPlanificationRefusee(null, "Les informations du rendez-vous sont obligatoires.");
    }

    @Test
    void planifierRefuseUnMedecinVide() {
        assertPlanificationRefusee(new RendezVous("  ", PATIENT_1, DEMAIN_9H), "Le médecin est obligatoire.");
        assertPlanificationRefusee(new RendezVous(null, PATIENT_1, DEMAIN_9H), "Le médecin est obligatoire.");
    }

    @Test
    void planifierRefuseUnPatientVide() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, "  ", DEMAIN_9H), "Le patient est obligatoire.");
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, null, DEMAIN_9H), "Le patient est obligatoire.");
    }

    @Test
    void planifierRefuseUnTelephonePatientInvalide() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, "1234", DEMAIN_9H),
                "Le numéro de téléphone du patient doit contenir 8 chiffres");
    }

    @Test
    void planifierRefuseUneDateAbsente() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_1, null),
                "La date et l'heure du rendez-vous sont obligatoires.");
    }

    @Test
    void planifierRefuseDesMinutesQuiNeSontPasUnMultipleDeQuinze() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 9, 10)),
                "L'heure du rendez-vous doit être un multiple de 15 minutes");
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_1, LocalDateTime.of(2030, 1, 11, 9, 1)),
                "L'heure du rendez-vous doit être un multiple de 15 minutes");
    }

    @Test
    void planifierRefuseUneDateDansLePasse() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_1, MAINTENANT.minusMinutes(15)),
                "La date et l'heure du rendez-vous ne peuvent pas être dans le passé.");
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_1, MAINTENANT.minusDays(3)),
                "La date et l'heure du rendez-vous ne peuvent pas être dans le passé.");
    }

    @Test
    void planifierAccepteLeMomentPresent() throws Exception {
        RendezVous enregistre = planifier(MEDECIN_1, PATIENT_1, MAINTENANT);

        assertEquals(MAINTENANT, enregistre.getDateHeure());
    }

    @Test
    void planifierRefuseUnMedecinInexistant() {
        assertPlanificationRefusee(new RendezVous("M999", PATIENT_1, DEMAIN_9H),
                "Aucun médecin ne porte le matricule M999.");
    }

    @Test
    void planifierRefuseUnPatientInexistant() {
        assertPlanificationRefusee(new RendezVous(MEDECIN_1, "29999999", DEMAIN_9H),
                "Aucun patient ne porte le numéro 29999999.");
    }

    @Test
    void planifierRefuseUnConflitDeMedecin() throws Exception {
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        assertPlanificationRefusee(new RendezVous(MEDECIN_1, PATIENT_2, DEMAIN_9H),
                "Ce médecin a déjà un rendez-vous le 11/01/2030 à 09:00.");
        assertEquals(1, service.listerTous().size());
    }

    @Test
    void planifierRefuseUnConflitDePatient() throws Exception {
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        assertPlanificationRefusee(new RendezVous(MEDECIN_2, PATIENT_1, DEMAIN_9H),
                "Ce patient a déjà un rendez-vous le 11/01/2030 à 09:00.");
        assertEquals(1, service.listerTous().size());
    }

    @Test
    void planifierAccepteLeMemeCreneauPourUnAutreMedecinEtUnAutrePatient() throws Exception {
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        planifier(MEDECIN_2, PATIENT_2, DEMAIN_9H);

        assertEquals(2, service.listerTous().size());
    }

    @Test
    void planifierAccepteLeCreneauDUnRendezVousAnnule() throws Exception {
        RendezVous premier = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);
        service.modifierStatut(premier.getId(), StatutRendezVous.ANNULE);

        RendezVous second = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        assertTrue(second.getId() != premier.getId());
        assertEquals(2, service.listerTous().size());
    }

    // ---------- Modification de la date et de l'heure ----------

    @Test
    void modifierDateHeureDeplaceLeRendezVous() throws Exception {
        RendezVous rendezVous = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        RendezVous modifie = service.modifierDateHeure(rendezVous.getId(), DEMAIN_11H);

        assertEquals(DEMAIN_11H, modifie.getDateHeure());
        RendezVous relu = rendezVousDAO.trouverParId(rendezVous.getId()).orElseThrow();
        assertEquals(DEMAIN_11H, relu.getDateHeure());
        assertEquals(MEDECIN_1, relu.getMatriculeMedecin());
        assertEquals(PATIENT_1, relu.getNumeroTelephonePatient());
        assertEquals(StatutRendezVous.PLANIFIE, relu.getStatut());
    }

    @Test
    void modifierDateHeureRefuseUneDateInvalide() throws Exception {
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();

        assertDateHeureRefusee(id, null, "La date et l'heure du rendez-vous sont obligatoires.");
        assertDateHeureRefusee(id, LocalDateTime.of(2030, 1, 11, 9, 20),
                "L'heure du rendez-vous doit être un multiple de 15 minutes");
        assertDateHeureRefusee(id, MAINTENANT.minusHours(1),
                "La date et l'heure du rendez-vous ne peuvent pas être dans le passé.");
        assertEquals(DEMAIN_9H, rendezVousDAO.trouverParId(id).orElseThrow().getDateHeure());
    }

    @Test
    void modifierDateHeureRefuseUnRendezVousInexistant() {
        assertDateHeureRefusee(999, DEMAIN_9H, "Aucun rendez-vous ne porte le numéro 999.");
    }

    @Test
    void modifierDateHeureRefuseUnRendezVousAnnuleOuEffectue() throws Exception {
        int annule = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();
        service.modifierStatut(annule, StatutRendezVous.ANNULE);
        int effectue = creerRendezVousEffectue(MEDECIN_2, PATIENT_2);

        assertDateHeureRefusee(annule, DEMAIN_11H,
                "Seul un rendez-vous planifié peut être déplacé : celui-ci est déjà annulé.");
        assertDateHeureRefusee(effectue, DEMAIN_11H,
                "Seul un rendez-vous planifié peut être déplacé : celui-ci est déjà effectué.");
    }

    @Test
    void modifierDateHeureRefuseLaMemeDateEtLaMemeHeure() throws Exception {
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();

        assertDateHeureRefusee(id, DEMAIN_9H, "Le rendez-vous est déjà prévu à cette date et à cette heure.");
    }

    @Test
    void modifierDateHeureRefuseUnConflitDeMedecin() throws Exception {
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);
        int id = planifier(MEDECIN_1, PATIENT_2, DEMAIN_10H).getId();

        assertDateHeureRefusee(id, DEMAIN_9H, "Ce médecin a déjà un rendez-vous le 11/01/2030 à 09:00.");
        assertEquals(DEMAIN_10H, rendezVousDAO.trouverParId(id).orElseThrow().getDateHeure());
    }

    @Test
    void modifierDateHeureRefuseUnConflitDePatient() throws Exception {
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);
        int id = planifier(MEDECIN_2, PATIENT_1, DEMAIN_10H).getId();

        assertDateHeureRefusee(id, DEMAIN_9H, "Ce patient a déjà un rendez-vous le 11/01/2030 à 09:00.");
        assertEquals(DEMAIN_10H, rendezVousDAO.trouverParId(id).orElseThrow().getDateHeure());
    }

    @Test
    void modifierDateHeureAccepteLeCreneauDUnRendezVousAnnule() throws Exception {
        int annule = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();
        service.modifierStatut(annule, StatutRendezVous.ANNULE);
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_10H).getId();

        RendezVous modifie = service.modifierDateHeure(id, DEMAIN_9H);

        assertEquals(DEMAIN_9H, modifie.getDateHeure());
    }

    // ---------- Modification du statut ----------

    @Test
    void annulerUnRendezVousPlanifieConserveLHistorique() throws Exception {
        RendezVous rendezVous = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        RendezVous annule = service.modifierStatut(rendezVous.getId(), StatutRendezVous.ANNULE);

        assertEquals(StatutRendezVous.ANNULE, annule.getStatut());
        List<RendezVousDetail> tous = service.listerTous();
        assertEquals(1, tous.size());
        assertEquals(StatutRendezVous.ANNULE, tous.get(0).getRendezVous().getStatut());
    }

    @Test
    void marquerEffectueUnRendezVousDontLHeureEstArrivee() throws Exception {
        RendezVous rendezVous = planifier(MEDECIN_1, PATIENT_1, MAINTENANT);

        RendezVous effectue = service.modifierStatut(rendezVous.getId(), StatutRendezVous.EFFECTUE);

        assertEquals(StatutRendezVous.EFFECTUE, effectue.getStatut());
        assertEquals(StatutRendezVous.EFFECTUE,
                rendezVousDAO.trouverParId(rendezVous.getId()).orElseThrow().getStatut());
    }

    @Test
    void marquerEffectueRefuseUnRendezVousFutur() throws Exception {
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();

        assertStatutRefuse(id, StatutRendezVous.EFFECTUE,
                "Un rendez-vous ne peut pas être marqué comme effectué avant sa date et son heure.");
        assertEquals(StatutRendezVous.PLANIFIE, rendezVousDAO.trouverParId(id).orElseThrow().getStatut());
    }

    @Test
    void modifierStatutRefuseLeMemeStatut() throws Exception {
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();

        assertStatutRefuse(id, StatutRendezVous.PLANIFIE, "Le rendez-vous est déjà planifié.");
    }

    @Test
    void modifierStatutRefuseUnRendezVousAnnuleOuEffectue() throws Exception {
        int annule = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();
        service.modifierStatut(annule, StatutRendezVous.ANNULE);
        int effectue = creerRendezVousEffectue(MEDECIN_2, PATIENT_2);

        assertStatutRefuse(annule, StatutRendezVous.PLANIFIE, "Un rendez-vous annulé ne peut plus changer de statut.");
        assertStatutRefuse(annule, StatutRendezVous.EFFECTUE, "Un rendez-vous annulé ne peut plus changer de statut.");
        assertStatutRefuse(effectue, StatutRendezVous.ANNULE, "Un rendez-vous effectué ne peut plus changer de statut.");
        assertStatutRefuse(effectue, StatutRendezVous.PLANIFIE, "Un rendez-vous effectué ne peut plus changer de statut.");
    }

    @Test
    void modifierStatutRefuseUnRendezVousInexistant() {
        assertStatutRefuse(999, StatutRendezVous.ANNULE, "Aucun rendez-vous ne porte le numéro 999.");
    }

    @Test
    void modifierStatutRefuseUnStatutAbsent() throws Exception {
        int id = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();

        assertStatutRefuse(id, null, "Le statut est obligatoire.");
    }

    // ---------- Lecture ----------

    @Test
    void listerTousTrieParDateEtJointMedecinEtPatient() throws Exception {
        planifier(MEDECIN_2, PATIENT_2, DEMAIN_11H);
        planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H);

        List<RendezVousDetail> tous = service.listerTous();

        assertEquals(2, tous.size());
        assertEquals(DEMAIN_9H, tous.get(0).getRendezVous().getDateHeure());
        assertEquals("Benali", tous.get(0).getMedecin().getNom());
        assertEquals("Ben Salah", tous.get(0).getPatient().getNom());
        assertEquals(DEMAIN_11H, tous.get(1).getRendezVous().getDateHeure());
        assertEquals("Trabelsi", tous.get(1).getMedecin().getNom());
        assertEquals("Zouari", tous.get(1).getPatient().getNom());
    }

    @Test
    void listerAVenirExclutLesRendezVousAnnulesEffectuesEtPasses() throws Exception {
        // Rendez-vous planifié dans le passé : inséré directement, le service refuserait de le créer.
        rendezVousDAO.ajouter(new RendezVous(MEDECIN_2, PATIENT_2, MAINTENANT.minusDays(1)));
        int effectue = creerRendezVousEffectue(MEDECIN_2, PATIENT_2);
        int annule = planifier(MEDECIN_1, PATIENT_1, DEMAIN_9H).getId();
        service.modifierStatut(annule, StatutRendezVous.ANNULE);
        int plusTard = planifier(MEDECIN_1, PATIENT_1, DEMAIN_11H).getId();
        int plusTot = planifier(MEDECIN_1, PATIENT_1, DEMAIN_10H).getId();

        List<Integer> identifiants = service.listerAVenir().stream()
                .map(detail -> detail.getRendezVous().getId()).toList();

        assertEquals(List.of(plusTot, plusTard), identifiants);
        assertFalse(identifiants.contains(effectue));
        assertEquals(5, service.listerTous().size());
    }

    @Test
    void trouverParIdRetourneLeRendezVousAvecSesDetails() throws Exception {
        RendezVous rendezVous = planifier(MEDECIN_2, PATIENT_2, DEMAIN_9H);

        RendezVousDetail detail = service.trouverParId(rendezVous.getId()).orElseThrow();

        assertEquals("Trabelsi", detail.getMedecin().getNom());
        assertEquals("Zouari", detail.getPatient().getNom());
        assertTrue(service.trouverParId(999).isEmpty());
    }

    // ---------- Erreur technique ----------

    @Test
    void erreurTechniqueEstTraduiteEnMessageSimple() {
        // Base créée sans les tables : toute requête échoue côté SQLite.
        DatabaseConnection baseSansTables =
                new DatabaseConnection(dossierTemporaire.resolve("sans_tables.db"));
        RendezVousService serviceCasse = new RendezVousService(new RendezVousDAO(baseSansTables),
                new MedecinDAO(baseSansTables), new PatientDAO(baseSansTables), horlogeFixee());

        ServiceException erreur = assertThrows(ServiceException.class, () -> serviceCasse.listerTous());

        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, erreur.getMessage());
        assertFalse(erreur.getMessage().toLowerCase().contains("sqlite"));
        assertNotNull(erreur.getCause());
    }

    // ---------- Méthodes utilitaires ----------

    private static Clock horlogeFixee() {
        ZoneId fuseau = ZoneId.systemDefault();
        return Clock.fixed(MAINTENANT.atZone(fuseau).toInstant(), fuseau);
    }

    private RendezVous planifier(String matricule, String telephone, LocalDateTime dateHeure)
            throws ServiceException {
        return service.planifier(new RendezVous(matricule, telephone, dateHeure));
    }

    /** Crée un rendez-vous à l'heure actuelle (autorisé) et le marque comme effectué. */
    private int creerRendezVousEffectue(String matricule, String telephone) throws ServiceException {
        RendezVous rendezVous = planifier(matricule, telephone, MAINTENANT);
        service.modifierStatut(rendezVous.getId(), StatutRendezVous.EFFECTUE);
        return rendezVous.getId();
    }

    private void assertPlanificationRefusee(RendezVous saisie, String debutMessage) {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.planifier(saisie));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
    }

    private void assertDateHeureRefusee(int id, LocalDateTime dateHeure, String debutMessage) {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifierDateHeure(id, dateHeure));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
    }

    private void assertStatutRefuse(int id, StatutRendezVous statut, String debutMessage) {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifierStatut(id, statut));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
    }
}