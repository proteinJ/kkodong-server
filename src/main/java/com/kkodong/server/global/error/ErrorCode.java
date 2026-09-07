package com.kkodong.server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 범용 보일러플레이트용 최소 에러코드 세트.
 * runApp(github.com/proteinJ/runApp)의 도메인 특화 코드(GroupRunning, Spot, Shop 등)는
 * 이 프로젝트에서 사용하는 도메인이 아니라서 제외했다.
 *
 * 새 프로젝트에서 도메인을 추가할 때는 그 도메인 접두사로 코드를 이어서 추가한다.
 * 예: 꼬동이라면 "// Dog (반려견 관련)" 섹션에 DOG_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", ...) 식으로.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common (공통)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "CM001", "올바르지 않은 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "CM002", "잘못된 HTTP 메서드 호출입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CM003", "서버 내부 오류가 발생했습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "CM004", "이미 존재하는 데이터입니다."),

    // User (회원 관련 — 이메일/비밀번호 기반 인증 공통)
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "M001", "이미 존재하는 이메일입니다."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.BAD_REQUEST, "M003", "이메일 또는 비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "존재하지 않는 회원입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "M005", "비밀번호가 올바르지 않습니다."),
    ONBOARDING_REQUIREMENTS_NOT_MET(HttpStatus.BAD_REQUEST, "M006", "온보딩에 필요한 정보(닉네임·위치·반려견)가 모두 입력되지 않았습니다."),
    INVALID_WALK_TIME_SLOT(HttpStatus.BAD_REQUEST, "M007", "산책 시간대 값이 올바르지 않습니다."),

    // Auth (인증 관련)
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "A001", "인증에 실패하였습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A002", "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A004", "존재하지 않거나 만료된 refresh token입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),
    INVALID_APPLE_TOKEN(HttpStatus.UNAUTHORIZED, "A006", "유효하지 않은 Apple 로그인 토큰입니다."),

    // Dog (반려견 관련)
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "D001", "이미지 파일(jpg/png/webp)만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "D002", "이미지 업로드에 실패했습니다."),
    DOG_NOT_FOUND(HttpStatus.NOT_FOUND, "D003", "존재하지 않는 반려견입니다."),
    INVALID_PERSONALITY_TRAIT(HttpStatus.BAD_REQUEST, "D004", "유효하지 않은 성향 태그입니다."),

    // Safety (차단 B / 신고 R — 같은 safety 패키지지만 접두사는 기능 단위로 나눈다)
    SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "B001", "자기 자신은 차단할 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "R001", "자기 자신은 신고할 수 없습니다."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "신고 대상을 찾을 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "R003", "이미 접수되어 처리 대기 중인 신고입니다."),
    INVALID_REPORT_REASON(HttpStatus.BAD_REQUEST, "R004", "유효하지 않은 신고 사유입니다."),
    INVALID_REPORT_TARGET_TYPE(HttpStatus.BAD_REQUEST, "R005", "유효하지 않은 신고 대상 유형입니다."),
    UNSUPPORTED_REPORT_TARGET(HttpStatus.BAD_REQUEST, "R006", "아직 지원하지 않는 신고 대상입니다."),

    // Friend (친구 관련)
    // ⚠️ 산책 시간대는 추천의 선행조건이 아니다 — 미입력은 중립(0.5)으로 처리되며 불이익이 없다
    //    (FRIEND_RECOMMENDATION_SPEC.md 2절 / API_SPEC.md 2.1 규약 6). 반면 자택 위치는
    //    거리 계산의 기준점이라 없으면 추천 자체가 성립하지 않는다(같은 문서 규약 8).
    NOT_FOUND_HOME_LOCATION(HttpStatus.BAD_REQUEST, "F001", "자택 위치를 먼저 등록해야 친구 추천을 받을 수 있습니다."),
    DOG_NOT_OWNED(HttpStatus.FORBIDDEN, "F002", "본인의 반려견이 아닙니다."),

    // Partner (꼬동 파트너 — 매장 P / 스태프 PS)
    // ⚠️ PC-12 — Supabase RLS를 쓰지 않으므로 소속·권한 검증은 전부 Service 계층 몫이다.
    //    아래 P003/P004가 그 검증에서 나오는 코드이며, 점주 API의 기본 방어선이다.
    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 매장입니다."),
    DUPLICATE_BUSINESS_REGISTRATION(HttpStatus.CONFLICT, "P002", "이미 등록된 사업자등록번호입니다."),
    BUSINESS_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "P003", "사업자등록번호 진위확인에 실패했습니다."),
    MERCHANT_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P004", "존재하지 않는 이용권 상품입니다."),
    INVALID_PRODUCT_SHAPE(HttpStatus.BAD_REQUEST, "P005", "횟수권은 총 회차가, 기간권은 유효기간이 필요합니다."),
    INVITE_NOT_USABLE(HttpStatus.BAD_REQUEST, "P006", "폐기되었거나 만료된 초대 코드입니다."),

    NOT_MERCHANT_STAFF(HttpStatus.FORBIDDEN, "PS001", "해당 매장의 스태프가 아닙니다."),
    MERCHANT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "PS002", "이 작업을 수행할 권한이 없습니다."),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "PS003", "존재하지 않는 스태프입니다."),
    ALREADY_MERCHANT_STAFF(HttpStatus.CONFLICT, "PS004", "이미 해당 매장에 소속 신청했거나 소속된 계정입니다."),
    // 마지막 원장이 나가면 매장을 관리할 사람이 없어져 매장이 잠긴다.
    // 행 단위 제약으로 표현할 수 없어 Service 계층에서 센다.
    LAST_DIRECTOR_CANNOT_RESIGN(HttpStatus.BAD_REQUEST, "PS005", "매장의 마지막 원장은 퇴사 처리할 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
