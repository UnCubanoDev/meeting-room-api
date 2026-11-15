# meeting-room-api

API REST para la gestion de salas de reunion y sus reservas.

Estado actual (incremento 3): la API ya expone la gestion de salas en `/api/rooms`
(listado, consulta por id y alta) con validacion de entrada, errores en formato
Problem Details (RFC 7807) y documentacion OpenAPI. Todavia no hay autenticacion ni
gestion de reservas.

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

## Endpoints

Todas las rutas cuelgan de `/api/rooms`.

| Metodo | Ruta | Descripcion | Respuestas |
| --- | --- | --- | --- |
| `GET` | `/api/rooms` | Lista todas las salas | `200` |
| `GET` | `/api/rooms/{id}` | Obtiene una sala por id | `200`, `404` |
| `POST` | `/api/rooms` | Da de alta una sala | `201` + cabecera `Location`, `400`, `409` |

Ejemplo de alta:

```bash
curl -i -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -d '{"name":"Sala Atlas","capacity":8,"location":"Planta 2","description":"Proyector 4K"}'
```

```json
{
  "id": 1,
  "name": "Sala Atlas",
  "capacity": 8,
  "location": "Planta 2",
  "description": "Proyector 4K",
  "active": true,
  "createdAt": "2026-11-15T20:13:13.836012Z",
  "updatedAt": "2026-11-15T20:13:13.836012Z"
}
```

Reglas de validacion: `name` obligatorio (maximo 100 caracteres), `capacity` entero mayor
que cero, `location` maximo 120 y `description` maximo 500. La respuesta de alta incluye la
cabecera `Location` con la URL del recurso creado.

### Errores

Los errores se devuelven como Problem Details (RFC 7807) con
`Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Validacion fallida",
  "status": 400,
  "detail": "La peticion contiene campos invalidos",
  "instance": "/api/rooms",
  "errors": {
    "name": "El nombre es obligatorio",
    "capacity": "La capacidad debe ser mayor que cero"
  }
}
```

| Situacion | Estado | `title` |
| --- | --- | --- |
| Sala inexistente | `404` | `Recurso no encontrado` |
| Nombre de sala duplicado | `409` | `Conflicto` |
| Payload invalido | `400` | `Validacion fallida` |

### Seguridad

`/api/**`, `/v3/api-docs/**`, `/swagger-ui/**` y `/error` son accesibles sin autenticacion;
el resto de rutas se deniega. La cadena de filtros es `stateless` y CSRF esta desactivado
porque la API no usa sesiones. La autenticacion se incorporara en un incremento posterior.

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

> Nota: la aplicacion arranca contra PostgreSQL real y aplica las migraciones de
> `src/main/resources/db/migration` al iniciar. Hibernate corre en modo `validate`, de modo
> que el esquema debe coincidir con las entidades. Los tests usan H2 en memoria (perfil
> `test`), con Flyway desactivado y el esquema generado por Hibernate.
