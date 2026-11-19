// src/test/java/com/cabinetmedical/util/FormatUtilRendezVousTest.java
package com.cabinetmedical.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class FormatUtilRendezVousTest {

    private static final LocalDateTime DATE_HEURE = LocalDateTime.of(2030, 1, 5, 9, 5);

    @Test
    void formaterHeure_ajouteLesZerosDeDebut() {
        assertEquals("09:05", FormatUtil.formaterHeure(LocalTime.of(9, 5)));
    }

    @Test
    void formaterHeure_nullDonneTexteVide() {
        assertEquals("", FormatUtil.formaterHeure(null));
    }

    @Test
    void formaterDateHeure_utiliseLeFormatFrancais() {
        assertEquals("05/01/2030 09:05", FormatUtil.formaterDateHeure(DATE_HEURE));
    }

    @Test
    void formaterDateHeure_nullDonneTexteVide() {
        assertEquals("", FormatUtil.formaterDateHeure(null));
    }

    @Test
    void formaterCreneau_ecritUneDescriptionEnPhrase() {
        assertEquals("le 05/01/2030 à 09:05", FormatUtil.formaterCreneau(DATE_HEURE));
        assertEquals("", FormatUtil.formaterCreneau(null));
    }
}