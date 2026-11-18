// src/test/java/com/cabinetmedical/service/AuthServiceTest.java
package com.cabinetmedical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.BaseTemporaire;
import com.cabinetmedical.dao.UtilisateurDAO;
import com.cabinetmedical.database.DatabaseConnection;
import com.cabinetmedical.model.Utilisateur;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests de AuthService sur une base TEMPORAIRE (jamais cabinet_medical.db). */
class AuthServiceTest {

    private static final String NOM = "admin";
    private static final String MOT_DE_PASSE = "Secret42";

    @TempDir
    Path dossierTemporaire;

    private UtilisateurDAO utilisateurDAO;
    private AuthService service;

    @BeforeEach
    void preparerBaseTemporaire() throws SQLException {
        DatabaseConnection connexion = BaseTemporaire.creerBaseTemporaire(dossierTemporaire);
        utilisateurDAO = new UtilisateurDAO(connexion);
        service = new AuthService(utilisateurDAO);
    }

    // ---------- Premier lancement et création du compte ----------

    @Test
    void premierLancementEstVraiSansCompteEtFauxApresLaCreation() throws Exception {
        assertTrue(service.estPremierLancement());

        creerAdministrateur();

        assertFalse(service.estPremierLancement());
    }

    @Test
    void creerAdministrateurEnregistreUnCompteSansLeConnecter() throws Exception {
        Utilisateur cree = service.creerAdministrateur(NOM, MOT_DE_PASSE, MOT_DE_PASSE);

        assertTrue(cree.getId() > 0);
        assertEquals(NOM, cree.getNomUtilisateur());
        assertEquals(1, utilisateurDAO.compter());
        assertFalse(service.estConnecte());
    }

    @Test
    void creerAdministrateurNeStockeJamaisLeMotDePasseEnClair() throws Exception {
        creerAdministrateur();

        Utilisateur stocke = utilisateurDAO.trouverParNomUtilisateur(NOM).orElseThrow();

        assertNotEquals(MOT_DE_PASSE, stocke.getMotDePasseHash());
        assertFalse(stocke.getMotDePasseHash().contains(MOT_DE_PASSE));
        assertFalse(stocke.getSel().contains(MOT_DE_PASSE));
        assertEquals(AuthService.ITERATIONS, stocke.getIterations());
        assertEquals(AuthService.TAILLE_SEL_OCTETS, Base64.getDecoder().decode(stocke.getSel()).length);
        assertEquals(AuthService.TAILLE_HASH_OCTETS,
                Base64.getDecoder().decode(stocke.getMotDePasseHash()).length);
    }

    @Test
    void deuxComptesAvecLeMemeMotDePasseOntDesSelsEtDesHashDifferents() throws Exception {
        creerAdministrateur();
        Path autreDossier = Files.createDirectory(dossierTemporaire.resolve("autre"));
        UtilisateurDAO autreDAO = new UtilisateurDAO(BaseTemporaire.creerBaseTemporaire(autreDossier));
        new AuthService(autreDAO).creerAdministrateur(NOM, MOT_DE_PASSE, MOT_DE_PASSE);

        Utilisateur premier = utilisateurDAO.trouverParNomUtilisateur(NOM).orElseThrow();
        Utilisateur second = autreDAO.trouverParNomUtilisateur(NOM).orElseThrow();

        assertNotEquals(premier.getSel(), second.getSel());
        assertNotEquals(premier.getMotDePasseHash(), second.getMotDePasseHash());
    }

    @Test
    void creerAdministrateurRefuseUnSecondCompte() throws Exception {
        creerAdministrateur();

        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.creerAdministrateur("autre", "abcd", "abcd"));

