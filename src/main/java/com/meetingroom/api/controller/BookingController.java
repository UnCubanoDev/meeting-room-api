package com.meetingroom.api.controller;

import com.meetingroom.api.dto.BookingRequest;
import com.meetingroom.api.dto.BookingResponse;
import com.meetingroom.api.entity.BookingStatus;
import com.meetingroom.api.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Reservas", description = "Reserva y cancelacion de salas")
public class BookingController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private static final SortableFields SORTABLE = SortableFields.of(Map.of(
            "start", "startTime",
            "end", "endTime",
            "createdAt", "createdAt",
            "id", "id"));

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    @Operation(summary = "Lista las reservas de forma paginada, con filtros opcionales")
    public PagedModel<BookingResponse> findAll(
            @Parameter(description = "Solo reservas de esta sala")
            @RequestParam(required = false) Long roomId,
            @Parameter(description = "Solo reservas que ocupan este dia (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Solo reservas en este estado")
            @RequestParam(required = false) BookingStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "start") Pageable pageable) {
        return new PagedModel<>(bookingService.findAll(roomId, date, status, SORTABLE.apply(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una reserva por su identificador")
    public BookingResponse findById(@PathVariable Long id) {
        return bookingService.findById(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reserva una sala a nombre del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request,
                                                  Authentication authentication) {
        BookingResponse created = bookingService.create(request, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * La cancelacion es una transicion de estado, no un borrado: la reserva sigue existiendo y
     * consultable, por eso se modela como accion ({@code POST .../cancel}) y no como {@code DELETE}.
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela una reserva (responsable o ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse cancel(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return bookingService.cancel(id, authentication.getName(), admin);
    }

}
