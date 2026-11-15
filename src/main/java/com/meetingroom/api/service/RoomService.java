package com.meetingroom.api.service;

import com.meetingroom.api.dto.RoomRequest;
import com.meetingroom.api.dto.RoomResponse;
import com.meetingroom.api.entity.Room;
import com.meetingroom.api.exception.DuplicateRoomNameException;
import com.meetingroom.api.exception.PersistenceErrors;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.repository.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Comprueba el nombre antes de insertar (camino rapido y mensaje claro) y delega la
     * garantia definitiva en la restriccion unica de la base de datos: entre la comprobacion
     * y el INSERT otra transaccion puede crear la misma sala.
     */
    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByName(request.name())) {
            throw new DuplicateRoomNameException(request.name());
        }
        Room room = new Room(request.name(), request.capacity(), request.location(), request.description());
        try {
            // saveAndFlush fuerza el INSERT dentro del try para poder clasificar la violacion.
            return RoomResponse.from(roomRepository.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            if (PersistenceErrors.isUniqueViolation(exception)) {
                throw new DuplicateRoomNameException(request.name(), exception);
            }
            throw exception;
        }
    }

    public Page<RoomResponse> findAll(Pageable pageable) {
        return roomRepository.findByActiveTrue(pageable)
                .map(RoomResponse::from);
    }

    public RoomResponse findById(Long id) {
        return roomRepository.findByIdAndActiveTrue(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna sala con id " + id));
    }

}
