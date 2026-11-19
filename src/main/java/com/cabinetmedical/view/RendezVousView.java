// src/main/java/com/cabinetmedical/view/RendezVousView.java
package com.cabinetmedical.view;

import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;
import com.cabinetmedical.util.FormatUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Section « Rendez-vous » : filtres, tableau des rendez-vous et formulaire de
 * planification / de modification. Même organisation que les sections Médecins et Patients.
 * Cette classe construit uniquement l'interface : aucune logique métier ici,
 * c'est RendezVousController qui relie la vue aux services.
 */
public class RendezVousView {

    /** Statut sur lequel porte le filtre (« Tous » = pas de filtre). */
    public enum Filtre {
        TOUS("Tous", null),
        PLANIFIE("Planifié", StatutRendezVous.PLANIFIE),
        EFFECTUE("Effectué", StatutRendezVous.EFFECTUE),
        ANNULE("Annulé", StatutRendezVous.ANNULE);

        private final String libelle;
        private final StatutRendezVous statut;

        Filtre(String libelle, StatutRendezVous statut) {
            this.libelle = libelle;
            this.statut = statut;
        }

        /** Le statut demandé, ou null pour tous les statuts. */
        public StatutRendezVous getStatut() {
            return statut;
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
    private static final LocalTime PREMIERE_HEURE = LocalTime.of(7, 0);
    private static final LocalTime DERNIERE_HEURE = LocalTime.of(20, 0);
    private static final int PAS_EN_MINUTES = 15;

    private final VBox racine = new VBox(16);

    private final ComboBox<Filtre> listeFiltres =
            new ComboBox<>(FXCollections.observableArrayList(Filtre.values()));
    private final TextField champRecherche = new TextField();
    private final Label resume = new Label();
    private final TableView<RendezVousDetail> tableau = new TableView<>();
    private final Label messageVide = new Label();

    private final Label titreFormulaire = new Label();
    private final Label message = new Label();
    private final Label aideFormulaire = new Label();
    private final ComboBox<Medecin> listeMedecins = new ComboBox<>();
    private final ComboBox<Patient> listePatients = new ComboBox<>();
    private final DatePicker champDate = new DatePicker();
    private final ComboBox<LocalTime> listeHeures = new ComboBox<>();
    private final Button boutonEnregistrer = new Button();
    private final Button boutonEffectue = new Button("Marquer comme effectué");
    private final Button boutonNouveau = new Button();
    private final Button boutonAnnulerRendezVous = new Button("Annuler le rendez-vous");

    // Faux pendant que le code modifie lui-même le filtre ou la sélection :
    // le contrôleur n'est alors pas prévenu (il l'est seulement pour une action de l'utilisateur).
    private boolean notificationsActives = true;
    private Runnable surRechercheModifiee;
    private Consumer<RendezVousDetail> surSelection;

    public RendezVousView() {
        configurerRecherche();
        configurerTableau();
        configurerFormulaire();
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

    public Button getBoutonEffectue() {
        return boutonEffectue;
    }

    public Button getBoutonNouveau() {
        return boutonNouveau;
    }

    public Button getBoutonAnnulerRendezVous() {
        return boutonAnnulerRendezVous;
    }

    /** Le statut choisi dans le filtre, ou null pour « Tous ». */
    public StatutRendezVous getStatutFiltre() {
        Filtre filtre = listeFiltres.getValue();
        return filtre == null ? null : filtre.getStatut();
    }

    public String getTexteRecherche() {
        return champRecherche.getText();
    }

    /** Le médecin choisi, ou null si aucun. */
    public Medecin getMedecin() {
        return listeMedecins.getValue();
    }

    /** Le patient choisi, ou null si aucun. */
    public Patient getPatient() {
        return listePatients.getValue();
    }

    /** La date choisie dans le calendrier, ou null si aucune. */
    public LocalDate getDate() {
        return champDate.getValue();
    }

    /** L'heure choisie, ou null si aucune. */
    public LocalTime getHeure() {
        return listeHeures.getValue();
    }

    // ---------- Réactions de l'utilisateur ----------

    /** Appelé quand l'utilisateur change le filtre de statut ou le texte de recherche. */
    public void setOnRechercheModifiee(Runnable action) {
        this.surRechercheModifiee = action;
    }

    /** Appelé quand l'utilisateur clique sur un rendez-vous du tableau. */
    public void setOnSelection(Consumer<RendezVousDetail> action) {
        this.surSelection = action;
    }

    // ---------- Choix des médecins et des patients ----------

    public void definirMedecins(List<Medecin> medecins) {
        listeMedecins.getItems().setAll(medecins);
    }

    public void definirPatients(List<Patient> patients) {
        listePatients.getItems().setAll(patients);
    }

    // ---------- Filtres et tableau ----------

    /** Remet les filtres à zéro (statut « Planifié », texte vide) sans prévenir le contrôleur. */
    public void reinitialiserRecherche() {
        notificationsActives = false;
        listeFiltres.setValue(Filtre.PLANIFIE);
        champRecherche.clear();
        notificationsActives = true;
    }

    /**
     * Remplace les lignes du tableau.
     *
     * @param filtre vrai si un filtre de statut ou un texte de recherche est actif
     * @param total  nombre de rendez-vous enregistrés (sans filtre)
     */
    public void afficherRendezVous(List<RendezVousDetail> rendezVous, boolean filtre, int total) {
        notificationsActives = false;
        tableau.getItems().setAll(rendezVous);
        tableau.getSelectionModel().clearSelection();
        notificationsActives = true;

        if (total == 0) {
            messageVide.setText("Aucun rendez-vous enregistré. L'agenda est encore vierge.");
            resume.setText("");
        } else if (filtre) {
            messageVide.setText("Aucun rendez-vous ne correspond à ces critères.");
            resume.setText(rendezVous.size() + (rendezVous.size() > 1 ? " rendez-vous affichés" : " rendez-vous affiché")
                    + " sur " + total);
        } else {
            resume.setText(total + " rendez-vous");
        }
    }

    /** Vide le tableau quand la lecture a échoué (pas de faux « agenda vide »). */
    public void afficherListeIndisponible() {
        notificationsActives = false;
        tableau.getItems().clear();
        notificationsActives = true;
        messageVide.setText("La liste des rendez-vous n'a pas pu être chargée.");
        resume.setText("");
    }

    /** Sélectionne la ligne du rendez-vous (sans prévenir le contrôleur). Sans effet s'il n'est pas affiché. */
    public void selectionner(int id) {
        int index = trouverIndex(id);
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

    /** Fait défiler le tableau jusqu'au rendez-vous, sans le sélectionner. */
    public void faireDefiler(int id) {
        int index = trouverIndex(id);
        if (index >= 0) {
            tableau.scrollTo(index);
        }
    }

    // ---------- Formulaire ----------

    /** Formulaire vide pour planifier un rendez-vous. */
    public void passerEnModeCreation() {
        listeMedecins.setValue(null);
        listePatients.setValue(null);
        champDate.setValue(null);
        listeHeures.setValue(null);
        listeMedecins.setDisable(false);
        listePatients.setDisable(false);
        champDate.setDisable(false);
        listeHeures.setDisable(false);

        titreFormulaire.setText("Nouveau rendez-vous");
        aideFormulaire.setText("Choisissez un médecin, un patient, puis une date et une heure "
                + "(par pas de 15 minutes). Une date passée ne peut pas être choisie.");
        boutonEnregistrer.setText("Planifier le rendez-vous");
        boutonNouveau.setText("Effacer les champs");
        afficher(boutonEnregistrer, true);
        afficher(boutonEffectue, false);
        afficher(boutonAnnulerRendezVous, false);
    }

    /**
     * Formulaire rempli avec le rendez-vous choisi. Le médecin et le patient ne changent jamais ;
     * seul un rendez-vous planifié peut être déplacé, marqué effectué ou annulé.
     * Un rendez-vous effectué ou annulé est affiché en lecture seule.
     */
    public void passerEnModeModification(RendezVousDetail detail) {
        RendezVous rendezVous = detail.getRendezVous();
        boolean planifie = rendezVous.getStatut() == StatutRendezVous.PLANIFIE;
        String statut = rendezVous.getStatut().getLibelle().toLowerCase(Locale.FRENCH);

        listeMedecins.setValue(detail.getMedecin());
        listePatients.setValue(detail.getPatient());
        champDate.setValue(rendezVous.getDateHeure().toLocalDate());
        listeHeures.setValue(rendezVous.getDateHeure().toLocalTime());
        listeMedecins.setDisable(true);
        listePatients.setDisable(true);
        champDate.setDisable(!planifie);
        listeHeures.setDisable(!planifie);

        titreFormulaire.setText("Rendez-vous " + statut + " de " + detail.getPatient().getNomComplet());
        if (planifie) {
            aideFormulaire.setText("Seuls la date et l'heure peuvent être changées. "
                    + "Le médecin et le patient restent ceux du rendez-vous.");
        } else {
            aideFormulaire.setText("Ce rendez-vous est " + statut
                    + " : il reste dans l'historique et ne peut plus être modifié.");
        }
        boutonEnregistrer.setText("Enregistrer la nouvelle date");
        boutonNouveau.setText("Nouveau rendez-vous");
        afficher(boutonEnregistrer, planifie);
        afficher(boutonEffectue, planifie);
        afficher(boutonAnnulerRendezVous, planifie);
    }

    public void focuserMedecin() {
        listeMedecins.requestFocus();
    }

    // ---------- Messages ----------

    public void afficherErreur(String texte) {
        afficherMessage(texte, STYLE_ERREUR);
    }

    public void afficherSucces(String texte) {
        afficherMessage(texte, STYLE_INFO);
    }

    /** Message d'information neutre (même présentation que le message de succès). */
    public void afficherInfo(String texte) {
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

    private static void afficher(Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }

    // ---------- Construction ----------

    private void configurerRecherche() {
        listeFiltres.setValue(Filtre.PLANIFIE);
        listeFiltres.setPrefWidth(150);
        listeFiltres.setMinWidth(Region.USE_PREF_SIZE);
        champRecherche.setPromptText("Rechercher un patient ou un médecin");
        champRecherche.setMaxWidth(Double.MAX_VALUE);

        listeFiltres.valueProperty().addListener((observable, ancien, nouveau) -> notifierRecherche());
        champRecherche.textProperty().addListener((observable, ancien, nouveau) -> notifierRecherche());
    }

    private void notifierRecherche() {
        if (notificationsActives && surRechercheModifiee != null) {
            surRechercheModifiee.run();
        }
    }

    private void configurerTableau() {
        tableau.getColumns().add(creerColonne("DATE ET HEURE", 150,
                detail -> FormatUtil.formaterDateHeure(detail.getRendezVous().getDateHeure())));
        tableau.getColumns().add(creerColonne("PATIENT", 200,
                detail -> detail.getPatient().getNomComplet()));
        tableau.getColumns().add(creerColonne("MÉDECIN", 200,
                detail -> detail.getMedecin().getNomComplet()));
        tableau.getColumns().add(creerColonneStatut());
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

    private TableColumn<RendezVousDetail, String> creerColonne(
            String titre, double largeur, Function<RendezVousDetail, String> valeur) {
        TableColumn<RendezVousDetail, String> colonne = new TableColumn<>(titre);
        colonne.setPrefWidth(largeur);
        colonne.setSortable(false);
        colonne.setReorderable(false);
        colonne.setCellValueFactory(cellule -> new ReadOnlyStringWrapper(valeur.apply(cellule.getValue())));
        return colonne;
    }

    /** Colonne du statut : une étiquette avec le libellé français, colorée selon le statut. */
    private TableColumn<RendezVousDetail, StatutRendezVous> creerColonneStatut() {
        TableColumn<RendezVousDetail, StatutRendezVous> colonne = new TableColumn<>("STATUT");
        colonne.setPrefWidth(130);
        colonne.setSortable(false);
        colonne.setReorderable(false);
        colonne.setCellValueFactory(cellule ->
                new ReadOnlyObjectWrapper<>(cellule.getValue().getRendezVous().getStatut()));
        colonne.setCellFactory(colonneTableau -> new TableCell<RendezVousDetail, StatutRendezVous>() {
            @Override
            protected void updateItem(StatutRendezVous statut, boolean vide) {
                super.updateItem(statut, vide);
                setText(null);
                setGraphic(vide || statut == null ? null : creerBadge(statut));
            }
        });
        return colonne;
    }

    private static Label creerBadge(StatutRendezVous statut) {
        Label badge = new Label(statut.getLibelle());
        badge.getStyleClass().add("badge-statut");
        String classe = switch (statut) {
            case PLANIFIE -> "badge-planifie";
            case EFFECTUE -> "badge-effectue";
            case ANNULE -> "badge-annule";
        };
        badge.getStyleClass().add(classe);
        return badge;
    }

    private void configurerFormulaire() {
        listeMedecins.setPromptText("Choisir un médecin");
        listeMedecins.setConverter(new StringConverter<Medecin>() {
            @Override
            public String toString(Medecin medecin) {
                return medecin == null ? "" : medecin.getNomComplet() + " (" + medecin.getMatricule() + ")";
            }

            @Override
            public Medecin fromString(String texte) {
                return null; // la liste n'est pas modifiable à la main
            }
        });

        listePatients.setPromptText("Choisir un patient");
        listePatients.setConverter(new StringConverter<Patient>() {
            @Override
            public String toString(Patient patient) {
                return patient == null ? ""
                        : patient.getNomComplet() + " (" + FormatUtil.formaterTelephone(patient.getNumeroTelephone()) + ")";
            }

            @Override
            public Patient fromString(String texte) {
                return null; // la liste n'est pas modifiable à la main
            }
        });

        // Date choisie uniquement dans le calendrier (pas de saisie au clavier, donc pas de texte illisible).
        champDate.setEditable(false);
        champDate.setPromptText("JJ/MM/AAAA");
        champDate.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return FormatUtil.formaterDate(date);
            }

            @Override
            public LocalDate fromString(String texte) {
                return FormatUtil.lireDate(texte);
            }
        });
        champDate.setDayCellFactory(selecteur -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean vide) {
                super.updateItem(date, vide);
                // Les jours passés ne peuvent pas être choisis (le service refuse aussi le passé).
                setDisable(vide || date.isBefore(LocalDate.now()));
            }
        });

        listeHeures.setPromptText("Choisir une heure");
        listeHeures.setVisibleRowCount(8);
        List<LocalTime> heures = new ArrayList<>();
        for (LocalTime heure = PREMIERE_HEURE; !heure.isAfter(DERNIERE_HEURE); heure = heure.plusMinutes(PAS_EN_MINUTES)) {
            heures.add(heure);
        }
        listeHeures.getItems().setAll(heures);
        listeHeures.setConverter(new StringConverter<LocalTime>() {
            @Override
            public String toString(LocalTime heure) {
                return FormatUtil.formaterHeure(heure);
            }

            @Override
            public LocalTime fromString(String texte) {
                return null; // la liste n'est pas modifiable à la main
            }
        });
    }

