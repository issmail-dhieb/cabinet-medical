// src/main/java/com/cabinetmedical/view/LoginView.java
package com.cabinetmedical.view;

import com.cabinetmedical.service.AuthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran d'accueil : panneau de marque à gauche, formulaire (dans une carte blanche) à droite.
 * Le formulaire est soit la connexion, soit la création du compte administrateur
 * (premier lancement). Cette classe construit uniquement l'interface : aucune logique
 * métier ici, c'est LoginController qui décide quoi afficher.
 */
public class LoginView {

    private static final double LARGEUR_PANNEAU_MARQUE = 340;
    private static final double LARGEUR_FORMULAIRE = 440;

    private final BorderPane racine = new BorderPane();
    private final StackPane zoneFormulaire = new StackPane();

    // Formulaire de connexion
    private final TextField champNomConnexion = new TextField();
    private final PasswordField champMotDePasseConnexion = new PasswordField();
    private final Button boutonConnexion = new Button("Se connecter");
    private final Label messageInfoConnexion = creerMessage("message-info");
    private final Label messageErreurConnexion = creerMessage("message-erreur");
    private final VBox formulaireConnexion;

    // Formulaire de création du compte administrateur
    private final TextField champNomCreation = new TextField();
    private final PasswordField champMotDePasseCreation = new PasswordField();
    private final PasswordField champConfirmationCreation = new PasswordField();
    private final Button boutonCreation = new Button("Créer le compte");
    private final Label messageErreurCreation = creerMessage("message-erreur");
    private final VBox formulaireCreation;

    public LoginView() {
        formulaireConnexion = construireFormulaireConnexion();
        formulaireCreation = construireFormulaireCreation();

        racine.getStyleClass().add("racine-ecran");
        racine.setLeft(construirePanneauMarque());
        zoneFormulaire.getStyleClass().add("zone-formulaire");
        racine.setCenter(zoneFormulaire);
    }

    // ---------- Affichage ----------

    public Parent getRacine() {
        return racine;
    }

    /** Affiche le formulaire de connexion (les anciens messages sont effacés). */
    public void afficherConnexion() {
        effacerMessages();
        zoneFormulaire.getChildren().setAll(formulaireConnexion);
        focuserNomConnexion();
    }

    /** Affiche le formulaire de création du compte administrateur (premier lancement). */
    public void afficherCreation() {
        effacerMessages();
        zoneFormulaire.getChildren().setAll(formulaireCreation);
        Platform.runLater(champNomCreation::requestFocus);
    }

    // ---------- Boutons ----------

    public Button getBoutonConnexion() {
        return boutonConnexion;
    }

    public Button getBoutonCreation() {
        return boutonCreation;
    }

    // ---------- Valeurs saisies ----------

    public String getNomConnexion() {
        return champNomConnexion.getText();
    }

    public String getMotDePasseConnexion() {
        return champMotDePasseConnexion.getText();
    }

    public String getNomCreation() {
        return champNomCreation.getText();
    }

    public String getMotDePasseCreation() {
        return champMotDePasseCreation.getText();
    }

    public String getConfirmationCreation() {
        return champConfirmationCreation.getText();
    }

    // ---------- Messages ----------

    public void afficherErreurConnexion(String message) {
        afficherMessage(messageErreurConnexion, message);
    }

    public void afficherInfoConnexion(String message) {
        afficherMessage(messageInfoConnexion, message);
    }

    public void afficherErreurCreation(String message) {
        afficherMessage(messageErreurCreation, message);
    }

    public void effacerMessages() {
        cacherMessage(messageInfoConnexion);
        cacherMessage(messageErreurConnexion);
        cacherMessage(messageErreurCreation);
    }

    // ---------- Champs ----------

    public void preremplirNomConnexion(String nom) {
        champNomConnexion.setText(nom);
    }

    public void viderChampsConnexion() {
        champNomConnexion.setText("");
        champMotDePasseConnexion.setText("");
    }

    public void viderMotDePasseConnexion() {
        champMotDePasseConnexion.setText("");
    }

    public void viderChampsCreation() {
        champNomCreation.setText("");
        viderMotsDePasseCreation();
    }

    public void viderMotsDePasseCreation() {
        champMotDePasseCreation.setText("");
        champConfirmationCreation.setText("");
    }

    public void focuserNomConnexion() {
        Platform.runLater(champNomConnexion::requestFocus);
    }

    public void focuserMotDePasseConnexion() {
        Platform.runLater(champMotDePasseConnexion::requestFocus);
    }

    // ---------- Construction ----------

