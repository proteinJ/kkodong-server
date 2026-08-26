package com.kkodong.server.domain.user.repository;

import com.kkodong.server.global.security.RefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    void deleteByKey(String key);
}
