package com.kkodong.server.domain.media.controller;

import com.kkodong.server.domain.media.dto.MediaResponse;
import com.kkodong.server.domain.media.service.MediaService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 사진·영상 API — PN-15.
 *
 * <p>⚠️ 이 기능은 스토리지 비용이 걸린 유일한 기능이다(PC-23). 상한은 설정값이며
 * UD-C 결정 전까지 잠정치다. 서버는 리사이즈하지 않고 상한을 넘으면 거부한다 —
 * 이 코드베이스는 이미 누끼 생성을 온디바이스로 처리해 "서버 이미지 처리 비용 0"을
 * 원칙으로 세웠다(FR-OB04-01).
 */
@Tag(name = "Partner-Media",
        description = "[점주] 사진·영상 업로드와 원생 태깅 API (PN-15). 태깅된 원생의 보호자 앨범으로 배분된다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "사진·영상 업로드 (PN-15)",
            description = """
                    여러 파일을 한 번에 올리고, 같은 원생 목록을 전부에 태깅합니다 —
                    활동 사진은 보통 같은 아이들을 연속으로 찍으므로 이쪽이 실제 사용에 맞습니다.
                    개별 조정은 태깅 수정으로 하세요.

                    ★ 태깅된 원생의 보호자 앨범으로 배분됩니다(FR-PN15-01).

                    ⚠️ 서버는 리사이즈하지 않습니다. 상한을 넘으면 거부하니(MD002/MD003)
                    앱에서 미리 줄여 올려 주세요. 썸네일도 클라이언트가 함께 올리는 것을 권합니다 —
                    없으면 앨범 한 화면에 원본 수십 장이 내려가 보호자 데이터를 태웁니다.

                    ⚠️ 부분 성공을 허용합니다. failed만 화면에 남겨 주세요.
                    응답의 totalBytes는 이번 업로드로 저장된 총 용량입니다 — 스토리지 증가가
                    눈에 보이라고 함께 줍니다(PC-23).

                    실패: 형식 불가 400(MD001) · 용량 초과 413(MD002) · 해상도 초과 400(MD003)""")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaResponse.uploadResult>> upload(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "업로드할 파일들") @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "촬영일. 생략 시 오늘", example = "2026-09-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn,
            @Parameter(description = "태깅할 원생 ID 목록") @RequestParam(required = false) List<UUID> enrollmentIds
    ) {
        var response = mediaService.upload(
                merchantId, principal.getUserId(), files, takenOn, enrollmentIds);
        return ResponseEntity.ok(ApiResponse.success("업로드 완료", response));
    }

    @Operation(summary = "원생 태깅 수정 (PN-15)",
            description = """
                    전달한 목록으로 태깅을 통째로 교체합니다(부분 추가/삭제가 아닙니다).
                    빈 목록을 보내면 모든 태깅이 해제되고, 그 사진은 어느 보호자 앨범에도 배분되지 않습니다.

                    ⚠️ 응답의 sharingRestricted를 확인하세요 — 태깅된 원생 중 촬영·공개 동의가
                    확인되지 않은 보호자가 있으면 true입니다(PC-29). 보호자 앨범 배분과
                    커뮤니티 내보내기(F-16)는 다른 동의입니다.""")
    @PutMapping("/{mediaId}/tags")
    public ResponseEntity<ApiResponse<MediaResponse.detailInfo>> retag(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "미디어 ID") @PathVariable UUID mediaId,
            @RequestBody List<UUID> enrollmentIds
    ) {
        var response = mediaService.retag(merchantId, principal.getUserId(), mediaId, enrollmentIds);
        return ResponseEntity.ok(ApiResponse.success("태깅 수정 완료", response));
    }

    @Operation(summary = "매장 앨범 조회 (PN-15, KG-12)",
            description = "촬영일 역순으로 반환합니다. date를 주면 그날 것만 나옵니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MediaResponse.detailInfo>>> getAlbum(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "촬영일 필터", example = "2026-09-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        var response = mediaService.getAlbum(merchantId, principal.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success("앨범 조회 완료", response));
    }

    @Operation(summary = "원생별 사진 조회 (PN-11, KG-11)",
            description = "해당 원생이 태깅된 사진만 반환합니다. 보호자 앨범에 배분될 것과 같은 목록입니다.")
    @GetMapping("/by-enrollment/{enrollmentId}")
    public ResponseEntity<ApiResponse<List<MediaResponse.detailInfo>>> getByEnrollment(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = mediaService.getByEnrollment(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("원생별 사진 조회 완료", response));
    }

    @Operation(summary = "미디어 삭제 (PN-15)",
            description = """
                    목록에서 제거하고 태깅도 함께 지웁니다.

                    ⚠️ R2의 실제 파일은 지우지 않습니다. 잘못 지웠을 때 되살릴 수 없는 편이
                    더 나쁘기 때문이며, 저장소 정리는 보관 정책(UD-C)이 정해진 뒤 일괄 작업으로 합니다.""")
    @DeleteMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "미디어 ID") @PathVariable UUID mediaId
    ) {
        mediaService.delete(merchantId, principal.getUserId(), mediaId);
        return ResponseEntity.ok(ApiResponse.success("삭제 완료"));
    }
}
