// src/main/java/com/cabinetmedical/service/PatientService.java
package com.cabinetmedical.service;

import com.cabinetmedical.dao.PatientDAO;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Règles métier des patients : validation, normalisation, doublons et
 * protection de l'historique des rendez-vous. Les erreurs sont signalées
 * par une ServiceException dont le message est destiné à l'utilisateur.
 */
public class PatientService {

    private static final Logger JOURNAL = Logger.getLogger(PatientService.class.getName());

    private static final int LONGUEUR_MAX_NOM = 50;
    private static final LocalDate DATE_NAISSANCE_MINIMALE = LocalDate.of(1900, 1, 1);
    private static final DateTimeFormatter FORMAT_FRANCAIS = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PatientDAO patientDAO;

    public PatientService(PatientDAO patientDAO) {
        this.patientDAO = patientDAO;
    }

    /**
     * Valide, nettoie puis enregistre un nouveau patient.
     *
     * @return le patient tel qu'il a été enregistré (téléphone sur 8 chiffres, textes nettoyés)
     */
    public Patient ajouter(Patient saisie) throws ServiceException {
        Patient patient = validerEtNormaliser(saisie);
        try {
            if (patientDAO.trouverParNumeroTelephone(patient.getNumeroTelephone()).isPresent()) {
                throw new ServiceException(messageNumeroDejaUtilise(patient.getNumeroTelephone()));
            }
            patientDAO.ajouter(patient);
            return patient;
        } catch (SQLException e) {
            throw erreurTechnique("ajout d'un patient", e);
        }
    }

    /**
     * Modifie un patient existant, identifié par son ancien numéro de téléphone.
     * La saisie contient les nouvelles valeurs, y compris un éventuel nouveau numéro.
     *
     * @return le patient tel qu'il a été enregistré
     */
    public Patient modifier(String ancienNumeroTelephone, Patient saisie) throws ServiceException {
        String ancienNumero = validerTelephone(ancienNumeroTelephone);
        Patient patient = validerEtNormaliser(saisie);
        try {
            boolean numeroChange = !ancienNumero.equals(patient.getNumeroTelephone());
            if (numeroChange
                    && patientDAO.trouverParNumeroTelephone(patient.getNumeroTelephone()).isPresent()) {
                throw new ServiceException(messageNumeroDejaUtilise(patient.getNumeroTelephone()));
            }
            if (!patientDAO.modifier(ancienNumero, patient)) {
                throw new ServiceException(messagePatientIntrouvable(ancienNumero));
            }
            return patient;
        } catch (SQLException e) {
            throw erreurTechnique("modification d'un patient", e);
        }
    }

    /** Supprime un patient, sauf s'il a un historique de rendez-vous (même annulés). */
    public void supprimer(String numeroTelephone) throws ServiceException {
        String numero = validerTelephone(numeroTelephone);
        try {
            int nombreRendezVous = patientDAO.compterRendezVous(numero);
            if (nombreRendezVous > 0) {
                throw new ServiceException(messageSuppressionImpossible(nombreRendezVous));
            }
            if (!patientDAO.supprimer(numero)) {
                throw new ServiceException(messagePatientIntrouvable(numero));
            }
        } catch (SQLException e) {
            throw erreurTechnique("suppression d'un patient", e);
        }
    }

    /** Recherche exacte par numéro de téléphone (la saisie est normalisée). Un numéro invalide ne trouve rien. */
    public Optional<Patient> trouverParNumeroTelephone(String numeroTelephone) throws ServiceException {
        String numero = ValidationUtil.normaliserTelephone(numeroTelephone);
        if (numero == null) {
            return Optional.empty();
        }
        try {
            return patientDAO.trouverParNumeroTelephone(numero);
        } catch (SQLException e) {
            throw erreurTechnique("recherche d'un patient par numéro de téléphone", e);
        }
    }

    /** Retourne tous les patients, triés par nom puis prénom. */
    public List<Patient> listerTous() throws ServiceException {
        try {
            return patientDAO.listerTous();
        } catch (SQLException e) {
            throw erreurTechnique("lecture de la liste des patients", e);
        }
    }

