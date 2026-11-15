package com.meetingroom.api.dto;

import com.meetingroom.api.entity.Room;

import java.time.OffsetDateTime;

public record RoomResponse(

        Long id,
        String name,
        int capacity,
        String location,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt

) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getLocation(),
                room.getDescription(),
                room.isActive(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }

}
