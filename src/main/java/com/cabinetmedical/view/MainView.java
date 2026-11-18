// src/main/java/com/cabinetmedical/view/MainView.java
package com.cabinetmedical.view;

import com.cabinetmedical.model.RendezVousDetail;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Fenêtre principale : navigation à gauche (encre), contenu à droite (papier).
 * Cette classe construit uniquement l'interface : aucune logique métier ici,
 * c'est MainController qui fournit les données et réagit aux boutons.
 */
public class MainView {

    /** Sections de la navigation, avec les textes affichés pour chacune. */
    public enum Section {
        TABLEAU_DE_BORD("Tableau de bord",
                "Situation du cabinet à l'instant T. Aucun chiffre n'a été embelli.",
                ""),
        MEDECINS("Médecins",
                "Registre des médecins du cabinet.",
                "Le registre des médecins est en cours d'impression. "
                        + "Cette section sera disponible prochainement."),
        PATIENTS("Patients",
                "Dossiers des patients du cabinet.",
                "Les dossiers patients sont en cours de classement. "
                        + "Cette section sera disponible prochainement."),
        RENDEZ_VOUS("Rendez-vous",
                "Agenda du cabinet.",
                "L'agenda est en cours de reliure. Cette section sera disponible prochainement.");

        private final String libelle;
        private final String description;
        private final String messageEnPreparation;

        Section(String libelle, String description, String messageEnPreparation) {
            this.libelle = libelle;
            this.description = description;
            this.messageEnPreparation = messageEnPreparation;
        }

        public String getLibelle() {
            return libelle;
        }

        public String getDescription() {
            return description;
        }

        public String getMessageEnPreparation() {
            return messageEnPreparation;
        }
    }

    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String STYLE_ACTIF = "navigation-element-actif";
    private static final double LARGEUR_NAVIGATION = 250;
    private static final double LARGEUR_MAX_REGISTRE = 520;
    private static final double LARGEUR_MAX_TABLEAU = 720;
    private static final double HAUTEUR_LIGNE_TABLEAU = 34;

    private final BorderPane racine = new BorderPane();
    private final Map<Section, Button> boutonsNavigation = new EnumMap<>(Section.class);
    private final Label titrePage = new Label();
    private final Label descriptionPage = new Label();
    private final VBox corps = new VBox(24);
    private final Label nomUtilisateur = new Label();
    private final Button boutonDeconnexion = new Button("Se déconnecter");

    public MainView() {
        for (Section section : Section.values()) {
            boutonsNavigation.put(section, creerBoutonNavigation(section));
        }
        racine.getStyleClass().add("racine-ecran");
        racine.setLeft(construirePanneauNavigation());
        racine.setCenter(construireZoneContenu());
    }

    // ---------- Accès aux éléments ----------

    public Parent getRacine() {
        return racine;
    }

    public Button getBoutonNavigation(Section section) {
        return boutonsNavigation.get(section);
    }

    public Button getBoutonDeconnexion() {
        return boutonDeconnexion;
    }

    // ---------- Affichage ----------

    public void definirUtilisateur(String nom) {
        nomUtilisateur.setText(nom);
    }

    /** Met en évidence la section choisie dans la navigation et met à jour le titre de la page. */
    public void marquerSectionActive(Section section) {
        for (Map.Entry<Section, Button> entree : boutonsNavigation.entrySet()) {
            List<String> classes = entree.getValue().getStyleClass();
            classes.remove(STYLE_ACTIF);
            if (entree.getKey() == section) {
                classes.add(STYLE_ACTIF);
            }
        }
        titrePage.setText(section.getLibelle());
        descriptionPage.setText(section.getDescription());
    }

    /**
     * Affiche le tableau de bord avec des données réelles.
     *
     * @param nombreAVenir nombre total de rendez-vous à venir
     * @param prochains    les prochains rendez-vous à lister (déjà limités par l'appelant)
     */
    public void afficherTableauDeBord(int nombreMedecins, int nombrePatients,
                                      int nombreAVenir, List<RendezVousDetail> prochains) {
        VBox registre = new VBox();
        registre.getStyleClass().add("registre");
        registre.setMaxWidth(LARGEUR_MAX_REGISTRE);
        registre.getChildren().addAll(
                creerLigneChiffre(nombreMedecins, "Médecins enregistrés"),
                creerLigneChiffre(nombrePatients, "Patients enregistrés"),
                creerLigneChiffre(nombreAVenir, "Rendez-vous à venir"));
        corps.getChildren().setAll(registre, construireBlocProchains(nombreAVenir, prochains));
    }

    /** Affiche une erreur à la place des chiffres (on ne montre pas de zéros trompeurs). */
    public void afficherErreurTableauDeBord(String message) {
        Label erreur = creerTexte(message, "message");
        erreur.getStyleClass().add("message-erreur");
        erreur.setMaxWidth(LARGEUR_MAX_REGISTRE);
        corps.getChildren().setAll(erreur);
    }

    /** Affiche le panneau d'une section dont l'interface n'est pas encore construite. */
    public void afficherSectionEnPreparation(Section section) {
        corps.getChildren().setAll(
                creerTexte("Section en préparation", "titre-section"),
                creerTexte(section.getMessageEnPreparation(), "sous-titre"));
    }

    /**
     * Affiche l'interface complète d'une section (par exemple la section Médecins).
     * Le contenu occupe toute la hauteur disponible ; la page défile seulement
     * si la fenêtre est trop petite pour son contenu.
     */
    public void afficherContenu(Parent contenu) {
        VBox.setVgrow(contenu, Priority.ALWAYS);
        corps.getChildren().setAll(contenu);
    }

    // ---------- Construction ----------

