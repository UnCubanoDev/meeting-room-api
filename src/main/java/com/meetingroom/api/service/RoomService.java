package com.meetingroom.api.service;

import com.meetingroom.api.dto.RoomRequest;
import com.meetingroom.api.dto.RoomResponse;
import com.meetingroom.api.entity.Room;
import com.meetingroom.api.exception.DuplicateRoomNameException;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByName(request.name())) {
            throw new DuplicateRoomNameException(request.name());
        }
        Room room = new Room(request.name(), request.capacity(), request.location(), request.description());
        return RoomResponse.from(roomRepository.save(room));
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAll().stream()
                .map(RoomResponse::from)
                .toList();
    }

    public RoomResponse findById(Long id) {
        return roomRepository.findById(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna sala con id " + id));
    }

}
