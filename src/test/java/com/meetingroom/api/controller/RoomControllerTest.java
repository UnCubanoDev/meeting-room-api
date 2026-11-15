package com.meetingroom.api.controller;

import com.meetingroom.api.dto.RoomResponse;
import com.meetingroom.api.exception.DuplicateRoomNameException;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.security.SecurityConfig;
import com.meetingroom.api.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import(SecurityConfig.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    private static RoomResponse sampleRoom() {
        OffsetDateTime now = OffsetDateTime.now();
        return new RoomResponse(1L, "Sala Azul", 8, "Planta 3", "Pizarra", true, now, now);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String payload(String name, String capacity, String location, String description) {
        return "{\"name\":" + quote(name) + ",\"capacity\":" + capacity
                + ",\"location\":" + quote(location) + ",\"description\":" + quote(description) + "}";
    }

    private static String payloadWithField(String field, int length) {
        String longValue = "x".repeat(length);
        return payload("name".equals(field) ? longValue : "Sala Azul",
                "8",
                "location".equals(field) ? longValue : "Planta 3",
                "description".equals(field) ? longValue : "Pizarra");
    }

    @Test
    void createsRoomAndReturnsLocationHeader() throws Exception {
        when(roomService.create(any())).thenReturn(sampleRoom());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Sala Azul", "8", "Planta 3", "Pizarra")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/rooms/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Sala Azul"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("", "0", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.capacity").exists());
    }

    @Test
    void rejectsMissingCapacity() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala Azul\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").value("La capacidad es obligatoria"));
    }

    @ParameterizedTest(name = "rechaza capacidad {0}")
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(int capacity) throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Sala Azul", String.valueOf(capacity), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").value("La capacidad debe ser mayor que cero"));
    }

    @ParameterizedTest(name = "acepta {0} de longitud {1}")
    @CsvSource({"name,100", "location,120", "description,500"})
    void acceptsValuesAtTheMaximumLength(String field, int length) throws Exception {
        when(roomService.create(any())).thenReturn(sampleRoom());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithField(field, length)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "rechaza {0} de longitud {1}")
    @CsvSource({"name,101", "location,121", "description,501"})
    void rejectsValuesAboveTheMaximumLength(String field, int length) throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithField(field, length)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void rejectsFractionalCapacity() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala Azul\",\"capacity\":8.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"));
    }

    @Test
    void rejectsOverflowingCapacity() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala Azul\",\"capacity\":2147483648}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"Sala Azul\",\"capacity\":8}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }

    @Test
    void rejectsNonNumericRoomId() throws Exception {
        mockMvc.perform(get("/api/rooms/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametro invalido"))
                .andExpect(jsonPath("$.detail").value("El parametro 'id' tiene un formato invalido"));
    }

    @Test
    void returnsRoomById() throws Exception {
        when(roomService.findById(1L)).thenReturn(sampleRoom());

        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sala Azul"));
    }

    @Test
    void returnsNotFoundProblemDetailWhenRoomDoesNotExist() throws Exception {
        when(roomService.findById(99L)).thenThrow(new ResourceNotFoundException("No existe ninguna sala con id 99"));

        mockMvc.perform(get("/api/rooms/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No existe ninguna sala con id 99"))
                .andExpect(jsonPath("$.instance").value("/api/rooms/99"));
    }

    @Test
    void returnsConflictWhenNameIsDuplicated() throws Exception {
        when(roomService.create(any())).thenThrow(new DuplicateRoomNameException("Sala Azul"));

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Sala Azul", "8", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Ya existe una sala con el nombre: Sala Azul"));
    }

    @Test
    void listsRoomsWithPaginationMetadata() throws Exception {
        Page<RoomResponse> page = new PageImpl<>(List.of(sampleRoom()));
        when(roomService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Sala Azul"))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

}
