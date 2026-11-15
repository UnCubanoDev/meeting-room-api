package com.meetingroom.api.controller;

import com.meetingroom.api.dto.RoomResponse;
import com.meetingroom.api.exception.DuplicateRoomNameException;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.security.SecurityConfig;
import com.meetingroom.api.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void createsRoomAndReturnsLocationHeader() throws Exception {
        when(roomService.create(any())).thenReturn(sampleRoom());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sala Azul","capacity":8,"location":"Planta 3","description":"Pizarra"}
                                """))
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
                        .content("""
                                {"name":"","capacity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.capacity").exists());
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
                        .content("""
                                {"name":"Sala Azul","capacity":8}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Ya existe una sala con el nombre: Sala Azul"));
    }

    @Test
    void listsRooms() throws Exception {
        when(roomService.findAll()).thenReturn(List.of(sampleRoom()));

        mockMvc.perform(get("/api/rooms").param("unused", "x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sala Azul"));
    }

}
