package com.kkodong.server.domain.merchant.domain;

/**
 * 요일별 운영시간 한 줄. {@code merchants.business_hours} JSONB 배열의 원소다.
 *
 * <p>컬럼으로 펴지 않는 이유: 요일 수·휴게시간·업종별 예외로 구조가 흔들려서
 * 펴는 순간 NULL 밭이 된다. 조회 조건으로 쓰지 않고 표시용으로만 읽는 값이라
 * JSONB로 통째로 다루는 편이 낫다.
 *
 * @param day   요일. mon·tue·wed·thu·fri·sat·sun (값 유효성은 서버에서 검증)
 * @param open  개점 시각 "HH:mm"
 * @param close 폐점 시각 "HH:mm"
 */
public record BusinessHour(String day, String open, String close) {
}
