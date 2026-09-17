package com.kkodong.server.domain.kindergarten.controller;

import com.kkodong.server.domain.kindergarten.dto.MyKindergartenResponse;
import com.kkodong.server.domain.kindergarten.service.MyKindergartenService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 견주가 <b>다니는</b> 유치원 (KG-09).
 *
 * <p><b>점주 경로와 섞지 않는다.</b> 알림장을 쓰는 것은 점주 앱({@code /api/v1/partner/...})이고
 * 견주는 발송된 것을 읽기만 한다. 점주 엔드포인트를 견주 토큰으로 부르면 403이므로
 * 견주용 경로를 따로 둔다 (API_SPEC 13절).
 *
 * <p>진입 경로는 <b>마이 탭</b>이다 — {@code UD-A} 부분 결정(2026-09-09). 재원 중인 사람만
 * 쓰는 화면이라 독립 탭으로 빼면 대부분의 사용자에게 평생 빈 탭이 하나 는다.
 */
@Tag(name = "Kindergarten (견주)", description = "견주가 다니는 유치원 조회 API — KG-09 마이 유치원 홈")
@RestController
@RequestMapping("/api/v1/kindergartens")
@RequiredArgsConstructor
public class MyKindergartenController {

    private final MyKindergartenService myKindergartenService;

    @Operation(
            summary = "내 유치원 목록 (KG-09)",
            description = """
                    내 강아지가 재원 중인 유치원을 모두 반환합니다. 마이 탭의 유치원 섹션을 그리는 데 씁니다.

                    - **다견 가구는 강아지 수만큼 행이 나옵니다** — 알림장이 아이별로 오기 때문입니다.
                    - 퇴원(`withdrawn`)한 곳은 서버가 제외합니다.
                    - `unreadNoteCount`는 발송됐고 아직 안 읽은 알림장 수입니다. 탭 배지가 이 값으로 그려집니다.
                    - 재원 중인 곳이 없으면 **빈 배열**입니다(에러 아님).
                    """
    )
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<MyKindergartenResponse.myItem>>> myKindergartens(
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        List<MyKindergartenResponse.myItem> response =
                myKindergartenService.getMyKindergartens(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("내 유치원 목록 조회 완료", response));
    }

    @Operation(
            summary = "마이 유치원 홈 (KG-09 / 화면 C-09)",
            description = """
                    오늘의 등원 상태 · 이용권 잔여 · 최근 알림장 3건을 한 번에 반환합니다.

                    **읽을 때 주의할 것**
                    - `todayAttendance`가 **`null`이면 "오늘은 등원하지 않는 날"**입니다.
                      `scheduled`("등원 전")와 다릅니다 — 합치면 안 가는 날이 결석처럼 읽힙니다.
                    - **하원은 상태가 아니라 `checkedOutAt`으로 판단합니다.**
                    - 기간권은 `remainingCount`·`totalCount`가 `null`입니다.
                    - `daysUntilExpiry`는 무기한이면 `null`, 이미 지났으면 음수입니다.

                    **에러** — 존재하지 않거나 내 강아지의 등록이 아니면 `404 KG001`입니다.
                    권한 없음을 403으로 주지 않는 이유는 `enrollmentId`가 점주 화면에도 노출되는 값이라,
                    403이면 "그 ID는 존재한다"가 드러나기 때문입니다.
                    """
    )
    @GetMapping("/enrollments/{enrollmentId}")
    public ResponseEntity<ApiResponse<MyKindergartenResponse.home>> kindergartenHome(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "원생 등록 ID. `GET /kindergartens/my` 응답의 `enrollmentId`")
            @PathVariable UUID enrollmentId
    ) {
        MyKindergartenResponse.home response =
                myKindergartenService.getHome(principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("마이 유치원 홈 조회 완료", response));
    }
}
