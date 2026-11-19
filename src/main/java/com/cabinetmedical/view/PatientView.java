// src/main/java/com/cabinetmedical/view/PatientView.java
package com.cabinetmedical.view;

import com.cabinetmedical.model.Patient;
import com.cabinetmedical.util.FormatUtil;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Section « Patients » : barre de recherche, tableau des patients et formulaire
 * d'ajout / de modification. Même organisation que la section Médecins.
 * Cette classe construit uniquement l'interface : aucune logique métier ici,
 * c'est PatientController qui relie la vue au service.
 */
public class PatientView {

    /** Champ sur lequel porte la recherche. */
    public enum Critere {
        NOM("Nom", "Rechercher par nom"),
        PRENOM("Prénom", "Rechercher par prénom"),
        TELEPHONE("Téléphone", "Rechercher par numéro de téléphone");

        private final String libelle;
        private final String invite;

        Critere(String libelle, String invite) {
            this.libelle = libelle;
            this.invite = invite;
        }

        public String getInvite() {
            return invite;
        }

        /** Le texte affiché dans la liste déroulante. */
        @Override
        public String toString() {
            return libelle;
        }
    }

    private static final String STYLE_ERREUR = "message-erreur";
    private static final String STYLE_INFO = "message-info";
    private static final double HAUTEUR_LIGNE_TABLEAU = 34;

    private final VBox racine = new VBox(16);

    private final ComboBox<Critere> listeCriteres =
            new ComboBox<>(FXCollections.observableArrayList(Critere.values()));
    private final TextField champRecherche = new TextField();
    private final Label resume = new Label();
    private final TableView<Patient> tableau = new TableView<>();
    private final Label messageVide = new Label();

    private final Label titreFormulaire = new Label();
    private final Label message = new Label();
    private final TextField champNom = new TextField();
    private final TextField champPrenom = new TextField();
    private final TextField champTelephone = new TextField();
    private final TextField champDateNaissance = new TextField();
    private final Button boutonEnregistrer = new Button();
    private final Button boutonAnnuler = new Button();
    private final Button boutonSupprimer = new Button("Supprimer");

    // Faux pendant que le code modifie lui-même la recherche ou la sélection :
    // le contrôleur n'est alors pas prévenu (il l'est seulement pour une action de l'utilisateur).
    private boolean notificationsActives = true;
    private Runnable surRechercheModifiee;
    private Consumer<Patient> surSelection;

    public PatientView() {
        configurerRecherche();
        configurerTableau();
        racine.getChildren().addAll(construireBarreRecherche(), tableau, construireFormulaire());
        VBox.setVgrow(tableau, Priority.ALWAYS);
        passerEnModeCreation();
    }

    // ---------- Accès aux éléments ----------

    public Parent getRacine() {
        return racine;
    }

    public Button getBoutonEnregistrer() {
        return boutonEnregistrer;
    }

    public Button getBoutonSupprimer() {
        return boutonSupprimer;
    }

    public Button getBoutonAnnuler() {
        return boutonAnnuler;
    }

    public Critere getCritere() {
        return listeCriteres.getValue();
    }

    public String getTexteRecherche() {
        return champRecherche.getText();
    }

    public String getNom() {
        return champNom.getText();
    }

    public String getPrenom() {
        return champPrenom.getText();
    }

    public String getTelephone() {
        return champTelephone.getText();
    }

    /** Texte de la date tel qu'il est saisi (jj/MM/aaaa) : c'est le contrôleur qui le convertit. */
    public String getDateNaissance() {
        return champDateNaissance.getText();
    }

    // ---------- Réactions de l'utilisateur ----------

    /** Appelé quand l'utilisateur change le texte ou le critère de recherche. */
    public void setOnRechercheModifiee(Runnable action) {
        this.surRechercheModifiee = action;
    }

    /** Appelé quand l'utilisateur clique sur un patient du tableau. */
    public void setOnSelection(Consumer<Patient> action) {
        this.surSelection = action;
    }

    /** Appelé quand l'utilisateur appuie sur Entrée dans un champ du formulaire. */
    public void setOnValidation(Runnable action) {
        for (TextField champ : List.of(champNom, champPrenom, champTelephone, champDateNaissance)) {
            champ.setOnAction(evenement -> action.run());
        }
    }

    // ---------- Recherche et tableau ----------

    /** Remet la recherche à zéro (critère « Nom », texte vide) sans prévenir le contrôleur. */
    public void reinitialiserRecherche() {
        notificationsActives = false;
        listeCriteres.setValue(Critere.NOM);
        champRecherche.clear();
        notificationsActives = true;
    }

