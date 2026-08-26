package com.kkodong.server.domain.user.service;

import com.nimbusds.jwt.JWTClaimsSet;
import com.kkodong.server.domain.user.domain.Role;
import com.kkodong.server.domain.user.domain.TokenDto;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.dto.LoginRequest;
import com.kkodong.server.domain.user.dto.SignupRequest;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.domain.user.repository.RefreshTokenRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.security.AppleIdTokenValidator;
import com.kkodong.server.global.security.JwtProvider;
import com.kkodong.server.global.security.PrincipalDetails;
import com.kkodong.server.global.security.RefreshToken;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppleIdTokenValidator appleIdTokenValidator;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        userRepository.save(user);
    }

    public TokenDto login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
        }

        TokenDto tokenDto = jwtProvider.createToken(authentication);
        saveRefreshToken(authentication.getName(), tokenDto.getRefreshToken());
        return tokenDto;
    }

    /**
     * Apple identity token 검증 후 sub(appleUserId)로 기존 회원을 찾거나, 없으면 신규 가입시킨다.
     * email은 identity_token의 email claim에서 가져온다(Apple ID 토큰은 매 로그인마다 email claim을 포함).
     */
    @Transactional
    public TokenDto loginWithApple(String identityToken) {
        JWTClaimsSet claims = appleIdTokenValidator.verify(identityToken);
        String appleUserId = claims.getSubject();

        User user = userRepository.findByAppleUserId(appleUserId)
                .orElseGet(() -> signUpAppleUser(appleUserId, claims));

        TokenDto tokenDto = jwtProvider.createTokenForSocial(user.getId(), user.getEmail(), user.getRole().name());
        saveRefreshToken(user.getEmail(), tokenDto.getRefreshToken());
        return tokenDto;
    }

    private User signUpAppleUser(String appleUserId, JWTClaimsSet claims) {
        String email;
        try {
            email = claims.getStringClaim("email");
        } catch (java.text.ParseException e) {
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
        if (email == null || userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }

        User user = com.kkodong.server.domain.user.domain.User.builder()
                .email(email)
                .appleUserId(appleUserId)
                .role(Role.USER)
                .build();
        return userRepository.save(user);
    }

    /**
     * Refresh Token 검증 후 새 토큰 쌍 발급 (회전 — 기존 RT는 즉시 폐기).
     * RT는 클레임이 없는 순수 서명 토큰이라, Redis에 key=RT문자열, value=email로
     * 저장해두고 그 매핑으로 사용자를 식별한다.
     */
    public TokenDto refresh(String refreshToken) {
        try {
            jwtProvider.validateTokenOrThrow(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        RefreshToken saved = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        User user = userRepository.findByEmail(saved.getValue())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        refreshTokenRepository.deleteById(refreshToken); // 회전: 재사용 방지

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new PrincipalDetails(user.getId(), user.getEmail(), user.getPassword(), List.of(authority)),
                "",
                List.of(authority)
        );

        TokenDto tokenDto = jwtProvider.createToken(authentication);
        saveRefreshToken(user.getEmail(), tokenDto.getRefreshToken());
        return tokenDto;
    }

    /**
     * 현재 AT를 Redis 블랙리스트에 등록(남은 만료시간만큼 TTL) + RT 삭제.
     */
    public void logout(String accessToken, String refreshToken) {
        Long remainingMillis = jwtProvider.getExpiration(accessToken);
        if (remainingMillis > 0) {
            redisTemplate.opsForValue().set(accessToken, "logout", Duration.ofMillis(remainingMillis));
        }
        refreshTokenRepository.deleteById(refreshToken);
    }

    private void saveRefreshToken(String email, String refreshToken) {
        refreshTokenRepository.save(RefreshToken.builder()
                .key(refreshToken)
                .value(email)
                .build());
    }
}
