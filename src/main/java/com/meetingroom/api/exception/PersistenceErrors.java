package com.meetingroom.api.exception;

import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/**
 * Distingue las violaciones de unicidad del resto de errores de integridad de datos.
 * <p>
 * Spring traduce la violacion de un indice unico a {@link DuplicateKeyException}, que es un
 * subtipo de {@code DataIntegrityViolationException}. Si el dialecto consultado no realiza esa
 * traduccion, se inspecciona el SQLState estandar de unicidad (23505) en la cadena de causas.
 */
public final class PersistenceErrors {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private PersistenceErrors() {
    }

    /**
     * @return {@code true} solo si la causa raiz es una violacion de restriccion unica.
     */
    public static boolean isUniqueViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause != cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

}
