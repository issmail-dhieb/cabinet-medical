// src/main/java/com/cabinetmedical/dao/MedecinDAO.java
package com.cabinetmedical.dao;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Accès à la table medecins. Cette classe contient uniquement les opérations
 * sur la base de données : la validation est faite par MedecinService.
 * Les recherches sont faites en Java pour ignorer la casse ET les accents
 * (le LIKE de SQLite ignore seulement la casse des lettres sans accent).
 */
public class MedecinDAO {

    private static final String SELECTION =
            "SELECT matricule, nom, prenom, specialite, telephone FROM medecins";

    private final DatabaseConnection connexion;

    public MedecinDAO(DatabaseConnection connexion) {
        this.connexion = connexion;
    }

    /** Ajoute un médecin. Lève une SQLException si le matricule existe déjà. */
    public void ajouter(Medecin medecin) throws SQLException {
        String requete = "INSERT INTO medecins (matricule, nom, prenom, specialite, telephone) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, medecin.getMatricule());
            statement.setString(2, medecin.getNom());
            statement.setString(3, medecin.getPrenom());
            statement.setString(4, medecin.getSpecialite());
            statement.setString(5, medecin.getTelephone());
            statement.executeUpdate();
        }
    }

    /**
     * Met à jour nom, prénom, spécialité et téléphone du médecin ayant ce matricule.
     * Le matricule lui-même n'est jamais modifié.
     *
     * @return true si un médecin a été modifié, false si le matricule n'existe pas
     */
    public boolean modifier(Medecin medecin) throws SQLException {
        String requete = "UPDATE medecins SET nom = ?, prenom = ?, specialite = ?, telephone = ? "
                + "WHERE matricule = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, medecin.getNom());
            statement.setString(2, medecin.getPrenom());
            statement.setString(3, medecin.getSpecialite());
            statement.setString(4, medecin.getTelephone());
            statement.setString(5, medecin.getMatricule());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Supprime le médecin ayant ce matricule.
     * La base refuse la suppression (SQLException) s'il a des rendez-vous.
     *
     * @return true si un médecin a été supprimé, false si le matricule n'existe pas
     */
    public boolean supprimer(String matricule) throws SQLException {
        String requete = "DELETE FROM medecins WHERE matricule = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, matricule);
            return statement.executeUpdate() > 0;
        }
    }

    /** Recherche exacte par matricule. */
    public Optional<Medecin> trouverParMatricule(String matricule) throws SQLException {
        String requete = SELECTION + " WHERE matricule = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, matricule);
            try (ResultSet resultat = statement.executeQuery()) {
                if (resultat.next()) {
                    return Optional.of(lireMedecin(resultat));
                }
                return Optional.empty();
            }
        }
    }

    /** Retourne tous les médecins, triés par nom puis prénom (ordre alphabétique français). */
    public List<Medecin> listerTous() throws SQLException {
        List<Medecin> medecins = new ArrayList<>();
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECTION);
             ResultSet resultat = statement.executeQuery()) {
            while (resultat.next()) {
                medecins.add(lireMedecin(resultat));
            }
        }
        Collator collator = Collator.getInstance(Locale.FRENCH);
        medecins.sort(Comparator.comparing(Medecin::getNom, collator)
                .thenComparing(Medecin::getPrenom, collator)
                .thenComparing(Medecin::getMatricule));
        return medecins;
    }

    /** Recherche partielle par matricule, sans tenir compte de la casse ni des accents. */
    public List<Medecin> chercherParMatricule(String texte) throws SQLException {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        return filtrer(medecin ->
                ValidationUtil.normaliserPourRecherche(medecin.getMatricule()).contains(recherche));
    }

    /**
     * Recherche partielle par nom ou prénom (dans les deux ordres : "Benali Sami"
     * ou "Sami Benali"), sans tenir compte de la casse ni des accents.
     */
    public List<Medecin> chercherParNom(String texte) throws SQLException {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        return filtrer(medecin -> {
            String nomPrenom = ValidationUtil.normaliserPourRecherche(
                    medecin.getNom() + " " + medecin.getPrenom());
            String prenomNom = ValidationUtil.normaliserPourRecherche(
                    medecin.getPrenom() + " " + medecin.getNom());
            return nomPrenom.contains(recherche) || prenomNom.contains(recherche);
        });
    }

    /** Recherche partielle par spécialité, sans tenir compte de la casse ni des accents. */
    public List<Medecin> chercherParSpecialite(String texte) throws SQLException {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        return filtrer(medecin ->
                ValidationUtil.normaliserPourRecherche(medecin.getSpecialite()).contains(recherche));
    }

    /**
     * Compte tous les rendez-vous du médecin, quel que soit leur statut
     * (planifié, effectué ou annulé) : ils font partie de l'historique.
     */
    public int compterRendezVous(String matricule) throws SQLException {
        String requete = "SELECT COUNT(*) FROM rendez_vous WHERE matricule_medecin = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, matricule);
            try (ResultSet resultat = statement.executeQuery()) {
                resultat.next();
                return resultat.getInt(1);
            }
        }
    }

    private List<Medecin> filtrer(Predicate<Medecin> condition) throws SQLException {
        List<Medecin> resultat = new ArrayList<>();
        for (Medecin medecin : listerTous()) {
            if (condition.test(medecin)) {
                resultat.add(medecin);
            }
        }
        return resultat;
    }

    private Medecin lireMedecin(ResultSet resultat) throws SQLException {
        return new Medecin(
                resultat.getString("matricule"),
                resultat.getString("nom"),
                resultat.getString("prenom"),
                resultat.getString("specialite"),
                resultat.getString("telephone"));
    }
}