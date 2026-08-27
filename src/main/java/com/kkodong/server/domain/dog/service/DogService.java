package com.kkodong.server.domain.dog.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.dog.domain.DogUpdate;
import com.kkodong.server.domain.dog.domain.EnergyLevel;
import com.kkodong.server.domain.dog.domain.Gender;
import com.kkodong.server.domain.dog.dto.DogRequest;
import com.kkodong.server.domain.dog.dto.DogResponse;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DogService {

    private final DogRepository dogRepository;
    private final ImageStorageService imageStorageService;
    private final DogProperties dogProperties;

    @Transactional
    public DogResponse.detailInfo animalRegistration(UUID ownerId, DogRequest.registration request) {
        List<String> traits = validateTraits(request.personalityTraits());

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
                // 미입력은 빈 배열 — dogs.personality_traits는 NOT NULL이고,
                // 빈 배열은 추천 점수에서 0점이 아니라 중립(0.5)으로 처리된다
                // (FRIEND_RECOMMENDATION_SPEC.md). null을 넘기면 @Builder.Default가 덮여버린다.
                .personalityTraits(traits != null ? traits : new ArrayList<>())
                .weightKg(request.weightKg())
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
                dog.getCutoutImageUrl(),
                dog.getAnimalRegistrationNumber(),
                dog.getPersonalityTraits(),
                dog.getWeightKg()
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

        dog.patch(new DogUpdate(
                request.name(),
                request.breed(),
                request.birthDate(),
                request.gender() != null ? Gender.valueOf(request.gender().toUpperCase()) : null,
                request.size() != null ? DogSize.valueOf(request.size().toUpperCase()) : null,
                request.energyLevel() != null ? EnergyLevel.valueOf(request.energyLevel().toUpperCase()) : null,
                request.neutered(),
                request.animalRegistrationNumber(),
                validateTraits(request.personalityTraits()),
                request.weightKg()
        ));

        return toPatchResponse(dog);
    }

    /**
     * 성향 태그 값 유효성 검증. 개수 제한(≤3)은 DTO의 @Size와 DB CHECK 제약이 담당하고,
     * 여기서는 값이 설정된 목록({@code kkodong.dog.personality.tags})에 속하는지만 본다.
     * 태그 세트는 실사용 데이터로 바뀔 수 있어 enum이 아니라 설정값으로 둔다
     * (FRIEND_RECOMMENDATION_SPEC.md 1절).
     *
     * <p>null은 그대로 통과시킨다 — PATCH에서 "변경하지 않음"을 뜻하기 때문.
     */
    private List<String> validateTraits(List<String> traits) {
        if (traits == null) return null;
        for (String trait : traits) {
            if (!dogProperties.personality().isValidLabel(trait)) {
                throw new BusinessException(ErrorCode.INVALID_PERSONALITY_TRAIT);
            }
        }
        return traits;
    }

    /**
     * 요청자가 해당 반려견의 보호자인지 확인한다. RLS를 쓰지 않으므로
     * 권한 검증은 전부 이 계층에서 한다(DEV_STACK_PIPELINE.md 2절).
     */
    private @NonNull Dog getDog(UUID userId, UUID dogId) {
        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOG_NOT_FOUND));

        if (!dog.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
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
                dog.getCutoutImageUrl(),
                dog.getAnimalRegistrationNumber(),
                dog.getPersonalityTraits(),
                dog.getWeightKg()
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

    /**
     * 누끼 이미지 업로드(ONBOARD-3). 배경 제거는 클라이언트 온디바이스(Vision)에서 끝내고
     * 서버는 결과 PNG를 저장만 한다 — 서버 이미지 처리 비용 0 (API_SPEC.md 1절).
     */
    @Transactional
    public DogResponse.detailInfo animalCutout(UUID userId, UUID dogId, MultipartFile file) {
        Dog dog = getDog(userId, dogId);

        String imageUrl = imageStorageService.upload(file, "dogs/" + dogId + "/cutout");
        dog.updateCutoutImage(imageUrl);

        return toResponse(dog);
    }

    public List<DogResponse.detailInfo> animalListGet(UUID userId) {
        List<Dog> dogList = dogRepository.findByOwnerId(userId);
        return dogList.stream().map(this::toResponse).toList();
    }
}
