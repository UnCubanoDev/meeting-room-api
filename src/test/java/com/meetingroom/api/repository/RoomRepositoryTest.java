package com.meetingroom.api.repository;

import com.meetingroom.api.entity.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class RoomRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void savesRoomAndReadsItBack() {
        Room room = new Room("Sala Azul", 8, "Planta 3 - Ala norte", "Sala con pizarra");

        Room saved = roomRepository.saveAndFlush(room);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        assertThat(roomRepository.findById(saved.getId()))
                .hasValueSatisfying(found -> {
                    assertThat(found.getName()).isEqualTo("Sala Azul");
                    assertThat(found.getCapacity()).isEqualTo(8);
                    assertThat(found.getLocation()).isEqualTo("Planta 3 - Ala norte");
                    assertThat(found.getDescription()).isEqualTo("Sala con pizarra");
                });
    }

    @Test
    void rejectsTwoRoomsWithTheSameName() {
        roomRepository.saveAndFlush(new Room("Sala Azul", 8, null, null));

        Room duplicate = new Room("Sala Azul", 4, null, null);

        assertThatThrownBy(() -> roomRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
