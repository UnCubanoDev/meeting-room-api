package com.meetingroom.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el fin de un {@link TimeRange} sea posterior a su inicio.
 *
 * <p>Es una restriccion de clase porque compara dos campos, pero el error se asocia al campo
 * {@code end} para que el cliente reciba el mismo formato que en cualquier otro error de campo.</p>
 */
@Documented
@Constraint(validatedBy = TimeRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeRange {

    String message() default "La hora de fin debe ser posterior a la de inicio";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
