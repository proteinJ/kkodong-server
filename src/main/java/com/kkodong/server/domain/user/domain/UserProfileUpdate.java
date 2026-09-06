package com.kkodong.server.domain.user.domain;

import org.locationtech.jts.geom.Point;

import java.util.List;

public record UserProfileUpdate(
        String displayName,
        String profileImageUrl,
        Point homeLocation,
        List<String> walkTimeSlots
) {
}
