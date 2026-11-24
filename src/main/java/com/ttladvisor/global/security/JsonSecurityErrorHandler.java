package com.ttladvisor.global.security;

import com.ttladvisor.global.error.ErrorCode;
import com.ttladvisor.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 필터에서 나는 401·403도 다른 API 에러와 같은 {@code {code, message, details}} 형식으로 응답한다.
 * 필터는 컨트롤러보다 앞에서 돌기 때문에 GlobalExceptionHandler가 잡지 못한다.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 로그인하지 않은 요청. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    /** CSRF 토큰 누락·불일치, 그 밖의 권한 부족. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        write(response, e instanceof CsrfException ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
    }
}
