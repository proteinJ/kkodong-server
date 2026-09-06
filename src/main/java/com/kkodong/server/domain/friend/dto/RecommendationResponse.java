package com.kkodong.server.domain.friend.dto;

import com.kkodong.server.domain.dog.dto.DogResponse;
import com.kkodong.server.domain.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class RecommendationResponse {
    public record page(
            @Schema(description = "추천 항목") List<item> items,
            @Schema(description = "다음 페이지 커서. 마지막 페이지면 null") String nextCursor,
            @Schema(description = "실제로 적용된 검색 반경(km)", example = "3") int appliedRadiusKm
    ) {
        public static page of(List<item> items, String nextCursor, int appliedRadiusKm) {
            return new page(items, nextCursor, appliedRadiusKm);
        }

        // 후보가 아예 없을 때, 반경은 확장 상한까지 갔다는 뜻이라 그대로 담는다.
        public static page of(int appliedRadiusKm) {
            return new page(List.of(), null, appliedRadiusKm);
        }

    }

    public record item(
            @Schema(description = "추천된 강아지") DogResponse.publicInfo dog,
            @Schema(description = "견주 요약") UserResponse.summary owner,
            @Schema(description = "정수 km로 반올림한 거리", example = "2") int distanceKm,
            @Schema(description = "추천 이유 문장",
                    example = "저녁에 산책하는 것도 같아요, 활발·사교적인 성격이 우리 아이랑 닮았어요")
            String reason,
            @Schema(description = "나와 이 강아지 사이의 pending 신청 상태",
                    allowableValues = {"none", "sent", "received"}, example = "none")
            String requestStatus
    ) {}
}
