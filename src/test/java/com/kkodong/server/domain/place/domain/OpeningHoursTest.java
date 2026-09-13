package com.kkodong.server.domain.place.domain;

import com.kkodong.server.domain.merchant.domain.BusinessHour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "지금 영업 중" 판정 규칙 (API_SPEC 14.2).
 *
 * <p>규칙 하나하나가 앱 화면의 "영업 중" 배지와 직결되고, 틀려도 컴파일은 통과한다.
 * 특히 자정을 넘기는 영업과 임시 휴무가 겹치는 경우는 사람이 머릿속으로 따라가다 틀리기 쉽다.
 */
class OpeningHoursTest {

    // 요일을 헷갈리지 않게 날짜를 고정하고, 아래 @BeforeAll 에서 실제 요일을 확인한다.
    private static final LocalDate SAT = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUN = LocalDate.of(2026, 9, 13);
    private static final LocalDate MON = LocalDate.of(2026, 9, 14);

    @BeforeAll
    static void fixedDatesHaveExpectedWeekdays() {
        assertThat(SAT.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(SUN.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(MON.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    private static LocalDateTime at(LocalDate date, String hhmm) {
        return LocalDateTime.of(date, LocalTime.parse(hhmm));
    }

    private static OpeningHours hours(BusinessHour... rows) {
        return OpeningHours.of(List.of(rows), List.of());
    }

    @Test
    @DisplayName("영업시간이 비어 있으면 null 이다 — 모르는 것을 닫힘으로 보이지 않는다")
    void emptyHoursIsUnknown() {
        assertThat(OpeningHours.of(List.of(), List.of()).isOpenAt(at(MON, "12:00"))).isNull();
        assertThat(OpeningHours.of(null, null).isOpenAt(at(MON, "12:00"))).isNull();
    }

    @Test
    @DisplayName("형식이 잘못된 줄만 있으면 null 이다")
    void onlyMalformedRowsIsUnknown() {
        OpeningHours hours = hours(
                new BusinessHour("monday", "09:00", "18:00"),  // 요일 오타
                new BusinessHour("mon", "9:00", "18:00"),      // HH:mm 아님
                new BusinessHour("mon", "09:00", "25:00"),     // 없는 시각
                new BusinessHour(null, "09:00", "18:00"));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isNull();
    }

    @Test
    @DisplayName("형식이 잘못된 줄은 건너뛰고 나머지 줄로 판정한다")
    void malformedRowsAreSkipped() {
        OpeningHours hours = hours(
                new BusinessHour("mon", "oops", "18:00"),
                new BusinessHour("mon", "09:00", "18:00"));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isTrue();
    }

    @Test
    @DisplayName("개점 시각은 포함하고 폐점 시각은 포함하지 않는다")
    void openInclusiveCloseExclusive() {
        OpeningHours hours = hours(new BusinessHour("mon", "09:00", "19:00"));

        assertThat(hours.isOpenAt(at(MON, "08:59"))).isFalse();
        assertThat(hours.isOpenAt(at(MON, "09:00"))).isTrue();
        assertThat(hours.isOpenAt(at(MON, "18:59"))).isTrue();
        assertThat(hours.isOpenAt(at(MON, "19:00"))).isFalse();
    }

    @Test
    @DisplayName("오늘 요일 줄이 없으면 false 다")
    void noRowForTodayIsClosed() {
        OpeningHours hours = hours(new BusinessHour("sat", "10:00", "15:00"));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isFalse();
    }

    @Test
    @DisplayName("요일은 대소문자와 앞뒤 공백을 가리지 않는다")
    void dayIsCaseInsensitive() {
        OpeningHours hours = hours(new BusinessHour(" MON ", "09:00", "18:00"));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isTrue();
    }

    @Test
    @DisplayName("같은 요일에 여러 줄이면 한 줄이라도 지금을 포함할 때 영업 중이다 (점심 휴게)")
    void multipleRowsPerDay() {
        OpeningHours hours = hours(
                new BusinessHour("mon", "09:00", "12:00"),
                new BusinessHour("mon", "13:00", "18:00"));

        assertThat(hours.isOpenAt(at(MON, "11:30"))).isTrue();
        assertThat(hours.isOpenAt(at(MON, "12:30"))).isFalse();
        assertThat(hours.isOpenAt(at(MON, "13:30"))).isTrue();
    }

    @Test
    @DisplayName("폐점이 개점보다 이르면 자정을 넘기는 영업이다 — 토 22:00~02:00 은 일요일 새벽까지")
    void crossesMidnight() {
        OpeningHours hours = hours(new BusinessHour("sat", "22:00", "02:00"));

        assertThat(hours.isOpenAt(at(SAT, "21:59"))).isFalse();
        assertThat(hours.isOpenAt(at(SAT, "23:00"))).isTrue();
        assertThat(hours.isOpenAt(at(SUN, "01:30"))).isTrue();
        assertThat(hours.isOpenAt(at(SUN, "02:00"))).isFalse();
    }

    @Test
    @DisplayName("00:00~00:00 은 24시간 영업이다")
    void allDay() {
        OpeningHours hours = hours(new BusinessHour("mon", "00:00", "00:00"));

        assertThat(hours.isOpenAt(at(MON, "00:00"))).isTrue();
        assertThat(hours.isOpenAt(at(MON, "12:00"))).isTrue();
        assertThat(hours.isOpenAt(at(MON, "23:59"))).isTrue();
    }

    @Test
    @DisplayName("폐점 24:00 은 그날 자정까지 영업이고 다음 날로 넘어가지 않는다")
    void closeAt2400() {
        OpeningHours hours = hours(new BusinessHour("sat", "09:00", "24:00"));

        assertThat(hours.isOpenAt(at(SAT, "23:59"))).isTrue();
        assertThat(hours.isOpenAt(at(SUN, "00:30"))).isFalse();
    }

    @Test
    @DisplayName("오늘이 휴무일이면 오늘 시작하는 영업은 없다")
    void closedToday() {
        OpeningHours hours = OpeningHours.of(
                List.of(new BusinessHour("mon", "09:00", "18:00")),
                List.of(MON.toString()));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isFalse();
    }

    @Test
    @DisplayName("오늘이 휴무일이어도 전날 밤부터 이어지는 새벽 영업은 전날 기준으로 영업 중이다")
    void closedTodayButOvernightFromYesterday() {
        OpeningHours hours = OpeningHours.of(
                List.of(new BusinessHour("sat", "22:00", "02:00")),
                List.of(SUN.toString()));

        assertThat(hours.isOpenAt(at(SUN, "01:30"))).isTrue();
    }

    @Test
    @DisplayName("전날이 휴무일이면 전날 밤 영업도 없으므로 새벽에도 닫혀 있다")
    void closedYesterdayCancelsOvernight() {
        OpeningHours hours = OpeningHours.of(
                List.of(new BusinessHour("sat", "22:00", "02:00")),
                List.of(SAT.toString()));

        assertThat(hours.isOpenAt(at(SAT, "23:00"))).isFalse();
        assertThat(hours.isOpenAt(at(SUN, "01:30"))).isFalse();
    }

    @Test
    @DisplayName("날짜로 읽을 수 없는 휴무일 값은 무시한다")
    void malformedClosedDatesAreIgnored() {
        OpeningHours hours = OpeningHours.of(
                List.of(new BusinessHour("mon", "09:00", "18:00")),
                List.of("9월 14일", ""));

        assertThat(hours.isOpenAt(at(MON, "12:00"))).isTrue();
    }
}
