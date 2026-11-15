package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByName(String name);

    /**
     * Las salas inactivas no forman parte de la superficie publica de la API.
     */
    Page<Room> findByActiveTrue(Pageable pageable);

    Optional<Room> findByIdAndActiveTrue(Long id);

}
