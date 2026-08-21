package com.kkodong.server.global.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;

/**
 * iOS ASAuthorizationController가 발급한 Apple identity token(JWT)을 검증한다.
 * Apple의 공개키(JWKS, https://appleid.apple.com/auth/keys)로 서명을 확인하고
 * iss/aud/exp를 검사한다 — Team ID/Key ID/.p8 private key는 필요 없다(그건 authorization
 * code 교환 등 서버-투-서버 흐름에만 필요, API_SPEC.md 0절 방식엔 불필요).
 */
@Slf4j
@Component
public class AppleIdTokenValidator {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.bundle-id}")
    private String bundleId;

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @PostConstruct
    public void init() throws MalformedURLException {
        JWKSource<SecurityContext> keySource =
                JWKSourceBuilder.create(URI.create(APPLE_JWKS_URL).toURL()).build();
        JWSKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

        jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);
        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(APPLE_ISSUER).audience(bundleId).build(),
                Set.of("sub", "email", "exp", "iat")
        ));
    }

    /**
     * 서명 + iss/aud/exp 검증 후 claims 반환. 실패 시 BusinessException(INVALID_APPLE_TOKEN).
     */
    public JWTClaimsSet verify(String identityToken) {
        try {
            return jwtProcessor.process(identityToken, null);
        } catch (Exception e) {
            log.warn("Apple identity token 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
    }
}