    /**
     * Remplace les lignes du tableau.
     *
     * @param recherche vrai si un texte de recherche est saisi
     * @param total     nombre de patients enregistrés (sans filtre)
     */
    public void afficherPatients(List<Patient> patients, boolean recherche, int total) {
        notificationsActives = false;
        tableau.getItems().setAll(patients);
        tableau.getSelectionModel().clearSelection();
        notificationsActives = true;

        if (total == 0) {
            messageVide.setText("Aucun patient enregistré. Le classeur est encore vide.");
            resume.setText("");
        } else if (recherche) {
            messageVide.setText("Aucun patient ne correspond à cette recherche.");
            resume.setText(patients.size() + (patients.size() > 1 ? " résultats" : " résultat") + " sur " + total);
        } else {
            resume.setText(total + (total > 1 ? " patients enregistrés" : " patient enregistré"));
        }
    }

    /** Vide le tableau quand la lecture des patients a échoué (pas de faux « registre vide »). */
    public void afficherListeIndisponible() {
        notificationsActives = false;
        tableau.getItems().clear();
        notificationsActives = true;
        messageVide.setText("La liste des patients n'a pas pu être chargée.");
        resume.setText("");
    }

    /** Sélectionne la ligne du patient (sans prévenir le contrôleur). Sans effet s'il n'est pas affiché. */
    public void selectionner(String numeroTelephone) {
        int index = trouverIndex(numeroTelephone);
        if (index >= 0) {
            notificationsActives = false;
            tableau.getSelectionModel().select(index);
            notificationsActives = true;
        }
    }

    public void deselectionner() {
        notificationsActives = false;
        tableau.getSelectionModel().clearSelection();
        notificationsActives = true;
    }

    /** Fait défiler le tableau jusqu'au patient, sans le sélectionner. */
    public void faireDefiler(String numeroTelephone) {
        int index = trouverIndex(numeroTelephone);
        if (index >= 0) {
            tableau.scrollTo(index);
        }
    }

    // ---------- Formulaire ----------

    /** Formulaire vide pour ajouter un patient. */
    public void passerEnModeCreation() {
        champNom.clear();
        champPrenom.clear();
        champTelephone.clear();
        champDateNaissance.clear();
        titreFormulaire.setText("Nouveau patient");
        boutonEnregistrer.setText("Ajouter le patient");
        boutonAnnuler.setText("Effacer les champs");
        boutonSupprimer.setVisible(false);
        boutonSupprimer.setManaged(false);
    }

    /** Formulaire rempli avec le patient choisi (le numéro de téléphone reste modifiable). */
    public void passerEnModeModification(Patient patient) {
        champNom.setText(patient.getNom());
        champPrenom.setText(patient.getPrenom());
        champTelephone.setText(FormatUtil.formaterTelephone(patient.getNumeroTelephone()));
        champDateNaissance.setText(FormatUtil.formaterDate(patient.getDateNaissance()));
        titreFormulaire.setText("Modifier le patient " + patient.getNomComplet());
        boutonEnregistrer.setText("Enregistrer les modifications");
        boutonAnnuler.setText("Annuler");
        boutonSupprimer.setVisible(true);
        boutonSupprimer.setManaged(true);
    }

    public void focuserNom() {
        champNom.requestFocus();
    }

    // ---------- Messages ----------

    public void afficherErreur(String texte) {
        afficherMessage(texte, STYLE_ERREUR);
    }

    public void afficherSucces(String texte) {
        afficherMessage(texte, STYLE_INFO);
    }

    public void effacerMessage() {
        message.setText("");
        message.setVisible(false);
        message.setManaged(false);
    }

    private void afficherMessage(String texte, String classeStyle) {
        message.getStyleClass().removeAll(STYLE_ERREUR, STYLE_INFO);
        message.getStyleClass().add(classeStyle);
        message.setText(texte);
        message.setVisible(true);
        message.setManaged(true);
    }

    // ---------- Construction ----------

    private void configurerRecherche() {
        listeCriteres.setValue(Critere.NOM);
        listeCriteres.setPrefWidth(150);
        listeCriteres.setMinWidth(Region.USE_PREF_SIZE);
        champRecherche.setPromptText(Critere.NOM.getInvite());
        champRecherche.setMaxWidth(Double.MAX_VALUE);

        listeCriteres.valueProperty().addListener((observable, ancien, nouveau) -> {
            champRecherche.setPromptText(nouveau.getInvite());
            notifierRecherche();
        });
        champRecherche.textProperty().addListener((observable, ancien, nouveau) -> notifierRecherche());
    }

    private void notifierRecherche() {
        if (notificationsActives && surRechercheModifiee != null) {
            surRechercheModifiee.run();
        }
    }

