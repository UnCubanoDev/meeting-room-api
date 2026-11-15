package com.meetingroom.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param timeZone zona en la que se interpreta el filtro {@code date} del listado de reservas:
 *                 "el dia 15" empieza y acaba a medianoche de esta zona, no de la del servidor.
 */
@ConfigurationProperties(prefix = "app.bookings")
public record BookingProperties(@DefaultValue("UTC") ZoneId timeZone) {
}
