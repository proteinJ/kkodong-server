package com.kkodong.server.domain.dog.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.dog.domain.EnergyLevel;
import com.kkodong.server.domain.dog.domain.Gender;
import com.kkodong.server.domain.dog.dto.DogRequest;
import com.kkodong.server.domain.dog.dto.DogResponse;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DogService {

    private final DogRepository dogRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public DogResponse.detailInfo animalRegistration(UUID ownerId, DogRequest.registration request) {
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

    private DogResponse.detailInfo toResponse(Dog dog) {
        return new DogResponse.detailInfo(
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
    public void animalDelete(UUID userId, UUID dogId) {
        Dog dog = getDog(userId, dogId);

        dogRepository.delete(dog);
    }

    @Transactional
    public DogResponse.patch animalPatch(UUID userId, UUID dogId, DogRequest.patch request) {
        Dog dog = getDog(userId, dogId);

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

    private @NonNull Dog getDog(UUID userId, UUID dogId) {
        Dog dog = dogRepository.findByDogId(dogId);
        if (!dog.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.METHOD_NOT_ALLOWED);
        }
        return dog;
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

    @Transactional
    public DogResponse.detailInfo animalGet(UUID userId, UUID dogId) {
        Dog dog = getDog(userId, dogId);

        return toResponse(dog);
    }

    @Transactional
    public DogResponse.detailInfo animalPhoto(UUID userId, UUID dogId, MultipartFile file) {
        Dog dog = getDog(userId, dogId);

        String imageUrl = imageStorageService.upload(file, "dogs/" + dogId);
        dog.updateProfileImage(imageUrl);

        return toResponse(dog);
    }

    public List<DogResponse.detailInfo> animalListGet(UUID userId) {
        List<Dog> dogList = dogRepository.findByOwnerId(userId);
        return dogList.stream().map(this::toResponse).toList();
    }
}
