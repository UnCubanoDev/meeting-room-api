package com.meetingroom.api.dto;

import com.meetingroom.api.entity.Booking;
import com.meetingroom.api.entity.BookingStatus;

import java.time.OffsetDateTime;

public record BookingResponse(

        Long id,
        Long roomId,
        String roomName,
        String responsible,
        OffsetDateTime start,
        OffsetDateTime end,
        BookingStatus status,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt

) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getResponsible(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getCancelledAt(),
                booking.getCreatedAt());
    }

}
