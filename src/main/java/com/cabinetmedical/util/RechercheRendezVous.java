// src/main/java/com/cabinetmedical/util/RechercheRendezVous.java
package com.cabinetmedical.util;

import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtre une liste de rendez-vous par statut et par texte (nom du patient ou du médecin).
 * Classe pure : ni base de données, ni JavaFX. L'ordre de la liste d'origine est conservé.
 */
public final class RechercheRendezVous {

    private RechercheRendezVous() {
        // Classe utilitaire : on ne crée pas d'instance.
    }

    /**
     * @param rendezVous les rendez-vous à filtrer (jamais null)
     * @param statut     le statut voulu, ou null pour tous les statuts
     * @param texte      le texte cherché dans le nom du patient ou du médecin ; vide = pas de filtre.
     *                   La recherche est partielle, sans tenir compte des majuscules ni des accents,
     *                   et accepte « prénom nom » comme « nom prénom ».
     */
    public static List<RendezVousDetail> filtrer(List<RendezVousDetail> rendezVous,
                                                 StatutRendezVous statut, String texte) {
        String recherche = ValidationUtil.normaliserPourRecherche(texte);
        List<RendezVousDetail> resultat = new ArrayList<>();
        for (RendezVousDetail detail : rendezVous) {
            boolean statutCorrespond = statut == null || detail.getRendezVous().getStatut() == statut;
            if (statutCorrespond && texteCorrespond(detail, recherche)) {
                resultat.add(detail);
            }
        }
        return resultat;
    }

    private static boolean texteCorrespond(RendezVousDetail detail, String recherche) {
        if (recherche.isEmpty()) {
            return true;
        }
        return nomContient(detail.getPatient().getPrenom(), detail.getPatient().getNom(), recherche)
                || nomContient(detail.getMedecin().getPrenom(), detail.getMedecin().getNom(), recherche);
    }

    private static boolean nomContient(String prenom, String nom, String recherche) {
        String prenomNom = ValidationUtil.normaliserPourRecherche(prenom + " " + nom);
        String nomPrenom = ValidationUtil.normaliserPourRecherche(nom + " " + prenom);
        return prenomNom.contains(recherche) || nomPrenom.contains(recherche);
    }
}