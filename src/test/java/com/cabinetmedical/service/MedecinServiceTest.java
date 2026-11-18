// src/test/java/com/cabinetmedical/service/MedecinServiceTest.java
package com.cabinetmedical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.dao.MedecinDAO;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de MedecinService (règles métier et validation) sur une base TEMPORAIRE.
 * Aucun test n'utilise JavaFX ni le fichier cabinet_medical.db.
 */
class MedecinServiceTest {

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private MedecinService service;

    @BeforeEach
    void preparerBaseTemporaire() throws Exception {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        service = new MedecinService(new MedecinDAO(connexion));
    }

    // ---------- Ajout ----------

    @Test
    void ajouterNettoieEtEnregistreLeMedecin() throws Exception {
        Medecin enregistre = service.ajouter(
                new Medecin("  m001 ", "  Ben   Ali ", " Sami ", "  Médecine   générale ", "+216 71 000 000"));

        assertEquals("M001", enregistre.getMatricule());
        assertEquals("Ben Ali", enregistre.getNom());
        assertEquals("Sami", enregistre.getPrenom());
        assertEquals("Médecine générale", enregistre.getSpecialite());
        assertEquals("71000000", enregistre.getTelephone());

        Medecin relu = service.trouverParMatricule("m001").orElseThrow();
        assertEquals("Ben Ali", relu.getNom());
        assertEquals("71000000", relu.getTelephone());
    }

