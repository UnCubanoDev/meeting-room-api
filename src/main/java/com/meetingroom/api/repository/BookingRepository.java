package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /**
     * Reservas activas de la sala que comparten algun instante con {@code [start, end)}.
     * Dos intervalos semiabiertos se solapan si cada uno empieza antes de que acabe el otro.
     */
    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = com.meetingroom.api.entity.BookingStatus.ACTIVE
              and b.startTime < :end
              and b.endTime > :start
            order by b.startTime
            """)
    List<Booking> findOverlapping(@Param("roomId") Long roomId,
                                  @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end);

    /**
     * La sala se carga junto con cada reserva para evitar una consulta por fila al paginar.
     */
    @Override
    @EntityGraph(attributePaths = "room")
    Page<Booking> findAll(Specification<Booking> specification, Pageable pageable);

    @EntityGraph(attributePaths = "room")
    Optional<Booking> findWithRoomById(Long id);

}
