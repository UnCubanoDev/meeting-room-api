package com.meetingroom.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de la aplicacion a respuestas RFC 7807 (Problem Details).
 * <p>
 * Se declara con orden 0 porque Spring Boot registra su propio {@code ProblemDetailsExceptionHandler}
 * (activado por {@code spring.mvc.problemdetails.enabled}) sin orden explicito, es decir, con la
 * prioridad minima; basta con adelantarse a ese valor. El motivo es que el handler de Boot responde
 * a {@link MethodArgumentNotValidException} sin detallar los campos invalidos.
 */
@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRoomNameException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateName(DuplicateRoomNameException exception,
                                                             HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflicto", exception.getMessage(), request);
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ProblemDetail> handleBookingConflict(BookingConflictException exception,
                                                               HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Sala ocupada", exception.getMessage(), request);
    }

    @ExceptionHandler(BookingStateException.class)
    public ResponseEntity<ProblemDetail> handleBookingState(BookingStateException exception,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Estado no valido", exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenOperation(ForbiddenOperationException exception,
                                                                  HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidSortException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSort(InvalidSortException exception,
                                                           HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Parametro invalido", exception.getMessage(), request);
    }

    /**
     * Solo las violaciones de unicidad son un conflicto de negocio. El resto (NOT NULL, CHECK,
     * longitudes) es un fallo interno: se registra con su causa raiz y se responde 500 para no
     * enmascarar el problema como si fuera un duplicado.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception,
                                                             HttpServletRequest request) {
        if (PersistenceErrors.isUniqueViolation(exception)) {
            log.warn("Violacion de unicidad en {}: {}", request.getRequestURI(),
                    NestedExceptionUtils.getMostSpecificCause(exception).getMessage());
            return problem(HttpStatus.CONFLICT, "Conflicto",
                    "El valor enviado ya existe y debe ser unico", request);
        }
        log.error("Error de integridad de datos en {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "No se pudo completar la operacion", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception,
                                                          HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La peticion contiene campos invalidos");
        problem.setTitle("Validacion fallida");
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        log.warn("Cuerpo ilegible en {}: {}", request.getRequestURI(),
                NestedExceptionUtils.getMostSpecificCause(exception).getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Cuerpo ilegible",
                "El cuerpo de la peticion no es un JSON valido para este recurso", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "El parametro '" + exception.getName() + "' tiene un formato invalido", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception,
                                                                   HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                                                    HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
                "El Content-Type de la peticion debe ser application/json", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }

}
