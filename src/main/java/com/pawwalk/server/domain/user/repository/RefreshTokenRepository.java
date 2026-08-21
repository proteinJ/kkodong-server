package com.pawwalk.server.domain.user.repository;

import com.pawwalk.server.global.security.RefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    void deleteByKey(String key);
}
