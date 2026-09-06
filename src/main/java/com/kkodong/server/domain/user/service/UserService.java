package com.kkodong.server.domain.user.service;

import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.domain.UserProfileUpdate;
import com.kkodong.server.domain.user.dto.UserResponse;
import com.kkodong.server.domain.user.dto.PasswordChangeRequest;
import com.kkodong.server.domain.user.dto.UpdateRequest;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.config.UserProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;

import java.util.UUID;

import com.kkodong.server.global.util.Locations;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DogRepository dogRepository;
    private final UserProperties userProperties;

    public UserResponse getMe(UUID userId) {
        User user = findUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = findUser(userId);
        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void deleteMe(UUID userId) {
        User user = findUser(userId);
        userRepository.delete(user);
        // 주의: 남아있는 Refresh Token은 Redis에 key=토큰문자열로 저장되어 있어 userId로
        // 일괄 삭제가 안 됨 — 자연 TTL(7일)로 만료됨. 탈퇴 즉시 완전 무효화가 필요하면
        // RefreshToken에 userId 보조 인덱스를 추가하는 걸 고려할 것.
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public UserResponse updateMyInfo(UUID userId, UpdateRequest request) {
        User user = findUser(userId);

        Point point = request.homeLocation() == null ? null : Locations.of(request.homeLocation().lat(), request.homeLocation().lng());

        if (!userProperties.walkTimeSlot().areValidKeys(request.walkTimeSlots())) {
            throw new BusinessException(ErrorCode.INVALID_WALK_TIME_SLOT);
        }

        user.updateProfile(new UserProfileUpdate(request.displayName(), request.profileImageUrl(), point, request.walkTimeSlots()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse onboarding(UUID userId) {
        User user = findUser(userId);

        boolean hasDog = dogRepository.existsByOwnerId(userId);

        // 온보딩 과정에서 필수 정보가 입력이 되었는가?
        if (user.getDisplayName() == null || user.getHomeLocation() == null || !hasDog) {
            throw new BusinessException(ErrorCode.ONBOARDING_REQUIREMENTS_NOT_MET);
        }

        user.completeOnboarding();

        return UserResponse.from(user);
    }
}
