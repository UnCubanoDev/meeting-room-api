package com.meetingroom.api.dto;

import com.meetingroom.api.validation.TimeRange;
import com.meetingroom.api.validation.ValidTimeRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

/**
 * Alta de una reserva. El responsable no viaja en el cuerpo: es el usuario autenticado, para
 * que nadie pueda reservar en nombre de otro.
 *
 * <p>Las fechas exigen desplazamiento horario (ISO-8601, p. ej. {@code 2030-01-15T10:00:00+01:00}):
 * una hora local sin zona es ambigua y se rechaza.</p>
 */
@ValidTimeRange
public record BookingRequest(

        @NotNull(message = "La sala es obligatoria")
        @Positive(message = "El identificador de sala debe ser mayor que cero")
        Long roomId,

        @NotNull(message = "La hora de inicio es obligatoria")
        @Future(message = "La hora de inicio debe estar en el futuro")
        OffsetDateTime start,

        @NotNull(message = "La hora de fin es obligatoria")
        OffsetDateTime end

) implements TimeRange {
}
