package com.kkodong.server.domain.meta.dto;

import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.UserProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MetaDataResponse(
        @Schema(description = "견종 선택지") breedOptions dogBreeds,
        @Schema(description = "성향 태그 선택지") traitOptions personalityTraits,
        @Schema(description = "산책 시간대 선택지") timeSlotOptions walkTimeSlots
) {


    public record breedOptions(
            @Schema(description = "최대 선택 개수", example = "1") int maxSelectable,
            @Schema(description = "그룹별 견종. 나열 순서가 화면 섹션 순서다") List<breedGroup> groups
    ) {}

    public record breedGroup(
            @Schema(description = "그룹 키", example = "small_companion") String key,
            @Schema(description = "섹션 제목", example = "소형 반려견") String display,
            @Schema(description = "견종 목록") List<String> breeds
    ) {}

    public record traitOptions(int maxSelectable, List<String> traits) {}

    public record timeSlotOptions(int maxSelectable, List<timeSlot> slots) {}

    public record timeSlot(
            @Schema(description = "저장·전송되는 키", example = "evening") String key,
            @Schema(description = "표시용", example = "저녁") String display
    ) {}
}
