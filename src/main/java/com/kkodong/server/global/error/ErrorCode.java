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
    // 견종은 자유 입력이 아니라 GET /meta 가 내려주는 목록에서 고르게 한다
    // (설정: kkodong.dog.breed.groups). DB에 제약이 없으므로 이 검증이 유일한 방어선이다.
    INVALID_BREED(HttpStatus.BAD_REQUEST, "D005", "유효하지 않은 견종입니다."),

    // Safety (차단 B / 신고 R — 같은 safety 패키지지만 접두사는 기능 단위로 나눈다)
    SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "B001", "자기 자신은 차단할 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "R001", "자기 자신은 신고할 수 없습니다."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "신고 대상을 찾을 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "R003", "이미 접수되어 처리 대기 중인 신고입니다."),
    INVALID_REPORT_REASON(HttpStatus.BAD_REQUEST, "R004", "유효하지 않은 신고 사유입니다."),
    INVALID_REPORT_TARGET_TYPE(HttpStatus.BAD_REQUEST, "R005", "유효하지 않은 신고 대상 유형입니다."),
    UNSUPPORTED_REPORT_TARGET(HttpStatus.BAD_REQUEST, "R006", "아직 지원하지 않는 신고 대상입니다."),

    // Kindergarten (견주 유치원 KG — 견주가 '다니는' 유치원을 읽는 경로)
    // ⚠️ 이 블록을 파일 끝이 아니라 여기에 넣은 이유: 점주 앱(#71~#74)이 enum 끝에
    //    자기 코드를 이어 붙이고 있어, 양쪽 다 끝에 추가하면 머지 충돌이 확정된다.
    //    중간에 끼워 넣으면 hunk가 겹치지 않아 자동 병합된다.
    // ⚠️ 이름에 MY_ 를 붙인 이유: 점주 쪽에 이미 ENROLLMENT_NOT_FOUND 가 있어
    //    같은 이름을 쓰면 머지 시 enum 상수가 중복돼 컴파일이 깨진다.
    //    점주 것은 "매장 입장에서 원생이 없다", 이것은 "내 유치원이 아니다"로 의미도 다르다.
    MY_ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "KG001", "유치원 등록 정보를 찾을 수 없습니다."),

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
    LAST_DIRECTOR_CANNOT_RESIGN(HttpStatus.BAD_REQUEST, "PS005", "매장의 마지막 원장은 퇴사 처리할 수 없습니다."),

    // Reservation (예약 RV / 원생 EN)
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RV001", "존재하지 않는 예약입니다."),
    // 승인된 예약을 또 승인하는 등, 현재 상태에서 불가능한 전이를 요청한 경우.
    // 상태머신은 Reservation.transition 계열 메서드가 강제한다.
    INVALID_RESERVATION_STATUS(HttpStatus.BAD_REQUEST, "RV002", "현재 예약 상태에서 할 수 없는 작업입니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "RV003", "같은 날짜에 이미 예약이 있습니다."),
    // ⚠️ 정원 상한은 DB 제약으로 표현할 수 없어(카운트 상한) 승인 트랜잭션에서
    //    (매장, 날짜) advisory lock 을 잡고 직접 센다 — MerchantDayLock 참조.
    DAILY_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "RV004", "해당 날짜의 정원이 가득 찼습니다."),
    RESERVATION_ENROLLMENT_REQUIRED(HttpStatus.BAD_REQUEST, "RV005", "유치원 예약은 원생만 신청할 수 있습니다."),
    INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "RV006", "예약 종료 시각은 시작 시각보다 뒤여야 합니다."),

    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EN001", "존재하지 않는 원생입니다."),
    ENROLLMENT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "EN002", "재원 중인 원생이 아닙니다."),

    // Attendance (출석 AT / 이용권 PA)
    ATTENDANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "AT001", "존재하지 않는 등원 기록입니다."),
    INVALID_ATTENDANCE_STATUS(HttpStatus.BAD_REQUEST, "AT002", "현재 등원 상태에서 할 수 없는 작업입니다."),

    // FR-PN09-02 — 만료·잔여 0인 원생은 출석 대상에서 제외된다. 등원 처리까지 왔다면
    // 화면이 낡은 것이므로, 차감 없이 등원시키지 않고 여기서 막는다.
    NO_USABLE_PASS(HttpStatus.BAD_REQUEST, "PA001", "사용 가능한 이용권이 없습니다. 이용권을 먼저 발급하거나 연장해 주세요."),
    PASS_NOT_FOUND(HttpStatus.NOT_FOUND, "PA002", "존재하지 않는 이용권입니다."),
    PASS_NOT_USABLE(HttpStatus.BAD_REQUEST, "PA003", "만료되었거나 잔여 회차가 없는 이용권입니다."),
    PASS_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "PA004", "이미 환불되었거나 종료된 이용권입니다."),
    // 회차를 남기고 환불하면 그 회차로 등원이 되어 매출과 장부가 어긋난다.
    INVALID_PASS_ADJUSTMENT(HttpStatus.BAD_REQUEST, "PA005", "조정 후 잔여 회차가 0보다 작을 수 없습니다."),

    // Application (등원 신청 AP) — 신청서 제출은 견주 앱(KG-06/07) 몫이고,
    // 점주 앱은 양식 등록(PN-06)과 접수·승인(PN-07)을 맡는다.
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AP001", "존재하지 않는 등원 신청입니다."),
    APPLICATION_ALREADY_REVIEWED(HttpStatus.BAD_REQUEST, "AP002", "이미 처리된 신청입니다."),
    // 승인은 원생을 만드는 행위라, 이미 재원 중인 강아지를 또 승인하면 원생이 둘로 갈린다.
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "AP003", "이미 재원 중인 반려견입니다."),
    APPLICATION_FORM_NOT_FOUND(HttpStatus.NOT_FOUND, "AP004", "활성화된 신청서 양식이 없습니다."),
    CONSENT_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AP005", "활성화된 서약서가 없습니다."),
    // 퇴원한 원생을 다시 휴원시키는 등, 현재 상태에서 불가능한 전이를 요청한 경우.
    INVALID_ENROLLMENT_STATUS(HttpStatus.BAD_REQUEST, "EN003", "현재 원생 상태에서 할 수 없는 작업입니다."),

    // DailyNote (알림장 DN)
    DAILY_NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "DN001", "존재하지 않는 알림장입니다."),
    // 이미 보호자가 읽었을 수 있는 내용을 조용히 바꾸지 않는다.
    DAILY_NOTE_ALREADY_SENT(HttpStatus.BAD_REQUEST, "DN002", "이미 발송된 알림장은 수정할 수 없습니다."),
    NOTE_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "DN003", "존재하지 않는 알림장 템플릿입니다."),
    DAILY_NOTE_EMPTY(HttpStatus.BAD_REQUEST, "DN004", "알림장 내용이 비어 있습니다."),

    // Media (유치원 사진·영상 MD)
    // ⚠️ PC-23 — 미디어 보관 비용이 "고정비 0" 원칙과 충돌하는 유일한 기능이다.
    //    상한은 설정값(kkodong.media)이며 UD-C 결정 전까지 잠정치다.
    INVALID_MEDIA_FILE(HttpStatus.BAD_REQUEST, "MD001", "지원하지 않는 파일 형식입니다."),
    MEDIA_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "MD002", "허용된 용량을 초과했습니다."),
    // 서버가 리사이즈하지 않고 거부하는 이유는 MediaStorageService 주석 참조.
    MEDIA_RESOLUTION_TOO_HIGH(HttpStatus.BAD_REQUEST, "MD003", "허용된 해상도를 초과했습니다. 앱에서 크기를 줄여 다시 올려 주세요."),
    MEDIA_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MD004", "미디어 업로드에 실패했습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "MD005", "존재하지 않는 미디어입니다."),
    TOO_MANY_MEDIA_FILES(HttpStatus.BAD_REQUEST, "MD006", "한 번에 올릴 수 있는 파일 수를 초과했습니다."),

    // Assignment (담당 배정 AS)
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AS001", "존재하지 않는 담당 배정입니다."),

    // Review (리뷰 RW) — 리뷰 작성은 견주 앱 몫이고, 점주 앱은 확인과 답글만 맡는다.
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RW001", "존재하지 않는 리뷰입니다."),
    REVIEW_DELETED(HttpStatus.BAD_REQUEST, "RW002", "삭제된 리뷰에는 답글을 달 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
