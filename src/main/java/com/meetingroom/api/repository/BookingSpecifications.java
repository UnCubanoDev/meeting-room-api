package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Booking;
import com.meetingroom.api.entity.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

/**
 * Filtros opcionales del listado de reservas. Cada uno devuelve {@code null} si su parametro
 * no se informa, de modo que se pueden combinar sin construir la consulta a mano.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> inRoom(Long roomId) {
        return roomId == null ? null : (root, query, cb) -> cb.equal(root.get("room").get("id"), roomId);
    }

    public static Specification<Booking> withStatus(BookingStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Reservas que ocupan algun instante de {@code [from, to)}, no solo las que empiezan en el.
     */
    public static Specification<Booking> overlapping(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("startTime"), to),
                cb.greaterThan(root.get("endTime"), from));
    }

}
