package com.meetingroom.api.validation;

import java.time.OffsetDateTime;

/**
 * Objeto con un intervalo temporal que {@link ValidTimeRange} puede validar.
 */
public interface TimeRange {

    OffsetDateTime start();

    OffsetDateTime end();

}
