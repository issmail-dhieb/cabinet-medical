// src/main/java/com/cabinetmedical/dao/RendezVousDAO.java
package com.cabinetmedical.dao;

import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès à la table rendez_vous. Cette classe contient uniquement les opérations
 * sur la base de données : la validation et les règles métier sont dans RendezVousService.
 * Les dates sont stockées en texte au format RendezVous.FORMAT_STOCKAGE, ce qui permet
 * de les comparer et de les trier directement en SQL.
 * Il n'y a volontairement aucune méthode de suppression : un rendez-vous est
 * conservé dans l'historique, il peut seulement être annulé.
 */
public class RendezVousDAO {

    private static final String SELECTION_RENDEZ_VOUS =
            "SELECT id, matricule_medecin, numero_telephone_patient, date_heure, statut FROM rendez_vous";

    // Un rendez-vous avec son médecin et son patient, en une seule requête.
    private static final String SELECTION_DETAIL = """
            SELECT r.id AS id,
                   r.matricule_medecin AS matricule_medecin,
                   r.numero_telephone_patient AS numero_telephone_patient,
                   r.date_heure AS date_heure,
                   r.statut AS statut,
                   m.nom AS medecin_nom,
                   m.prenom AS medecin_prenom,
                   m.specialite AS medecin_specialite,
                   m.telephone AS medecin_telephone,
                   p.nom AS patient_nom,
                   p.prenom AS patient_prenom,
                   p.date_naissance AS patient_date_naissance
            FROM rendez_vous r
            JOIN medecins m ON m.matricule = r.matricule_medecin
            JOIN patients p ON p.numero_telephone = r.numero_telephone_patient
            """;

    private static final String TRI_CHRONOLOGIQUE = " ORDER BY r.date_heure, r.id";

    private final DatabaseConnection connexion;

    public RendezVousDAO(DatabaseConnection connexion) {
        this.connexion = connexion;
    }

