package com.pawwalk.server.domain.dog.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class DogRequest {

    public record registration(
        @NotBlank String name,
        String breed,
        LocalDate birthDate,
        String gender,
        String size,
        String energyLevel,
        Boolean neutered,
        String animalRegistrationNumber
    ) {}
}
