package com.kkodong.server.domain.dog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code PATCH /api/v1/dogs/{dogId}} 의 도메인 커맨드.
 *
 * <p>Dog.patch()에 필드를 하나씩 위치 인자로 넘기던 것을 묶었다. 필드가 늘어도
 * 시그니처와 호출부가 바뀌지 않고, 같은 타입 인자의 순서를 바꿔 넣는 실수가 차단된다.
 *
 * <p>DTO({@code DogRequest.patch})를 그대로 넘기지 않는 이유: 문자열 → enum 변환
 * (gender/size/energyLevel)은 잘못된 값에 400을 돌려줘야 하는 입력 검증이라
 * Service 계층에 남겨둔다. 도메인은 이미 검증된 타입만 받는다.
 *
 * <p>{@code null}인 필드는 "변경하지 않음"을 뜻한다(PATCH 의미론).
 * 누끼 이미지는 전용 엔드포인트로만 들어오므로 여기 포함하지 않는다.
 */
public record DogUpdate(
        String name,
        String breed,
        LocalDate birthDate,
        Gender gender,
        DogSize size,
        EnergyLevel energyLevel,
        Boolean neutered,
        String animalRegistrationNumber,
        List<String> personalityTraits,
        BigDecimal weightKg
) {}
