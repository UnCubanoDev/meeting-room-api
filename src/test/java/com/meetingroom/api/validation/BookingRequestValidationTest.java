package com.meetingroom.api.validation;

import com.meetingroom.api.dto.BookingRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRequestValidationTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2030, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsAValidRequest() {
        assertThat(validator.validate(new BookingRequest(1L, START, START.plusHours(1)))).isEmpty();
    }

    @Test
    void reportsEndBeforeStartOnTheEndField() {
        Set<ConstraintViolation<BookingRequest>> violations =
                validator.validate(new BookingRequest(1L, START, START.minusMinutes(1)));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("end");
            assertThat(violation.getMessage()).isEqualTo("La hora de fin debe ser posterior a la de inicio");
        });
    }

    @Test
    void rejectsEmptyIntervals() {
        assertThat(validator.validate(new BookingRequest(1L, START, START)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("end");
    }

    @Test
    void rejectsStartInThePast() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        assertThat(validator.validate(new BookingRequest(1L, past, past.plusHours(1))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("start");
    }

    /**
     * Si faltan las fechas solo se informa el campo obligatorio, sin un segundo error de rango.
     */
    @Test
    void reportsMissingFieldsOnlyOnce() {
        assertThat(validator.validate(new BookingRequest(null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("roomId", "start", "end");
    }

}
