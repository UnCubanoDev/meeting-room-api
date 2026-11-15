package com.meetingroom.api.exception;

/**
 * La sala ya esta ocupada en el intervalo solicitado.
 */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        this(message, null);
    }

    public BookingConflictException(String message, Throwable cause) {
        super(message, cause);
    }

}
