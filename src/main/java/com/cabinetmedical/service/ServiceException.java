// src/main/java/com/cabinetmedical/service/ServiceException.java
package com.cabinetmedical.service;

/**
 * Erreur remontée par la couche service. Son message est toujours écrit en
 * français et peut être affiché tel quel à l'utilisateur (jamais de SQL
 * ni de détail technique).
 */
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE_ERREUR_TECHNIQUE =
            "Une erreur technique est survenue. Veuillez réessayer ; "
                    + "si le problème persiste, contactez l'administrateur.";

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Crée l'erreur générique affichée quand un problème technique (base de données) survient. */
    public static ServiceException erreurTechnique(Throwable cause) {
        return new ServiceException(MESSAGE_ERREUR_TECHNIQUE, cause);
    }
}