package com.kkodong.server.domain.dog.repository;

import com.kkodong.server.domain.dog.domain.Dog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DogRepository extends JpaRepository<Dog, UUID> {
    List<Dog> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID userId);
}
