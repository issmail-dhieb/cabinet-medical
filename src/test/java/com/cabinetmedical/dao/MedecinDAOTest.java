// src/test/java/com/cabinetmedical/dao/MedecinDAOTest.java
package com.cabinetmedical.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de MedecinDAO sur une base TEMPORAIRE (jamais cabinet_medical.db).
 * Le DAO ne valide rien : la validation est testée dans MedecinServiceTest.
 */
class MedecinDAOTest {

    @TempDir
    Path dossierTemporaire;

    private DatabaseConnection connexion;
    private MedecinDAO dao;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        dao = new MedecinDAO(connexion);
    }

    // ---------- Ajout, lecture, modification, suppression ----------

    @Test
    void ajouterPuisTrouverParMatricule() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));

        Optional<Medecin> trouve = dao.trouverParMatricule("M001");

        assertTrue(trouve.isPresent());
        assertEquals("Benali", trouve.get().getNom());
        assertEquals("Sami", trouve.get().getPrenom());
        assertEquals("Cardiologie", trouve.get().getSpecialite());
        assertEquals("71000000", trouve.get().getTelephone());
    }

    @Test
    void trouverParMatriculeInexistantRetourneVide() throws SQLException {
        assertTrue(dao.trouverParMatricule("M999").isEmpty());
    }

    @Test
    void ajouterMatriculeEnDoubleLeveUneException() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));

        assertThrows(SQLException.class,
                () -> dao.ajouter(medecin("M001", "Trabelsi", "Karim", "Pédiatrie")));
        assertEquals(1, dao.listerTous().size());
    }

    @Test
    void modifierMetAJourLesChampsSansToucherAuMatricule() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));

        boolean modifie = dao.modifier(new Medecin("M001", "Trabelsi", "Karim", "Pédiatrie", "72000000"));

        assertTrue(modifie);
        Medecin relu = dao.trouverParMatricule("M001").orElseThrow();
        assertEquals("M001", relu.getMatricule());
        assertEquals("Trabelsi", relu.getNom());
        assertEquals("Karim", relu.getPrenom());
        assertEquals("Pédiatrie", relu.getSpecialite());
        assertEquals("72000000", relu.getTelephone());
    }

    @Test
    void modifierMedecinInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.modifier(medecin("M999", "Benali", "Sami", "Cardiologie")));
    }

    @Test
    void supprimerMedecinSansRendezVous() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));

        assertTrue(dao.supprimer("M001"));
        assertTrue(dao.trouverParMatricule("M001").isEmpty());
    }

    @Test
    void supprimerMedecinInexistantRetourneFaux() throws SQLException {
        assertFalse(dao.supprimer("M999"));
    }

    @Test
    void supprimerMedecinAvecRendezVousEstBloqueParLaBase() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));
        BaseTemporaire.insererRendezVous(connexion, "M001", "2030-01-15T09:00", "ANNULE");

        assertThrows(SQLException.class, () -> dao.supprimer("M001"));
        assertTrue(dao.trouverParMatricule("M001").isPresent());
    }

    @Test
    void compterRendezVousInclutLesRendezVousAnnules() throws SQLException {
        dao.ajouter(medecin("M001", "Benali", "Sami", "Cardiologie"));
        dao.ajouter(medecin("M002", "Trabelsi", "Karim", "Pédiatrie"));
        assertEquals(0, dao.compterRendezVous("M001"));

        BaseTemporaire.insererRendezVous(connexion, "M001", "2030-01-15T09:00", "ANNULE");
        BaseTemporaire.insererRendezVous(connexion, "M001", "2030-01-15T09:15", "PLANIFIE");

        assertEquals(2, dao.compterRendezVous("M001"));
        assertEquals(0, dao.compterRendezVous("M002"));
    }

    // ---------- Liste et tri ----------

    @Test
    void listerTousTrieParNomPuisPrenomEnFrancais() throws SQLException {
        dao.ajouter(medecin("M001", "Zouari", "Sami", "Cardiologie"));
        dao.ajouter(medecin("M002", "Ben Ali", "Leila", "Pédiatrie"));
        dao.ajouter(medecin("M003", "Éloi", "Karim", "Dermatologie"));

        List<String> noms = dao.listerTous().stream().map(Medecin::getNom).toList();

        assertEquals(List.of("Ben Ali", "Éloi", "Zouari"), noms);
    }

    // ---------- Recherches ----------

    @Test
    void chercherParNomIgnoreCasseEtAccents() throws SQLException {
        ajouterDeuxMedecins();

        assertEquals(List.of("M001"), matricules(dao.chercherParNom("eloise")));
        assertEquals(List.of("M001"), matricules(dao.chercherParNom("ÉLOÏSE")));
        assertEquals(List.of("M001"), matricules(dao.chercherParNom("BEN SAL")));
        assertEquals(List.of("M002"), matricules(dao.chercherParNom("trab")));
    }

    @Test
    void chercherParNomAccepteLeNomEtLePrenomDansLesDeuxOrdres() throws SQLException {
        ajouterDeuxMedecins();

        assertEquals(List.of("M001"), matricules(dao.chercherParNom("salah éloïse")));
        assertEquals(List.of("M001"), matricules(dao.chercherParNom("éloïse ben")));
    }

    @Test
    void chercherParNomSansResultatRetourneListeVide() throws SQLException {
        ajouterDeuxMedecins();

        assertTrue(dao.chercherParNom("xyz").isEmpty());
    }

    @Test
    void chercherParSpecialiteIgnoreCasseEtAccents() throws SQLException {
        ajouterDeuxMedecins();

        assertEquals(List.of("M002"), matricules(dao.chercherParSpecialite("medecine gen")));
        assertEquals(List.of("M001"), matricules(dao.chercherParSpecialite("PEDIATRIE")));
    }

    @Test
    void chercherParMatriculeEstPartiel() throws SQLException {
        ajouterDeuxMedecins();

        assertEquals(List.of("M001", "M002"), matricules(dao.chercherParMatricule("m0")));
        assertEquals(List.of("M002"), matricules(dao.chercherParMatricule("002")));
    }

    @Test
    void rechercheVideRetourneTousLesMedecins() throws SQLException {
        ajouterDeuxMedecins();

        assertEquals(2, dao.chercherParNom("").size());
        assertEquals(2, dao.chercherParSpecialite("   ").size());
        assertEquals(2, dao.chercherParMatricule(null).size());
    }

    // ---------- Méthodes utilitaires ----------

    private void ajouterDeuxMedecins() throws SQLException {
        dao.ajouter(medecin("M001", "Ben Salah", "Éloïse", "Pédiatrie"));
        dao.ajouter(medecin("M002", "Trabelsi", "Karim", "Médecine générale"));
    }

    private static Medecin medecin(String matricule, String nom, String prenom, String specialite) {
        return new Medecin(matricule, nom, prenom, specialite, "71000000");
    }

    private static List<String> matricules(List<Medecin> medecins) {
        return medecins.stream().map(Medecin::getMatricule).toList();
    }
}