        assertEquals("Un compte administrateur existe déjà.", erreur.getMessage());
        assertEquals(1, utilisateurDAO.compter());
    }

    @Test
    void creerAdministrateurNettoieLeNomUtilisateur() throws Exception {
        Utilisateur cree = service.creerAdministrateur("  admin  ", MOT_DE_PASSE, MOT_DE_PASSE);

        assertEquals("admin", cree.getNomUtilisateur());
        assertTrue(utilisateurDAO.trouverParNomUtilisateur("admin").isPresent());
    }

    @Test
    void creerAdministrateurRefuseUnNomVide() throws Exception {
        assertCreationRefusee("  ", MOT_DE_PASSE, MOT_DE_PASSE, "Le nom d'utilisateur est obligatoire.");
        assertCreationRefusee(null, MOT_DE_PASSE, MOT_DE_PASSE, "Le nom d'utilisateur est obligatoire.");
    }

    @Test
    void creerAdministrateurRefuseUnNomInvalide() throws Exception {
        String debut = "Le nom d'utilisateur doit contenir entre 3 et 30 caractères";

        assertCreationRefusee("ab", MOT_DE_PASSE, MOT_DE_PASSE, debut);
        assertCreationRefusee("a b c", MOT_DE_PASSE, MOT_DE_PASSE, debut);
        assertCreationRefusee("Jérôme", MOT_DE_PASSE, MOT_DE_PASSE, debut);
        assertCreationRefusee("admin@cabinet", MOT_DE_PASSE, MOT_DE_PASSE, debut);
        assertCreationRefusee("x".repeat(31), MOT_DE_PASSE, MOT_DE_PASSE, debut);
    }

    @Test
    void creerAdministrateurRefuseUnMotDePasseVide() throws Exception {
        assertCreationRefusee(NOM, null, null, "Le mot de passe est obligatoire.");
        assertCreationRefusee(NOM, "", "", "Le mot de passe est obligatoire.");
        assertCreationRefusee(NOM, "    ", "    ", "Le mot de passe est obligatoire.");
    }

    @Test
    void creerAdministrateurRefuseUnMotDePasseTropCourt() throws Exception {
        assertCreationRefusee(NOM, "abc", "abc", "Le mot de passe doit contenir au moins 4 caractères.");
    }

    @Test
    void creerAdministrateurAccepteUnMotDePasseDeQuatreCaracteres() throws Exception {
        service.creerAdministrateur(NOM, "1234", "1234");

        assertEquals(NOM, service.connecter(NOM, "1234").getNomUtilisateur());
    }

    @Test
    void creerAdministrateurRefuseUnMotDePasseTropLong() throws Exception {
        String tropLong = "x".repeat(101);

        assertCreationRefusee(NOM, tropLong, tropLong, "Le mot de passe ne doit pas dépasser 100 caractères.");
    }

    @Test
    void creerAdministrateurRefuseUneConfirmationDifferente() throws Exception {
        assertCreationRefusee(NOM, "abcd", "abce", "Les deux mots de passe ne sont pas identiques.");
        assertCreationRefusee(NOM, "abcd", null, "Les deux mots de passe ne sont pas identiques.");
    }

    @Test
    void lesEspacesDuMotDePasseFontPartieDuMotDePasse() throws Exception {
        service.creerAdministrateur(NOM, "  ab  ", "  ab  ");

        assertEquals(NOM, service.connecter(NOM, "  ab  ").getNomUtilisateur());
        service.deconnecter();
        assertConnexionRefusee(NOM, "ab", AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
    }

    // ---------- Connexion et déconnexion ----------

    @Test
    void connecterAvecLesBonsIdentifiantsOuvreLaSession() throws Exception {
        creerAdministrateur();

        Utilisateur connecte = service.connecter(NOM, MOT_DE_PASSE);

        assertEquals(NOM, connecte.getNomUtilisateur());
        assertTrue(service.estConnecte());
        assertEquals(NOM, service.getUtilisateurConnecte().orElseThrow().getNomUtilisateur());
    }

    @Test
    void connecterIgnoreLaCasseDuNomUtilisateur() throws Exception {
        creerAdministrateur();

        Utilisateur connecte = service.connecter("  ADMIN ", MOT_DE_PASSE);

        assertEquals(NOM, connecte.getNomUtilisateur());
        assertTrue(service.estConnecte());
    }

    @Test
    void connecterRefuseUnMauvaisMotDePasse() throws Exception {
        creerAdministrateur();

        assertConnexionRefusee(NOM, "Secret43", AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
        assertConnexionRefusee(NOM, "secret42", AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
    }

    @Test
    void connecterRefuseUnUtilisateurInconnuAvecLeMemeMessageQuUnMauvaisMotDePasse() throws Exception {
        creerAdministrateur();

        assertConnexionRefusee("inconnu", MOT_DE_PASSE, AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
    }

    @Test
    void connecterRefuseDesChampsVides() throws Exception {
        creerAdministrateur();

        assertConnexionRefusee("  ", MOT_DE_PASSE, "Le nom d'utilisateur est obligatoire.");
        assertConnexionRefusee(null, MOT_DE_PASSE, "Le nom d'utilisateur est obligatoire.");
        assertConnexionRefusee(NOM, "", "Le mot de passe est obligatoire.");
        assertConnexionRefusee(NOM, null, "Le mot de passe est obligatoire.");
    }

    @Test
    void connecterRefuseTouteConnexionQuandAucunCompteNExiste() {
        assertConnexionRefusee(NOM, "1234", AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
    }

    @Test
    void deconnecterTermineLaSession() throws Exception {
        creerAdministrateur();
        service.connecter(NOM, MOT_DE_PASSE);

        service.deconnecter();

        assertFalse(service.estConnecte());
        assertTrue(service.getUtilisateurConnecte().isEmpty());
        service.deconnecter();
        assertFalse(service.estConnecte());
        service.connecter(NOM, MOT_DE_PASSE);
        assertTrue(service.estConnecte());
    }

    // ---------- Compatibilité du hachage et erreurs techniques ----------

    @Test
    void connecterUtiliseLeNombreDIterationsEnregistrePourLUtilisateur() throws Exception {
        // Empreinte calculée ici, avec du code indépendant d'AuthService, avec 1000 itérations.
        byte[] sel = "sel-de-test-1234".getBytes(StandardCharsets.UTF_8);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("abcd".toCharArray(), sel, 1000, 256)).getEncoded();
        utilisateurDAO.ajouter(new Utilisateur("docteur", Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(sel), 1000));

        assertEquals("docteur", service.connecter("docteur", "abcd").getNomUtilisateur());
        service.deconnecter();
        assertConnexionRefusee("docteur", "abce", AuthService.MESSAGE_IDENTIFIANTS_INCORRECTS);
    }

    @Test
    void connecterTraduitDesDonneesIllisiblesEnErreurTechnique() throws Exception {
        utilisateurDAO.ajouter(new Utilisateur("casse", "pas du base64 !!!", "pas du base64 !!!", 1000));

        assertConnexionRefusee("casse", "abcd", ServiceException.MESSAGE_ERREUR_TECHNIQUE);
    }

    @Test
    void erreurTechniqueEstTraduiteEnMessageSimple() {
        // Base créée sans les tables : toute requête échoue côté SQLite.
        DatabaseConnection baseSansTables =
                new DatabaseConnection(dossierTemporaire.resolve("sans_tables.db"));
        AuthService serviceCasse = new AuthService(new UtilisateurDAO(baseSansTables));

        ServiceException erreur = assertThrows(ServiceException.class, () -> serviceCasse.estPremierLancement());

        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, erreur.getMessage());
        assertFalse(erreur.getMessage().toLowerCase().contains("sqlite"));
        assertNotNull(erreur.getCause());
        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, assertThrows(ServiceException.class,
                () -> serviceCasse.creerAdministrateur(NOM, MOT_DE_PASSE, MOT_DE_PASSE)).getMessage());
        assertEquals(ServiceException.MESSAGE_ERREUR_TECHNIQUE, assertThrows(ServiceException.class,
                () -> serviceCasse.connecter(NOM, MOT_DE_PASSE)).getMessage());
    }

    // ---------- Méthodes utilitaires ----------

    private void creerAdministrateur() throws ServiceException {
        service.creerAdministrateur(NOM, MOT_DE_PASSE, MOT_DE_PASSE);
    }

    private void assertCreationRefusee(String nom, String motDePasse, String confirmation,
                                       String debutMessage) throws SQLException {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.creerAdministrateur(nom, motDePasse, confirmation));
        assertTrue(erreur.getMessage().startsWith(debutMessage),
                "Message inattendu : " + erreur.getMessage());
        assertEquals(0, utilisateurDAO.compter());
    }

    private void assertConnexionRefusee(String nom, String motDePasse, String message) {
        ServiceException erreur = assertThrows(ServiceException.class,
                () -> service.connecter(nom, motDePasse));
        assertEquals(message, erreur.getMessage());
        assertFalse(service.estConnecte());
    }
}