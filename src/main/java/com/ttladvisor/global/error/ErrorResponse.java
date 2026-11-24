package com.ttladvisor.global.error;

import java.util.Map;

/**
 * 모든 에러 응답의 공통 형식: {@code {code, message, details}}.
 * details는 추가 정보가 없으면 빈 객체다.
 */
public record ErrorResponse(String code, String message, Map<String, Object> details) {

    public ErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.name(), code.defaultMessage(), Map.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorResponse(code.name(), message != null ? message : code.defaultMessage(), details);
    }
}
