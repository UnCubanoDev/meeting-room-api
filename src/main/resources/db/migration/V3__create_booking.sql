-- Reservas de salas. El intervalo es semiabierto: [start_time, end_time).
CREATE TABLE booking
(
    id           BIGSERIAL   PRIMARY KEY,
    room_id      BIGINT      NOT NULL,
    responsible  VARCHAR(50) NOT NULL,
    start_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time     TIMESTAMP WITH TIME ZONE NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT ck_booking_period CHECK (end_time > start_time),
    CONSTRAINT ck_booking_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

-- Listados por sala y franja horaria, y busqueda de solapes.
CREATE INDEX ix_booking_room_start ON booking (room_id, start_time);
CREATE INDEX ix_booking_start ON booking (start_time);

-- Garantia definitiva contra solapes: dos reservas activas de la misma sala no pueden
-- compartir ningun instante, aunque dos transacciones lleguen a la vez. La aplicacion ya
-- serializa las altas por sala; esta restriccion protege frente a cualquier otro camino.
-- btree_gist es una extension "trusted" desde PostgreSQL 13: basta con ser propietario de la base.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE booking
    ADD CONSTRAINT ex_booking_room_overlap
        EXCLUDE USING gist (room_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)
        WHERE (status = 'ACTIVE');
