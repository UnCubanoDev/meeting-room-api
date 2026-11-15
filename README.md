# meeting-room-api

API REST para la gestion de salas de reunion y sus reservas.

Estado actual (incremento 5): la API expone la gestion de salas en `/api/rooms` con listado
paginado (solo salas activas), consulta por id y alta. Incluye validacion de entrada, errores
uniformes en formato Problem Details (RFC 7807), documentacion OpenAPI y autenticacion JWT
(el alta de salas exige rol ADMIN). Todavia no hay gestion de reservas.

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
| `GET` | `/api/rooms` | Lista paginada de salas activas | `200` |
| `GET` | `/api/rooms/{id}` | Obtiene una sala activa por id | `200`, `400`, `404` |
| `POST` | `/api/rooms` | Da de alta una sala | `201` + cabecera `Location`, `400`, `409`, `415` |

El listado acepta `page`, `size` y `sort` (por defecto `size=20` y `sort=name`; el maximo de
`size` es 100). Devuelve el envoltorio estable de Spring (`PagedModel`):

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
| Payload invalido | `400` | `Validacion fallida` |
| JSON malformado, capacidad fraccionaria o fuera de rango | `400` | `Cuerpo ilegible` |
| `{id}` con formato no numerico | `400` | `Parametro invalido` |
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
