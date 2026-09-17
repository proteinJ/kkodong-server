package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.friend.domain.CursorState;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 반경 + 오프셋 인코딩/디코딩
 */
public class RecommendationCursor {

    private static final String DELIMITER = ":";

    // 다른 곳에서 new RecommendationCursor() 로 객체를 만들 수 없게 막기 위해 private 생성자 만듦.
    private RecommendationCursor() {}

    public static String encode(CursorState state) {
        String raw = state.radiusKm() + DELIMITER + state.offset();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(DELIMITER, -1);
            if (parts.length != 2) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            int radiusKm = Integer.parseInt(parts[0]);
            int offset = Integer.parseInt(parts[1]);
            if (radiusKm <= 0 || offset <= 0) { // offset 0짜리는 나올 수 가 없다.
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            return new CursorState(radiusKm, offset);
        } catch (IllegalArgumentException e) {
            // Base64 깨짐 + NumberFormatException 둘 다 잡힘
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
