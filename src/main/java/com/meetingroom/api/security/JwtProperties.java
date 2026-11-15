package com.meetingroom.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracion del token JWT. El secreto debe tener al menos 32 bytes porque el
 * algoritmo HMAC-SHA256 no admite claves mas cortas.
 *
 * @param secret clave simetrica compartida para firmar y validar
 * @param ttl    tiempo de vida del token
 * @param issuer emisor que se incluye y se valida en el claim {@code iss}
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {
}
