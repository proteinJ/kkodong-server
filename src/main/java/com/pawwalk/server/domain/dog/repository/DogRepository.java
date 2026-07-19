package com.pawwalk.server.domain.dog.repository;

import com.pawwalk.server.domain.dog.domain.Dog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DogRepository extends JpaRepository<Dog, UUID> {
    Dog findByDogId(UUID dogId);

    List<Dog> findByOwnerId(UUID ownerId);
}
