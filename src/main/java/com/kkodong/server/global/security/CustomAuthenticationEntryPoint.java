package com.kkodong.server.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String jwtErrorCode = (String) request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_CODE_ATTR);
        String code = (jwtErrorCode != null) ? jwtErrorCode : ErrorCode.AUTHENTICATION_FAILED.getCode();

        ErrorCode errorCode = switch (code) {
            case "A002" -> ErrorCode.TOKEN_EXPIRED;
            case "A003" -> ErrorCode.INVALID_TOKEN;
            default -> ErrorCode.AUTHENTICATION_FAILED;
        };

        ApiResponse<Void> errorResponse = ApiResponse.error(errorCode.getMessage(), errorCode.getCode());
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
