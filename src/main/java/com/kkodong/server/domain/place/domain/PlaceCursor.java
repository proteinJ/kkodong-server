package com.kkodong.server.domain.place.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * 주변 매장 목록의 "여기까지 봤다" 위치 (MAP-01, API_SPEC 14.2).
 *
 * <p><b>offset 이 아니라 마지막 행의 (거리, id) 를 기억한다.</b> 다음 페이지를 부르는 사이에
 * 매장이 새로 뜨거나 내려가면 offset 은 한 칸씩 밀려 중복·누락이 생긴다. 정렬 키 자체를
 * 기억하면 "이 값 다음부터"라서 앞쪽 변화에 흔들리지 않는다.
 *
 * <p><b>거리를 {@link Double#toString(double)} 으로 적는 이유</b>: 가장 짧으면서 원래 값으로
 * 정확히 되돌아오는 표기다. 거리가 같은 매장끼리는 SQL 에서 {@code =} 비교가 필요한데,
 * 소수 자릿수를 잘라 적으면 그 비교가 깨져 동률 매장이 빠지거나 두 번 나온다.
 *
 * <p>앱에는 뜻 없는 문자열로만 준다. 앱이 내용을 해석하지 않아야 나중에 형식을 바꿀 수 있다.
 */
public record PlaceCursor(double distanceMeters, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** 거리 표기({@code 1.0E-7} 등)와 UUID 어디에도 나오지 않는 문자. */
    private static final char SEPARATOR = ':';

    public PlaceCursor {
        Objects.requireNonNull(id, "id");
    }

    /** URL 에 그대로 넣을 수 있는 토큰. */
    public String encode() {
        String raw = Double.toString(distanceMeters) + SEPARATOR + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws IllegalArgumentException 비었거나 형식이 틀린 토큰. 호출자가 400 으로 바꾼다
     */
    public static PlaceCursor decode(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("커서가 비어 있다");
        }
        try {
            String raw = new String(DECODER.decode(token.strip()), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                throw new IllegalArgumentException("구분자가 없다");
            }
            double distance = Double.parseDouble(raw.substring(0, separator));
            if (!Double.isFinite(distance) || distance < 0) {
                throw new IllegalArgumentException("거리 값이 올바르지 않다");
            }
            return new PlaceCursor(distance, UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException e) {
            // Base64·숫자·UUID 파싱 실패가 전부 IllegalArgumentException 계열이다.
            throw new IllegalArgumentException("잘못된 커서", e);
        }
    }
}
