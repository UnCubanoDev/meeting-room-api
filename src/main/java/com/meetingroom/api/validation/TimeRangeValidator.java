package com.meetingroom.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRange> {

    /**
     * Los valores nulos no se evaluan aqui: ya los rechaza {@code @NotNull} en cada campo, y
     * repetir el error solo anadiria ruido a la respuesta.
     */
    @Override
    public boolean isValid(TimeRange range, ConstraintValidatorContext context) {
        if (range == null || range.start() == null || range.end() == null) {
            return true;
        }
        if (range.end().isAfter(range.start())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("end")
                .addConstraintViolation();
        return false;
    }

}
