package com.kkodong.server.domain.assignment.service;

import com.kkodong.server.domain.assignment.domain.StaffAssignment;
import com.kkodong.server.domain.assignment.dto.AssignmentRequest;
import com.kkodong.server.domain.assignment.dto.AssignmentResponse;
import com.kkodong.server.domain.assignment.repository.StaffAssignmentRepository;
import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.domain.MerchantStaff;
import com.kkodong.server.domain.merchant.domain.StaffStatus;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 담당 배정(PN-13, FR-PN13-01).
 *
 * <p>배정 대상은 <b>그날 등원한 원생</b>이다. 결석·미등원 아이를 배정해 봐야 현장에 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private final StaffAssignmentRepository assignmentRepository;
    private final MerchantStaffRepository staffRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 배정 현황(PN-13). 선생님별 담당과 마릿수, 아직 담당이 없는 원생을 함께 준다.
     *
     * <p>담당이 적은 선생님부터 보여준다 — 이 화면의 목적이 "몰리지 않게 하는 것"이라,
     * 다음에 누구에게 맡길지가 목록 맨 위에 있어야 한다.
     */
    public AssignmentResponse.board getBoard(UUID merchantId, UUID userId, LocalDate date) {
        accessGuard.requireStaff(merchantId, userId);

        List<Attendance> attended = attendanceRepository
                .findAllByMerchantIdAndAttendanceDate(merchantId, date).stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ATTENDED)
                .toList();
        List<UUID> attendedEnrollments = attended.stream().map(Attendance::getEnrollmentId).toList();

        Map<UUID, StaffAssignment> byEnrollment = assignmentRepository
                .findAllByMerchantIdAndAssignedDate(merchantId, date).stream()
                .collect(Collectors.toMap(StaffAssignment::getEnrollmentId, Function.identity()));

        Map<UUID, String> dogNames = dogNames(attendedEnrollments);
        Map<UUID, MerchantStaff> activeStaff = staffRepository
                .findAllByMerchantIdAndStatus(merchantId, StaffStatus.ACTIVE).stream()
                .collect(Collectors.toMap(MerchantStaff::getId, Function.identity()));
        Map<UUID, User> users = userRepository
                .findAllById(activeStaff.values().stream().map(MerchantStaff::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // 배정된 강아지를 선생님별로 모은다. 등원하지 않은 원생의 배정은 화면에서 뺀다 —
        // 배정만 남고 실제로 안 온 경우(당일 결석)가 있다.
        Map<UUID, List<AssignmentResponse.board.assignedDog>> dogsByStaff = new HashMap<>();
        List<AssignmentResponse.board.assignedDog> unassigned = new ArrayList<>();

        for (UUID enrollmentId : attendedEnrollments) {
            var dog = new AssignmentResponse.board.assignedDog(enrollmentId, dogNames.get(enrollmentId));
            StaffAssignment assignment = byEnrollment.get(enrollmentId);
            if (assignment == null || !activeStaff.containsKey(assignment.getStaffId())) {
                // 담당 선생님이 퇴사했으면 미배정으로 되돌려 보여준다 — 아무도 안 맡은 상태다.
                unassigned.add(dog);
            } else {
                dogsByStaff.computeIfAbsent(assignment.getStaffId(), k -> new ArrayList<>()).add(dog);
            }
        }

        List<AssignmentResponse.board.staffLoad> loads = activeStaff.values().stream()
                .map(staff -> {
                    User user = users.get(staff.getUserId());
                    var dogs = dogsByStaff.getOrDefault(staff.getId(), List.of());
                    return new AssignmentResponse.board.staffLoad(
                            staff.getId(), staff.getUserId(),
                            user == null ? null : user.getDisplayName(),
                            staff.getRole(), dogs.size(), dogs);
                })
                .sorted(Comparator.comparingInt(AssignmentResponse.board.staffLoad::count))
                .toList();

        return new AssignmentResponse.board(date, attended.size(), loads, unassigned);
    }

    /**
     * 배정(PN-13). 여러 강아지를 한 선생님에게 맡긴다.
     *
     * <p>이미 다른 선생님에게 배정된 강아지는 <b>옮긴다</b>. 아침에 배정을 조정하는 것이
     * 정상 작업이고, "이미 배정됨"으로 막으면 먼저 해제하는 두 번의 조작이 필요해진다.
     * 옮길 때 행을 지웠다 만들지 않아 배정 시각이 보존된다.
     */
    @Transactional
    public AssignmentResponse.board assign(
            UUID merchantId, UUID userId, AssignmentRequest.assign request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        MerchantStaff staff = staffRepository.findById(request.staffId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        // ⚠️ 남의 매장 스태프에게 우리 원생을 맡길 수 없다.
        if (!staff.getMerchantId().equals(merchantId) || staff.getStatus() != StaffStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STAFF_NOT_FOUND);
        }

        for (UUID enrollmentId : validEnrollments(merchantId, request.enrollmentIds())) {
            assignmentRepository
                    .findByEnrollmentIdAndAssignedDate(enrollmentId, request.assignedDate())
                    .ifPresentOrElse(
                            existing -> existing.reassignTo(staff.getId()),
                            () -> assignmentRepository.save(StaffAssignment.builder()
                                    .merchantId(merchantId)
                                    .enrollmentId(enrollmentId)
                                    .staffId(staff.getId())
                                    .assignedDate(request.assignedDate())
                                    .build()));
        }
        return getBoard(merchantId, userId, request.assignedDate());
    }

    /** 배정 해제(PN-13). 해당 원생들이 미배정으로 돌아간다. */
    @Transactional
    public AssignmentResponse.board unassign(
            UUID merchantId, UUID userId, AssignmentRequest.unassign request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        List<StaffAssignment> targets = assignmentRepository
                .findAllByEnrollmentIdInAndAssignedDate(request.enrollmentIds(), request.assignedDate())
                .stream()
                .filter(a -> a.getMerchantId().equals(merchantId))
                .toList();
        assignmentRepository.deleteAll(targets);

        return getBoard(merchantId, userId, request.assignedDate());
    }

    /** 요청된 원생 중 이 매장 소속만 남긴다. */
    private List<UUID> validEnrollments(UUID merchantId, List<UUID> enrollmentIds) {
        return enrollmentRepository.findAllById(enrollmentIds).stream()
                .filter(e -> e.getMerchantId().equals(merchantId))
                .map(Enrollment::getId)
                .toList();
    }

    private Map<UUID, String> dogNames(List<UUID> enrollmentIds) {
        if (enrollmentIds.isEmpty()) return Map.of();
        return enrollmentRepository.findAllById(enrollmentIds).stream()
                .collect(Collectors.toMap(Enrollment::getId, Enrollment::getDogNameSnapshot));
    }
}
