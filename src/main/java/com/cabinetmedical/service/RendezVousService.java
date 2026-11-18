// src/main/java/com/cabinetmedical/service/RendezVousService.java
package com.cabinetmedical.service;

import com.cabinetmedical.dao.MedecinDAO;
import com.cabinetmedical.dao.PatientDAO;
import com.cabinetmedical.dao.RendezVousDAO;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;
import com.cabinetmedical.util.ValidationUtil;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Règles métier des rendez-vous : existence du médecin et du patient, dates valides,
 * conflits de créneau, transitions de statut et conservation de l'historique.
 * Les erreurs sont signalées par une ServiceException dont le message est destiné
 * à l'utilisateur.
 *
 * Règles principales :
 * - un créneau est une date et une heure exactes, en pas de 15 minutes ;
 * - un médecin (ou un patient) ne peut pas avoir deux rendez-vous non annulés au même créneau ;
 * - un rendez-vous ne peut pas être fixé dans le passé ;
 * - un rendez-vous planifié peut être annulé, ou marqué effectué une fois son heure arrivée ;
 * - un rendez-vous effectué ou annulé ne change plus jamais ; aucun rendez-vous n'est supprimé.
 */
public class RendezVousService {

    private static final Logger JOURNAL = Logger.getLogger(RendezVousService.class.getName());

    private static final int PAS_EN_MINUTES = 15;
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final RendezVousDAO rendezVousDAO;
    private final MedecinDAO medecinDAO;
    private final PatientDAO patientDAO;
    private final Clock horloge;

    /** Utilise l'heure réelle de l'ordinateur. */
    public RendezVousService(RendezVousDAO rendezVousDAO, MedecinDAO medecinDAO, PatientDAO patientDAO) {
        this(rendezVousDAO, medecinDAO, patientDAO, Clock.systemDefaultZone());
    }

    /** Utilise l'horloge donnée (permet aux tests de fixer l'heure actuelle). */
    public RendezVousService(RendezVousDAO rendezVousDAO, MedecinDAO medecinDAO, PatientDAO patientDAO,
                             Clock horloge) {
        this.rendezVousDAO = rendezVousDAO;
        this.medecinDAO = medecinDAO;
        this.patientDAO = patientDAO;
        this.horloge = horloge;
    }

    /**
     * Valide puis enregistre un nouveau rendez-vous. Seuls le médecin, le patient et la date
     * de la saisie sont utilisés : l'identifiant est généré et le statut est toujours PLANIFIE.
     *
     * @return le rendez-vous tel qu'il a été enregistré (avec son identifiant)
     */
    public RendezVous planifier(RendezVous saisie) throws ServiceException {
        if (saisie == null) {
            throw new ServiceException("Les informations du rendez-vous sont obligatoires.");
        }
        String matricule = validerMatriculeMedecin(saisie.getMatriculeMedecin());
        String telephone = validerTelephonePatient(saisie.getNumeroTelephonePatient());
        LocalDateTime dateHeure = validerDateHeure(saisie.getDateHeure());
        try {
            verifierMedecinExiste(matricule);
            verifierPatientExiste(telephone);
            verifierPasDeConflit(matricule, telephone, dateHeure, 0);
            RendezVous aEnregistrer = new RendezVous(matricule, telephone, dateHeure);
            aEnregistrer.setId(rendezVousDAO.ajouter(aEnregistrer));
            return aEnregistrer;
        } catch (SQLException e) {
            throw erreurTechnique("planification d'un rendez-vous", e);
        }
    }

