package com.kkodong.server.domain.friend.domain;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.user.domain.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Subject(
        UUID dogId, UUID ownerId,
        String breed, LocalDate birthDate, DogSize size,
        List<String> personalityTraits, // 강아지것
        List<String> walkTimeSlots // 견주 것 <- 두 테이블에서 온다.
) {

    public static Subject of(Dog dog, User owner) {
        return new Subject(
                dog.getId(),
                owner.getId(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getSize(),
                dog.getPersonalityTraits(),
                owner.getWalkTimeSlots()
        );
    }
}


