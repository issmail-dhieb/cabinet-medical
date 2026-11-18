// src/test/java/com/cabinetmedical/service/PatientServiceTest.java
package com.cabinetmedical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.dao.MedecinDAO;
import com.cabinetmedical.dao.PatientDAO;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests de PatientService sur une base TEMPORAIRE (jamais cabinet_medical.db). */
class PatientServiceTest {

    private static final LocalDate NAISSANCE = LocalDate.of(1990, 5, 12);

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private PatientService service;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        service = new PatientService(new PatientDAO(connexion));
    }

    // ---------- Ajout ----------

    @Test
    void ajouterEnregistreEtNormaliseLaSaisie() throws Exception {
        Patient enregistre = service.ajouter(
                new Patient(" +216 20 111 111 ", "  Ben   Salah ", " Éloïse ", NAISSANCE));

        assertEquals("20111111", enregistre.getNumeroTelephone());
        assertEquals("Ben Salah", enregistre.getNom());
        assertEquals("Éloïse", enregistre.getPrenom());
        Patient relu = service.trouverParNumeroTelephone("20111111").orElseThrow();
        assertEquals("Ben Salah", relu.getNom());
        assertEquals(NAISSANCE, relu.getDateNaissance());
    }

    @Test
    void ajouterRefuseUnNumeroDejaEnregistre() throws Exception {
        service.ajouter(patientValide());

        assertAjoutRefuse(new Patient("20 111 111", "Benali", "Karim", NAISSANCE),
                "Un patient avec le numéro 20111111 existe déjà.");
        assertEquals(1, service.listerTous().size());
    }

    @Test
    void ajouterRefuseUnNomVide() {
        assertAjoutRefuse(new Patient("20111111", "   ", "Sami", NAISSANCE), "Le nom est obligatoire.");
    }

    @Test
    void ajouterRefuseUnPrenomVide() {
        assertAjoutRefuse(new Patient("20111111", "Trabelsi", "", NAISSANCE), "Le prénom est obligatoire.");
    }

    @Test
    void ajouterRefuseUnNomAvecDesChiffres() {
        assertAjoutRefuse(new Patient("20111111", "Trabelsi2", "Sami", NAISSANCE),
                "Le nom ne peut contenir que des lettres");
    }

    @Test
    void ajouterRefuseUnNomTropLong() {
        assertAjoutRefuse(new Patient("20111111", "A".repeat(51), "Sami", NAISSANCE),
                "Le nom ne doit pas dépasser 50 caractères.");
    }

    @Test
    void ajouterRefuseUnTelephoneVide() {
        assertAjoutRefuse(new Patient("  ", "Trabelsi", "Sami", NAISSANCE),
                "Le numéro de téléphone est obligatoire.");
    }

    @Test
    void ajouterRefuseUnTelephoneInvalide() {
        assertAjoutRefuse(new Patient("1234", "Trabelsi", "Sami", NAISSANCE),
                "Le numéro de téléphone doit contenir 8 chiffres");
        assertAjoutRefuse(new Patient("abcdefgh", "Trabelsi", "Sami", NAISSANCE),
                "Le numéro de téléphone doit contenir 8 chiffres");
    }

    @Test
    void ajouterRefuseUnPatientNull() {
        assertAjoutRefuse(null, "Les informations du patient sont obligatoires.");
    }

    @Test
    void ajouterRefuseUneDateDeNaissanceAbsente() {
        assertAjoutRefuse(new Patient("20111111", "Trabelsi", "Sami", null),
                "La date de naissance est obligatoire.");
    }

    @Test
    void ajouterRefuseUneDateDeNaissanceFuture() {
        assertAjoutRefuse(new Patient("20111111", "Trabelsi", "Sami", LocalDate.now().plusDays(1)),
                "La date de naissance ne peut pas être dans le futur.");
    }

    @Test
    void ajouterRefuseUneDateDeNaissanceTropAncienne() {
        assertAjoutRefuse(new Patient("20111111", "Trabelsi", "Sami", LocalDate.of(1899, 12, 31)),
                "La date de naissance est trop ancienne");
    }

    @Test
    void ajouterAccepteUneDateDeNaissanceDAujourdhui() throws Exception {
        Patient enregistre = service.ajouter(new Patient("20111111", "Trabelsi", "Bébé", LocalDate.now()));

        assertEquals(LocalDate.now(), enregistre.getDateNaissance());
    }

    // ---------- Modification ----------

    @Test
    void modifierMetAJourLesChampsDuPatient() throws Exception {
        service.ajouter(patientValide());

        Patient modifie = service.modifier("20111111",
                new Patient("20111111", "Benali  ", "Karim", LocalDate.of(1985, 3, 1)));

        assertEquals("Benali", modifie.getNom());
        Patient relu = service.trouverParNumeroTelephone("20111111").orElseThrow();
        assertEquals("Benali", relu.getNom());
        assertEquals("Karim", relu.getPrenom());
        assertEquals(LocalDate.of(1985, 3, 1), relu.getDateNaissance());
    }

    @Test
    void modifierPeutChangerLeNumeroDeTelephone() throws Exception {
        service.ajouter(patientValide());

        Patient modifie = service.modifier("20111111",
                new Patient(" +216 20 999 999", "Trabelsi", "Sami", NAISSANCE));

        assertEquals("20999999", modifie.getNumeroTelephone());
        assertTrue(service.trouverParNumeroTelephone("20111111").isEmpty());
        assertTrue(service.trouverParNumeroTelephone("20999999").isPresent());
    }

    @Test
    void modifierRefuseUnNumeroDejaPris() throws Exception {
        service.ajouter(patientValide());
        service.ajouter(new Patient("21222222", "Benali", "Karim", NAISSANCE));

        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifier("20111111", new Patient("21222222", "Trabelsi", "Sami", NAISSANCE)));

        assertEquals("Un patient avec le numéro 21222222 existe déjà.", erreur.getMessage());
        assertEquals("Trabelsi", service.trouverParNumeroTelephone("20111111").orElseThrow().getNom());
        assertEquals("Benali", service.trouverParNumeroTelephone("21222222").orElseThrow().getNom());
    }

    @Test
    void modifierRefuseUnPatientInexistant() {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifier("20999999", new Patient("20999999", "Trabelsi", "Sami", NAISSANCE)));

        assertEquals("Aucun patient ne porte le numéro 20999999.", erreur.getMessage());
    }

    @Test
    void modifierRefuseLesDonneesInvalides() throws Exception {
        service.ajouter(patientValide());

        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifier("20111111", new Patient("20111111", "Trabelsi2", "Sami", NAISSANCE)));

        assertTrue(erreur.getMessage().startsWith("Le nom ne peut contenir que des lettres"),
                "Message inattendu : " + erreur.getMessage());
        assertEquals("Trabelsi", service.trouverParNumeroTelephone("20111111").orElseThrow().getNom());
    }

    // ---------- Suppression ----------

    @Test
    void supprimerUnPatientSansRendezVous() throws Exception {
        service.ajouter(patientValide());

        service.supprimer(" +216 20 111 111 ");

        assertTrue(service.trouverParNumeroTelephone("20111111").isEmpty());
    }

    @Test
    void supprimerRefuseSiDesRendezVousExistent() throws Exception {
        new MedecinDAO(connexion).ajouter(new Medecin("M001", "Benali", "Sami", "Cardiologie", "71000000"));
        service.ajouter(patientValide());

        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:00", "ANNULE");
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("20111111"));
        assertTrue(erreur.getMessage().contains(
                "Ce patient ne peut pas être supprimé : 1 rendez-vous est enregistré"),
                "Message inattendu : " + erreur.getMessage());
        assertTrue(service.trouverParNumeroTelephone("20111111").isPresent());

        BaseTemporaire.insererRendezVousPatient(connexion, "M001", "20111111", "2030-01-15T09:15", "PLANIFIE");
        ServiceException erreurDeux = assertThrows(ServiceException.class, () -> service.supprimer("20111111"));
        assertTrue(erreurDeux.getMessage().contains("2 rendez-vous sont enregistrés"),
                "Message inattendu : " + erreurDeux.getMessage());
        assertTrue(service.trouverParNumeroTelephone("20111111").isPresent());
    }

    @Test
    void supprimerRefuseUnPatientInexistant() {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("20999999"));

        assertEquals("Aucun patient ne porte le numéro 20999999.", erreur.getMessage());
    }

    @Test
    void supprimerRefuseUnNumeroVide() {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("  "));

        assertEquals("Le numéro de téléphone est obligatoire.", erreur.getMessage());
    }

    // ---------- Lecture et recherche ----------

    @Test
    void trouverParNumeroNormaliseLaSaisie() throws Exception {
        service.ajouter(patientValide());

        assertTrue(service.trouverParNumeroTelephone("  +216 20 111 111 ").isPresent());
        assertTrue(service.trouverParNumeroTelephone("20999999").isEmpty());
        assertTrue(service.trouverParNumeroTelephone("   ").isEmpty());
        assertTrue(service.trouverParNumeroTelephone("abc").isEmpty());
        assertTrue(service.trouverParNumeroTelephone(null).isEmpty());
    }

    @Test
    void chercherParNomPrenomEtNumero() throws Exception {
        ajouterTroisPatients();

        assertEquals(List.of("20111111"), numeros(service.chercherParNom("ben sal")));
        assertEquals(List.of("21222222"), numeros(service.chercherParPrenom("KARIM")));
        assertEquals(List.of("55123456"), numeros(service.chercherParNumeroTelephone("+216 55 123")));
        assertTrue(service.chercherParNom("xyz").isEmpty());
    }

    @Test
    void texteDeRechercheVideRetourneTousLesPatientsTries() throws Exception {
        ajouterTroisPatients();
        List<String> attendu = List.of("20111111", "21222222", "55123456");

        assertEquals(attendu, numeros(service.chercherParNom("")));
        assertEquals(attendu, numeros(service.chercherParPrenom(null)));
        assertEquals(attendu, numeros(service.chercherParNumeroTelephone("  ")));
        assertEquals(attendu, numeros(service.listerTous()));
    }

    // ---------- Erreur technique ----------

    @Test
    void erreurTechniqueEstTraduiteEnMessageSimple() {
        // Base créée sans les tables : toute requête échoue côté SQLite.
        DatabaseConnection baseSansTables =
                new DatabaseConnection(dossierTemporaire.resolve("sans_tables.db"));
        PatientService serviceCasse = new PatientService(new PatientDAO(baseSansTables));

        ServiceException erreur = assertThrows(ServiceException.class, () -> serviceCasse.listerTous());

        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, erreur.getMessage());
        assertFalse(erreur.getMessage().toLowerCase().contains("sqlite"));
        assertNotNull(erreur.getCause());
    }

    // ---------- Méthodes utilitaires ----------

    private void assertAjoutRefuse(Patient patient, String debutMessage) {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.ajouter(patient));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
    }

    private void ajouterTroisPatients() throws Exception {
        service.ajouter(new Patient("20111111", "Ben Salah", "Éloïse", NAISSANCE));
        service.ajouter(new Patient("21222222", "Trabelsi", "Karim", NAISSANCE));
        service.ajouter(new Patient("55123456", "Zouari", "Leila", NAISSANCE));
    }

    private static Patient patientValide() {
        return new Patient("20111111", "Trabelsi", "Sami", NAISSANCE);
    }

    private static List<String> numeros(List<Patient> patients) {
        return patients.stream().map(Patient::getNumeroTelephone).toList();
    }
}