    /**
     * Déplace un rendez-vous planifié à une autre date et heure.
     * Le médecin et le patient du rendez-vous ne changent pas.
     *
     * @return le rendez-vous tel qu'il a été enregistré
     */
    public RendezVous modifierDateHeure(int id, LocalDateTime nouvelleDateHeure) throws ServiceException {
        LocalDateTime dateHeure = validerDateHeure(nouvelleDateHeure);
        try {
            RendezVous rendezVous = chargerRendezVous(id);
            if (rendezVous.getStatut() != StatutRendezVous.PLANIFIE) {
                throw new ServiceException("Seul un rendez-vous planifié peut être déplacé : celui-ci est déjà "
                        + enMinuscules(rendezVous.getStatut()) + ".");
            }
            if (rendezVous.getDateHeure().equals(dateHeure)) {
                throw new ServiceException("Le rendez-vous est déjà prévu à cette date et à cette heure.");
            }
            verifierPasDeConflit(rendezVous.getMatriculeMedecin(),
                    rendezVous.getNumeroTelephonePatient(), dateHeure, id);
            if (!rendezVousDAO.modifierDateHeure(id, dateHeure)) {
                throw new ServiceException(messageRendezVousIntrouvable(id));
            }
            rendezVous.setDateHeure(dateHeure);
            return rendezVous;
        } catch (SQLException e) {
            throw erreurTechnique("modification de la date d'un rendez-vous", e);
        }
    }

    /**
     * Change le statut d'un rendez-vous planifié : PLANIFIE vers ANNULE (à tout moment)
     * ou PLANIFIE vers EFFECTUE (une fois la date et l'heure du rendez-vous arrivées).
     * Un rendez-vous effectué ou annulé ne peut plus changer.
     *
     * @return le rendez-vous tel qu'il a été enregistré
     */
    public RendezVous modifierStatut(int id, StatutRendezVous nouveauStatut) throws ServiceException {
        if (nouveauStatut == null) {
            throw new ServiceException("Le statut est obligatoire.");
        }
        try {
            RendezVous rendezVous = chargerRendezVous(id);
            StatutRendezVous statutActuel = rendezVous.getStatut();
            if (statutActuel == nouveauStatut) {
                throw new ServiceException("Le rendez-vous est déjà " + enMinuscules(statutActuel) + ".");
            }
            if (statutActuel != StatutRendezVous.PLANIFIE) {
                throw new ServiceException("Un rendez-vous " + enMinuscules(statutActuel)
                        + " ne peut plus changer de statut.");
            }
            if (nouveauStatut == StatutRendezVous.EFFECTUE
                    && rendezVous.getDateHeure().isAfter(maintenant())) {
                throw new ServiceException("Un rendez-vous ne peut pas être marqué comme effectué "
                        + "avant sa date et son heure.");
            }
            if (!rendezVousDAO.modifierStatut(id, nouveauStatut)) {
                throw new ServiceException(messageRendezVousIntrouvable(id));
            }
            rendezVous.setStatut(nouveauStatut);
            return rendezVous;
        } catch (SQLException e) {
            throw erreurTechnique("modification du statut d'un rendez-vous", e);
        }
    }

    /** Recherche un rendez-vous par identifiant, avec son médecin et son patient. */
    public Optional<RendezVousDetail> trouverParId(int id) throws ServiceException {
        try {
            return rendezVousDAO.trouverDetailParId(id);
        } catch (SQLException e) {
            throw erreurTechnique("recherche d'un rendez-vous", e);
        }
    }

    /** Retourne tout l'historique (planifiés, effectués, annulés), du plus ancien au plus récent. */
    public List<RendezVousDetail> listerTous() throws ServiceException {
        try {
            return rendezVousDAO.listerTous();
        } catch (SQLException e) {
            throw erreurTechnique("lecture de la liste des rendez-vous", e);
        }
    }

    /** Retourne les rendez-vous planifiés à partir de maintenant, du plus proche au plus lointain. */
    public List<RendezVousDetail> listerAVenir() throws ServiceException {
        try {
            return rendezVousDAO.listerAVenir(maintenant());
        } catch (SQLException e) {
            throw erreurTechnique("lecture des rendez-vous à venir", e);
        }
    }

    // ---------- Validation ----------

    private String validerMatriculeMedecin(String saisie) throws ServiceException {
        String matricule = ValidationUtil.normaliserMatricule(saisie);
        if (matricule.isEmpty()) {
            throw new ServiceException("Le médecin est obligatoire.");
        }
        return matricule;
    }

