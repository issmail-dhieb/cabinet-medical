// src/test/java/com/cabinetmedical/util/FormatUtilTest.java
package com.cabinetmedical.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FormatUtilTest {

    @Test
    void formaterTelephone_groupeLesHuitChiffres() {
        assertEquals("20 123 456", FormatUtil.formaterTelephone("20123456"));
    }

    @Test
    void formaterTelephone_laisseUnTexteNonStandardInchange() {
        assertEquals("1234", FormatUtil.formaterTelephone("1234"));
        assertNull(FormatUtil.formaterTelephone(null));
    }

    @Test
    void formaterDate_utiliseLeFormatFrancais() {
        assertEquals("05/03/1990", FormatUtil.formaterDate(LocalDate.of(1990, 3, 5)));
    }

    @Test
    void formaterDate_retourneUneChaineVidePourNull() {
        assertEquals("", FormatUtil.formaterDate(null));
    }

    @Test
    void lireDate_accepteLeFormatFrancais() {
        assertEquals(LocalDate.of(1990, 3, 25), FormatUtil.lireDate("25/03/1990"));
    }

    @Test
    void lireDate_accepteLesZerosFacultatifsEtIgnoreLesEspacesAutour() {
        assertEquals(LocalDate.of(1990, 3, 5), FormatUtil.lireDate("5/3/1990"));
        assertEquals(LocalDate.of(1990, 3, 25), FormatUtil.lireDate("  25/03/1990  "));
    }

    @Test
    void lireDate_accepteLe29FevrierDUneAnneeBissextile() {
        assertEquals(LocalDate.of(2000, 2, 29), FormatUtil.lireDate("29/02/2000"));
    }

    @Test
    void lireDate_refuseUneDateQuiNExistePas() {
        assertNull(FormatUtil.lireDate("31/02/1990"));
        assertNull(FormatUtil.lireDate("29/02/2023"));
        assertNull(FormatUtil.lireDate("32/01/1990"));
        assertNull(FormatUtil.lireDate("10/13/1990"));
    }

    @Test
    void lireDate_refuseUnMauvaisFormat() {
        assertNull(FormatUtil.lireDate("1990-03-25"));
        assertNull(FormatUtil.lireDate("25-03-1990"));
        assertNull(FormatUtil.lireDate("25/03/90"));
        assertNull(FormatUtil.lireDate("abc"));
    }

    @Test
    void lireDate_retourneNullPourUnTexteVideOuNull() {
        assertNull(FormatUtil.lireDate(""));
        assertNull(FormatUtil.lireDate("   "));
        assertNull(FormatUtil.lireDate(null));
    }
}