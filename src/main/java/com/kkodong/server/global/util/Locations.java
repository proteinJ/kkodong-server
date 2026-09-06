package com.kkodong.server.global.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 위경도 값을 Point 객체로 변환 - 이때 위경도 순서 뒤집힘 [경도, 위도]:[lng, lat]
 */
public class Locations {
    // GemoetryFactory는 스레드 세이프 - 매번 만들 필요 없음
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public static Point of(double lat, double lng) {
        return FACTORY.createPoint(new Coordinate(lng, lat)); // x: lng(경도), y: lat(위도)
    }

    public static Double latOf(Point p) {
        return p.getY();
    }

    public static Double lngOf(Point p) {
        return p.getX();
    }
}