    private String validerTelephonePatient(String saisie) throws ServiceException {
        if (ValidationUtil.nettoyerTexte(saisie).isEmpty()) {
            throw new ServiceException("Le patient est obligatoire.");
        }
        String telephone = ValidationUtil.normaliserTelephone(saisie);
        if (telephone == null) {
            throw new ServiceException("Le numéro de téléphone du patient doit contenir 8 chiffres "
                    + "(les espaces et le préfixe +216 sont acceptés).");
        }
        return telephone;
    }

    private LocalDateTime validerDateHeure(LocalDateTime saisie) throws ServiceException {
        if (saisie == null) {
            throw new ServiceException("La date et l'heure du rendez-vous sont obligatoires.");
        }
        LocalDateTime dateHeure = saisie.truncatedTo(ChronoUnit.MINUTES);
        if (dateHeure.getMinute() % PAS_EN_MINUTES != 0) {
            throw new ServiceException("L'heure du rendez-vous doit être un multiple de " + PAS_EN_MINUTES
                    + " minutes (par exemple 09:00, 09:15, 09:30 ou 09:45).");
        }
        if (dateHeure.isBefore(maintenant())) {
            throw new ServiceException("La date et l'heure du rendez-vous ne peuvent pas être dans le passé.");
        }
        return dateHeure;
    }

    // ---------- Vérifications avec la base de données ----------

    private void verifierMedecinExiste(String matricule) throws ServiceException, SQLException {
        if (medecinDAO.trouverParMatricule(matricule).isEmpty()) {
            throw new ServiceException("Aucun médecin ne porte le matricule " + matricule + ".");
        }
    }

    private void verifierPatientExiste(String numeroTelephone) throws ServiceException, SQLException {
        if (patientDAO.trouverParNumeroTelephone(numeroTelephone).isEmpty()) {
            throw new ServiceException("Aucun patient ne porte le numéro " + numeroTelephone + ".");
        }
    }

    private void verifierPasDeConflit(String matricule, String numeroTelephone, LocalDateTime dateHeure,
                                      int idExclu) throws ServiceException, SQLException {
        if (rendezVousDAO.existeConflitMedecin(matricule, dateHeure, idExclu)) {
            throw new ServiceException("Ce médecin a déjà un rendez-vous " + creneau(dateHeure) + ".");
        }
        if (rendezVousDAO.existeConflitPatient(numeroTelephone, dateHeure, idExclu)) {
            throw new ServiceException("Ce patient a déjà un rendez-vous " + creneau(dateHeure) + ".");
        }
    }

    private RendezVous chargerRendezVous(int id) throws ServiceException, SQLException {
        Optional<RendezVous> rendezVous = rendezVousDAO.trouverParId(id);
        if (rendezVous.isEmpty()) {
            throw new ServiceException(messageRendezVousIntrouvable(id));
        }
        return rendezVous.get();
    }

    // ---------- Temps, messages et journal ----------

    /** L'instant présent, arrondi à la minute (comme les dates des rendez-vous). */
    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).truncatedTo(ChronoUnit.MINUTES);
    }

    private String creneau(LocalDateTime dateHeure) {
        return "le " + dateHeure.format(FORMAT_DATE) + " à " + dateHeure.format(FORMAT_HEURE);
    }

    private String enMinuscules(StatutRendezVous statut) {
        return statut.getLibelle().toLowerCase(Locale.FRENCH);
    }

    private String messageRendezVousIntrouvable(int id) {
        return "Aucun rendez-vous ne porte le numéro " + id + ".";
    }

    /** Écrit le détail technique dans le journal et prépare un message simple pour l'utilisateur. */
    private ServiceException erreurTechnique(String operation, SQLException e) {
        JOURNAL.log(Level.SEVERE, "Erreur lors de l'opération : " + operation, e);
        return ServiceException.erreurTechnique(e);
    }
}