    /** Recherche partielle par nom. Un texte vide retourne tous les patients. */
    public List<Patient> chercherParNom(String texte) throws ServiceException {
        try {
            return patientDAO.chercherParNom(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de patients par nom", e);
        }
    }

    /** Recherche partielle par prénom. Un texte vide retourne tous les patients. */
    public List<Patient> chercherParPrenom(String texte) throws ServiceException {
        try {
            return patientDAO.chercherParPrenom(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de patients par prénom", e);
        }
    }

    /** Recherche partielle par numéro de téléphone. Un texte vide retourne tous les patients. */
    public List<Patient> chercherParNumeroTelephone(String texte) throws ServiceException {
        try {
            return patientDAO.chercherParNumeroTelephone(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de patients par numéro de téléphone", e);
        }
    }

    // ---------- Validation ----------

    private Patient validerEtNormaliser(Patient saisie) throws ServiceException {
        if (saisie == null) {
            throw new ServiceException("Les informations du patient sont obligatoires.");
        }
        String numeroTelephone = validerTelephone(saisie.getNumeroTelephone());
        String nom = validerTexte(saisie.getNom(), "Le nom", LONGUEUR_MAX_NOM);
        String prenom = validerTexte(saisie.getPrenom(), "Le prénom", LONGUEUR_MAX_NOM);
        LocalDate dateNaissance = validerDateNaissance(saisie.getDateNaissance());
        return new Patient(numeroTelephone, nom, prenom, dateNaissance);
    }

    private String validerTexte(String saisie, String libelle, int longueurMax) throws ServiceException {
        String texte = ValidationUtil.nettoyerTexte(saisie);
        if (texte.isEmpty()) {
            throw new ServiceException(libelle + " est obligatoire.");
        }
        if (!ValidationUtil.estNomValide(texte)) {
            throw new ServiceException(libelle
                    + " ne peut contenir que des lettres, des espaces, des tirets et des apostrophes.");
        }
        if (texte.length() > longueurMax) {
            throw new ServiceException(libelle + " ne doit pas dépasser " + longueurMax + " caractères.");
        }
        return texte;
    }

    private String validerTelephone(String saisie) throws ServiceException {
        if (ValidationUtil.nettoyerTexte(saisie).isEmpty()) {
            throw new ServiceException("Le numéro de téléphone est obligatoire.");
        }
        String telephone = ValidationUtil.normaliserTelephone(saisie);
        if (telephone == null) {
            throw new ServiceException("Le numéro de téléphone doit contenir 8 chiffres "
                    + "(les espaces et le préfixe +216 sont acceptés).");
        }
        return telephone;
    }

    private LocalDate validerDateNaissance(LocalDate date) throws ServiceException {
        if (date == null) {
            throw new ServiceException("La date de naissance est obligatoire.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ServiceException("La date de naissance ne peut pas être dans le futur.");
        }
        if (date.isBefore(DATE_NAISSANCE_MINIMALE)) {
            throw new ServiceException("La date de naissance est trop ancienne (avant le "
                    + DATE_NAISSANCE_MINIMALE.format(FORMAT_FRANCAIS) + ").");
        }
        return date;
    }

    // ---------- Messages et journal ----------

    private String messageNumeroDejaUtilise(String numeroTelephone) {
        return "Un patient avec le numéro " + numeroTelephone + " existe déjà.";
    }

    private String messagePatientIntrouvable(String numeroTelephone) {
        return "Aucun patient ne porte le numéro " + numeroTelephone + ".";
    }

    private String messageSuppressionImpossible(int nombreRendezVous) {
        String enregistres = nombreRendezVous == 1
                ? "1 rendez-vous est enregistré"
                : nombreRendezVous + " rendez-vous sont enregistrés";
        return "Ce patient ne peut pas être supprimé : " + enregistres + " à son nom. "
                + "Les rendez-vous effectués ou annulés comptent aussi, "
                + "car l'historique doit être conservé.";
    }

    /** Écrit le détail technique dans le journal et prépare un message simple pour l'utilisateur. */
    private ServiceException erreurTechnique(String operation, SQLException e) {
        JOURNAL.log(Level.SEVERE, "Erreur lors de l'opération : " + operation, e);
        return ServiceException.erreurTechnique(e);
    }
}