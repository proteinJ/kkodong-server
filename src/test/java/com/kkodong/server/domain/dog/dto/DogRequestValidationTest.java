package com.kkodong.server.domain.dog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DogRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private DogRequest.registration request(String name, List<String> traits) {
        return new DogRequest.registration(
                name, "poodle", null, null, null, null, null, null,
                traits, BigDecimal.valueOf(5.6)
        );
    }

    @Test
    void name이_없으면_NotBlank_위반이_발생한다() {
        Set<ConstraintViolation<DogRequest.registration>> violations =
                validator.validate(request(null, List.of()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void name이_있으면_위반이_없다() {
        assertThat(validator.validate(request("초코", List.of()))).isEmpty();
    }

    @Test
    void 성향_태그가_3개면_위반이_없다() {
        assertThat(validator.validate(request("초코", List.of("활발함", "사교적", "차분함")))).isEmpty();
    }

    @Test
    void 성향_태그가_4개면_Size_위반이_발생한다() {
        Set<ConstraintViolation<DogRequest.registration>> violations =
                validator.validate(request("초코", List.of("활발함", "사교적", "차분함", "낯가림")));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("personalityTraits"));
    }
}
