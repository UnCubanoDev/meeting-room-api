package com.meetingroom.api.entity;

/**
 * Estados de una reserva. La cancelacion es una baja logica: la reserva se conserva como
 * historico, pero deja de ocupar la sala.
 */
public enum BookingStatus {

    ACTIVE,
    CANCELLED

}
