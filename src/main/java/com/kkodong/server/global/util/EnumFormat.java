package com.kkodong.server.global.util;

import java.util.Locale;

public class EnumFormat {

    public static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
