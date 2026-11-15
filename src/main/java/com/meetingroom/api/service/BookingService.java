package com.meetingroom.api.service;

import com.meetingroom.api.config.BookingProperties;
import com.meetingroom.api.dto.BookingRequest;
import com.meetingroom.api.dto.BookingResponse;
import com.meetingroom.api.entity.Booking;
import com.meetingroom.api.entity.BookingStatus;
import com.meetingroom.api.entity.Room;
import com.meetingroom.api.exception.BookingConflictException;
import com.meetingroom.api.exception.BookingStateException;
import com.meetingroom.api.exception.ForbiddenOperationException;
import com.meetingroom.api.exception.PersistenceErrors;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.repository.BookingRepository;
import com.meetingroom.api.repository.BookingSpecifications;
import com.meetingroom.api.repository.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;
    private final ZoneId timeZone;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository, Clock clock,
                          BookingProperties properties) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
        this.timeZone = properties.timeZone();
    }

    /**
     * Reserva la sala si esta libre en {@code [start, end)}.
     *
     * <p>La sala se lee con bloqueo de escritura: dos altas concurrentes sobre la misma sala se
     * ejecutan una tras otra, asi que la segunda ve la reserva de la primera y responde 409. En
     * PostgreSQL la restriccion {@code ex_booking_room_overlap} actua ademas como red de seguridad.</p>
     */
    @Transactional
    public BookingResponse create(BookingRequest request, String responsible) {
        Room room = roomRepository.findActiveByIdForUpdate(request.roomId())
                .orElseThrow(() -> roomNotFound(request.roomId()));

        List<Booking> overlapping = bookingRepository.findOverlapping(room.getId(), request.start(), request.end());
        if (!overlapping.isEmpty()) {
            throw conflictWith(room, overlapping.get(0));
        }

        Booking booking = new Booking(room, responsible, request.start(), request.end());
        try {
            // saveAndFlush fuerza el INSERT dentro del try para poder clasificar la violacion.
            return BookingResponse.from(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException exception) {
            if (PersistenceErrors.isExclusionViolation(exception)) {
                throw new BookingConflictException(
                        "La sala '" + room.getName() + "' ya esta reservada en ese horario", exception);
            }
            throw exception;
        }
    }

    /**
     * Lista las reservas con filtros opcionales. {@code date} selecciona las reservas que ocupan
     * algun momento de ese dia en la zona configurada, aunque empiecen el dia anterior.
     */
    public Page<BookingResponse> findAll(Long roomId, LocalDate date, BookingStatus status, Pageable pageable) {
        OffsetDateTime from = date == null ? null : date.atStartOfDay(timeZone).toOffsetDateTime();
        OffsetDateTime to = date == null ? null : date.plusDays(1).atStartOfDay(timeZone).toOffsetDateTime();

        Specification<Booking> filter = Specification.allOf(
                BookingSpecifications.inRoom(roomId),
                BookingSpecifications.withStatus(status),
                BookingSpecifications.overlapping(from, to));

        return bookingRepository.findAll(filter, pageable).map(BookingResponse::from);
    }

    public BookingResponse findById(Long id) {
        return BookingResponse.from(load(id));
    }

    /**
     * Solo el responsable o un administrador pueden cancelar. Cancelar dos veces, o cancelar una
     * reserva que ya ha terminado, es un conflicto con el estado del recurso (409).
     */
    @Transactional
    public BookingResponse cancel(Long id, String username, boolean admin) {
        Booking booking = load(id);

        if (!admin && !booking.getResponsible().equals(username)) {
            throw new ForbiddenOperationException("Solo el responsable o un administrador pueden cancelar la reserva");
        }
        if (booking.isCancelled()) {
            throw new BookingStateException("La reserva " + id + " ya esta cancelada");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!booking.getEndTime().isAfter(now)) {
            throw new BookingStateException("La reserva " + id + " ya ha terminado y no se puede cancelar");
        }

        booking.cancel(now);
        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }

    private Booking load(Long id) {
        return bookingRepository.findWithRoomById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna reserva con id " + id));
    }

    private static ResourceNotFoundException roomNotFound(Long roomId) {
        return new ResourceNotFoundException("No existe ninguna sala con id " + roomId);
    }

    private static BookingConflictException conflictWith(Room room, Booking existing) {
        return new BookingConflictException("La sala '" + room.getName() + "' ya esta reservada de "
                + existing.getStartTime() + " a " + existing.getEndTime() + " (reserva " + existing.getId() + ")");
    }

}
