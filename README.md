# meeting-room-api

API REST para la gestion de salas de reunion y sus reservas.

Estado actual (incremento 6): la API gestiona salas (`/api/rooms`) y reservas
(`/api/bookings`). Las reservas se crean, listan con filtros por sala, dia y estado con
paginacion, y se cancelan; un solape con otra reserva activa de la misma sala responde `409`.
Incluye validacion de entrada, errores uniformes en formato Problem Details (RFC 7807),
documentacion OpenAPI y autenticacion JWT.

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

### Salas

| Metodo | Ruta | Descripcion | Respuestas |
| --- | --- | --- | --- |
| `GET` | `/api/rooms` | Lista paginada de salas activas | `200` |
| `GET` | `/api/rooms/{id}` | Obtiene una sala activa por id | `200`, `400`, `404` |
| `POST` | `/api/rooms` | Da de alta una sala | `201` + cabecera `Location`, `400`, `409`, `415` |

El listado acepta `page`, `size` y `sort` (por defecto `size=20` y `sort=name`; el maximo de
`size` es 100). Se puede ordenar por `name`, `capacity`, `location`, `createdAt` e `id`; cualquier
otro valor responde `400`. Devuelve el envoltorio estable de Spring (`PagedModel`):

```json
{
  "content": [
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
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Las salas con `active = false` no aparecen en el listado ni en el detalle.

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

Reglas de validacion: `name` obligatorio (maximo 100 caracteres), `capacity` obligatorio,
entero y mayor que cero (no admite decimales: `8.5` se rechaza, no se trunca), `location`
maximo 120 y `description` maximo 500. La respuesta de alta incluye la cabecera `Location`
con la URL del recurso creado.

### Reservas

| Metodo | Ruta | Descripcion | Respuestas |
| --- | --- | --- | --- |
| `GET` | `/api/bookings` | Lista paginada, con filtros opcionales | `200`, `400` |
| `GET` | `/api/bookings/{id}` | Obtiene una reserva por id | `200`, `400`, `404` |
| `POST` | `/api/bookings` | Reserva una sala a nombre del usuario autenticado | `201` + `Location`, `400`, `401`, `404`, `409` |
| `POST` | `/api/bookings/{id}/cancel` | Cancela una reserva | `200`, `401`, `403`, `404`, `409` |

Ejemplo de alta (el token se obtiene en `POST /api/auth/login`, ver [Seguridad](#seguridad)):

```bash
curl -i -X POST http://localhost:8080/api/bookings   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -d '{"roomId":1,"start":"2030-01-15T10:00:00+01:00","end":"2030-01-15T11:00:00+01:00"}'
```

```json
{
  "id": 1,
  "roomId": 1,
  "roomName": "Sala Atlas",
  "responsible": "admin",
  "start": "2030-01-15T09:00:00Z",
  "end": "2030-01-15T10:00:00Z",
  "status": "ACTIVE",
  "cancelledAt": null,
  "createdAt": "2026-11-15T20:13:13.836012Z"
}
```

Reglas:

- **Responsable**: es siempre el usuario del token; el cuerpo no puede indicarlo, para que nadie
  reserve en nombre de otro.
- **Fechas**: ISO-8601 con desplazamiento horario (`Z`, `+01:00`...). Una hora sin zona es
  ambigua y se rechaza con `400`. Las respuestas se devuelven en UTC.
- **Validacion**: `roomId`, `start` y `end` obligatorios; `start` en el futuro; `end` posterior
  a `start` (el error se informa en el campo `end`). La sala debe existir y estar activa (`404`).
- **Solape (`409`)**: el intervalo es semiabierto `[start, end)`. Una reserva de 10:00 a 11:00
  choca con otra de 10:30 a 11:30, pero no con una de 11:00 a 12:00. Las reservas canceladas no
  ocupan la sala.
- **Concurrencia**: el alta bloquea la fila de la sala (`SELECT ... FOR UPDATE`), asi que dos
  peticiones simultaneas sobre la misma sala se resuelven una tras otra y la segunda recibe
  `409`. En PostgreSQL, la restriccion `EXCLUDE` de la migracion `V3` (extension `btree_gist`)
  lo garantiza ademas a nivel de base de datos.
- **Cancelacion**: solo el responsable o un `ADMIN` (`403` en otro caso). Es una baja logica: la
  reserva pasa a `CANCELLED`, guarda `cancelledAt` y sigue siendo consultable. Cancelar dos
  veces, o una reserva que ya ha terminado, responde `409`.

Listado: `GET /api/bookings?roomId=1&date=2030-01-15&status=ACTIVE&page=0&size=20&sort=start,asc`.
Todos los filtros son opcionales y se combinan:

| Parametro | Efecto |
| --- | --- |
| `roomId` | Solo reservas de esa sala |
| `date` | Reservas que ocupan algun momento de ese dia (`YYYY-MM-DD`), aunque empiecen la vispera. El dia se interpreta en la zona `BOOKINGS_TIME_ZONE` (UTC por defecto) |
| `status` | `ACTIVE` o `CANCELLED`; sin el, se listan ambas |
| `sort` | `start` (por defecto), `end`, `createdAt` o `id` |

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
| Sala inexistente (o inactiva) | `404` | `Recurso no encontrado` |
| Nombre de sala duplicado | `409` | `Conflicto` |
| Sala ya reservada en ese horario | `409` | `Sala ocupada` |
| Cancelar una reserva cancelada o terminada | `409` | `Estado no valido` |
| Cancelar una reserva ajena sin ser `ADMIN` | `403` | `Acceso denegado` |
| Payload invalido | `400` | `Validacion fallida` |
| JSON malformado, capacidad fraccionaria o fuera de rango | `400` | `Cuerpo ilegible` |
| `{id}`, `date` o `status` con formato invalido, o `sort` no permitido | `400` | `Parametro invalido` |
| `Content-Type` distinto de `application/json` | `415` | `Tipo de contenido no soportado` |
| Credenciales incorrectas (login) | `401` | `Credenciales invalidas` |
| Falta el token o no es valido | `401` | `No autenticado` |
| Token valido sin rol suficiente | `403` | `Acceso denegado` |
| Fallo interno de integridad | `500` | `Error interno` |

Solo las violaciones de unicidad se traducen a `409`; cualquier otro fallo de integridad de
datos se registra en el log con su causa raiz y responde `500` para no enmascararlo.

Los `401` y `403` que se producen en la cadena de filtros tambien usan Problem Details,
aunque no pasan por el manejador de excepciones del controlador.

### Seguridad

La API es `stateless` (sin sesiones, CSRF desactivado) y se autentica con **JWT firmado en
HS256**. El token se obtiene en `POST /api/auth/login` y se envia como
`Authorization: Bearer <token>`; expira a los 30 minutos y no hay refresh.

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "username": "admin",
  "role": "ADMIN"
}
```

