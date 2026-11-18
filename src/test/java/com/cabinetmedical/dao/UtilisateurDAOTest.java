// src/test/java/com/cabinetmedical/dao/UtilisateurDAOTest.java
package com.cabinetmedical.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.model.Utilisateur;

import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests de UtilisateurDAO sur une base TEMPORAIRE (jamais cabinet_medical.db). */
class UtilisateurDAOTest {

    @TempDir
    Path dossierTemporaire;

    private UtilisateurDAO dao;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        dao = new UtilisateurDAO(BaseTemporaire.creerBaseTemporaire(dossierTemporaire));
    }

    @Test
    void ajouterEnregistreLUtilisateurTelQuelEtRetourneSonIdentifiant() throws Exception {
        int id = dao.ajouter(new Utilisateur("admin", "HASH_EN_BASE64", "SEL_EN_BASE64", 210000));

        assertTrue(id > 0);
        Utilisateur relu = dao.trouverParNomUtilisateur("admin").orElseThrow();
        assertEquals(id, relu.getId());
        assertEquals("admin", relu.getNomUtilisateur());
        assertEquals("HASH_EN_BASE64", relu.getMotDePasseHash());
        assertEquals("SEL_EN_BASE64", relu.getSel());
        assertEquals(210000, relu.getIterations());
    }

    @Test
    void ajouterGenereUnIdentifiantDifferentPourChaqueUtilisateur() throws Exception {
        int premier = dao.ajouter(new Utilisateur("admin", "h1", "s1", 1000));
        int second = dao.ajouter(new Utilisateur("docteur", "h2", "s2", 1000));

        assertTrue(premier != second);
    }

    @Test
    void trouverParNomUtilisateurIgnoreLaCasse() throws Exception {
        dao.ajouter(new Utilisateur("Admin", "h", "s", 1000));

        Utilisateur trouve = dao.trouverParNomUtilisateur("ADMIN").orElseThrow();

        assertEquals("Admin", trouve.getNomUtilisateur());
        assertTrue(dao.trouverParNomUtilisateur("admin").isPresent());
    }

    @Test
    void trouverParNomUtilisateurRetourneVideSiLeNomEstInconnu() throws Exception {
        dao.ajouter(new Utilisateur("admin", "h", "s", 1000));

        assertTrue(dao.trouverParNomUtilisateur("inconnu").isEmpty());
    }

    @Test
    void ajouterRefuseUnNomDejaUtiliseMemeAvecUneAutreCasse() throws Exception {
        dao.ajouter(new Utilisateur("admin", "h1", "s1", 1000));

        assertThrows(SQLException.class, () -> dao.ajouter(new Utilisateur("ADMIN", "h2", "s2", 1000)));
        assertEquals(1, dao.compter());
    }

    @Test
    void compterRetourneLeNombreDUtilisateurs() throws Exception {
        assertEquals(0, dao.compter());

        dao.ajouter(new Utilisateur("admin", "h1", "s1", 1000));
        assertEquals(1, dao.compter());

        dao.ajouter(new Utilisateur("docteur", "h2", "s2", 1000));
        assertEquals(2, dao.compter());
    }
}