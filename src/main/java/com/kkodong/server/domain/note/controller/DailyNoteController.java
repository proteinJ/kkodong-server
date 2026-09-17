package com.kkodong.server.domain.note.controller;

import com.kkodong.server.domain.note.dto.DailyNoteRequest;
import com.kkodong.server.domain.note.dto.DailyNoteResponse;
import com.kkodong.server.domain.note.service.DailyNoteService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 알림장 API — PN-14.
 *
 * <p>매일 원생 수만큼 반복되는 기능이라 입력 부담이 곧 이탈이다(FR-PN14-01).
 * 그래서 일괄 작성과 템플릿이 부가 기능이 아니라 본체다.
 */
@Tag(name = "Partner-DailyNote",
        description = "[점주] 알림장 작성·일괄작성·발송·템플릿 API (PN-14). 매일 쓰는 기능이라 입력 부담 최소화가 설계 목표다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/daily-notes")
@RequiredArgsConstructor
public class DailyNoteController {

    private final DailyNoteService dailyNoteService;

    @Operation(summary = "알림장 작성 현황 (PN-14)",
            description = """
                    해당 날짜의 작성 현황과 알림장 목록을 반환합니다.

                    ★ pending이 핵심입니다 — 오늘 등원했는데 아직 알림장이 없는 원생 목록입니다.
                    이 목록이 비는 것이 하루 마감 조건이며, 점주가 이 화면에서 실제로 보는 것은
                    "누구 걸 안 썼나"입니다. 이 목록을 그대로 일괄 작성 대상으로 넘기면 됩니다.

                    결석·취소한 원생은 대상에서 제외됩니다 — 알림장을 쓸 일이 없습니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<DailyNoteResponse.workspace>> getWorkspace(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "조회할 날짜", example = "2026-09-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        var response = dailyNoteService.getWorkspace(merchantId, principal.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success("알림장 현황 조회 완료", response));
    }

    @Operation(summary = "알림장 작성·수정 (PN-14)",
            description = """
                    한 원생의 알림장을 초안으로 저장합니다. 같은 (원생, 날짜)에 이미 초안이 있으면
                    그것을 고칩니다 — 하루 한 장이므로 "저장"을 여러 번 눌러도 결과가 같습니다.

                    templateId를 함께 보내면 템플릿을 바탕으로 하되, content의 비어 있지 않은
                    항목이 템플릿을 덮습니다. "템플릿 불러오고 컨디션만 고치기"가 한 번의 호출로 됩니다.

                    ⚠️ 이미 발송된 알림장은 수정할 수 없습니다(400, DN002) —
                    보호자가 이미 읽었을 수 있는 내용을 조용히 바꾸면 안 됩니다.""")
    @PostMapping
    public ResponseEntity<ApiResponse<DailyNoteResponse.detailInfo>> write(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody DailyNoteRequest.write request
    ) {
        var response = dailyNoteService.write(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("알림장 저장 완료", response));
    }

    @Operation(summary = "알림장 일괄 작성 (PN-14)",
            description = """
                    여러 원생에게 같은 내용으로 초안을 만듭니다. 작성 현황의 pending 목록을
                    그대로 넘기는 것이 기본 사용법입니다.

                    ⚠️ 만들어지는 것은 원생 수만큼의 개별 알림장입니다. 이후 한 명만 따로 고칠 수
                    있어야 하고 읽음 시각도 각자 다르기 때문입니다 — 공유 본문 하나가 아닙니다.

                    이미 발송된 알림장은 건드리지 않고 건너뜁니다(skipped에 DN002로 표시).
                    초안은 덮어씁니다. 부분 성공을 허용하니 skipped만 화면에 남겨 주세요.""")
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<DailyNoteResponse.bulkResult>> writeBulk(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody DailyNoteRequest.writeBulk request
    ) {
        var response = dailyNoteService.writeBulk(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("알림장 일괄 작성 완료", response));
    }

    @Operation(summary = "알림장 발송 (PN-14)",
            description = """
                    선택한 알림장을 보호자에게 공개합니다. 발송 전(DRAFT)에는 보호자에게 보이지
                    않습니다 — 쓰다 만 알림장이 새면 그 자체로 CS가 됩니다.

                    ⚠️ 다섯 항목이 모두 비어 있으면 발송되지 않습니다(DN004).
                    보호자에게 "알림장이 왔는데 내용이 없는" 상태는 안 보낸 것보다 나쁩니다.

                    이미 발송된 건은 건너뜁니다. 부분 성공을 허용합니다.""")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<DailyNoteResponse.bulkResult>> send(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody DailyNoteRequest.send request
    ) {
        var response = dailyNoteService.send(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("알림장 발송 완료", response));
    }

    @Operation(summary = "원생별 알림장 이력 (PN-11, KG-10)",
            description = "최근 30건을 날짜 역순으로 반환합니다. 초안도 포함되니 점주 화면에서만 쓰세요.")
    @GetMapping("/by-enrollment/{enrollmentId}")
    public ResponseEntity<ApiResponse<List<DailyNoteResponse.detailInfo>>> getByEnrollment(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = dailyNoteService.getByEnrollment(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("알림장 이력 조회 완료", response));
    }

    // ---------- 템플릿 (FR-PN14-01) ----------

    @Operation(summary = "알림장 템플릿 목록 (PN-14)",
            description = """
                    매장 단위로 공유되는 템플릿입니다 — 선생님이 바뀌어도 매장의 말투가 이어져야 합니다.
                    유치원 일과는 대체로 반복되므로, 이 목록이 실제 작성 시간을 좌우합니다.""")
    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<DailyNoteResponse.templateInfo>>> getTemplates(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = dailyNoteService.getTemplates(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("템플릿 목록 조회 완료", response));
    }

    @Operation(summary = "알림장 템플릿 저장 (PN-14)",
            description = "자주 쓰는 문구를 저장해 둡니다. 예: \"평범한 하루\", \"컨디션 안 좋은 날\"")
    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<DailyNoteResponse.templateInfo>> saveTemplate(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody DailyNoteRequest.saveTemplate request
    ) {
        var response = dailyNoteService.saveTemplate(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("템플릿 저장 완료", response));
    }

    @Operation(summary = "알림장 템플릿 삭제 (PN-14)",
            description = """
                    템플릿을 지워도 이미 작성된 알림장은 영향받지 않습니다 —
                    불러올 때 내용이 복사되므로 참조가 남지 않습니다.""")
    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "템플릿 ID") @PathVariable UUID templateId
    ) {
        dailyNoteService.deleteTemplate(merchantId, principal.getUserId(), templateId);
        return ResponseEntity.ok(ApiResponse.success("템플릿 삭제 완료"));
    }
}
