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
- Docker (con Docker Compose) para levantar PostgreSQL, o un PostgreSQL 14+ propio. No se
  necesita para los tests, que usan H2 en memoria.

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

> **Usuarios de desarrollo:** solo con el perfil `dev` se cargan `admin` / `admin1234` (`ADMIN`)
> y `usuario` / `usuario1234` (`USER`), desde `db/dev-data`. Sus hashes estan versionados, asi que
> **no son secretos**. Fuera de `dev` no se crea ningun usuario: el esquema sale vacio.

Los tokens se validan por firma, vigencia y emisor (`iss`): un token firmado con la misma clave
pero emitido por otro no se acepta. El secreto y la vida del token se configuran con
`JWT_SECRET` y `JWT_TTL` (ver [Configuracion](#configuracion)).

## Como ejecutarlo

### 1. Levantar PostgreSQL

```bash
docker compose up -d
```

Arranca PostgreSQL 16 en `localhost:5432` (base, usuario y contrasena `meeting_room`), con los
datos en un volumen. Para usar otro puerto: `DB_PORT=5433 docker compose up -d`, y arrancar la
aplicacion con `DB_URL=jdbc:postgresql://localhost:5433/meeting_room`.

### 2. Arrancar la API con el perfil `dev`

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Linux / macOS
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev   # Windows
```

Al iniciar, Flyway crea el esquema y el perfil `dev` carga los usuarios de prueba
(`admin` / `admin1234` y `usuario` / `usuario1234`). La API queda en `http://localhost:8080`, con
Swagger UI en `/swagger-ui.html` (boton **Authorize** para pegar el token) y la especificacion en
`/v3/api-docs`.

### 3. Probarla

```bash
# Token de administrador
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login   -H "Content-Type: application/json"   -d '{"username":"admin","password":"admin1234"}' | sed -E 's/.*"accessToken":"([^"]+)".*//')

# Crear una sala (requiere ADMIN)
curl -i -X POST http://localhost:8080/api/rooms   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"name":"Sala Atlas","capacity":8}'

# Reservarla; repetir la misma peticion devuelve 409
curl -i -X POST http://localhost:8080/api/bookings   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"roomId":1,"start":"2030-01-15T10:00:00Z","end":"2030-01-15T11:00:00Z"}'

# Consultar (publico, sin token)
curl "http://localhost:8080/api/bookings?roomId=1&date=2030-01-15"
```

### Tests

```bash
./mvnw clean test        # Linux / macOS
mvnw.cmd clean test      # Windows
```

No necesitan Docker ni PostgreSQL.

### Configuracion

Todo se toma de variables de entorno; el perfil `dev` solo aporta valores por defecto de
desarrollo.

| Variable | Por defecto | Con perfil `dev` | Descripcion |
| --- | --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/meeting_room` | igual | URL JDBC de PostgreSQL |
| `DB_USERNAME` | `meeting_room` | igual | Usuario de la base |
| `DB_PASSWORD` | _(vacio)_ | `meeting_room` | Contrasena de la base |
| `JWT_SECRET` | _(ninguno: no arranca)_ | secreto de desarrollo | Clave de firma HS256, minimo 32 bytes |
| `JWT_TTL` | `PT30M` | igual | Vida del token (ISO-8601) |
| `BOOKINGS_TIME_ZONE` | `UTC` | igual | Zona del filtro `date` de reservas |
| `SERVER_PORT` | `8080` | igual | Puerto HTTP |

Fuera de `dev` (por ejemplo, `SPRING_PROFILES_ACTIVE` vacio en un servidor) hay que definir al
menos `DB_PASSWORD` y `JWT_SECRET`, y crear los usuarios por otra via.

> Nota: la aplicacion arranca contra PostgreSQL real y aplica las migraciones de
> `src/main/resources/db/migration` al iniciar. Hibernate corre en modo `validate`, de modo
> que el esquema debe coincidir con las entidades. Los tests usan H2 en memoria (perfil
> `test`), con Flyway desactivado y el esquema generado por Hibernate.

## Decisiones tomadas

- **Responsable del token**: el cuerpo de `POST /api/bookings` no acepta `responsible`; se toma
  del JWT para que nadie pueda reservar en nombre de otro.
- **Intervalo semiabierto `[start, end)`**: una reserva que termina a las 11:00 no bloquea la que
  empieza a las 11:00. Se aplica tanto en la busqueda de solapes como en la restriccion `EXCLUDE`.
- **Cancelacion logica** (`POST .../{id}/cancel` en lugar de `DELETE`): la reserva pasa a
  `CANCELLED`, guarda `cancelledAt` y sigue consultable como historial.
- **Concurrencia**: bloqueo `SELECT ... FOR UPDATE` de la sala mas la restriccion `EXCLUDE` de
  PostgreSQL como red de seguridad ante escrituras simultaneas.
- **JWT HS256 con Spring Security Resource Server**: sin librerias de terceros; firma, vigencia e
  emisor los valida el framework. Los roles viajan en un claim propio (`roles`).
- **Superficie cerrada por defecto**: `anyRequest().denyAll()`; un endpoint nuevo nace protegido
  y hay que autorizarlo explicitamente.
- **Errores uniformes**: Problem Details (RFC 7807) tambien para los `401`/`403` de la cadena de
  filtros, no solo para las excepciones de los controladores.
- **H2 solo en tests**: las migraciones usan SQL especifico de PostgreSQL (`btree_gist`,
  `EXCLUDE`), por eso Flyway se desactiva en el perfil `test` y Hibernate genera el esquema.

## Que haria con mas tiempo

- Tests de concurrencia reales con Testcontainers/PostgreSQL para verificar la restriccion
  `EXCLUDE` bajo carga, ademas del bloqueo por sala.
- Refresh y revocacion de tokens: hoy expiran a los 30 minutos y no se pueden invalidar.
- Gestion completa de salas (editar/desactivar) y un endpoint de disponibilidad que devuelva
  huecos libres.
- Auditoria de cancelaciones (quien, cuando y motivo) y notificaciones al responsable.
- Observabilidad con Micrometer/Prometheus y externalizacion de secretos en produccion.

## Uso de la IA

> Este proyecto fue desarrollado con la asistencia de un agente IA (este pro-expert) actuando como ingeniero senior orquestador de subagentes especializados. Los subagentes invocados fueron:
> - `@code-review` (revisión de seguridad enfocada) en los incrementos 3 y 5.
> - Herramientas nativas: `read`, `edit`, `write`, `grep`, `glob`, `bash` (para `mvnw`, `git`, `curl`, `javap`, `jshell`). No se usaron búsquedas web (`webfetch`).

### Qué se pidió, qué se aceptó y qué se corrigió

**Incremento 3** (revisión `@code-review`):
- **Aplicado**: `ProblemDetailsExceptionHandler` sin `@Order` → se añadió `@Order(0)` para ganar al handler por defecto de Boot.
- **Deferido**: la dependencia `spring-security-test` sin uso real en `pom.xml:83` y el `.param("unused","x")` en `RoomControllerTest.java:112` → ambos removidos en el incremento 4.

**Incremento 5** (revisión `@code-review`):
- **Aplicado**: el claim `iss` debe ser una URL para que `Jwt.getIssuer()` funcione; el valor `meeting-room-api` era una trampa latente. Se corrigió en `application.properties` y `TokenServiceTest.java`.
- **Aplicado**: hash BCrypt dummy para pruebas de igualdad de tiempo (`timing-equalizer`), verificado con `BCryptPasswordEncoder.matches`.
- **Aplicado**: encabezado `WWW-Authenticate: Bearer` en respuestas 401 (`ProblemAuthenticationEntryPoint.java:28-29`).
- **Aplicado**: `Cache-Control: no-store` en respuestas de login (`AuthController.java:27-33`).
- **Aplicado**: validación de `JWT_TTL` positiva (`JwtProperties.java:16`).
- **Aplicado**: eliminación de código muerto `existsByUsername` en `AppUserRepository.java:12`.
- **Deferido**: rate limiting (necesita infraestructura externa); refresh tokens (fuera de alcance); auditoría de cancelaciones (pendiente para incremento posterior).

**Incremento 6** (reservas): se implementaron las reglas de solape, cancelación y filtros con paginación; se corrigió el orden de validación del sort (`e8b9e51`) y se añadió `docker-compose` para desarrollo local (`084890e`).

### Herramientas usadas

- `read` / `edit` / `write` / `grep` / `glob` para manipulación de archivos.
- `bash`: `.\mvnw.cmd -B clean test`, `git diff`, `git push`, `curl` para pruebas HTTP reales, `javap` para inspeccionar bytecode (firmas de `PagedModel`, `DaoAuthenticationProvider`, `NimbusJwtDecoder`), `jshell` para generar hashes BCrypt.
- No se usaron búsquedas web (`webfetch`).

### Verificación

- Ejecución: `.\mvnw.cmd -B clean test` → 101/101 tests verdes.
- Pruebas HTTP reales con `curl` contra la app arrancada con Flyway + `ddl-auto=validate` sobre H2: login con credenciales reales → JWT con `iss=https://meeting-room-api`, `sub=admin`, `roles:["ADMIN"]`; contraseña incorrecta → 401 idéntico; token manipulado → 401; `/api/reservas` anónimo → 401; `/api/reservas` con token USER → 403; todas las respuestas en `problem+json`.
- Inspección de bytecode con `javap` para confirmar firmas reales (`PagedModel(Page)`, `DaoAuthenticationProvider(UserDetailsService)`, `NimbusJwtDecoder` con `SingleKeyJWSKeySelector(HS256, key)`) en lugar de confiar en la memoria.

> **Nota**: el agente nunca commiteó ni pusheó sin petición explícita del usuario. El HEAD actual es `0259d62` (docs(readme): reescribe la guia de ejecucion), sincronizado con `origin/master`.
