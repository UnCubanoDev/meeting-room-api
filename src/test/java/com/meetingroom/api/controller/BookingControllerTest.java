package com.meetingroom.api.controller;

import com.meetingroom.api.dto.BookingResponse;
import com.meetingroom.api.entity.BookingStatus;
import com.meetingroom.api.exception.BookingConflictException;
import com.meetingroom.api.exception.BookingStateException;
import com.meetingroom.api.exception.ForbiddenOperationException;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.service.BookingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del controlador de reservas. Igual que en {@code RoomControllerTest}, los filtros
 * de seguridad se desactivan: el usuario se pasa como principal de la peticion y las reglas de
 * acceso por ruta se verifican en {@code BookingApiIntegrationTest}.
 */
@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2030, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private static Authentication user(String name, String... roles) {
        return new UsernamePasswordAuthenticationToken(name, null,
                AuthorityUtils.createAuthorityList(Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new)));
    }

    private static BookingResponse sampleBooking(BookingStatus status) {
        return new BookingResponse(5L, 1L, "Sala Azul", "ana", START, START.plusHours(1), status,
                status == BookingStatus.CANCELLED ? START.minusDays(1) : null, START.minusDays(2));
    }

    private static String payload(String roomId, String start, String end) {
        return "{\"roomId\":" + roomId + ",\"start\":" + start + ",\"end\":" + end + "}";
    }

    @Test
    void createsBookingForTheAuthenticatedUser() throws Exception {
        when(bookingService.create(any(), eq("ana"))).thenReturn(sampleBooking(BookingStatus.ACTIVE));

        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("1", "\"2030-01-15T10:00:00Z\"", "\"2030-01-15T11:00:00Z\"")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.roomName").value("Sala Azul"))
                .andExpect(jsonPath("$.responsible").value("ana"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ignoresAResponsibleSentInTheBody() throws Exception {
        when(bookingService.create(any(), eq("ana"))).thenReturn(sampleBooking(BookingStatus.ACTIVE));

        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"start\":\"2030-01-15T10:00:00Z\",\"end\":\"2030-01-15T11:00:00Z\","
                                + "\"responsible\":\"otro\"}"))
                .andExpect(status().isCreated());

        verify(bookingService).create(any(), eq("ana"));
    }

    @Test
    void rejectsEndBeforeStartWithAFieldError() throws Exception {
        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("1", "\"2030-01-15T11:00:00Z\"", "\"2030-01-15T10:00:00Z\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validacion fallida"))
                .andExpect(jsonPath("$.errors.end").value("La hora de fin debe ser posterior a la de inicio"));

        verifyNoInteractions(bookingService);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roomId").value("La sala es obligatoria"))
                .andExpect(jsonPath("$.errors.start").value("La hora de inicio es obligatoria"))
                .andExpect(jsonPath("$.errors.end").value("La hora de fin es obligatoria"));
    }

    @Test
    void rejectsStartInThePast() throws Exception {
        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("1", "\"2020-01-15T10:00:00Z\"", "\"2020-01-15T11:00:00Z\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.start").value("La hora de inicio debe estar en el futuro"));
    }

    @Test
    void rejectsDatesWithoutOffset() throws Exception {
        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("1", "\"2030-01-15T10:00:00\"", "\"2030-01-15T11:00:00\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"));
    }

    @Test
    void mapsOverlapToConflict() throws Exception {
        when(bookingService.create(any(), eq("ana")))
                .thenThrow(new BookingConflictException("La sala 'Sala Azul' ya esta reservada"));

        mockMvc.perform(post("/api/bookings").principal(user("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("1", "\"2030-01-15T10:00:00Z\"", "\"2030-01-15T11:00:00Z\"")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sala ocupada"))
                .andExpect(jsonPath("$.detail").value("La sala 'Sala Azul' ya esta reservada"));
    }

    @Test
    void passesFiltersAndTranslatedSortToTheService() throws Exception {
        when(bookingService.findAll(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleBooking(BookingStatus.ACTIVE))));

        mockMvc.perform(get("/api/bookings")
                        .param("roomId", "1")
                        .param("date", "2030-01-15")
                        .param("status", "ACTIVE")
                        .param("sort", "start,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).findAll(eq(1L), eq(LocalDate.of(2030, 1, 15)), eq(BookingStatus.ACTIVE),
                pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("startTime")));
    }

    @Test
    void listsWithoutFiltersSortedByStartByDefault() throws Exception {
        when(bookingService.findAll(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).findAll(isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("startTime"));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsUnknownSortProperty() throws Exception {
        mockMvc.perform(get("/api/bookings").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametro invalido"))
                .andExpect(jsonPath("$.detail").value(containsString("'password'")));

        verifyNoInteractions(bookingService);
    }

    @Test
    void rejectsMalformedDateFilter() throws Exception {
        mockMvc.perform(get("/api/bookings").param("date", "15/01/2030"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El parametro 'date' tiene un formato invalido"));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/bookings").param("status", "PENDIENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El parametro 'status' tiene un formato invalido"));
    }

    @Test
    void returnsNotFoundForUnknownBooking() throws Exception {
        when(bookingService.findById(99L)).thenThrow(new ResourceNotFoundException("No existe ninguna reserva con id 99"));

        mockMvc.perform(get("/api/bookings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void cancelsPassingTheAdminFlag() throws Exception {
        when(bookingService.cancel(5L, "admin", true)).thenReturn(sampleBooking(BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/bookings/5/cancel").principal(user("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    void mapsCancellationErrors() throws Exception {
        when(bookingService.cancel(5L, "luis", false))
                .thenThrow(new ForbiddenOperationException("Solo el responsable o un administrador pueden cancelar la reserva"));
        when(bookingService.cancel(6L, "luis", false))
                .thenThrow(new BookingStateException("La reserva 6 ya esta cancelada"));

        mockMvc.perform(post("/api/bookings/5/cancel").principal(user("luis", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        mockMvc.perform(post("/api/bookings/6/cancel").principal(user("luis", "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Estado no valido"));
    }

}
