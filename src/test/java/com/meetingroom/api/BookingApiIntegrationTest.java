package com.meetingroom.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de reservas sobre HTTP con tokens reales: login, alta, solape, filtros,
 * cancelacion y reglas de acceso. Recorre controlador, seguridad, servicio, JPA y H2.
 *
 * <p>Cada test crea su propia sala para no depender del orden de ejecucion ni de los datos de
 * otros tests que compartan el mismo contexto de Spring.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingApiIntegrationTest {

    private static final String PASSWORD = "clave1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ensureUser("reservas-admin", Role.ADMIN);
        ensureUser("ana", Role.USER);
        ensureUser("luis", Role.USER);
    }

    @Test
    void bookingLifecycle() throws Exception {
        String ana = tokenFor("ana");
        String luis = tokenFor("luis");
        long roomId = createRoom();

        long bookingId = id(book(ana, roomId, "2030-03-10T10:00:00Z", "2030-03-10T11:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsible").value("ana"))
                .andExpect(jsonPath("$.status").value("ACTIVE")));

        // Solape parcial con otro usuario: 409 con el detalle de la reserva que lo impide.
        book(luis, roomId, "2030-03-10T10:30:00Z", "2030-03-10T11:30:00Z")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sala ocupada"))
                .andExpect(jsonPath("$.detail", containsString("reserva " + bookingId)));

        // Contigua: empieza justo cuando termina la anterior.
        book(luis, roomId, "2030-03-10T11:00:00Z", "2030-03-10T12:00:00Z")
                .andExpect(status().isCreated());

        // Un tercero no puede cancelar la reserva de ana.
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", "Bearer " + luis))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", "Bearer " + ana))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Estado no valido"));

        // Una reserva cancelada libera la franja.
        book(luis, roomId, "2030-03-10T10:00:00Z", "2030-03-10T11:00:00Z")
                .andExpect(status().isCreated());

        // La consulta es publica y la cancelada sigue visible como historico.
        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adminCanCancelAnyBooking() throws Exception {
        long roomId = createRoom();
        long bookingId = id(book(tokenFor("ana"), roomId, "2030-03-11T10:00:00Z", "2030-03-11T11:00:00Z")
                .andExpect(status().isCreated()));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor("reservas-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void filtersByRoomAndDayWithPagination() throws Exception {
        String ana = tokenFor("ana");
        long roomId = createRoom();
        long otherRoomId = createRoom();

        book(ana, roomId, "2030-04-01T09:00:00Z", "2030-04-01T10:00:00Z").andExpect(status().isCreated());
        book(ana, roomId, "2030-04-01T15:00:00Z", "2030-04-01T16:00:00Z").andExpect(status().isCreated());
        // Empieza la vispera y termina el dia filtrado: tambien ocupa ese dia.
        book(ana, roomId, "2030-03-31T23:30:00Z", "2030-04-01T00:30:00Z").andExpect(status().isCreated());
        book(ana, roomId, "2030-04-02T09:00:00Z", "2030-04-02T10:00:00Z").andExpect(status().isCreated());
        book(ana, otherRoomId, "2030-04-01T09:00:00Z", "2030-04-01T10:00:00Z").andExpect(status().isCreated());

        mockMvc.perform(get("/api/bookings")
                        .param("roomId", String.valueOf(roomId))
                        .param("date", "2030-04-01")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].start").value("2030-03-31T23:30:00Z"))
                .andExpect(jsonPath("$.content[1].start").value("2030-04-01T09:00:00Z"));

        mockMvc.perform(get("/api/bookings")
                        .param("roomId", String.valueOf(roomId))
                        .param("date", "2030-04-01")
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].start").value("2030-04-01T15:00:00Z"));
    }

    @Test
    void creatingAndCancellingRequireAToken() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "2030-03-12T10:00:00Z", "2030-03-12T11:00:00Z")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));

        mockMvc.perform(post("/api/bookings/1/cancel"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidBookingsWithClearErrors() throws Exception {
        String ana = tokenFor("ana");
        long roomId = createRoom();

        book(ana, roomId, "2030-03-13T11:00:00Z", "2030-03-13T10:00:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.end").value("La hora de fin debe ser posterior a la de inicio"));

        book(ana, 999_999L, "2030-03-13T10:00:00Z", "2030-03-13T11:00:00Z")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe ninguna sala con id 999999"));
    }

    private ResultActions book(String token, long roomId, String start, String end) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(roomId, start, end)));
    }

    private static String body(long roomId, String start, String end) {
        return "{\"roomId\":" + roomId + ",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
    }

    private long createRoom() throws Exception {
        String name = "Sala " + UUID.randomUUID();
        return id(mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + tokenFor("reservas-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"capacity\":6}"))
                .andExpect(status().isCreated()));
    }

    private long id(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String tokenFor(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    /**
     * Otros tests del mismo contexto borran la tabla de usuarios; se recrean si faltan.
     */
    private void ensureUser(String username, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.saveAndFlush(new AppUser(username, passwordEncoder.encode(PASSWORD), role));
        }
    }

}
