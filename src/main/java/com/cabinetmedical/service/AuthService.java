// src/main/java/com/cabinetmedical/service/AuthService.java
package com.cabinetmedical.service;

import com.cabinetmedical.dao.UtilisateurDAO;
import com.cabinetmedical.model.Utilisateur;
import com.cabinetmedical.util.ValidationUtil;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Authentification : création du compte administrateur au premier lancement,
 * vérification des identifiants, connexion et déconnexion.
 *
 * Mots de passe : jamais conservés en clair. On enregistre uniquement l'empreinte
 * PBKDF2WithHmacSHA256 (ITERATIONS itérations), un sel aléatoire unique de
 * TAILLE_SEL_OCTETS octets et le nombre d'itérations, le tout en Base64.
 *
 * L'utilisateur connecté est conservé dans cette instance : l'application doit donc
 * créer un seul AuthService et le partager entre l'écran de connexion et l'écran principal.
 */
public class AuthService {

    private static final Logger JOURNAL = Logger.getLogger(AuthService.class.getName());

    public static final String ALGORITHME = "PBKDF2WithHmacSHA256";
    public static final int ITERATIONS = 210_000;
    public static final int TAILLE_SEL_OCTETS = 16;
    public static final int TAILLE_HASH_OCTETS = 32;

    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 4;
    public static final int LONGUEUR_MAX_MOT_DE_PASSE = 100;
    public static final int LONGUEUR_MIN_NOM_UTILISATEUR = 3;
    public static final int LONGUEUR_MAX_NOM_UTILISATEUR = 30;

    /** Même message pour un nom inconnu et pour un mauvais mot de passe : on ne révèle pas lequel est faux. */
    public static final String MESSAGE_IDENTIFIANTS_INCORRECTS =
            "Nom d'utilisateur ou mot de passe incorrect.";

    // Lettres sans accent, chiffres, point, tiret, tiret bas (la casse est ignorée par la base).
    private static final Pattern MOTIF_NOM_UTILISATEUR = Pattern.compile("[A-Za-z0-9._-]+");

    private final UtilisateurDAO utilisateurDAO;
    private final SecureRandom generateur = new SecureRandom();
    private Utilisateur utilisateurConnecte;

    public AuthService(UtilisateurDAO utilisateurDAO) {
        this.utilisateurDAO = utilisateurDAO;
    }

    // ---------- Premier lancement ----------

    /** Retourne true tant qu'aucun compte n'existe : l'application doit alors proposer la création de l'administrateur. */
    public boolean estPremierLancement() throws ServiceException {
        try {
            return utilisateurDAO.compter() == 0;
        } catch (SQLException e) {
            throw erreurTechnique("vérification des comptes utilisateurs", e);
        }
    }

    /**
     * Crée le compte administrateur. Possible uniquement s'il n'existe encore aucun compte.
     * Cette méthode ne connecte pas l'utilisateur : il doit ensuite se connecter normalement.
     *
     * @return le compte enregistré (avec son identifiant)
     */
    public Utilisateur creerAdministrateur(String nomUtilisateur, String motDePasse, String confirmation)
            throws ServiceException {
        verifierAucunCompte();
        String nom = validerNomUtilisateur(nomUtilisateur);
        validerMotDePasse(motDePasse);
        if (!motDePasse.equals(confirmation)) {
            throw new ServiceException("Les deux mots de passe ne sont pas identiques.");
        }
        try {
            byte[] sel = genererSel();
            byte[] hash = calculerHash(motDePasse, sel, ITERATIONS, TAILLE_HASH_OCTETS);
            Utilisateur aEnregistrer = new Utilisateur(nom, encoder(hash), encoder(sel), ITERATIONS);
            aEnregistrer.setId(utilisateurDAO.ajouter(aEnregistrer));
            return aEnregistrer;
        } catch (SQLException | GeneralSecurityException e) {
            throw erreurTechnique("création du compte administrateur", e);
        }
    }

    // ---------- Connexion et déconnexion ----------

