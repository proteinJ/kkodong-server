package com.kkodong.server.domain.enrollment.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 원생 목록 한 줄(PN-10). 원생 + 이용권 집계 + 출석 집계를 한 번에 담는다.
 *
 * <p><b>왜 프로젝션인가</b>: 목록에 필요한 것은 원생 자체가 아니라 "잔여가 얼마고 언제
 * 만료되며 마지막으로 언제 왔는가"다. 엔티티로 읽고 건별로 이용권·출석을 조회하면
 * 원생 수만큼 쿼리가 늘어난다(N+1). 정렬 기준이 전부 이 집계값이라 더더욱 한 번에 읽어야 한다.
 *
 * <p>Spring Data 인터페이스 프로젝션이라 구현체를 만들지 않는다 —
 * 네이티브 쿼리의 컬럼 별칭과 getter 이름이 대응한다.
 */
public interface EnrollmentRow {

    UUID getId();
    UUID getDogId();
    UUID getOwnerUserId();
    String getDogName();
    String getDogBreed();
    String getOwnerName();
    String getStatus();
    LocalDate getEnrolledOn();

    /**
     * 활성 <b>횟수권</b>의 잔여 합계. 기간권만 가진 원생은 null이다.
     * ⚠️ null과 0을 구분해야 한다 — "회차 개념이 없음"과 "다 썼음"은 다르다.
     */
    Integer getRemainingCount();

    /** 활성 이용권 중 가장 이른 만료일. 무기한만 있으면 null. */
    LocalDate getNearestExpiry();

    /** 활성 이용권 보유 여부. 없으면 등원 처리 자체가 막힌다(FR-PN09-02). */
    boolean getHasActivePass();

    /** 마지막으로 등원한 날. 한 번도 안 왔으면 null. */
    LocalDate getLastAttendedOn();

    /** 누적 등원 횟수. 등원 빈도순 정렬의 기준이다. */
    long getAttendedCount();
}
