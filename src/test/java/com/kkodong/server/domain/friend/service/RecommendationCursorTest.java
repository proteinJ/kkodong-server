package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.friend.domain.CursorState;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationCursorTest {

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("인코딩한 커서를 디코딩하면 원래 값이 나온다")
    void roundTrip() {
        CursorState state = new CursorState(3, 40);

        assertThat(RecommendationCursor.decode(RecommendationCursor.encode(state))).isEqualTo(state);
    }

    @Test
    @DisplayName("커서가 없으면 첫 페이지로 보고 null 을 돌려준다")
    void emptyCursorIsFirstPage() {
        assertThat(RecommendationCursor.decode(null)).isNull();
        assertThat(RecommendationCursor.decode("")).isNull();
        assertThat(RecommendationCursor.decode("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "3:40", "Mzo0MA+"})
    @DisplayName("Base64 URL 형식이 아니면 INVALID_CURSOR")
    void malformedBase64(String cursor) {
        assertInvalid(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"340", "3:40:", "3:4:0", "a:40", "3:b", "0:40", "3:0", "-1:40", "3:-20"})
    @DisplayName("형식이나 값이 어긋난 내용이면 INVALID_CURSOR")
    void malformedContent(String raw) {
        assertInvalid(base64(raw));
    }

    private static void assertInvalid(String cursor) {
        assertThatThrownBy(() -> RecommendationCursor.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
