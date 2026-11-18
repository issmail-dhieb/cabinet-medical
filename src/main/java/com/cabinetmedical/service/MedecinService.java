// src/main/java/com/cabinetmedical/service/MedecinService.java
package com.cabinetmedical.service;

import com.cabinetmedical.dao.MedecinDAO;
import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Règles métier des médecins : validation, normalisation, doublons et
 * protection de l'historique des rendez-vous. Les erreurs sont signalées
 * par une ServiceException dont le message est destiné à l'utilisateur.
 */
public class MedecinService {

    private static final Logger JOURNAL = Logger.getLogger(MedecinService.class.getName());

    private static final int LONGUEUR_MAX_NOM = 50;
    private static final int LONGUEUR_MAX_SPECIALITE = 60;

    private final MedecinDAO medecinDAO;

    public MedecinService(MedecinDAO medecinDAO) {
        this.medecinDAO = medecinDAO;
    }

    /**
     * Valide, nettoie puis enregistre un nouveau médecin.
     *
     * @return le médecin tel qu'il a été enregistré (matricule en majuscules, téléphone sur 8 chiffres)
     */
    public Medecin ajouter(Medecin saisie) throws ServiceException {
        Medecin medecin = validerEtNormaliser(saisie);
        try {
            if (medecinDAO.trouverParMatricule(medecin.getMatricule()).isPresent()) {
                throw new ServiceException(
                        "Un médecin avec le matricule " + medecin.getMatricule() + " existe déjà.");
            }
            medecinDAO.ajouter(medecin);
            return medecin;
        } catch (SQLException e) {
            throw erreurTechnique("ajout d'un médecin", e);
        }
    }

    /**
     * Modifie nom, prénom, spécialité et téléphone d'un médecin existant.
     * Le matricule identifie le médecin et ne peut pas être changé.
     *
     * @return le médecin tel qu'il a été enregistré
     */
    public Medecin modifier(Medecin saisie) throws ServiceException {
        Medecin medecin = validerEtNormaliser(saisie);
        try {
            if (!medecinDAO.modifier(medecin)) {
                throw new ServiceException(messageMedecinIntrouvable(medecin.getMatricule()));
            }
            return medecin;
        } catch (SQLException e) {
            throw erreurTechnique("modification d'un médecin", e);
        }
    }

    /** Supprime un médecin, sauf s'il a un historique de rendez-vous (même annulés). */
    public void supprimer(String matricule) throws ServiceException {
        String matriculeNormalise = ValidationUtil.normaliserMatricule(matricule);
        if (matriculeNormalise.isEmpty()) {
            throw new ServiceException("Le matricule est obligatoire.");
        }
        try {
            int nombreRendezVous = medecinDAO.compterRendezVous(matriculeNormalise);
            if (nombreRendezVous > 0) {
                throw new ServiceException(messageSuppressionImpossible(nombreRendezVous));
            }
            if (!medecinDAO.supprimer(matriculeNormalise)) {
                throw new ServiceException(messageMedecinIntrouvable(matriculeNormalise));
            }
        } catch (SQLException e) {
            throw erreurTechnique("suppression d'un médecin", e);
        }
    }

    /** Recherche exacte par matricule (la saisie est nettoyée). Un matricule vide ne trouve rien. */
    public Optional<Medecin> trouverParMatricule(String matricule) throws ServiceException {
        String matriculeNormalise = ValidationUtil.normaliserMatricule(matricule);
        if (matriculeNormalise.isEmpty()) {
            return Optional.empty();
        }
        try {
            return medecinDAO.trouverParMatricule(matriculeNormalise);
        } catch (SQLException e) {
            throw erreurTechnique("recherche d'un médecin par matricule", e);
        }
    }

    /** Retourne tous les médecins, triés par nom puis prénom. */
    public List<Medecin> listerTous() throws ServiceException {
        try {
            return medecinDAO.listerTous();
        } catch (SQLException e) {
            throw erreurTechnique("lecture de la liste des médecins", e);
        }
    }

    /** Recherche partielle par matricule. Un texte vide retourne tous les médecins. */
    public List<Medecin> chercherParMatricule(String texte) throws ServiceException {
        try {
            return medecinDAO.chercherParMatricule(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de médecins par matricule", e);
        }
    }

    /** Recherche partielle par nom ou prénom. Un texte vide retourne tous les médecins. */
    public List<Medecin> chercherParNom(String texte) throws ServiceException {
        try {
            return medecinDAO.chercherParNom(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de médecins par nom", e);
        }
    }

    /** Recherche partielle par spécialité. Un texte vide retourne tous les médecins. */
    public List<Medecin> chercherParSpecialite(String texte) throws ServiceException {
        try {
            return medecinDAO.chercherParSpecialite(texte);
        } catch (SQLException e) {
            throw erreurTechnique("recherche de médecins par spécialité", e);
        }
    }

    // ---------- Validation ----------

    private Medecin validerEtNormaliser(Medecin saisie) throws ServiceException {
        if (saisie == null) {
            throw new ServiceException("Les informations du médecin sont obligatoires.");
        }
        String matricule = validerMatricule(saisie.getMatricule());
        String nom = validerTexte(saisie.getNom(), "Le nom", LONGUEUR_MAX_NOM);
        String prenom = validerTexte(saisie.getPrenom(), "Le prénom", LONGUEUR_MAX_NOM);
        String specialite = validerTexte(saisie.getSpecialite(), "La spécialité", LONGUEUR_MAX_SPECIALITE);
        String telephone = validerTelephone(saisie.getTelephone());
        return new Medecin(matricule, nom, prenom, specialite, telephone);
    }

    private String validerMatricule(String saisie) throws ServiceException {
        String matricule = ValidationUtil.normaliserMatricule(saisie);
        if (matricule.isEmpty()) {
            throw new ServiceException("Le matricule est obligatoire.");
        }
        if (!ValidationUtil.estMatriculeValide(matricule)) {
            throw new ServiceException("Le matricule ne peut contenir que des lettres (sans accent), "
                    + "des chiffres, des tirets (-) et des tirets bas (_), sans espace, "
                    + "avec " + ValidationUtil.LONGUEUR_MAX_MATRICULE + " caractères au maximum.");
        }
        return matricule;
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

    // ---------- Messages et journal ----------

    private String messageMedecinIntrouvable(String matricule) {
        return "Aucun médecin ne porte le matricule " + matricule + ".";
    }

    private String messageSuppressionImpossible(int nombreRendezVous) {
        String enregistres = nombreRendezVous == 1
                ? "1 rendez-vous est enregistré"
                : nombreRendezVous + " rendez-vous sont enregistrés";
        return "Ce médecin ne peut pas être supprimé : " + enregistres + " à son nom. "
                + "Les rendez-vous effectués ou annulés comptent aussi, "
                + "car l'historique doit être conservé.";
    }

    /** Écrit le détail technique dans le journal et prépare un message simple pour l'utilisateur. */
    private ServiceException erreurTechnique(String operation, SQLException e) {
        JOURNAL.log(Level.SEVERE, "Erreur lors de l'opération : " + operation, e);
        return ServiceException.erreurTechnique(e);
    }
}