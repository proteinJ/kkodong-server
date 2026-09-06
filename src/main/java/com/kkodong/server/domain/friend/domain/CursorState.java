package com.kkodong.server.domain.friend.domain;

public record CursorState(
        int radiusKm, // 1페이지에서 확정된 반경 - 다시 계산하지 않는다.
        int offset // 몇 개를 건너뛰고 할건지 (10, 20, 30 ...)
) {
}