    private HBox construireBarreRecherche() {
        Label etiquette = new Label("STATUT");
        etiquette.getStyleClass().add("etiquette-champ");
        etiquette.setMinWidth(Region.USE_PREF_SIZE);

        resume.getStyleClass().add("texte-aide");
        resume.setMinWidth(Region.USE_PREF_SIZE);

        HBox.setHgrow(champRecherche, Priority.ALWAYS);
        HBox barre = new HBox(12, etiquette, listeFiltres, champRecherche, resume);
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

        aideFormulaire.getStyleClass().add("texte-aide");
        aideFormulaire.setWrapText(true);
        aideFormulaire.setMinHeight(Region.USE_PREF_SIZE);
        aideFormulaire.setPadding(new Insets(20, 0, 0, 0));
        GridPane.setValignment(aideFormulaire, VPos.TOP);

        GridPane grille = new GridPane();
        grille.setHgap(16);
        grille.setVgap(12);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints colonne = new ColumnConstraints();
            colonne.setPercentWidth(25);
            grille.getColumnConstraints().add(colonne);
        }
        grille.add(creerChamp("MÉDECIN", listeMedecins), 0, 0, 2, 1);
        grille.add(creerChamp("PATIENT", listePatients), 2, 0, 2, 1);
        grille.add(creerChamp("DATE", champDate), 0, 1);
        grille.add(creerChamp("HEURE", listeHeures), 1, 1);
        grille.add(aideFormulaire, 2, 1, 2, 1);

        return new VBox(12, regle, titreFormulaire, message, grille, construireBoutons());
    }

    private HBox construireBoutons() {
        boutonEnregistrer.getStyleClass().add("bouton-principal");
        boutonAnnulerRendezVous.getStyleClass().add("bouton-danger");
        for (Button bouton : List.of(boutonEnregistrer, boutonEffectue, boutonNouveau, boutonAnnulerRendezVous)) {
            bouton.setMinWidth(Region.USE_PREF_SIZE);
        }

        // Le bouton destructif est isolé à droite, loin du bouton principal.
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox boutons = new HBox(12, boutonEnregistrer, boutonEffectue, boutonNouveau, espace, boutonAnnulerRendezVous);
        boutons.setAlignment(Pos.CENTER_LEFT);
        return boutons;
    }

    private VBox creerChamp(String etiquette, Region champ) {
        Label label = new Label(etiquette);
        label.getStyleClass().add("etiquette-champ");
        champ.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, label, champ);
    }

    private int trouverIndex(int id) {
        List<RendezVousDetail> lignes = tableau.getItems();
        for (int i = 0; i < lignes.size(); i++) {
            if (lignes.get(i).getRendezVous().getId() == id) {
                return i;
            }
        }
        return -1;
    }
}