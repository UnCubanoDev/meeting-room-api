package com.meetingroom.api.exception;

import java.util.Collection;

/**
 * El parametro {@code sort} pide ordenar por una propiedad no publicada.
 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Collection<String> allowed) {
        super("No se puede ordenar por '" + property + "'. Valores permitidos: " + String.join(", ", allowed));
    }

}
