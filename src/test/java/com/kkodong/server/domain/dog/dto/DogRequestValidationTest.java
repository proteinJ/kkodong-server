package com.kkodong.server.domain.dog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DogRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void name이_없으면_NotBlank_위반이_발생한다() {
        DogRequest.registration request = new DogRequest.registration(
                null, "poodle", null, null, null, null, null, null
        );

        Set<ConstraintViolation<DogRequest.registration>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void name이_있으면_위반이_없다() {
        DogRequest.registration request = new DogRequest.registration(
                "초코", "poodle", null, null, null, null, null, null
        );

        Set<ConstraintViolation<DogRequest.registration>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
