// src/main/java/com/cabinetmedical/dao/UtilisateurDAO.java
package com.cabinetmedical.dao;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Utilisateur;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Accès à la table utilisateurs. Cette classe contient uniquement les opérations
 * sur la base de données : la validation, le hachage des mots de passe et la
 * vérification des identifiants sont faits par AuthService.
 * Le nom d'utilisateur est comparé sans tenir compte de la casse
 * (colonne déclarée COLLATE NOCASE dans la table).
 */
public class UtilisateurDAO {

    private static final String SELECTION =
            "SELECT id, nom_utilisateur, mot_de_passe_hash, sel, iterations FROM utilisateurs";

    private final DatabaseConnection connexion;

    public UtilisateurDAO(DatabaseConnection connexion) {
        this.connexion = connexion;
    }

    /**
     * Ajoute un utilisateur. L'identifiant du paramètre est ignoré : la base en génère un nouveau.
     * Lève une SQLException si le nom d'utilisateur existe déjà (casse ignorée).
     *
     * @return l'identifiant généré par la base
     */
    public int ajouter(Utilisateur utilisateur) throws SQLException {
        String requete = "INSERT INTO utilisateurs (nom_utilisateur, mot_de_passe_hash, sel, iterations) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, utilisateur.getNomUtilisateur());
            statement.setString(2, utilisateur.getMotDePasseHash());
            statement.setString(3, utilisateur.getSel());
            statement.setInt(4, utilisateur.getIterations());
            statement.executeUpdate();
            return lireDernierIdInsere(connection);
        }
    }

    /** Recherche exacte par nom d'utilisateur, sans tenir compte de la casse. */
    public Optional<Utilisateur> trouverParNomUtilisateur(String nomUtilisateur) throws SQLException {
        String requete = SELECTION + " WHERE nom_utilisateur = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, nomUtilisateur);
            try (ResultSet resultat = statement.executeQuery()) {
                if (resultat.next()) {
                    return Optional.of(lireUtilisateur(resultat));
                }
                return Optional.empty();
            }
        }
    }

    /** Compte les utilisateurs enregistrés (0 signifie que l'application est à son premier lancement). */
    public int compter() throws SQLException {
        String requete = "SELECT COUNT(*) FROM utilisateurs";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete);
             ResultSet resultat = statement.executeQuery()) {
            resultat.next();
            return resultat.getInt(1);
        }
    }

    private int lireDernierIdInsere(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultat = statement.executeQuery("SELECT last_insert_rowid()")) {
            resultat.next();
            return resultat.getInt(1);
        }
    }

    private Utilisateur lireUtilisateur(ResultSet resultat) throws SQLException {
        return new Utilisateur(
                resultat.getInt("id"),
                resultat.getString("nom_utilisateur"),
                resultat.getString("mot_de_passe_hash"),
                resultat.getString("sel"),
                resultat.getInt("iterations"));
    }
}