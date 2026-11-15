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
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.repository.BookingRepository;
import com.meetingroom.api.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2030, 1, 15, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime TEN = NOW.withHour(10);
    private static final OffsetDateTime ELEVEN = NOW.withHour(11);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    private BookingService bookingService;
    private Room room;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        bookingService = new BookingService(bookingRepository, roomRepository, clock,
                new BookingProperties(ZoneOffset.UTC));
        room = new Room("Sala Azul", 8, null, null);
        ReflectionTestUtils.setField(room, "id", 1L);
    }

    @Test
    void createsBookingWhenRoomIsFree() {
        when(roomRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, TEN, ELEVEN)).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.create(new BookingRequest(1L, TEN, ELEVEN), "ana");

        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.roomName()).isEqualTo("Sala Azul");
        assertThat(response.responsible()).isEqualTo("ana");
        assertThat(response.start()).isEqualTo(TEN);
        assertThat(response.end()).isEqualTo(ELEVEN);
        assertThat(response.status()).isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    void rejectsBookingThatOverlapsAnActiveOne() {
        Booking existing = booking("luis", TEN.withMinute(30), ELEVEN.withMinute(30));
        when(roomRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, TEN, ELEVEN)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, TEN, ELEVEN), "ana"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("Sala Azul")
                .hasMessageContaining("reserva 7");

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    /**
     * Red de seguridad: si la restriccion EXCLUDE de PostgreSQL detecta un solape que la
     * comprobacion previa no vio, se responde igualmente 409.
     */
    @Test
    void translatesExclusionViolationIntoConflict() {
        when(roomRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, TEN, ELEVEN)).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("solape",
                        new SQLException("conflicting key value violates exclusion constraint", "23P01")));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, TEN, ELEVEN), "ana"))
                .isInstanceOf(BookingConflictException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failsWhenRoomDoesNotExistOrIsInactive() {
        when(roomRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(99L, TEN, ELEVEN), "ana"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("sala con id 99");
    }

    @Test
    void responsibleCanCancelTheirBooking() {
        Booking booking = booking("ana", TEN, ELEVEN);
        when(bookingRepository.findWithRoomById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancel(7L, "ana", false);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void adminCanCancelAnyBooking() {
        Booking booking = booking("ana", TEN, ELEVEN);
        when(bookingRepository.findWithRoomById(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);

        assertThat(bookingService.cancel(7L, "admin", true).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void otherUsersCannotCancelTheBooking() {
        when(bookingRepository.findWithRoomById(7L)).thenReturn(Optional.of(booking("ana", TEN, ELEVEN)));

        assertThatThrownBy(() -> bookingService.cancel(7L, "luis", false))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void cannotCancelTwice() {
        Booking booking = booking("ana", TEN, ELEVEN);
        booking.cancel(NOW.minusHours(1));
        when(bookingRepository.findWithRoomById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(7L, "ana", false))
                .isInstanceOf(BookingStateException.class)
                .hasMessageContaining("ya esta cancelada");
    }

    @Test
    void cannotCancelABookingThatHasAlreadyEnded() {
        when(bookingRepository.findWithRoomById(7L))
                .thenReturn(Optional.of(booking("ana", NOW.minusHours(2), NOW)));

        assertThatThrownBy(() -> bookingService.cancel(7L, "ana", false))
                .isInstanceOf(BookingStateException.class)
                .hasMessageContaining("ya ha terminado");
    }

    @Test
    void failsWhenBookingDoesNotExist() {
        when(bookingRepository.findWithRoomById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("reserva con id 99");
    }

    private Booking booking(String responsible, OffsetDateTime start, OffsetDateTime end) {
        Booking booking = new Booking(room, responsible, start, end);
        ReflectionTestUtils.setField(booking, "id", 7L);
        return booking;
    }

}
