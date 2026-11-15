package com.meetingroom.api.exception;

public class DuplicateRoomNameException extends RuntimeException {

    public DuplicateRoomNameException(String name) {
        super("Ya existe una sala con el nombre: " + name);
    }

}