    /**
     * Vérifie les identifiants. S'ils sont corrects, l'utilisateur devient l'utilisateur connecté.
     * Un échec ne modifie pas la session en cours.
     *
     * @return l'utilisateur connecté
     */
    public Utilisateur connecter(String nomUtilisateur, String motDePasse) throws ServiceException {
        String nom = ValidationUtil.nettoyerTexte(nomUtilisateur);
        if (nom.isEmpty()) {
            throw new ServiceException("Le nom d'utilisateur est obligatoire.");
        }
        if (motDePasse == null || motDePasse.isEmpty()) {
            throw new ServiceException("Le mot de passe est obligatoire.");
        }
        try {
            Optional<Utilisateur> trouve = utilisateurDAO.trouverParNomUtilisateur(nom);
            if (trouve.isEmpty() || !motDePasseCorrespond(motDePasse, trouve.get())) {
                throw new ServiceException(MESSAGE_IDENTIFIANTS_INCORRECTS);
            }
            utilisateurConnecte = trouve.get();
            return utilisateurConnecte;
        } catch (SQLException | GeneralSecurityException | IllegalArgumentException e) {
            // IllegalArgumentException : sel ou hash illisible (Base64) ou itérations invalides dans la base.
            throw erreurTechnique("vérification des identifiants", e);
        }
    }

    /** Termine la session. Sans effet si personne n'est connecté. */
    public void deconnecter() {
        utilisateurConnecte = null;
    }

    public boolean estConnecte() {
        return utilisateurConnecte != null;
    }

    public Optional<Utilisateur> getUtilisateurConnecte() {
        return Optional.ofNullable(utilisateurConnecte);
    }

    // ---------- Validation ----------

    private void verifierAucunCompte() throws ServiceException {
        if (!estPremierLancement()) {
            throw new ServiceException("Un compte administrateur existe déjà.");
        }
    }

    private String validerNomUtilisateur(String saisie) throws ServiceException {
        String nom = ValidationUtil.nettoyerTexte(saisie);
        if (nom.isEmpty()) {
            throw new ServiceException("Le nom d'utilisateur est obligatoire.");
        }
        if (nom.length() < LONGUEUR_MIN_NOM_UTILISATEUR
                || nom.length() > LONGUEUR_MAX_NOM_UTILISATEUR
                || !MOTIF_NOM_UTILISATEUR.matcher(nom).matches()) {
            throw new ServiceException("Le nom d'utilisateur doit contenir entre "
                    + LONGUEUR_MIN_NOM_UTILISATEUR + " et " + LONGUEUR_MAX_NOM_UTILISATEUR
                    + " caractères : lettres sans accent, chiffres, point (.), tiret (-) ou tiret bas (_), "
                    + "sans espace.");
        }
        return nom;
    }

    // Le mot de passe n'est pas nettoyé : les espaces font partie du mot de passe.
    private void validerMotDePasse(String motDePasse) throws ServiceException {
        if (motDePasse == null || motDePasse.isBlank()) {
            throw new ServiceException("Le mot de passe est obligatoire.");
        }
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new ServiceException("Le mot de passe doit contenir au moins "
                    + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
        }
        if (motDePasse.length() > LONGUEUR_MAX_MOT_DE_PASSE) {
            throw new ServiceException("Le mot de passe ne doit pas dépasser "
                    + LONGUEUR_MAX_MOT_DE_PASSE + " caractères.");
        }
    }

    // ---------- Hachage ----------

    private boolean motDePasseCorrespond(String saisie, Utilisateur utilisateur)
            throws GeneralSecurityException {
        byte[] sel = Base64.getDecoder().decode(utilisateur.getSel());
        byte[] attendu = Base64.getDecoder().decode(utilisateur.getMotDePasseHash());
        byte[] calcule = calculerHash(saisie, sel, utilisateur.getIterations(), attendu.length);
        // Comparaison en temps constant (ne s'arrête pas au premier octet différent).
        return MessageDigest.isEqual(attendu, calcule);
    }

    private byte[] genererSel() {
        byte[] sel = new byte[TAILLE_SEL_OCTETS];
        generateur.nextBytes(sel);
        return sel;
    }

    private byte[] calculerHash(String motDePasse, byte[] sel, int iterations, int longueurOctets)
            throws GeneralSecurityException {
        char[] caracteres = motDePasse.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(caracteres, sel, iterations, longueurOctets * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHME).generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
            Arrays.fill(caracteres, '\0');
        }
    }

    private String encoder(byte[] octets) {
        return Base64.getEncoder().encodeToString(octets);
    }

    // ---------- Journal ----------

    /** Écrit le détail technique dans le journal (jamais le mot de passe) et prépare un message simple. */
    private ServiceException erreurTechnique(String operation, Exception e) {
        JOURNAL.log(Level.SEVERE, "Erreur lors de l'opération : " + operation, e);
        return ServiceException.erreurTechnique(e);
    }
}