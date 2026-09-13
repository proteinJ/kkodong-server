package com.kkodong.server.domain.place.domain;

import com.kkodong.server.domain.merchant.domain.BusinessHour;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 매장 영업시간표와 임시 휴무일로 "지금 영업 중인가"를 판정한다 (MAP-01, API_SPEC 14.2).
 *
 * <p><b>왜 서버가 판정하나</b>: 클라이언트가 영업시간표만 보고 다시 계산하면 임시 휴무와
 * 자정을 넘기는 영업을 놓쳐 "영업 중이라더니 닫혀 있는" 헛걸음이 난다.
 *
 * <p><b>형식이 잘못된 줄은 예외 대신 건너뛴다.</b> 점주가 영업시간을 저장할 때 서버 검증이
 * 없어서(PN-04) 요일 오타나 {@code 9:00} 같은 값이 그대로 들어올 수 있다. 한 매장의 한 줄
 * 때문에 지도 목록 전체가 500 이 되면 안 된다.
 *
 * <p>시간대는 여기서 다루지 않는다. 호출자가 매장 시간대로 바꾼 {@link LocalDateTime} 을 넘긴다.
 */
public final class OpeningHours {

    private static final DateTimeFormatter HH_MM =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    /** 점주가 흔히 쓰는 "24:00" 을 자정으로 읽는다. {@link LocalTime} 은 24시를 표현하지 못한다. */
    private static final String END_OF_DAY = "24:00";

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "mon", DayOfWeek.MONDAY,
            "tue", DayOfWeek.TUESDAY,
            "wed", DayOfWeek.WEDNESDAY,
            "thu", DayOfWeek.THURSDAY,
            "fri", DayOfWeek.FRIDAY,
            "sat", DayOfWeek.SATURDAY,
            "sun", DayOfWeek.SUNDAY);

    private final Map<DayOfWeek, List<Slot>> slotsByDay;
    private final Set<LocalDate> closedDates;

    private OpeningHours(Map<DayOfWeek, List<Slot>> slotsByDay, Set<LocalDate> closedDates) {
        this.slotsByDay = slotsByDay;
        this.closedDates = closedDates;
    }

    /**
     * @param hours       {@code merchants.business_hours} 원소들. null 이어도 된다
     * @param closedDates {@code merchants.closed_dates} 원소들(ISO 날짜). null 이어도 된다
     */
    public static OpeningHours of(List<BusinessHour> hours, List<String> closedDates) {
        Map<DayOfWeek, List<Slot>> slots = new EnumMap<>(DayOfWeek.class);
        if (hours != null) {
            for (BusinessHour hour : hours) {
                Slot slot = parseSlot(hour);
                if (slot != null) {
                    slots.computeIfAbsent(slot.day(), d -> new ArrayList<>()).add(slot);
                }
            }
        }

        Set<LocalDate> closed = new HashSet<>();
        if (closedDates != null) {
            for (String text : closedDates) {
                LocalDate date = parseDate(text);
                if (date != null) {
                    closed.add(date);
                }
            }
        }
        return new OpeningHours(slots, closed);
    }

    /**
     * @return 영업 중이면 true, 아니면 false. <b>읽을 수 있는 영업시간 줄이 하나도 없으면 null</b> —
     *         모르는 것을 "닫힘"으로 보이지 않는다
     */
    public Boolean isOpenAt(LocalDateTime now) {
        if (slotsByDay.isEmpty()) {
            return null;
        }
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        // 오늘 시작하는 영업. 오늘이 휴무일이면 없는 것으로 본다.
        if (!closedDates.contains(today)) {
            for (Slot slot : slotsOn(today)) {
                if (slot.coversSameDay(time)) {
                    return true;
                }
            }
        }

        // 어제 밤에 시작해 오늘 새벽까지 이어지는 영업. 판단 기준은 어제(어제 줄·어제 휴무)다.
        LocalDate yesterday = today.minusDays(1);
        if (!closedDates.contains(yesterday)) {
            for (Slot slot : slotsOn(yesterday)) {
                if (slot.coversNextDay(time)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Slot> slotsOn(LocalDate date) {
        return slotsByDay.getOrDefault(date.getDayOfWeek(), List.of());
    }

    private static Slot parseSlot(BusinessHour hour) {
        if (hour == null || hour.day() == null) {
            return null;
        }
        DayOfWeek day = DAYS.get(hour.day().strip().toLowerCase(Locale.ROOT));
        LocalTime open = parseTime(hour.open());
        LocalTime close = parseTime(hour.close());
        if (day == null || open == null || close == null) {
            return null;
        }
        return new Slot(day, open, close);
    }

    private static LocalTime parseTime(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (END_OF_DAY.equals(trimmed)) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(trimmed, HH_MM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 요일 한 줄. {@code close} 가 {@code open} 보다 이르거나 같으면 자정을 넘기는 영업이다 —
     * {@code 22:00~02:00} 은 밤샘, {@code 00:00~00:00} 은 24시간, {@code 09:00~24:00} 은 자정까지.
     */
    private record Slot(DayOfWeek day, LocalTime open, LocalTime close) {

        private boolean crossesMidnight() {
            return !close.isAfter(open);
        }

        /** 이 줄의 요일 당일 {@code time} 에 영업 중인가. 폐점 시각은 포함하지 않는다. */
        private boolean coversSameDay(LocalTime time) {
            if (time.isBefore(open)) {
                return false;
            }
            return crossesMidnight() || time.isBefore(close);
        }

        /** 이 줄의 요일 다음 날 새벽 {@code time} 에 아직 영업 중인가. */
        private boolean coversNextDay(LocalTime time) {
            return crossesMidnight() && time.isBefore(close);
        }
    }
}
