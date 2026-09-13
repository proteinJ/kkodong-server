package com.kkodong.server.domain.place.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커서 토큰 (API_SPEC 14.2).
 *
 * <p>가장 중요한 성질은 "되돌렸을 때 거리가 비트 단위로 같다"이다. 조금이라도 달라지면
 * 거리가 같은 매장끼리의 경계에서 SQL 비교가 어긋나, 페이지를 넘길 때 매장이 빠지거나 겹친다.
 */
class PlaceCursorTest {

    private static final UUID ID = UUID.fromString("3f2b8a3e-6d8c-4c2a-9a41-0c1d2e3f4a5b");

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 99.64193784721032, 1.0E-7, 9999.999999999998, 12345.678901234567})
    @DisplayName("인코딩했다 되돌리면 거리와 id 가 정확히 같다")
    void roundTripIsExact(double distance) {
        PlaceCursor original = new PlaceCursor(distance, ID);

        PlaceCursor decoded = PlaceCursor.decode(original.encode());

        assertThat(Double.doubleToLongBits(decoded.distanceMeters()))
                .isEqualTo(Double.doubleToLongBits(distance));
        assertThat(decoded.id()).isEqualTo(ID);
    }

    @Test
    @DisplayName("토큰은 URL 에 그대로 넣을 수 있는 문자로만 이뤄진다")
    void tokenIsUrlSafe() {
        assertThat(new PlaceCursor(123.456, ID).encode()).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("비어 있는 토큰은 거절한다")
    void rejectsBlank(String token) {
        assertThatThrownBy(() -> PlaceCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> malformedTokens() {
        return Stream.of(
                "@@@not-base64@@@",
                base64("no-separator"),
                base64(":" + ID),               // 거리 없음
                base64("abc:" + ID),            // 숫자 아님
                base64("-1.0:" + ID),           // 음수 거리
                base64("NaN:" + ID),
                base64("Infinity:" + ID),
                base64("12.5:not-a-uuid"));
    }

    @ParameterizedTest
    @MethodSource("malformedTokens")
    @DisplayName("형식이 틀린 토큰은 IllegalArgumentException 으로 거절한다 — 서비스가 400 으로 바꾼다")
    void rejectsMalformed(String token) {
        assertThatThrownBy(() -> PlaceCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }
}
