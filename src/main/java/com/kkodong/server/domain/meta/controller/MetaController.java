package com.kkodong.server.domain.meta.controller;

import com.kkodong.server.domain.meta.dto.MetaDataResponse;
import com.kkodong.server.domain.meta.service.MetaDataService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.UserProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meta", description = "클라이언트 선택지 제공")
@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaDataService metaService;

    @Operation(summary = "메타 데이터 조회", description = "견종·성향 태그·산책 시간대 선택지. 클라이언트가 하드코딩하지 않도록 서버가 내려준다.")
    @GetMapping
    public ApiResponse<MetaDataResponse> getMetaData() {
        MetaDataResponse res = metaService.getMetaData();
        return ApiResponse.success("메타 데이터 조회 성공", res);
    }
}
