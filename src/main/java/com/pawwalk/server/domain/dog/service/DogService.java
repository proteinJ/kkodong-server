package com.pawwalk.server.domain.dog.service;

import com.pawwalk.server.domain.dog.domain.Dog;
import com.pawwalk.server.domain.dog.domain.DogSize;
import com.pawwalk.server.domain.dog.domain.EnergyLevel;
import com.pawwalk.server.domain.dog.domain.Gender;
import com.pawwalk.server.domain.dog.dto.DogRequest;
import com.pawwalk.server.domain.dog.dto.DogResponse;
import com.pawwalk.server.domain.dog.repository.DogRepository;
import com.pawwalk.server.global.error.BusinessException;
import com.pawwalk.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DogService {

    private final DogRepository dogRepository;

    @Transactional
    public DogResponse.registration animalRegistration(UUID ownerId, DogRequest.registration request) {
        Dog dog = Dog.builder()
                .ownerId(ownerId)
                .name(request.name())
                .breed(request.breed())
                .birthDate(request.birthDate())
                .gender(request.gender() != null ? Gender.valueOf(request.gender().toUpperCase()) : null)
                .size(request.size() != null ? DogSize.valueOf(request.size().toUpperCase()) : null)
                .energyLevel(request.energyLevel() != null ? EnergyLevel.valueOf(request.energyLevel().toUpperCase()) : null)
                .neutered(request.neutered())
                .animalRegistrationNumber(request.animalRegistrationNumber())
                .build();

        Dog saved = dogRepository.save(dog);
        return toResponse(saved);
    }

    private DogResponse.registration toResponse(Dog dog) {
        return new DogResponse.registration(
                dog.getId(),
                dog.getOwnerId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getGender() != null ? dog.getGender().name().toLowerCase() : null,
                dog.getSize() != null ? dog.getSize().name().toLowerCase() : null,
                dog.getEnergyLevel() != null ? dog.getEnergyLevel().name().toLowerCase() : null,
                dog.getNeutered(),
                dog.getProfileImageUrl(),
                dog.getAnimalRegistrationNumber()
        );
    }

    @Transactional
    public void animalDelete(UUID memberId, UUID dogId) {
        Dog dog = dogRepository.findByDogId(dogId);
        if (!dog.getOwnerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.METHOD_NOT_ALLOWED);
        }

        dogRepository.delete(dog);
    }

    @Transactional
    public DogResponse.patch animalPatch(UUID memberId, UUID dogId, DogRequest.patch request) {
        Dog dog = dogRepository.findByDogId(dogId);
        if (!dog.getOwnerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.METHOD_NOT_ALLOWED);
        }

        dog.patch(
            request.name(),
            request.breed(),
            request.birthDate(),
            request.gender() != null ? Gender.valueOf(request.gender().toUpperCase()) : null,
            request.size() != null ? DogSize.valueOf(request.size().toUpperCase()) : null,
            request.energyLevel() != null ? EnergyLevel.valueOf(request.energyLevel().toUpperCase()) : null,
            request.neutered(),
            request.animalRegistrationNumber()
        );

        return toPatchResponse(dog);
    }

    private DogResponse.patch toPatchResponse(Dog dog) {
        return new DogResponse.patch(
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getGender() != null ? dog.getGender().name().toLowerCase() : null,
                dog.getSize() != null ? dog.getSize().name().toLowerCase() : null,
                dog.getEnergyLevel() != null ? dog.getEnergyLevel().name().toLowerCase() : null,
                dog.getNeutered(),
                dog.getProfileImageUrl(),
                dog.getAnimalRegistrationNumber()
        );
    }
}
