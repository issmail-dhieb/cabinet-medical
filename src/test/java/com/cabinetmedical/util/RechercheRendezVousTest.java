// src/test/java/com/cabinetmedical/util/RechercheRendezVousTest.java
package com.cabinetmedical.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cabinetmedical.model.Medecin;
import com.cabinetmedical.model.Patient;
import com.cabinetmedical.model.RendezVous;
import com.cabinetmedical.model.RendezVousDetail;
import com.cabinetmedical.model.StatutRendezVous;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RechercheRendezVousTest {

    private static final Medecin DR_MARTIN = new Medecin("MED1", "Martin", "Claire", "Cardiologie", "71000000");
    private static final Medecin DR_BEN_SALAH = new Medecin("MED2", "Ben Salah", "Élodie", "Pédiatrie", "71000001");
    private static final Patient ALI = new Patient("20123456", "Trabelsi", "Ali", LocalDate.of(1990, 3, 25));
    private static final Patient SONIA = new Patient("22333444", "Gharbi", "Sonia", LocalDate.of(1985, 1, 10));

    // 1 : Martin / Ali (planifié)   2 : Martin / Sonia (effectué)
    // 3 : Ben Salah / Ali (annulé)  4 : Ben Salah / Sonia (planifié)
    private final List<RendezVousDetail> tous = List.of(
            detail(1, DR_MARTIN, ALI, StatutRendezVous.PLANIFIE),
            detail(2, DR_MARTIN, SONIA, StatutRendezVous.EFFECTUE),
            detail(3, DR_BEN_SALAH, ALI, StatutRendezVous.ANNULE),
            detail(4, DR_BEN_SALAH, SONIA, StatutRendezVous.PLANIFIE));

    @Test
    void sansStatutNiTexte_retourneToutDansLOrdreDOrigine() {
        assertEquals(List.of(1, 2, 3, 4), ids(RechercheRendezVous.filtrer(tous, null, "")));
    }

    @Test
    void filtreParStatut() {
        assertEquals(List.of(1, 4), ids(RechercheRendezVous.filtrer(tous, StatutRendezVous.PLANIFIE, "")));
    }

    @Test
    void rechercheParPrenomDuPatient() {
        assertEquals(List.of(1, 3), ids(RechercheRendezVous.filtrer(tous, null, "ali")));
    }

    @Test
    void accepteNomPrenomEtPrenomNom() {
        assertEquals(List.of(1, 3), ids(RechercheRendezVous.filtrer(tous, null, "trabelsi ali")));
        assertEquals(List.of(1, 3), ids(RechercheRendezVous.filtrer(tous, null, "ali trabelsi")));
    }

    @Test
    void rechercheParNomDuMedecin() {
        assertEquals(List.of(1, 2), ids(RechercheRendezVous.filtrer(tous, null, "martin")));
    }

    @Test
    void ignoreLesAccentsEtLesMajuscules() {
        assertEquals(List.of(3, 4), ids(RechercheRendezVous.filtrer(tous, null, "ELODIE")));
        assertEquals(List.of(3, 4), ids(RechercheRendezVous.filtrer(tous, null, "ben salah")));
    }

    @Test
    void combineStatutEtTexte() {
        assertEquals(List.of(3), ids(RechercheRendezVous.filtrer(tous, StatutRendezVous.ANNULE, "ali")));
    }

    @Test
    void aucunResultat_retourneUneListeVide() {
        assertTrue(RechercheRendezVous.filtrer(tous, null, "zzz").isEmpty());
    }

    @Test
    void texteDeSeulsEspaces_neFiltrePas() {
        assertEquals(4, RechercheRendezVous.filtrer(tous, null, "   ").size());
    }

    private static RendezVousDetail detail(int id, Medecin medecin, Patient patient, StatutRendezVous statut) {
        RendezVous rendezVous = new RendezVous(id, medecin.getMatricule(), patient.getNumeroTelephone(),
                LocalDateTime.of(2030, 1, 10, 9, 0).plusHours(id), statut);
        return new RendezVousDetail(rendezVous, medecin, patient);
    }

    private static List<Integer> ids(List<RendezVousDetail> details) {
        return details.stream().map(detail -> detail.getRendezVous().getId()).toList();
    }
}