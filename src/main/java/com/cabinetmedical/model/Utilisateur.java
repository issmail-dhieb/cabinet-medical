// src/main/java/com/cabinetmedical/model/Utilisateur.java
package com.cabinetmedical.model;

/**
 * Un compte utilisateur de l'application. Correspond à la table utilisateurs.
 * Le mot de passe n'est jamais conservé en clair : on garde uniquement son
 * empreinte (hash), le sel et le nombre d'itérations utilisés pour la calculer.
 * Le hash et le sel sont du texte (encodage Base64, décidé en phase 7).
 * L'identifiant 0 signifie que l'utilisateur n'est pas encore enregistré.
 */
public class Utilisateur {

    private int id;
    private String nomUtilisateur;
    private String motDePasseHash;
    private String sel;
    private int iterations;

    /** Nouvel utilisateur, pas encore enregistré : id = 0. */
    public Utilisateur(String nomUtilisateur, String motDePasseHash, String sel, int iterations) {
        this(0, nomUtilisateur, motDePasseHash, sel, iterations);
    }

    /** Utilisateur complet, par exemple lu depuis la base de données. */
    public Utilisateur(int id, String nomUtilisateur, String motDePasseHash, String sel, int iterations) {
        this.id = id;
        this.nomUtilisateur = nomUtilisateur;
        this.motDePasseHash = motDePasseHash;
        this.sel = sel;
        this.iterations = iterations;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNomUtilisateur() {
        return nomUtilisateur;
    }

    public void setNomUtilisateur(String nomUtilisateur) {
        this.nomUtilisateur = nomUtilisateur;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public void setMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }

    public String getSel() {
        return sel;
    }

    public void setSel(String sel) {
        this.sel = sel;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    /** N'affiche volontairement ni le hash ni le sel, pour ne rien laisser fuiter dans les journaux. */
    @Override
    public String toString() {
        return "Utilisateur{id=" + id + ", nomUtilisateur='" + nomUtilisateur + "'}";
    }
}