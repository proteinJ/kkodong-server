package com.kkodong.server.domain.user.domain;

import org.locationtech.jts.geom.Point;

public record UserProfileUpdate(
        String displayName,
        String profileImageUrl,
        Point homeLocation
) {
}
