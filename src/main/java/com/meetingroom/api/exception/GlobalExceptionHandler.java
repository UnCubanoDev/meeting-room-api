package com.meetingroom.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de la aplicacion a respuestas RFC 7807 (Problem Details).
 * <p>
 * Se declara con maxima precedencia porque Spring Boot registra su propio
 * {@code ProblemDetailsExceptionHandler} (activado por {@code spring.mvc.problemdetails.enabled}),
 * que responde a {@link MethodArgumentNotValidException} sin detallar los campos invalidos.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }

}