    private Button creerBoutonNavigation(Section section) {
        Button bouton = new Button(section.getLibelle());
        bouton.getStyleClass().add("navigation-element");
        bouton.setMaxWidth(Double.MAX_VALUE);
        bouton.setAlignment(Pos.CENTER_LEFT);
        return bouton;
    }

    private VBox construirePanneauNavigation() {
        Label marque = new Label("Cabinet médical");
        marque.getStyleClass().add("navigation-marque");

        Region filet = new Region();
        filet.getStyleClass().add("filet-marque");
        filet.setPrefSize(48, 4);
        filet.setMinSize(48, 4);
        filet.setMaxSize(48, 4);

        VBox entete = new VBox(16, marque, filet);
        entete.setPadding(new Insets(0, 24, 24, 24));

        VBox elements = new VBox(2);
        elements.getChildren().addAll(boutonsNavigation.values());

        Region espace = new Region();
        VBox.setVgrow(espace, Priority.ALWAYS);

        Label etiquette = new Label("CONNECTÉ EN TANT QUE");
        etiquette.getStyleClass().add("navigation-etiquette");
        nomUtilisateur.getStyleClass().add("navigation-utilisateur");
        nomUtilisateur.setWrapText(true);
        nomUtilisateur.setMinHeight(Region.USE_PREF_SIZE);
        boutonDeconnexion.getStyleClass().add("bouton-navigation");
        boutonDeconnexion.setMaxWidth(Double.MAX_VALUE);

        VBox pied = new VBox(8, etiquette, nomUtilisateur, boutonDeconnexion);
        pied.setPadding(new Insets(0, 24, 0, 24));

        VBox panneau = new VBox(0, entete, elements, espace, pied);
        panneau.getStyleClass().add("panneau-navigation");
        panneau.setPadding(new Insets(32, 0, 24, 0));
        panneau.setPrefWidth(LARGEUR_NAVIGATION);
        panneau.setMinWidth(LARGEUR_NAVIGATION);
        panneau.setMaxWidth(LARGEUR_NAVIGATION);
        return panneau;
    }

    private VBox construireZoneContenu() {
        titrePage.getStyleClass().add("titre-page");
        titrePage.setWrapText(true);
        titrePage.setMinHeight(Region.USE_PREF_SIZE);
        descriptionPage.getStyleClass().add("sous-titre");
        descriptionPage.setWrapText(true);
        descriptionPage.setMinHeight(Region.USE_PREF_SIZE);

        Region regle = new Region();
        regle.getStyleClass().add("regle");
        regle.setPrefHeight(1);
        regle.setMinHeight(1);
        regle.setMaxHeight(1);

        VBox entete = new VBox(12, titrePage, descriptionPage, regle);

        corps.setPadding(new Insets(8, 16, 8, 0));
        ScrollPane defilement = new ScrollPane(corps);
        defilement.setFitToWidth(true);
        defilement.setFitToHeight(true);
        defilement.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(defilement, Priority.ALWAYS);

        VBox zone = new VBox(16, entete, defilement);
        zone.getStyleClass().add("contenu-principal");
        zone.setPadding(new Insets(32));
        return zone;
    }

    private HBox creerLigneChiffre(int valeur, String libelle) {
        Label chiffre = new Label(String.valueOf(valeur));
        chiffre.getStyleClass().add("chiffre");
        chiffre.setPrefWidth(80);
        chiffre.setMinWidth(Region.USE_PREF_SIZE);

        Label texte = new Label(libelle);
        texte.getStyleClass().add("libelle-chiffre");

        HBox ligne = new HBox(16, chiffre, texte);
        ligne.getStyleClass().add("ligne-chiffre");
        ligne.setAlignment(Pos.BASELINE_LEFT);
        return ligne;
    }

    private VBox construireBlocProchains(int nombreAVenir, List<RendezVousDetail> prochains) {
        VBox bloc = new VBox(12, creerTexte("Prochains rendez-vous", "titre-section"));
        bloc.setMaxWidth(LARGEUR_MAX_TABLEAU);
        if (prochains.isEmpty()) {
            bloc.getChildren().add(creerTexte(
                    "Aucun rendez-vous à venir. La salle d'attente respire.", "etat-vide"));
            return bloc;
        }
        bloc.getChildren().add(creerTableau(prochains));
        if (nombreAVenir > prochains.size()) {
            bloc.getChildren().add(creerTexte("Affichage des " + prochains.size()
                    + " prochains rendez-vous sur " + nombreAVenir + ".", "texte-aide"));
        }
        return bloc;
    }

    private TableView<RendezVousDetail> creerTableau(List<RendezVousDetail> lignes) {
        TableView<RendezVousDetail> tableau = new TableView<>(FXCollections.observableArrayList(lignes));
        tableau.getColumns().add(creerColonne("DATE ET HEURE", 170,
                detail -> detail.getRendezVous().getDateHeure().format(FORMAT_DATE_HEURE)));
        tableau.getColumns().add(creerColonne("PATIENT", 220,
                detail -> detail.getPatient().getNomComplet()));
        tableau.getColumns().add(creerColonne("MÉDECIN", 200,
                detail -> detail.getMedecin().getNomComplet()));
        tableau.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableau.setFixedCellSize(HAUTEUR_LIGNE_TABLEAU);
        double hauteur = (lignes.size() + 1) * HAUTEUR_LIGNE_TABLEAU + 6;
        tableau.setPrefHeight(hauteur);
        tableau.setMinHeight(hauteur);
        tableau.setMaxHeight(hauteur);
        return tableau;
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

    private static Label creerTexte(String texte, String classeStyle) {
        Label label = new Label(texte);
        label.getStyleClass().add(classeStyle);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}