    @Test
    void ajouterRefuseUnMedecinNull() throws Exception {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.ajouter(null));
        assertEquals("Les informations du médecin sont obligatoires.", erreur.getMessage());
    }

    @Test
    void ajouterRefuseUnMatriculeVide() throws Exception {
        assertAjoutRefuse(new Medecin("   ", "Benali", "Sami", "Cardiologie", "71000000"),
                "Le matricule est obligatoire.");
        assertAjoutRefuse(new Medecin(null, "Benali", "Sami", "Cardiologie", "71000000"),
                "Le matricule est obligatoire.");
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnMatriculeInvalide() throws Exception {
        String[] matriculesInvalides = {"M 001", "M@01", "É01", "A".repeat(21)};
        for (String matricule : matriculesInvalides) {
            assertAjoutRefuse(new Medecin(matricule, "Benali", "Sami", "Cardiologie", "71000000"),
                    "Le matricule ne peut contenir que");
        }
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnNomvideOuInvalide() throws Exception {
        assertAjoutRefuse(new Medecin("M001", "  ", "Sami", "Cardiologie", "71000000"),
                "Le nom est obligatoire.");
        assertAjoutRefuse(new Medecin("M001", "Benali2", "Sami", "Cardiologie", "71000000"),
                "Le nom ne peut contenir que");
        assertAjoutRefuse(new Medecin("M001", "A".repeat(51), "Sami", "Cardiologie", "71000000"),
                "Le nom ne doit pas dépasser 50 caractères.");
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnPrenomVideOuInvalide() throws Exception {
        assertAjoutRefuse(new Medecin("M001", "Benali", "", "Cardiologie", "71000000"),
                "Le prénom est obligatoire.");
        assertAjoutRefuse(new Medecin("M001", "Benali", "Sami!", "Cardiologie", "71000000"),
                "Le prénom ne peut contenir que");
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUneSpecialiteVideOuInvalide() throws Exception {
        assertAjoutRefuse(new Medecin("M001", "Benali", "Sami", " ", "71000000"),
                "La spécialité est obligatoire.");
        assertAjoutRefuse(new Medecin("M001", "Benali", "Sami", "Cardio2", "71000000"),
                "La spécialité ne peut contenir que");
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnTelephoneVide() throws Exception {
        assertAjoutRefuse(new Medecin("M001", "Benali", "Sami", "Cardiologie", "   "),
                "Le numéro de téléphone est obligatoire.");
        assertAjoutRefuse(new Medecin("M001", "Benali", "Sami", "Cardiologie", null),
                "Le numéro de téléphone est obligatoire.");
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnTelephoneInvalide() throws Exception {
        String[] telephonesInvalides = {"1234567", "123456789", "abcdefgh", "+33 12345678", "71-000-000"};
        for (String telephone : telephonesInvalides) {
            assertAjoutRefuse(new Medecin("M001", "Benali", "Sami", "Cardiologie", telephone),
                    "Le numéro de téléphone doit contenir 8 chiffres");
        }
        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void ajouterRefuseUnMatriculeDejaUtiliseMemeAvecAutreCasse() throws Exception {
        service.ajouter(medecinValide());

        ServiceException erreur = assertThrows(ServiceException.class, () -> service.ajouter(
                new Medecin("m001", "Trabelsi", "Karim", "Pédiatrie", "72000000")));

        assertEquals("Un médecin avec le matricule M001 existe déjà.", erreur.getMessage());
        assertEquals(1, service.listerTous().size());
        assertEquals("Benali", service.trouverParMatricule("M001").orElseThrow().getNom());
    }

    // ---------- Modification ----------

    @Test
    void modifierMetAJourLesInformations() throws Exception {
        service.ajouter(medecinValide());

        Medecin modifie = service.modifier(
                new Medecin("m001", "Trabelsi", "Karim", "Pédiatrie", "72 000 000"));

        assertEquals("M001", modifie.getMatricule());
        Medecin relu = service.trouverParMatricule("M001").orElseThrow();
        assertEquals("Trabelsi", relu.getNom());
        assertEquals("Karim", relu.getPrenom());
        assertEquals("Pédiatrie", relu.getSpecialite());
        assertEquals("72000000", relu.getTelephone());
    }

    @Test
    void modifierRefuseUnMedecinInexistant() throws Exception {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifier(new Medecin("M999", "Benali", "Sami", "Cardiologie", "71000000")));

        assertEquals("Aucun médecin ne porte le matricule M999.", erreur.getMessage());
    }

    @Test
    void modifierRefuseLesDonneesInvalidesSansRienChanger() throws Exception {
        service.ajouter(medecinValide());

        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.modifier(new Medecin("M001", "Trabelsi", "Karim", "Pédiatrie", "abc")));

        assertTrue(erreur.getMessage().startsWith("Le numéro de téléphone doit contenir 8 chiffres"));
        Medecin relu = service.trouverParMatricule("M001").orElseThrow();
        assertEquals("Benali", relu.getNom());
        assertEquals("71000000", relu.getTelephone());
    }

    // ---------- Suppression ----------

    @Test
    void supprimerUnMedecinSansRendezVous() throws Exception {
        service.ajouter(medecinValide());

        service.supprimer(" m001 ");

        assertTrue(service.listerTous().isEmpty());
    }

    @Test
    void supprimerRefuseSiHistoriqueDeRendezVousMemeAnnule() throws Exception {
        service.ajouter(medecinValide());
        BaseTemporaire.insererRendezVous(connexion, "M001", "2030-01-15T09:00", "ANNULE");

        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("M001"));

        assertTrue(erreur.getMessage().startsWith(
                "Ce médecin ne peut pas être supprimé : 1 rendez-vous est enregistré"),
                "Message inattendu : " + erreur.getMessage());
        assertTrue(service.trouverParMatricule("M001").isPresent());

        BaseTemporaire.insererRendezVous(connexion, "M001", "2030-01-15T09:15", "PLANIFIE");
        ServiceException erreurDeux = assertThrows(ServiceException.class, () -> service.supprimer("M001"));
        assertTrue(erreurDeux.getMessage().contains("2 rendez-vous sont enregistrés"),
                "Message inattendu : " + erreurDeux.getMessage());
        assertTrue(service.trouverParMatricule("M001").isPresent());
    }

    @Test
    void supprimerRefuseUnMedecinInexistant() throws Exception {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("M999"));

        assertEquals("Aucun médecin ne porte le matricule M999.", erreur.getMessage());
    }

    @Test
    void supprimerRefuseUnMatriculeVide() throws Exception {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.supprimer("  "));

        assertEquals("Le matricule est obligatoire.", erreur.getMessage());
    }

    // ---------- Lecture et recherche ----------

    @Test
    void trouverParMatriculeNormaliseLaSaisie() throws Exception {
        service.ajouter(medecinValide());

        assertTrue(service.trouverParMatricule("  m001 ").isPresent());
        assertTrue(service.trouverParMatricule("M999").isEmpty());
        assertTrue(service.trouverParMatricule("   ").isEmpty());
        assertTrue(service.trouverParMatricule(null).isEmpty());
    }

    @Test
    void chercherParNomSpecialiteEtMatricule() throws Exception {
        ajouterTroisMedecins();

        assertEquals(List.of("M001"), matricules(service.chercherParNom("eloise")));
        assertEquals(List.of("M002"), matricules(service.chercherParSpecialite("MEDECINE GEN")));
        assertEquals(List.of("M003"), matricules(service.chercherParMatricule("m003")));
        assertTrue(service.chercherParNom("xyz").isEmpty());
    }

    @Test
    void texteDeRechercheVideRetourneTousLesMedecinsTries() throws Exception {
        ajouterTroisMedecins();

        assertEquals(List.of("M001", "M002", "M003"), matricules(service.chercherParNom("")));
        assertEquals(List.of("M001", "M002", "M003"), matricules(service.chercherParSpecialite(null)));
        assertEquals(List.of("M001", "M002", "M003"), matricules(service.listerTous()));
    }

    // ---------- Erreur technique ----------

    @Test
    void erreurTechniqueEstTraduiteEnMessageSimple() {
        // Base créée sans les tables : toute requête échoue côté SQLite.
        DatabaseConnection baseSansTables =
                new DatabaseConnection(dossierTemporaire.resolve("sans_tables.db"));
        MedecinService serviceCasse = new MedecinService(new MedecinDAO(baseSansTables));

        ServiceException erreur = assertThrows(ServiceException.class, () -> serviceCasse.listerTous());

        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, erreur.getMessage());
        assertFalse(erreur.getMessage().toLowerCase().contains("sqlite"));
        assertNotNull(erreur.getCause());
    }

    // ---------- Méthodes utilitaires ----------

    private void assertAjoutRefuse(Medecin medecin, String debutMessage) {
        ServiceException erreur = assertThrows(ServiceException.class, () -> service.ajouter(medecin));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
    }

    private void ajouterTroisMedecins() throws Exception {
        service.ajouter(new Medecin("M001", "Ben Salah", "Éloïse", "Pédiatrie", "71000000"));
        service.ajouter(new Medecin("M002", "Trabelsi", "Karim", "Médecine générale", "72000000"));
        service.ajouter(new Medecin("M003", "Zouari", "Leila", "Cardiologie", "73000000"));
    }

    private static Medecin medecinValide() {
        return new Medecin("M001", "Benali", "Sami", "Cardiologie", "71000000");
    }

    private static List<String> matricules(List<Medecin> medecins) {
        return medecins.stream().map(Medecin::getMatricule).toList();
    }
}