# meeting-room-api

API REST para la gestion de salas de reunion y sus reservas.

Este repositorio contiene unicamente el **primer incremento**: el esqueleto del proyecto
(estructura de paquetes, configuracion base y build). Aun no incluye entidades, endpoints,
reglas de negocio, autenticacion ni migraciones de base de datos.

## Tecnologias

| Area | Tecnologia |
| --- | --- |
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.5.16 |
| Build | Maven (via Maven Wrapper) |
| Web | Spring Web (Spring MVC) |
| Persistencia | Spring Data JPA / Hibernate |
| Seguridad | Spring Security |
| Validacion | Jakarta Bean Validation |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| Documentacion | SpringDoc OpenAPI 3 (Swagger UI) |
| Testing | JUnit 5, Mockito, MockMvc, AssertJ |

## Requisitos

- JDK 17 o superior (`java -version`).
- No es necesario instalar Maven: el proyecto incluye el Maven Wrapper.
- PostgreSQL 14+ para ejecutar la aplicacion en local (no se necesita para los tests).

## Estructura de paquetes

```
com.meetingroom.api
├── config      # Configuracion transversal y OpenAPI
├── security    # Cadena de filtros, autenticacion y autorizacion
├── controller  # Endpoints REST
├── service     # Casos de uso y reglas de negocio
├── repository  # Repositorios Spring Data JPA
├── entity      # Entidades JPA
├── dto         # Objetos de transferencia
└── exception   # Excepciones y manejo centralizado de errores
```

## Ejecucion preliminar

Compilar y ejecutar los tests:

```bash
./mvnw clean test        # Linux / macOS
mvnw.cmd clean test      # Windows
```

Arrancar la aplicacion:

```bash
./mvnw spring-boot:run
```

La configuracion de base de datos se toma de variables de entorno, sin credenciales en el
repositorio:

| Variable | Valor por defecto |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/meeting_room` |
| `DB_USERNAME` | `meeting_room` |
| `DB_PASSWORD` | _(vacio)_ |
| `SERVER_PORT` | `8080` |

Una vez arrancada, la documentacion estara disponible en `/swagger-ui.html`
(especificacion en `/v3/api-docs`).

> Nota: la aplicacion arranca contra PostgreSQL real; no hay migraciones Flyway todavia,
> asi que el esquema debe existir previamente o permanecer vacio. Los tests usan H2 en
> memoria (perfil `test`) y no requieren base de datos.
