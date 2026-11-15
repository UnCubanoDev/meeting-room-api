package com.meetingroom.api.service;

import com.meetingroom.api.dto.RoomRequest;
import com.meetingroom.api.dto.RoomResponse;
import com.meetingroom.api.entity.Room;
import com.meetingroom.api.exception.DuplicateRoomNameException;
import com.meetingroom.api.exception.ResourceNotFoundException;
import com.meetingroom.api.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createsRoomWhenNameIsAvailable() {
        RoomRequest request = new RoomRequest("Sala Azul", 8, "Planta 3", "Pizarra");
        when(roomRepository.existsByName("Sala Azul")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.create(request);

        assertThat(response.name()).isEqualTo("Sala Azul");
        assertThat(response.capacity()).isEqualTo(8);
        assertThat(response.location()).isEqualTo("Planta 3");
        assertThat(response.active()).isTrue();
        verify(roomRepository).saveAndFlush(any(Room.class));
    }

    @Test
    void translatesUniqueViolationIntoDuplicateRoomName() {
        when(roomRepository.existsByName("Sala Azul")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenThrow(new DuplicateKeyException("uq_room_name"));

        assertThatThrownBy(() -> roomService.create(new RoomRequest("Sala Azul", 8, null, null)))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageContaining("Sala Azul")
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void translatesUniqueViolationDetectedBySqlState() {
        when(roomRepository.existsByName("Sala Azul")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado",
                        new SQLException("Violacion de unicidad", "23505")));

        assertThatThrownBy(() -> roomService.create(new RoomRequest("Sala Azul", 8, null, null)))
                .isInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void propagatesIntegrityViolationsThatAreNotUniqueViolations() {
        when(roomRepository.existsByName("Sala Azul")).thenReturn(false);
        DataIntegrityViolationException notUnique = new DataIntegrityViolationException("check de capacidad",
                new SQLException("Fallo del CHECK", "23514"));
        when(roomRepository.saveAndFlush(any(Room.class))).thenThrow(notUnique);

        assertThatThrownBy(() -> roomService.create(new RoomRequest("Sala Azul", 8, null, null)))
                .isSameAs(notUnique)
                .isNotInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void rejectsCreationWhenNameAlreadyExists() {
        when(roomRepository.existsByName("Sala Azul")).thenReturn(true);
        RoomRequest request = new RoomRequest("Sala Azul", 8, null, null);

        assertThatThrownBy(() -> roomService.create(request))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageContaining("Sala Azul");

        verify(roomRepository, never()).saveAndFlush(any(Room.class));
    }

    @Test
    void returnsRoomById() {
        Room room = new Room("Sala Azul", 8, "Planta 3", null);
        ReflectionTestUtils.setField(room, "id", 42L);
        when(roomRepository.findById(42L)).thenReturn(Optional.of(room));

        RoomResponse response = roomService.findById(42L);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("Sala Azul");
    }

    @Test
    void failsWhenRoomDoesNotExist() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listsAllRooms() {
        when(roomRepository.findAll()).thenReturn(List.of(new Room("Sala Azul", 8, null, null)));

        List<RoomResponse> rooms = roomService.findAll();

        assertThat(rooms).extracting(RoomResponse::name).containsExactly("Sala Azul");
    }

}
