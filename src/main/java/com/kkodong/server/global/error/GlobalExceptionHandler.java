package com.kkodong.server.global.error;

import com.kkodong.server.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        setErrorLoggingAttributes(request, errorCode);
        log.warn("Business exception: code={}, message={}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        setErrorLoggingAttributes(request, ErrorCode.INVALID_INPUT_VALUE.getCode(), message);
        log.warn("Validation exception: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        setErrorLoggingAttributes(request, ErrorCode.INVALID_INPUT_VALUE);
        log.warn("HttpMessageNotReadable: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        setErrorLoggingAttributes(request, ErrorCode.INTERNAL_SERVER_ERROR);
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다."));
    }

    private void setErrorLoggingAttributes(HttpServletRequest request, ErrorCode errorCode) {
        setErrorLoggingAttributes(request, errorCode.getCode(), errorCode.getMessage());
    }

    private void setErrorLoggingAttributes(HttpServletRequest request, String code, String message) {
        request.setAttribute(ErrorLoggingAttribute.ERROR_CODE, code);
        request.setAttribute(ErrorLoggingAttribute.ERROR_MESSAGE, message);
    }
}
