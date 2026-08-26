package com.kkodong.server.global.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

/**
 * Refresh Token은 Postgres가 아니라 Redis에 저장한다(RedisHash).
 * timeToLive(초)가 지나면 Redis가 자동으로 만료시켜서 별도 배치/스케줄러 없이 폐기됨.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash(value = "refreshToken", timeToLive = 604800) // 7일
public class RefreshToken {

    @Id
    private String key;

    private String value;
}
