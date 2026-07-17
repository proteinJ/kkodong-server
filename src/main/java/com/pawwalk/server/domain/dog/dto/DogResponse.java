package com.pawwalk.server.domain.dog.dto;

import java.time.LocalDate;
import java.util.UUID;

public class DogResponse {

    public record registration(
        UUID id,
        UUID ownerId,
        String name,
        String breed,
        LocalDate birthDate,
        String gender,
        String size,
        String energyLevel,
        Boolean neutered,
        String profileImageUrl,
        String animalRegistrationNumber
    ) {}
}
