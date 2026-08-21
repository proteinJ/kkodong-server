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

    // User (회원 관련 — 이메일/비밀번호 기반 인증 공통)
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "M001", "이미 존재하는 이메일입니다."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.BAD_REQUEST, "M003", "이메일 또는 비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "존재하지 않는 회원입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "M005", "비밀번호가 올바르지 않습니다."),
    ONBOARDING_REQUIREMENTS_NOT_MET(HttpStatus.BAD_REQUEST, "M006", "온보딩에 필요한 정보(닉네임·위치·반려견)가 모두 입력되지 않았습니다."),

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
    INVALID_PERSONALITY_TRAIT(HttpStatus.BAD_REQUEST, "D004", "유효하지 않은 성향 태그입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
