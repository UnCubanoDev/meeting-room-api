package com.meetingroom.api.exception;

public class DuplicateRoomNameException extends RuntimeException {

    public DuplicateRoomNameException(String name) {
        this(name, null);
    }

    public DuplicateRoomNameException(String name, Throwable cause) {
        super("Ya existe una sala con el nombre: " + name, cause);
    }

}
