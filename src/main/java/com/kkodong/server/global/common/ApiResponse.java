package com.kkodong.server.global.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final String code;

    private ApiResponse(boolean success, String message, T data, String code) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.code = code;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, "SUCCESS");
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, "SUCCESS");
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, message, null, code);
    }
}
