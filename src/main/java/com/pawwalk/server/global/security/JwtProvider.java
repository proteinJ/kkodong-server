package com.pawwalk.server.global.security;

import com.pawwalk.server.domain.user.domain.TokenDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtProvider {

    private static final String AUTHORITIES_KEY = "auth";

    @Value("${jwt.secret}")
    private String secretkey;

    @Value("${jwt.access-token-validity-in-milliseconds}")
    private long accessTokenValidityInMilliseconds;

    @Value("${jwt.refresh-token-validity-in-milliseconds}")
    private long refreshTokenValidityInMilliseconds;

    private Key key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getEncoder().encode(secretkey.getBytes());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 이메일/비밀번호 로그인 성공 후 Authentication으로부터 AT/RT 생성
     */
    public TokenDto createToken(Authentication auth) {
        String authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        UUID userId = null;
        if (auth.getPrincipal() instanceof PrincipalDetails principal) {
            userId = principal.getUserId();
        }

        long now = (new Date()).getTime();
        Date accessTokenExpiresIn = new Date(now + accessTokenValidityInMilliseconds);
        Date refreshTokenExpiresIn = new Date(now + refreshTokenValidityInMilliseconds);

        String accessToken = Jwts.builder()
                .setSubject(auth.getName())
                .claim("userId", userId != null ? userId.toString() : null)
                .claim(AUTHORITIES_KEY, authorities)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = Jwts.builder()
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn.getTime())
                .build();
    }

    /**
     * 소셜/외부 로그인(Sign in with Apple 등) 검증 후 userId/email/권한만으로 발급.
     * Apple 로그인 추가 시: Apple identity token을 JWKS로 검증한 뒤 이 메서드로 토큰 발급.
     */
    public TokenDto createTokenForSocial(UUID userId, String email, String roleOrAuthority) {
        String authority = roleOrAuthority;
        if (authority != null && !authority.startsWith("ROLE_")) {
            authority = "ROLE_" + authority;
        }
        if (authority == null || authority.isBlank()) {
            authority = "ROLE_USER";
        }

        long now = (new Date()).getTime();
        Date accessTokenExpiresIn = new Date(now + accessTokenValidityInMilliseconds);
        Date refreshTokenExpiresIn = new Date(now + refreshTokenValidityInMilliseconds);

        String accessToken = Jwts.builder()
                .setSubject(email)
                .claim("userId", userId != null ? userId.toString() : null)
                .claim(AUTHORITIES_KEY, authority)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = Jwts.builder()
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn.getTime())
                .build();
    }

    /**
     * 토큰을 복호화해 Spring Security가 인식하는 Authentication 객체로 변환
     */
    public Authentication getAuthentication(String accessToken) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(accessToken)
                .getBody();

        if (claims.get(AUTHORITIES_KEY) == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        Object userIdObj = claims.get("userId");
        UUID userId = userIdObj != null ? UUID.fromString(userIdObj.toString()) : null;

        PrincipalDetails principal = new PrincipalDetails(
                userId,
                claims.getSubject(),
                "",
                authorities
        );

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    /** 토큰 유효성 검사 (boolean 반환 — 하위 호환용) */
    public boolean validateToken(String token) {
        try {
            validateTokenOrThrow(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 토큰 유효성 검사 — 실패 시 JwtException 계열 예외 throw */
    public void validateTokenOrThrow(String token) {
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }

    /** 토큰 만료까지 남은 시간(ms) — 로그아웃 시 블랙리스트 TTL로 사용 */
    public Long getExpiration(String accessToken) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(accessToken)
                .getBody().getExpiration();

        long now = (new Date()).getTime();
        return expiration.getTime() - now;
    }
}
