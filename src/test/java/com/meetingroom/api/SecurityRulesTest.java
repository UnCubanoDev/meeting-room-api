package com.meetingroom.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingroom.api.entity.AppUser;
import com.meetingroom.api.entity.Role;
import com.meetingroom.api.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de acceso de la API: que es publico, que exige token y que exige rol ADMIN.
 * Todos los rechazos deben salir en formato Problem Details, igual que el resto de errores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

    private static final String PASSWORD = "usuario1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.saveAndFlush(new AppUser("admin", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        userRepository.saveAndFlush(new AppUser("usuario", passwordEncoder.encode(PASSWORD), Role.USER));
    }

    @Test
    void roomsArePublicToRead() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk());
    }

    @Test
    void creatingRoomsRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(room("Sala Anonima")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void creatingRoomsRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenFor("usuario"))
                        .content(room("Sala Sin Permiso")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanCreateRooms() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenFor("admin"))
                        .content(room("Sala Con Permiso")))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() throws Exception {
        String forged = forgeToken("usuario", "USER", Instant.now().plusSeconds(600));

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + forged)
                        .content(room("Sala Falsificada")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .issuer("https://meeting-room-api")
                                .subject("admin")
                                .issuedAt(Instant.now().minusSeconds(3600))
                                .expiresAt(Instant.now().minusSeconds(60))
                                .claim("roles", java.util.List.of("ADMIN"))
                                .build()))
                .getTokenValue();

        mockMvc.perform(get("/api/rooms").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void rejectsMalformedAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/rooms").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void unpublishedRoutesAreDeniedInProblemDetails() throws Exception {
        // Sin token, el anonimo es rechazado por la entrada de autenticacion: 401.
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"));

        // Con token valido pero sin autorizacion para esa ruta: 403.
        mockMvc.perform(get("/api/reservas").header("Authorization", "Bearer " + tokenFor("usuario")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    private String tokenFor(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String forgeToken(String subject, String role, Instant expiresAt) {
        SecretKeySpec otherKey = new SecretKeySpec(
                "otro-secreto-de-firma-que-no-es-el-de-la-api".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .issuer("https://meeting-room-api")
                                .subject(subject)
                                .issuedAt(Instant.now())
                                .expiresAt(expiresAt)
                                .claim("roles", java.util.List.of(role))
                                .build()))
                .getTokenValue();
    }

    private static String room(String name) {
        return "{\"name\":\"" + name + "\",\"capacity\":4}";
    }

}
