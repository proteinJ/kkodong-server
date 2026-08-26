package com.kkodong.server.domain.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenDto {

    @Schema(description = "토큰 타입", example = "Bearer")
    private String grantType;
    @Schema(description = "access token")
    private String accessToken;
    @Schema(description = "refresh token")
    private String refreshToken;
    @Schema(description = "access token 만료까지 남은 시간(ms)")
    private Long accessTokenExpiresIn;
}
