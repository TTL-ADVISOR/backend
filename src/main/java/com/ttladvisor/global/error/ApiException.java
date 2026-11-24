package com.ttladvisor.global.error;

import java.util.Map;

/**
 * 서비스 계층에서 던지는 비즈니스 예외. {@link GlobalExceptionHandler}가 에러 응답으로 바꾼다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, Map.of());
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, null, details);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message != null ? message : errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
