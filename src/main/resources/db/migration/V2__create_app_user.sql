-- Usuarios que pueden autenticarse contra la API.
CREATE TABLE app_user
(
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Usuario de arranque para entornos locales.
-- Contrasena: admin1234 (BCrypt, coste 10). CAMBIAR EN CUALQUIER ENTORNO REAL:
-- este hash queda versionado en el repositorio, por lo que no es un secreto.
INSERT INTO app_user (username, password_hash, role, created_at, updated_at)
VALUES ('admin',
        '$2a$10$7O8CpNJgFQbzFMBc9SDRmubcEDTZNQZsOmvfPPRgvXMlLP8nlO34y',
        'ADMIN',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
