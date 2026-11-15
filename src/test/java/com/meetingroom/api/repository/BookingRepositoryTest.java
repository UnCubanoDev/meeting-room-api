package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Booking;
import com.meetingroom.api.entity.BookingStatus;
import com.meetingroom.api.entity.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BookingRepositoryTest {

    private static final OffsetDateTime DAY = OffsetDateTime.of(2030, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    private Room room;
    private Room otherRoom;

    @BeforeEach
    void setUp() {
        room = roomRepository.saveAndFlush(new Room("Sala Azul", 8, null, null));
        otherRoom = roomRepository.saveAndFlush(new Room("Sala Roja", 4, null, null));
    }

    @Test
    void findsOnlyActiveBookingsThatShareAnInstant() {
        Booking tenToEleven = save(room, 10, 11);
        save(room, 12, 13);
        save(otherRoom, 10, 11);
        Booking cancelled = save(room, 9, 12);
        cancelled.cancel(DAY);
        bookingRepository.saveAndFlush(cancelled);

        assertThat(bookingRepository.findOverlapping(room.getId(), at(10, 30), at(11, 30)))
                .extracting(Booking::getId)
                .containsExactly(tenToEleven.getId());
    }

    /**
     * El intervalo es semiabierto: terminar cuando otra empieza, o empezar cuando otra
     * termina, no es un solape.
     */
    @Test
    void contiguousBookingsDoNotOverlap() {
        save(room, 10, 11);

        assertThat(bookingRepository.findOverlapping(room.getId(), at(11, 0), at(12, 0))).isEmpty();
        assertThat(bookingRepository.findOverlapping(room.getId(), at(9, 0), at(10, 0))).isEmpty();
        assertThat(bookingRepository.findOverlapping(room.getId(), at(9, 0), at(12, 0))).hasSize(1);
    }

    @Test
    void combinesOptionalFiltersAndPaginates() {
        save(room, 9, 10);
        save(room, 11, 12);
        save(otherRoom, 11, 12);
        Booking nextDay = new Booking(room, "ana", DAY.plusDays(1).withHour(9), DAY.plusDays(1).withHour(10));
        bookingRepository.saveAndFlush(nextDay);

        Specification<Booking> filter = Specification.allOf(
                BookingSpecifications.inRoom(room.getId()),
                BookingSpecifications.withStatus(BookingStatus.ACTIVE),
                BookingSpecifications.overlapping(DAY, DAY.plusDays(1)));

        Page<Booking> page = bookingRepository.findAll(filter, PageRequest.of(0, 1, Sort.by("startTime")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Booking::getStartTime).containsExactly(at(9, 0));
        assertThat(page.getContent().get(0).getRoom().getName()).isEqualTo("Sala Azul");

        List<Booking> unfiltered = bookingRepository.findAll(
                Specification.allOf(BookingSpecifications.inRoom(null)), Sort.unsorted());
        assertThat(unfiltered).hasSize(4);
    }

    private Booking save(Room target, int fromHour, int toHour) {
        return bookingRepository.saveAndFlush(new Booking(target, "ana", at(fromHour, 0), at(toHour, 0)));
    }

    private static OffsetDateTime at(int hour, int minute) {
        return DAY.withHour(hour).withMinute(minute);
    }

}