Matriz de acceso:

| Ruta | Acceso |
| --- | --- |
| `POST /api/auth/login` | Publico |
| `GET /api/rooms`, `GET /api/rooms/{id}` | Publico |
| `POST /api/rooms` | `ROLE_ADMIN` |
| `GET /api/bookings`, `GET /api/bookings/{id}` | Publico |
| `POST /api/bookings`, `POST /api/bookings/{id}/cancel` | Cualquier usuario autenticado (la cancelacion ademas exige ser el responsable o `ADMIN`) |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/error` | Publico |
| Cualquier otra ruta | Denegada |

Las rutas se habilitan una por una: un endpoint nuevo nace cerrado y hay que autorizarlo de
forma explicita. Los usuarios viven en la tabla `app_user` y las contrasenas se guardan con
BCrypt.

> **Usuario de arranque:** la migracion `V2` inserta `admin` / `admin1234` para desarrollo.
> Ese hash esta versionado en el repositorio, asi que **no es un secreto**: cambialo antes de
> desplegar en cualquier entorno real.

Variables de entorno:

| Variable | Por defecto | Descripcion |
| --- | --- | --- |
| `JWT_SECRET` | secreto de desarrollo | Clave de firma HS256 (minimo 32 bytes) |
| `JWT_TTL` | `PT30M` | Vida del token (formato ISO-8601) |
| `BOOKINGS_TIME_ZONE` | `UTC` | Zona en la que se interpreta el filtro `date` de reservas |

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
