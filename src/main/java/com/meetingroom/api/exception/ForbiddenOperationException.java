package com.meetingroom.api.exception;

/**
 * El usuario esta autenticado pero no puede actuar sobre este recurso concreto.
 *
 * <p>Las reglas por ruta y rol viven en {@code SecurityConfig}; esta excepcion cubre las que
 * dependen del propio recurso, como cancelar una reserva ajena.</p>
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }

}
