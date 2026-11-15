package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByName(String name);

    /**
     * Las salas inactivas no forman parte de la superficie publica de la API.
     */
    Page<Room> findByActiveTrue(Pageable pageable);

    Optional<Room> findByIdAndActiveTrue(Long id);

    /**
     * Bloquea la fila de la sala ({@code SELECT ... FOR UPDATE}) hasta el final de la transaccion.
     * Las altas de reservas de una misma sala quedan serializadas, de modo que la comprobacion de
     * solape y el INSERT no pueden intercalarse con los de otra peticion concurrente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id and r.active = true")
    Optional<Room> findActiveByIdForUpdate(@Param("id") Long id);

}
