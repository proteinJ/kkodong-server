package com.kkodong.server.domain.enrollment.dto;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.EnrollmentStatus;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRow;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class EnrollmentResponse {

    /**
     * 원생 목록 한 줄(PN-10).
     *
     * <p>이용권·출석 집계를 함께 담아 점주가 목록에서 바로 판단할 수 있게 한다 —
     * 특히 {@code paymentDue}가 켜진 원생이 재결제 안내 대상이다(FR-PN10-01의 "영업 리스트").
     */
    public record listItem(
            @Schema(description = "원생 ID") UUID id,
            @Schema(description = "반려견 ID. 견주 탈퇴 시 null") UUID dogId,
            @Schema(description = "강아지 이름") String dogName,
            @Schema(description = "견종") String dogBreed,
            @Schema(description = "보호자 이름") String ownerName,
            @Schema(description = "재원 상태") EnrollmentStatus status,
            @Schema(description = "등록일") LocalDate enrolledOn,

            @Schema(description = "활성 횟수권 잔여 합계. 기간권만 있으면 null") Integer remainingCount,
            @Schema(description = "가장 이른 만료일. 무기한만 있으면 null") LocalDate nearestExpiry,
            @Schema(description = "만료까지 남은 일수. 음수면 이미 지남") Long daysUntilExpiry,
            @Schema(description = "사용 가능한 이용권 보유 여부") Boolean hasActivePass,

            @Schema(description = """
                    재결제 안내 대상인지. 잔여 2회 미만 또는 만료 7일 이내면 true (FR-PN17-01).
                    ⚠️ 이용권이 아예 없는 원생도 true — 등원 처리 자체가 막히므로 가장 급하다""")
            Boolean paymentDue,

            @Schema(description = "마지막 등원일. 한 번도 안 왔으면 null") LocalDate lastAttendedOn,
            @Schema(description = "누적 등원 횟수") Long attendedCount
    ) {
        /** FR-PN17-01의 임계값. PN-17 자동 집계도 같은 기준을 쓴다. */
        private static final int LOW_COUNT_THRESHOLD = 2;
        private static final int EXPIRY_WARNING_DAYS = 7;

        public static listItem from(EnrollmentRow r, LocalDate today) {
            Long daysLeft = r.getNearestExpiry() == null ? null
                    : java.time.temporal.ChronoUnit.DAYS.between(today, r.getNearestExpiry());

            boolean due = !r.getHasActivePass()
                    || (r.getRemainingCount() != null && r.getRemainingCount() < LOW_COUNT_THRESHOLD)
                    || (daysLeft != null && daysLeft <= EXPIRY_WARNING_DAYS);

            return new listItem(
                    r.getId(), r.getDogId(), r.getDogName(), r.getDogBreed(), r.getOwnerName(),
                    EnrollmentStatus.valueOf(r.getStatus().toUpperCase()), r.getEnrolledOn(),
                    r.getRemainingCount(), r.getNearestExpiry(), daysLeft, r.getHasActivePass(),
                    due, r.getLastAttendedOn(), r.getAttendedCount());
        }

        /** 검색 대조용. 강아지 이름과 보호자 이름 어느 쪽으로도 찾을 수 있어야 한다. */
        public boolean matches(String keyword) {
            if (!StringUtils.hasText(keyword)) return true;
            String k = keyword.toLowerCase();
            return (dogName != null && dogName.toLowerCase().contains(k))
                    || (ownerName != null && ownerName.toLowerCase().contains(k));
        }
    }

    /**
     * 원생 상세(PN-11).
     *
     * <p>{@code FR-PN11-01} — 꼬동 프로필에서 전달된 반려견 정보를 그대로 보여준다.
     * 유치원마다 새로 받지 않아도 되는 것이 점주가 꼬동을 쓰는 이유다.
     */
    public record detailInfo(
            @Schema(description = "원생 ID") UUID id,
            @Schema(description = "재원 상태") EnrollmentStatus status,
            @Schema(description = "등록일") LocalDate enrolledOn,
            @Schema(description = "퇴원 시각") OffsetDateTime withdrawnAt,
            @Schema(description = "승인된 신청 ID. 제출값·동의 이력을 여기서 되짚는다") UUID applicationId,

            @Schema(description = "반려견 정보. 견주 탈퇴로 삭제됐으면 null이고 스냅샷만 남는다")
            dogProfile dog,
            @Schema(description = "탈퇴 후에도 남는 강아지 이름 스냅샷") String dogNameSnapshot,
            @Schema(description = "보호자 이름 스냅샷") String ownerNameSnapshot,
            @Schema(description = "보호자 유저 ID. 탈퇴 시 null") UUID ownerUserId,

            @Schema(description = "이용권 목록. 사용 가능한 것이 위로 온다") List<PassResponse.detailInfo> passes,
            @Schema(description = "최근 등원 이력") List<attendanceHistory> recentAttendances,

            @Schema(description = "⚠️ 특이사항 메모. 보호자에게 노출하지 않는다") String staffMemo
    ) {
        /** 꼬동 프로필에서 가져온 반려견 정보(FR-PN11-01). */
        public record dogProfile(
                @Schema(description = "이름") String name,
                @Schema(description = "견종") String breed,
                @Schema(description = "생년월일") LocalDate birthDate,
                @Schema(description = "성별") String gender,
                @Schema(description = "체급") String size,
                @Schema(description = "체중(kg)") BigDecimal weightKg,
                @Schema(description = "중성화 여부") Boolean neutered,
                @Schema(description = "성향 태그") List<String> personalityTraits,
                @Schema(description = "프로필 사진 URL") String profileImageUrl,
                @Schema(description = "동물등록번호") String animalRegistrationNumber
        ) {
            public static dogProfile from(Dog d) {
                return new dogProfile(
                        d.getName(), d.getBreed(), d.getBirthDate(),
                        d.getGender() == null ? null : d.getGender().name().toLowerCase(),
                        d.getSize() == null ? null : d.getSize().name().toLowerCase(),
                        d.getWeightKg(), d.getNeutered(), d.getPersonalityTraits(),
                        d.getProfileImageUrl(), d.getAnimalRegistrationNumber());
            }
        }

        /** 등원 이력 한 줄(PN-11). 어느 이용권이 쓰였는지까지 이어볼 수 있다. */
        public record attendanceHistory(
                @Schema(description = "등원 기록 ID") UUID id,
                @Schema(description = "날짜") LocalDate attendanceDate,
                @Schema(description = "상태") AttendanceStatus status,
                @Schema(description = "등원 시각") OffsetDateTime checkedInAt,
                @Schema(description = "하원 시각") OffsetDateTime checkedOutAt,
                @Schema(description = "차감된 이용권 ID") UUID passId,
                @Schema(description = "되돌린 시각. null이면 되돌린 적 없음") OffsetDateTime revertedAt
        ) {
            public static attendanceHistory from(Attendance a) {
                return new attendanceHistory(a.getId(), a.getAttendanceDate(), a.getStatus(),
                        a.getCheckedInAt(), a.getCheckedOutAt(), a.getPassId(), a.getRevertedAt());
            }
        }

        public static detailInfo of(Enrollment e, Dog dog,
                                    List<PassResponse.detailInfo> passes,
                                    List<attendanceHistory> attendances) {
            return new detailInfo(
                    e.getId(), e.getStatus(), e.getEnrolledOn(), e.getWithdrawnAt(), e.getApplicationId(),
                    dog == null ? null : dogProfile.from(dog),
                    e.getDogNameSnapshot(), e.getOwnerNameSnapshot(), e.getOwnerUserId(),
                    passes, attendances, e.getStaffMemo());
        }
    }
}