    private void configurerTableau() {
        tableau.getColumns().add(creerColonne("NOM", 150, Patient::getNom));
        tableau.getColumns().add(creerColonne("PRÉNOM", 150, Patient::getPrenom));
        tableau.getColumns().add(creerColonne("TÉLÉPHONE", 120,
                patient -> FormatUtil.formaterTelephone(patient.getNumeroTelephone())));
        tableau.getColumns().add(creerColonne("DATE DE NAISSANCE", 160,
                patient -> FormatUtil.formaterDate(patient.getDateNaissance())));
        tableau.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableau.setFixedCellSize(HAUTEUR_LIGNE_TABLEAU);
        // Hauteur minimale : l'en-tête + 4 lignes. Le tableau grandit ensuite avec la fenêtre.
        tableau.setMinHeight(5 * HAUTEUR_LIGNE_TABLEAU + 6);
        tableau.setPrefHeight(7 * HAUTEUR_LIGNE_TABLEAU + 6);

        messageVide.getStyleClass().add("etat-vide");
        messageVide.setWrapText(true);
        tableau.setPlaceholder(messageVide);

        tableau.getSelectionModel().selectedItemProperty().addListener((observable, ancien, nouveau) -> {
            if (notificationsActives && nouveau != null && surSelection != null) {
                surSelection.accept(nouveau);
            }
        });
    }

    private TableColumn<Patient, String> creerColonne(
            String titre, double largeur, Function<Patient, String> valeur) {
        TableColumn<Patient, String> colonne = new TableColumn<>(titre);
        colonne.setPrefWidth(largeur);
        colonne.setSortable(false);
        colonne.setReorderable(false);
        colonne.setCellValueFactory(cellule -> new ReadOnlyStringWrapper(valeur.apply(cellule.getValue())));
        return colonne;
    }

    private HBox construireBarreRecherche() {
        Label etiquette = new Label("RECHERCHER PAR");
        etiquette.getStyleClass().add("etiquette-champ");
        etiquette.setMinWidth(Region.USE_PREF_SIZE);

        resume.getStyleClass().add("texte-aide");
        resume.setMinWidth(Region.USE_PREF_SIZE);

        HBox.setHgrow(champRecherche, Priority.ALWAYS);
        HBox barre = new HBox(12, etiquette, listeCriteres, champRecherche, resume);
        barre.setAlignment(Pos.CENTER_LEFT);
        return barre;
    }

    private VBox construireFormulaire() {
        Region regle = new Region();
        regle.getStyleClass().add("regle");
        regle.setPrefHeight(1);
        regle.setMinHeight(1);
        regle.setMaxHeight(1);

        titreFormulaire.getStyleClass().add("titre-section");
        titreFormulaire.setWrapText(true);
        titreFormulaire.setMinHeight(Region.USE_PREF_SIZE);

        message.getStyleClass().add("message");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setMinHeight(Region.USE_PREF_SIZE);
        effacerMessage();

        champTelephone.setPromptText("8 chiffres");
        champDateNaissance.setPromptText("JJ/MM/AAAA");

        Label aide = new Label("Date au format JJ/MM/AAAA, par exemple 25/03/1990. "
                + "Le numéro de téléphone identifie le patient.");
        aide.getStyleClass().add("texte-aide");
        aide.setWrapText(true);
        aide.setMinHeight(Region.USE_PREF_SIZE);
        aide.setPadding(new Insets(20, 0, 0, 0));
        GridPane.setValignment(aide, VPos.TOP);

        GridPane grille = new GridPane();
        grille.setHgap(16);
        grille.setVgap(12);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints colonne = new ColumnConstraints();
            colonne.setPercentWidth(100.0 / 3);
            grille.getColumnConstraints().add(colonne);
        }
        grille.add(creerChamp("NOM", champNom), 0, 0);
        grille.add(creerChamp("PRÉNOM", champPrenom), 1, 0);
        grille.add(creerChamp("TÉLÉPHONE", champTelephone), 2, 0);
        grille.add(creerChamp("DATE DE NAISSANCE", champDateNaissance), 0, 1);
        grille.add(aide, 1, 1, 2, 1);

        return new VBox(12, regle, titreFormulaire, message, grille, construireBoutons());
    }

    private HBox construireBoutons() {
        boutonEnregistrer.getStyleClass().add("bouton-principal");
        boutonSupprimer.getStyleClass().add("bouton-danger");
        for (Button bouton : List.of(boutonEnregistrer, boutonAnnuler, boutonSupprimer)) {
            bouton.setMinWidth(Region.USE_PREF_SIZE);
        }

        // Le bouton destructif est isolé à droite, loin du bouton principal.
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox boutons = new HBox(12, boutonEnregistrer, boutonAnnuler, espace, boutonSupprimer);
        boutons.setAlignment(Pos.CENTER_LEFT);
        return boutons;
    }

    private VBox creerChamp(String etiquette, TextField champ) {
        Label label = new Label(etiquette);
        label.getStyleClass().add("etiquette-champ");
        champ.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, label, champ);
    }

    private int trouverIndex(String numeroTelephone) {
        List<Patient> lignes = tableau.getItems();
        for (int i = 0; i < lignes.size(); i++) {
            if (lignes.get(i).getNumeroTelephone().equals(numeroTelephone)) {
                return i;
            }
        }
        return -1;
    }
}