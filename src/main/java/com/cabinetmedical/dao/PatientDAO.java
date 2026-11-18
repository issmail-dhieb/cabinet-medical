// src/main/java/com/cabinetmedical/dao/PatientDAO.java
package com.cabinetmedical.dao;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Accès à la table patients. Cette classe contient uniquement les opérations
 * sur la base de données : la validation est faite par PatientService.
 * Les recherches sont faites en Java pour ignorer la casse ET les accents
 * (le LIKE de SQLite ignore seulement la casse des lettres sans accent).
 */
public class PatientDAO {

    private static final String SELECTION =
            "SELECT numero_telephone, nom, prenom, date_naissance FROM patients";

    private static final String PREFIXE_TUNISIE = "+216";

    private final DatabaseConnection connexion;

    public PatientDAO(DatabaseConnection connexion) {
        this.connexion = connexion;
    }

    /** Ajoute un patient. Lève une SQLException si le numéro de téléphone existe déjà. */
    public void ajouter(Patient patient) throws SQLException {
        String requete = "INSERT INTO patients (numero_telephone, nom, prenom, date_naissance) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, patient.getNumeroTelephone());
            statement.setString(2, patient.getNom());
            statement.setString(3, patient.getPrenom());
            statement.setString(4, patient.getDateNaissance().toString());
            statement.executeUpdate();
        }
    }

    /**
     * Met à jour le patient identifié par son ancien numéro de téléphone.
     * Le nouveau numéro peut être différent : la base met alors à jour
     * automatiquement ses rendez-vous (ON UPDATE CASCADE).
     * Lève une SQLException si le nouveau numéro appartient déjà à un autre patient.
     *
     * @return true si un patient a été modifié, false si l'ancien numéro n'existe pas
     */
    public boolean modifier(String ancienNumeroTelephone, Patient patient) throws SQLException {
        String requete = "UPDATE patients SET numero_telephone = ?, nom = ?, prenom = ?, "
                + "date_naissance = ? WHERE numero_telephone = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, patient.getNumeroTelephone());
            statement.setString(2, patient.getNom());
            statement.setString(3, patient.getPrenom());
            statement.setString(4, patient.getDateNaissance().toString());
            statement.setString(5, ancienNumeroTelephone);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Supprime le patient ayant ce numéro de téléphone.
     * La base refuse la suppression (SQLException) s'il a des rendez-vous.
     *
     * @return true si un patient a été supprimé, false si le numéro n'existe pas
     */
    public boolean supprimer(String numeroTelephone) throws SQLException {
        String requete = "DELETE FROM patients WHERE numero_telephone = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, numeroTelephone);
            return statement.executeUpdate() > 0;
        }
    }

    /** Recherche exacte par numéro de téléphone. */
    public Optional<Patient> trouverParNumeroTelephone(String numeroTelephone) throws SQLException {
        String requete = SELECTION + " WHERE numero_telephone = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, numeroTelephone);
            try (ResultSet resultat = statement.executeQuery()) {
                if (resultat.next()) {
                    return Optional.of(lirePatient(resultat));
                }
                return Optional.empty();
            }
        }
    }

    /** Retourne tous les patients, triés par nom puis prénom (ordre alphabétique français). */
    public List<Patient> listerTous() throws SQLException {
        List<Patient> patients = new ArrayList<>();
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECTION);
             ResultSet resultat = statement.executeQuery()) {
            while (resultat.next()) {
                patients.add(lirePatient(resultat));
            }
        }
        Collator collator = Collator.getInstance(Locale.FRENCH);
        patients.sort(Comparator.comparing(Patient::getNom, collator)
                .thenComparing(Patient::getPrenom, collator)
                .thenComparing(Patient::getNumeroTelephone));
        return patients;
    }

    /** Recherche partielle par nom, sans tenir compte de la casse ni des accents. */
    public List<Patient> chercherParNom(String texte) throws SQLException {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        return filtrer(patient ->
                ValidationUtil.normaliserPourRecherche(patient.getNom()).contains(recherche));
    }

    /** Recherche partielle par prénom, sans tenir compte de la casse ni des accents. */
    public List<Patient> chercherParPrenom(String texte) throws SQLException {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        return filtrer(patient ->
                ValidationUtil.normaliserPourRecherche(patient.getPrenom()).contains(recherche));
    }

    /**
     * Recherche partielle par numéro de téléphone. Les espaces et le préfixe +216
     * saisis par l'utilisateur sont ignorés.
     */
    public List<Patient> chercherParNumeroTelephone(String texte) throws SQLException {
        String recherche = nettoyerNumeroPourRecherche(texte);
        return filtrer(patient -> patient.getNumeroTelephone().contains(recherche));
    }

    /**
     * Compte tous les rendez-vous du patient, quel que soit leur statut
     * (planifié, effectué ou annulé) : ils font partie de l'historique.
     */
    public int compterRendezVous(String numeroTelephone) throws SQLException {
        String requete = "SELECT COUNT(*) FROM rendez_vous WHERE numero_telephone_patient = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, numeroTelephone);
            try (ResultSet resultat = statement.executeQuery()) {
                resultat.next();
                return resultat.getInt(1);
            }
        }
    }

    private List<Patient> filtrer(Predicate<Patient> condition) throws SQLException {
        List<Patient> resultat = new ArrayList<>();
        for (Patient patient : listerTous()) {
            if (condition.test(patient)) {
                resultat.add(patient);
            }
        }
        return resultat;
    }

    private String nettoyerNumeroPourRecherche(String texte) {
        if (texte == null) {
            return "";
        }
        String numero = texte.replaceAll("\\s+", "");
        if (numero.startsWith(PREFIXE_TUNISIE)) {
            numero = numero.substring(PREFIXE_TUNISIE.length());
        }
        return numero;
    }

    private Patient lirePatient(ResultSet resultat) throws SQLException {
        String dateTexte = resultat.getString("date_naissance");
        try {
            return new Patient(
                    resultat.getString("numero_telephone"),
                    resultat.getString("nom"),
                    resultat.getString("prenom"),
                    LocalDate.parse(dateTexte));
        } catch (DateTimeParseException e) {
            throw new SQLException("Date de naissance illisible dans la base : " + dateTexte, e);
        }
    }
}