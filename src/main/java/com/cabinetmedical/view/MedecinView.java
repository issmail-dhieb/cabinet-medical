// src/main/java/com/cabinetmedical/view/MedecinView.java
package com.cabinetmedical.view;

import com.cabinetmedical.model.Medecin;
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
 * Section « Médecins » : barre de recherche, tableau des médecins et formulaire
 * d'ajout / de modification (dans une carte blanche). Cette classe construit uniquement
 * l'interface : aucune logique métier ici, c'est MedecinController qui relie la vue au service.
 */
public class MedecinView {

    /** Champ sur lequel porte la recherche. */
    public enum Critere {
        MATRICULE("Matricule", "Rechercher par matricule"),
        NOM("Nom", "Rechercher par nom ou prénom"),
        SPECIALITE("Spécialité", "Rechercher par spécialité");

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

    private static final String STYLE_VERROUILLE = "champ-verrouille";
    private static final String STYLE_ERREUR = "message-erreur";
    private static final String STYLE_INFO = "message-info";
    private static final double HAUTEUR_LIGNE_TABLEAU = 34;

    private final VBox racine = new VBox(16);

    private final ComboBox<Critere> listeCriteres =
            new ComboBox<>(FXCollections.observableArrayList(Critere.values()));
    private final TextField champRecherche = new TextField();
    private final Label resume = new Label();
    private final TableView<Medecin> tableau = new TableView<>();
    private final Label messageVide = new Label();

    private final Label titreFormulaire = new Label();
    private final Label message = new Label();
    private final TextField champMatricule = new TextField();
    private final TextField champNom = new TextField();
    private final TextField champPrenom = new TextField();
    private final TextField champSpecialite = new TextField();
    private final TextField champTelephone = new TextField();
    private final Button boutonEnregistrer = new Button();
    private final Button boutonAnnuler = new Button();
    private final Button boutonSupprimer = new Button("Supprimer");

    // Faux pendant que le code modifie lui-même la recherche ou la sélection :
    // le contrôleur n'est alors pas prévenu (il l'est seulement pour une action de l'utilisateur).
    private boolean notificationsActives = true;
    private Runnable surRechercheModifiee;
    private Consumer<Medecin> surSelection;

