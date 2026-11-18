// src/main/java/com/cabinetmedical/database/DatabaseConnection.java
package com.cabinetmedical.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Fournit des connexions SQLite vers un fichier de base de données.
 * Le chemin du fichier est un paramètre : l'application utilise
 * cabinet_medical.db, les tests utilisent un fichier temporaire.
 */
public class DatabaseConnection {

    public static final String NOM_FICHIER_PAR_DEFAUT = "cabinet_medical.db";

    private final Path fichier;
    private final String url;

    /** Utilise le fichier cabinet_medical.db du dossier de lancement. */
    public DatabaseConnection() {
        this(Path.of(NOM_FICHIER_PAR_DEFAUT));
    }

    /** Utilise le fichier indiqué (utile pour les tests). */
    public DatabaseConnection(Path fichier) {
        Objects.requireNonNull(fichier, "Le fichier de base de données est obligatoire");
        this.fichier = fichier.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.fichier;
    }

    /**
     * Ouvre une nouvelle connexion avec les clés étrangères activées.
     * L'appelant doit la fermer (try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    public Path getFichier() {
        return fichier;
    }
}