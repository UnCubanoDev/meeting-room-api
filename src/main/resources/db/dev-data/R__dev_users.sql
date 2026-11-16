-- Usuarios de desarrollo. Solo se aplica con el perfil dev, que anade db/dev-data a
-- spring.flyway.locations; en cualquier otro entorno esta carpeta no se lee.
--
-- Contrasenas (BCrypt, coste 10). Al estar versionadas en el repositorio NO son secretas:
--   admin   / admin1234   (ADMIN)
--   usuario / usuario1234 (USER)
--
-- Es una migracion repetible: Flyway la vuelve a aplicar si cambia su contenido, por eso
-- cada INSERT ignora los usuarios que ya existen.
INSERT INTO app_user (username, password_hash, role, created_at, updated_at)
VALUES ('admin',
        '$2a$10$7O8CpNJgFQbzFMBc9SDRmubcEDTZNQZsOmvfPPRgvXMlLP8nlO34y',
        'ADMIN',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
       ('usuario',
        '$2a$10$KXdGcI3S3hEopaKsH57OAuKcVvNzy4MFTX1/1KkKX5kfId9hzlqeK',
        'USER',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
