package com.meetingroom.api.security;

import com.meetingroom.api.entity.AppUser;
import com.meetingroom.api.entity.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contenido y firma del token emitido. Se valida con el mismo decodificador que usa la API,
 * de modo que el test comprueba lo que realmente se aceptara en produccion.
 */
class TokenServiceTest {

    private static final String SECRET = "secreto-de-prueba-con-longitud-suficiente-para-hs256";
    private static final String ISSUER = "https://meeting-room-api";

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    private final TokenService tokenService = new TokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            new JwtProperties(SECRET, Duration.ofMinutes(30), ISSUER));

    @Test
    void issuesTokenWithSubjectRoleAndIssuer() {
        String token = tokenService.issue(new AppUser("ana", "irrelevante", Role.USER));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("ana");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getExpiresAt()).isBefore(Instant.now().plus(Duration.ofMinutes(31)));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String token = tokenService.issue(new AppUser("ana", "irrelevante", Role.ADMIN));

        SecretKey otherKey = new SecretKeySpec(
                "otro-secreto-de-prueba-con-longitud-suficiente".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(otherKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        assertThatThrownBy(() -> otherDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.issue(new AppUser("ana", "irrelevante", Role.ADMIN));
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> decoder.decode(tampered))
                .isInstanceOf(JwtException.class);
    }

}
