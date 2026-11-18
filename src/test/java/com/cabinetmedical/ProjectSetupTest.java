// src/test/java/com/cabinetmedical/ProjectSetupTest.java
package com.cabinetmedical;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Vérifie uniquement la configuration du projet (pas de base de données ici).
 */
class ProjectSetupTest {

    @Test
    void javaVersionEstAuMoins21() {
        assertTrue(Runtime.version().feature() >= 21,
                "Java 21 ou plus est requis");
    }

    @Test
    void piloteSqliteEstDisponibleDansLeClasspath() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }
}