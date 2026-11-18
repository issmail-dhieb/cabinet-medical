// src/test/java/com/cabinetmedical/util/ValidationUtilTest.java
package com.cabinetmedical.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests des outils de validation (aucune base de données ici). */
class ValidationUtilTest {

    @Test
    void nettoyerTexteSupprimeLesEspacesEnTrop() {
        assertEquals("Ben Salah", ValidationUtil.nettoyerTexte("  Ben    Salah "));
        assertEquals("", ValidationUtil.nettoyerTexte("   "));
        assertEquals("", ValidationUtil.nettoyerTexte(null));
    }

    @Test
    void normaliserMatriculeMetEnMajusculesEtSupprimeLesEspacesExterieurs() {
        assertEquals("M001", ValidationUtil.normaliserMatricule("  m001 "));
        assertEquals("", ValidationUtil.normaliserMatricule(null));
    }

    @Test
    void estMatriculeValideAccepteLettresChiffresTiretsEtRefuseLeReste() {
        assertTrue(ValidationUtil.estMatriculeValide("M001"));
        assertTrue(ValidationUtil.estMatriculeValide("DR-12_A"));
        assertTrue(ValidationUtil.estMatriculeValide("A".repeat(20)));
        assertFalse(ValidationUtil.estMatriculeValide("A".repeat(21)));
        assertFalse(ValidationUtil.estMatriculeValide(""));
        assertFalse(ValidationUtil.estMatriculeValide("M 001"));
        assertFalse(ValidationUtil.estMatriculeValide("M@01"));
        assertFalse(ValidationUtil.estMatriculeValide("É01"));
        assertFalse(ValidationUtil.estMatriculeValide(null));
    }

    @Test
    void estNomValideAccepteLettresEspacesTiretsEtApostrophes() {
        assertTrue(ValidationUtil.estNomValide("Ben Ali"));
        assertTrue(ValidationUtil.estNomValide("Jean-Pierre"));
        assertTrue(ValidationUtil.estNomValide("O'Brien"));
        assertTrue(ValidationUtil.estNomValide("D’Arcy"));
        assertTrue(ValidationUtil.estNomValide("Éloïse"));
        assertFalse(ValidationUtil.estNomValide("Benali2"));
        assertFalse(ValidationUtil.estNomValide("-Ben"));
        assertFalse(ValidationUtil.estNomValide("Ben."));
        assertFalse(ValidationUtil.estNomValide(""));
        assertFalse(ValidationUtil.estNomValide(null));
    }

    @Test
    void normaliserTelephoneAccepteLesFormatsValides() {
        String[] formatsValides = {"71000000", "71 000 000", "+21671000000", "+216 71 000 000", "  71000000  "};
        for (String format : formatsValides) {
            assertEquals("71000000", ValidationUtil.normaliserTelephone(format),
                    "Format testé : '" + format + "'");
        }
    }

    @Test
    void normaliserTelephoneRefuseLesFormatsInvalides() {
        String[] formatsInvalides = {"", "   ", "1234567", "123456789", "abcdefgh",
                "+33 12345678", "+216 1234567", "71-000-000"};
        for (String format : formatsInvalides) {
            assertNull(ValidationUtil.normaliserTelephone(format), "Format testé : '" + format + "'");
        }
        assertNull(ValidationUtil.normaliserTelephone(null));
    }

    @Test
    void normaliserPourRechercheEnleveAccentsCasseEtEspaces() {
        assertEquals("eloise ben", ValidationUtil.normaliserPourRecherche("  ÉLOÏSE   Ben "));
        assertEquals("medecine generale", ValidationUtil.normaliserPourRecherche("Médecine Générale"));
        assertEquals("", ValidationUtil.normaliserPourRecherche(null));
    }
}