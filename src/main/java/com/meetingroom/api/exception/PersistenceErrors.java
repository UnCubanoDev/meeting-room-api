package com.meetingroom.api.exception;

import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/**
 * Distingue las violaciones de unicidad y de exclusion del resto de errores de integridad.
 * <p>
 * Spring traduce la violacion de un indice unico a {@link DuplicateKeyException}, que es un
 * subtipo de {@code DataIntegrityViolationException}. Si el dialecto consultado no realiza esa
 * traduccion, se inspecciona el SQLState estandar en la cadena de causas.
 */
public final class PersistenceErrors {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String EXCLUSION_VIOLATION_SQL_STATE = "23P01";

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
            cause = cause.getCause();
        }
        return hasSqlState(throwable, UNIQUE_VIOLATION_SQL_STATE);
    }

    /**
     * @return {@code true} si la causa es una restriccion {@code EXCLUDE} de PostgreSQL, como la
     * que impide dos reservas activas solapadas en la misma sala.
     */
    public static boolean isExclusionViolation(Throwable throwable) {
        return hasSqlState(throwable, EXCLUSION_VIOLATION_SQL_STATE);
    }

    private static boolean hasSqlState(Throwable throwable, String sqlState) {
        Throwable cause = throwable;
        while (cause != null && cause != cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

}
