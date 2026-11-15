-- Salas de reunion disponibles para reservar.
CREATE TABLE room
(
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    capacity    INTEGER      NOT NULL,
    location    VARCHAR(120),
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_room_name UNIQUE (name),
    CONSTRAINT ck_room_capacity_positive CHECK (capacity > 0)
);
