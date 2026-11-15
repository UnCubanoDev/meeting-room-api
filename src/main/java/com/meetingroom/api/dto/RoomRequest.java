package com.meetingroom.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name,

        @Positive(message = "La capacidad debe ser mayor que cero")
        int capacity,

        @Size(max = 120, message = "La ubicacion no puede superar los 120 caracteres")
        String location,

        @Size(max = 500, message = "La descripcion no puede superar los 500 caracteres")
        String description

) {
}
