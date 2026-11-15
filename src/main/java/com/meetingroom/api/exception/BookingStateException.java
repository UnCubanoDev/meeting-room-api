package com.meetingroom.api.exception;

/**
 * La operacion no es compatible con el estado actual de la reserva (ya cancelada o terminada).
 */
public class BookingStateException extends RuntimeException {

    public BookingStateException(String message) {
        super(message);
    }

}
