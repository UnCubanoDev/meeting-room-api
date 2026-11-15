package com.meetingroom.api;

import com.meetingroom.api.repository.RoomRepository;
import com.meetingroom.api.entity.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre la cadena completa: HTTP -> controlador -> servicio -> repositorio -> H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void persistsRoomAndExposesItThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sala Integracion","capacity":6,"location":"Planta 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Sala Integracion"));

        assertThat(roomRepository.existsByName("Sala Integracion")).isTrue();

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Sala Integracion")));
    }

    @Test
    void returnsConflictWhenCreatingADuplicatedRoom() throws Exception {
        String payload = """
                {"name":"Sala Duplicada","capacity":4}
                """;

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail", containsString("Sala Duplicada")))
                .andExpect(jsonPath("$.instance").value("/api/rooms"));
    }

    /**
     * La ruta de detalle debe seguir siendo publica: si el matcher no la cubriera, el resultado
     * seria 403 en lugar de 404.
     */
    @Test
    void allowsRoomDetailRoute() throws Exception {
        mockMvc.perform(get("/api/rooms/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * La superficie publica se declara ruta por ruta: lo que no esta listado queda cerrado,
     * aunque cuelgue de /api.
     */
    @Test
    void deniesRoutesThatAreNotPublished() throws Exception {
        mockMvc.perform(get("/api/desconocidas"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    /**
     * Los listados se paginan (PagedModel) y excluyen las salas inactivas; el detalle de una
     * sala inactiva tampoco se publica.
     */
    @Test
    void paginatesActiveRoomsOnly() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Sala Paginada " + i + "\",\"capacity\":4}"))
                    .andExpect(status().isCreated());
        }
        Room inactive = new Room("Sala Oculta", 2, null, null);
        ReflectionTestUtils.setField(inactive, "active", false);
        Long inactiveId = roomRepository.saveAndFlush(inactive).getId();

        mockMvc.perform(get("/api/rooms").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Sala Oculta"))));

        mockMvc.perform(get("/api/rooms/" + inactiveId))
                .andExpect(status().isNotFound());
    }

    @Test
    void clampsPageSizeToTheConfiguredMaximum() throws Exception {
        mockMvc.perform(get("/api/rooms").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }

}
