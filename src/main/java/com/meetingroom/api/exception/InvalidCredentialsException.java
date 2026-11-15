package com.meetingroom.api.exception;

/**
 * Credenciales incorrectas o usuario deshabilitado. El mensaje es deliberadamente generico
 * para no revelar si el usuario existe.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Usuario o contrasena incorrectos");
    }

}
