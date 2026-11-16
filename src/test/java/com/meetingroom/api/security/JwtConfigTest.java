package com.meetingroom.api.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuracion base no trae secreto por defecto: si falta o es debil, la aplicacion debe
 * negarse a arrancar en lugar de firmar tokens con una clave conocida.
 */
class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @ParameterizedTest(name = "rechaza secreto \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void refusesToStartWithoutSecret(String secret) {
        assertThatThrownBy(() -> config.jwtSecretKey(properties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> config.jwtSecretKey(properties("demasiado-corto")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32 bytes");
    }

    @Test
    void acceptsALongEnoughSecret() {
        assertThat(config.jwtSecretKey(properties("x".repeat(32))).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(30), "https://meeting-room-api");
    }

}