    private VBox construirePanneauMarque() {
        Label nom = new Label("Cabinet médical");
        nom.getStyleClass().add("marque-nom");

        Region filet = new Region();
        filet.getStyleClass().add("filet-marque");
        filet.setPrefSize(48, 4);
        filet.setMinSize(48, 4);
        filet.setMaxSize(48, 4);

        Label slogan = creerTexteMultiligne(
                "Ici, on soigne les patients. Pas les mots de passe oubliés.", "marque-slogan");

        VBox panneau = new VBox(16, nom, filet, slogan);
        panneau.getStyleClass().add("panneau-marque");
        panneau.setAlignment(Pos.CENTER_LEFT);
        panneau.setPadding(new Insets(40));
        panneau.setPrefWidth(LARGEUR_PANNEAU_MARQUE);
        panneau.setMinWidth(LARGEUR_PANNEAU_MARQUE);
        panneau.setMaxWidth(LARGEUR_PANNEAU_MARQUE);
        return panneau;
    }

    private VBox construireFormulaireConnexion() {
        boutonConnexion.getStyleClass().add("bouton-principal");
        boutonConnexion.setDefaultButton(true);
        return assemblerFormulaire(
                "Connexion",
                "Identifiez-vous pour accéder au cabinet.",
                messageInfoConnexion,
                messageErreurConnexion,
                creerChamp("NOM D'UTILISATEUR", champNomConnexion),
                creerChamp("MOT DE PASSE", champMotDePasseConnexion),
                boutonConnexion);
    }

    private VBox construireFormulaireCreation() {
        boutonCreation.getStyleClass().add("bouton-principal");
        boutonCreation.setDefaultButton(true);
        String aideNom = AuthService.LONGUEUR_MIN_NOM_UTILISATEUR + " à "
                + AuthService.LONGUEUR_MAX_NOM_UTILISATEUR
                + " caractères : lettres sans accent, chiffres, point, tiret ou tiret bas.";
        String aideMotDePasse = AuthService.LONGUEUR_MIN_MOT_DE_PASSE
                + " caractères minimum. « 1234 » passe le test, pas le bon sens.";
        return assemblerFormulaire(
                "Compte administrateur",
                "Premier lancement. Vous êtes le seul à pouvoir ouvrir la porte : "
                        + "choisissez la clé avec soin.",
                messageErreurCreation,
                creerChampAvecAide("NOM D'UTILISATEUR", champNomCreation, aideNom),
                creerChampAvecAide("MOT DE PASSE", champMotDePasseCreation, aideMotDePasse),
                creerChamp("CONFIRMATION DU MOT DE PASSE", champConfirmationCreation),
                boutonCreation);
    }

    /** Assemble un formulaire dans une carte blanche arrondie. */
    private VBox assemblerFormulaire(String titre, String sousTitre, Node... contenu) {
        Label labelTitre = new Label(titre);
        labelTitre.getStyleClass().add("titre-page");
        labelTitre.setWrapText(true);
        labelTitre.setMinHeight(Region.USE_PREF_SIZE);

        Label labelSousTitre = creerTexteMultiligne(sousTitre, "sous-titre");

        Region regle = new Region();
        regle.getStyleClass().add("regle");
        regle.setPrefHeight(1);
        regle.setMinHeight(1);
        regle.setMaxHeight(1);

        VBox formulaire = new VBox(12);
        formulaire.getChildren().addAll(labelTitre, labelSousTitre, regle);
        formulaire.getChildren().addAll(contenu);
        formulaire.getStyleClass().add("carte");
        formulaire.setPadding(new Insets(28));
        formulaire.setAlignment(Pos.TOP_LEFT);
        formulaire.setMaxWidth(LARGEUR_FORMULAIRE);
        formulaire.setMaxHeight(Region.USE_PREF_SIZE);
        return formulaire;
    }

    private VBox creerChamp(String etiquette, Node champ) {
        Label labelEtiquette = new Label(etiquette);
        labelEtiquette.getStyleClass().add("etiquette-champ");
        labelEtiquette.setLabelFor(champ);
        return new VBox(4, labelEtiquette, champ);
    }

    private VBox creerChampAvecAide(String etiquette, Node champ, String aide) {
        VBox bloc = creerChamp(etiquette, champ);
        bloc.getChildren().add(creerTexteMultiligne(aide, "texte-aide"));
        return bloc;
    }

    private static Label creerTexteMultiligne(String texte, String classeStyle) {
        Label label = new Label(texte);
        label.getStyleClass().add(classeStyle);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label creerMessage(String classeStyle) {
        Label message = new Label();
        message.getStyleClass().addAll("message", classeStyle);
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setMinHeight(Region.USE_PREF_SIZE);
        message.setVisible(false);
        message.setManaged(false);
        return message;
    }

    private static void afficherMessage(Label message, String texte) {
        message.setText(texte);
        message.setVisible(true);
        message.setManaged(true);
    }

    private static void cacherMessage(Label message) {
        message.setText("");
        message.setVisible(false);
        message.setManaged(false);
    }
}