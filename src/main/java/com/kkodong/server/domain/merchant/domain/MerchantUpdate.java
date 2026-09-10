package com.kkodong.server.domain.merchant.domain;

import org.locationtech.jts.geom.Point;

import java.util.List;

/**
 * 매장 정보 PATCH용 커맨드(PN-04). null 필드는 "안 바꿈"을 뜻한다.
 *
 * <p>{@link com.kkodong.server.domain.dog.domain.DogUpdate}와 같은 패턴이다 —
 * 전체 덮어쓰기 시그니처는 필드가 늘 때마다 호출부까지 고쳐야 하고, 같은 타입 인자가
 * 연달아 있어 순서를 바꿔 넣어도 컴파일이 통과하는 위험이 있다.
 *
 * <p>⚠️ 사업자등록번호·대표자명·업종은 여기 없다. 진위확인을 통과한 값이라
 * 일반 수정으로 바뀌어서는 안 된다 — 바꾸려면 재검증을 거치는 별도 경로를 쓴다.
 */
public record MerchantUpdate(
        String name,
        String address,
        Point location,
        String phone,
        String description,
        List<String> imageUrls,
        List<BusinessHour> businessHours,
        List<String> closedDates
) {
}