    /**
     * Ajoute un rendez-vous avec son statut. L'identifiant du paramètre est ignoré :
     * la base en génère un nouveau. Lève une SQLException si le médecin ou le patient
     * n'existe pas, ou si le créneau est déjà pris par un rendez-vous non annulé.
     *
     * @return l'identifiant généré par la base
     */
    public int ajouter(RendezVous rendezVous) throws SQLException {
        String requete = "INSERT INTO rendez_vous "
                + "(matricule_medecin, numero_telephone_patient, date_heure, statut) VALUES (?, ?, ?, ?)";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, rendezVous.getMatriculeMedecin());
            statement.setString(2, rendezVous.getNumeroTelephonePatient());
            statement.setString(3, rendezVous.getDateHeure().format(RendezVous.FORMAT_STOCKAGE));
            statement.setString(4, rendezVous.getStatut().name());
            statement.executeUpdate();
            return lireDernierIdInsere(connection);
        }
    }

    /**
     * Change la date et l'heure du rendez-vous.
     * Lève une SQLException si le créneau est déjà pris par un autre rendez-vous non annulé.
     *
     * @return true si un rendez-vous a été modifié, false si l'identifiant n'existe pas
     */
    public boolean modifierDateHeure(int id, LocalDateTime dateHeure) throws SQLException {
        String requete = "UPDATE rendez_vous SET date_heure = ? WHERE id = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, dateHeure.format(RendezVous.FORMAT_STOCKAGE));
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Change le statut du rendez-vous.
     *
     * @return true si un rendez-vous a été modifié, false si l'identifiant n'existe pas
     */
    public boolean modifierStatut(int id, StatutRendezVous statut) throws SQLException {
        String requete = "UPDATE rendez_vous SET statut = ? WHERE id = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, statut.name());
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    /** Recherche exacte par identifiant. */
    public Optional<RendezVous> trouverParId(int id) throws SQLException {
        String requete = SELECTION_RENDEZ_VOUS + " WHERE id = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setInt(1, id);
            try (ResultSet resultat = statement.executeQuery()) {
                if (resultat.next()) {
                    return Optional.of(lireRendezVous(resultat));
                }
                return Optional.empty();
            }
        }
    }

    /** Recherche exacte par identifiant, avec les informations du médecin et du patient. */
    public Optional<RendezVousDetail> trouverDetailParId(int id) throws SQLException {
        String requete = SELECTION_DETAIL + " WHERE r.id = ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setInt(1, id);
            try (ResultSet resultat = statement.executeQuery()) {
                if (resultat.next()) {
                    return Optional.of(lireDetail(resultat));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retourne tous les rendez-vous (planifiés, effectués et annulés), du plus ancien
     * au plus récent, avec les informations du médecin et du patient.
     */
    public List<RendezVousDetail> listerTous() throws SQLException {
        String requete = SELECTION_DETAIL + TRI_CHRONOLOGIQUE;
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete);
             ResultSet resultat = statement.executeQuery()) {
            return lireDetails(resultat);
        }
    }

    /**
     * Retourne les rendez-vous planifiés dont la date et l'heure sont égales ou
     * postérieures à l'instant donné, du plus proche au plus lointain.
     */
    public List<RendezVousDetail> listerAVenir(LocalDateTime maintenant) throws SQLException {
        String requete = SELECTION_DETAIL
                + " WHERE r.statut = 'PLANIFIE' AND r.date_heure >= ?" + TRI_CHRONOLOGIQUE;
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, maintenant.format(RendezVous.FORMAT_STOCKAGE));
            try (ResultSet resultat = statement.executeQuery()) {
                return lireDetails(resultat);
            }
        }
    }

    /**
     * Indique si le médecin a déjà un rendez-vous non annulé à cette date et heure.
     *
     * @param idExclu identifiant d'un rendez-vous à ignorer (celui qu'on déplace), ou 0 pour n'en ignorer aucun
     */
    public boolean existeConflitMedecin(String matricule, LocalDateTime dateHeure, int idExclu)
            throws SQLException {
        return existeConflit("matricule_medecin", matricule, dateHeure, idExclu);
    }

    /**
     * Indique si le patient a déjà un rendez-vous non annulé à cette date et heure.
     *
     * @param idExclu identifiant d'un rendez-vous à ignorer (celui qu'on déplace), ou 0 pour n'en ignorer aucun
     */
    public boolean existeConflitPatient(String numeroTelephone, LocalDateTime dateHeure, int idExclu)
            throws SQLException {
        return existeConflit("numero_telephone_patient", numeroTelephone, dateHeure, idExclu);
    }

    // Le nom de colonne vient uniquement des deux méthodes ci-dessus (jamais de l'utilisateur).
    private boolean existeConflit(String colonne, String valeur, LocalDateTime dateHeure, int idExclu)
            throws SQLException {
        String requete = "SELECT COUNT(*) FROM rendez_vous WHERE " + colonne + " = ? "
                + "AND date_heure = ? AND statut <> 'ANNULE' AND id <> ?";
        try (Connection connection = connexion.getConnection();
             PreparedStatement statement = connection.prepareStatement(requete)) {
            statement.setString(1, valeur);
            statement.setString(2, dateHeure.format(RendezVous.FORMAT_STOCKAGE));
            statement.setInt(3, idExclu);
            try (ResultSet resultat = statement.executeQuery()) {
                resultat.next();
                return resultat.getInt(1) > 0;
            }
        }
    }

    private int lireDernierIdInsere(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultat = statement.executeQuery("SELECT last_insert_rowid()")) {
            resultat.next();
            return resultat.getInt(1);
        }
    }

    private List<RendezVousDetail> lireDetails(ResultSet resultat) throws SQLException {
        List<RendezVousDetail> details = new ArrayList<>();
        while (resultat.next()) {
            details.add(lireDetail(resultat));
        }
        return details;
    }

    private RendezVous lireRendezVous(ResultSet resultat) throws SQLException {
        String dateTexte = resultat.getString("date_heure");
        String statutTexte = resultat.getString("statut");
        try {
            return new RendezVous(
                    resultat.getInt("id"),
                    resultat.getString("matricule_medecin"),
                    resultat.getString("numero_telephone_patient"),
                    LocalDateTime.parse(dateTexte, RendezVous.FORMAT_STOCKAGE),
                    StatutRendezVous.depuisCode(statutTexte));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new SQLException("Rendez-vous illisible dans la base : date '" + dateTexte
                    + "', statut '" + statutTexte + "'", e);
        }
    }

    private RendezVousDetail lireDetail(ResultSet resultat) throws SQLException {
        RendezVous rendezVous = lireRendezVous(resultat);
        Medecin medecin = new Medecin(
                rendezVous.getMatriculeMedecin(),
                resultat.getString("medecin_nom"),
                resultat.getString("medecin_prenom"),
                resultat.getString("medecin_specialite"),
                resultat.getString("medecin_telephone"));
        String naissanceTexte = resultat.getString("patient_date_naissance");
        try {
            Patient patient = new Patient(
                    rendezVous.getNumeroTelephonePatient(),
                    resultat.getString("patient_nom"),
                    resultat.getString("patient_prenom"),
                    LocalDate.parse(naissanceTexte));
            return new RendezVousDetail(rendezVous, medecin, patient);
        } catch (DateTimeParseException e) {
            throw new SQLException("Date de naissance illisible dans la base : " + naissanceTexte, e);
        }
    }
}