package com.pawwalk.server.domain.user.service;

import com.pawwalk.server.domain.user.domain.User;
import com.pawwalk.server.domain.user.domain.Role;
import com.pawwalk.server.domain.user.domain.TokenDto;
import com.pawwalk.server.domain.user.dto.LoginRequest;
import com.pawwalk.server.domain.user.dto.SignupRequest;
import com.pawwalk.server.domain.user.repository.UserRepository;
import com.pawwalk.server.domain.user.repository.RefreshTokenRepository;
import com.pawwalk.server.global.error.BusinessException;
import com.pawwalk.server.global.error.ErrorCode;
import com.pawwalk.server.global.security.JwtProvider;
import com.pawwalk.server.global.security.PrincipalDetails;
import com.pawwalk.server.global.security.RefreshToken;
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
