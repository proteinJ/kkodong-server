package com.kkodong.server.domain.friend.controller;

import com.kkodong.server.domain.friend.service.RecommendationService;
import com.kkodong.server.domain.friend.dto.RecommendationResponse;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@Tag(name = "Friend", description = "친구 추천 관련")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "산책 강아지 친구 추천", description = "산책을 같이하기 좋은 강아지 친구를 소개 시켜준다.")
    @GetMapping("/recommendations")
    public ApiResponse<RecommendationResponse.page> recommendations(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestParam UUID dogId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor
            ) {
        RecommendationResponse.page response = recommendationService.recommend(principal.getUserId(), dogId, limit, cursor);
        return ApiResponse.success("산책 강아지 친구 추천 성공", response);
    }
}