    public MedecinView() {
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

    public String getMatricule() {
        return champMatricule.getText();
    }

    public String getNom() {
        return champNom.getText();
    }

    public String getPrenom() {
        return champPrenom.getText();
    }

    public String getSpecialite() {
        return champSpecialite.getText();
    }

    public String getTelephone() {
        return champTelephone.getText();
    }

    // ---------- Réactions de l'utilisateur ----------

    /** Appelé quand l'utilisateur change le texte ou le critère de recherche. */
    public void setOnRechercheModifiee(Runnable action) {
        this.surRechercheModifiee = action;
    }

    /** Appelé quand l'utilisateur clique sur un médecin du tableau. */
    public void setOnSelection(Consumer<Medecin> action) {
        this.surSelection = action;
    }

    /** Appelé quand l'utilisateur appuie sur Entrée dans un champ du formulaire. */
    public void setOnValidation(Runnable action) {
        for (TextField champ : List.of(champMatricule, champNom, champPrenom, champSpecialite, champTelephone)) {
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
     * @param total     nombre de médecins enregistrés (sans filtre)
     */
    public void afficherMedecins(List<Medecin> medecins, boolean recherche, int total) {
        notificationsActives = false;
        tableau.getItems().setAll(medecins);
        tableau.getSelectionModel().clearSelection();
        notificationsActives = true;

        if (total == 0) {
            messageVide.setText("Aucun médecin enregistré. Le registre attend son premier nom.");
            resume.setText("");
        } else if (recherche) {
            messageVide.setText("Aucun médecin ne correspond à cette recherche.");
            resume.setText(medecins.size() + (medecins.size() > 1 ? " résultats" : " résultat") + " sur " + total);
        } else {
            resume.setText(total + (total > 1 ? " médecins enregistrés" : " médecin enregistré"));
        }
    }

    /** Vide le tableau quand la lecture des médecins a échoué (pas de faux « registre vide »). */
    public void afficherListeIndisponible() {
        notificationsActives = false;
        tableau.getItems().clear();
        notificationsActives = true;
        messageVide.setText("La liste des médecins n'a pas pu être chargée.");
        resume.setText("");
    }

    /** Sélectionne la ligne du médecin (sans prévenir le contrôleur). Sans effet s'il n'est pas affiché. */
    public void selectionner(String matricule) {
        int index = trouverIndex(matricule);
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

    /** Fait défiler le tableau jusqu'au médecin, sans le sélectionner. */
    public void faireDefiler(String matricule) {
        int index = trouverIndex(matricule);
        if (index >= 0) {
            tableau.scrollTo(index);
        }
    }

    // ---------- Formulaire ----------

    /** Formulaire vide pour ajouter un médecin : le matricule est modifiable. */
    public void passerEnModeCreation() {
        champMatricule.clear();
        champNom.clear();
        champPrenom.clear();
        champSpecialite.clear();
        champTelephone.clear();
        definirMatriculeVerrouille(false);
        titreFormulaire.setText("Nouveau médecin");
        boutonEnregistrer.setText("Ajouter le médecin");
        boutonAnnuler.setText("Effacer les champs");
        boutonSupprimer.setVisible(false);
        boutonSupprimer.setManaged(false);
    }

    /** Formulaire rempli avec le médecin choisi : le matricule est verrouillé. */
    public void passerEnModeModification(Medecin medecin) {
        champMatricule.setText(medecin.getMatricule());
        champNom.setText(medecin.getNom());
        champPrenom.setText(medecin.getPrenom());
        champSpecialite.setText(medecin.getSpecialite());
        champTelephone.setText(FormatUtil.formaterTelephone(medecin.getTelephone()));
        definirMatriculeVerrouille(true);
        titreFormulaire.setText("Modifier le médecin " + medecin.getNomComplet());
        boutonEnregistrer.setText("Enregistrer les modifications");
        boutonAnnuler.setText("Annuler");
        boutonSupprimer.setVisible(true);
        boutonSupprimer.setManaged(true);
    }

    public void focuserMatricule() {
        champMatricule.requestFocus();
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
        champRecherche.getStyleClass().add("champ-recherche");
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
        tableau.getColumns().add(creerColonne("MATRICULE", 110, Medecin::getMatricule));
        tableau.getColumns().add(creerColonne("NOM", 140, Medecin::getNom));
        tableau.getColumns().add(creerColonne("PRÉNOM", 140, Medecin::getPrenom));
        tableau.getColumns().add(creerColonne("SPÉCIALITÉ", 170, Medecin::getSpecialite));
        tableau.getColumns().add(creerColonne("TÉLÉPHONE", 110,
                medecin -> FormatUtil.formaterTelephone(medecin.getTelephone())));
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

    private TableColumn<Medecin, String> creerColonne(
            String titre, double largeur, Function<Medecin, String> valeur) {
        TableColumn<Medecin, String> colonne = new TableColumn<>(titre);
        colonne.setPrefWidth(largeur);
        colonne.setSortable(false);
        colonne.setReorderable(false);
        colonne.setCellValueFactory(cellule -> new ReadOnlyStringWrapper(valeur.apply(cellule.getValue())));
        return colonne;
    }

    /** Barre de recherche dans une carte blanche (le champ en pilule grise reste visible dessus). */
    private HBox construireBarreRecherche() {
        Label etiquette = new Label("RECHERCHER PAR");
        etiquette.getStyleClass().add("etiquette-champ");
        etiquette.setMinWidth(Region.USE_PREF_SIZE);

        resume.getStyleClass().add("texte-aide");
        resume.setMinWidth(Region.USE_PREF_SIZE);

        HBox.setHgrow(champRecherche, Priority.ALWAYS);
        HBox barre = new HBox(12, etiquette, listeCriteres, champRecherche, resume);
        barre.getStyleClass().add("carte");
        barre.setPadding(new Insets(10, 16, 10, 16));
        barre.setAlignment(Pos.CENTER_LEFT);
        return barre;
    }

    /** Formulaire dans une carte blanche arrondie. */
    private VBox construireFormulaire() {
        titreFormulaire.getStyleClass().add("titre-section");
        titreFormulaire.setWrapText(true);
        titreFormulaire.setMinHeight(Region.USE_PREF_SIZE);

        message.getStyleClass().add("message");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setMinHeight(Region.USE_PREF_SIZE);
        effacerMessage();

        champTelephone.setPromptText("8 chiffres");

        Label aide = new Label("Le matricule est définitif après l'ajout.");
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
        grille.add(creerChamp("MATRICULE", champMatricule), 0, 0);
        grille.add(creerChamp("NOM", champNom), 1, 0);
        grille.add(creerChamp("PRÉNOM", champPrenom), 2, 0);
        grille.add(creerChamp("SPÉCIALITÉ", champSpecialite), 0, 1);
        grille.add(creerChamp("TÉLÉPHONE", champTelephone), 1, 1);
        grille.add(aide, 2, 1);

        VBox formulaire = new VBox(12, titreFormulaire, message, grille, construireBoutons());
        formulaire.getStyleClass().add("carte");
        formulaire.setPadding(new Insets(20));
        return formulaire;
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

    private void definirMatriculeVerrouille(boolean verrouille) {
        champMatricule.setEditable(!verrouille);
        champMatricule.setFocusTraversable(!verrouille);
        champMatricule.getStyleClass().remove(STYLE_VERROUILLE);
        if (verrouille) {
            champMatricule.getStyleClass().add(STYLE_VERROUILLE);
        }
    }

    private int trouverIndex(String matricule) {
        List<Medecin> lignes = tableau.getItems();
        for (int i = 0; i < lignes.size(); i++) {
            if (lignes.get(i).getMatricule().equals(matricule)) {
                return i;
            }
        }
        return -1;
    }
}