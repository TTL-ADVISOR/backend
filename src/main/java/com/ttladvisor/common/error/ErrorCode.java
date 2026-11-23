package com.ttladvisor.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. docs/API.md "에러 응답" 표와 1:1로 맞춘다.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드를 찾을 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석을 찾을 수 없습니다."),
    ENDPOINT_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "엔드포인트 분석 결과를 찾을 수 없습니다."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    UPLOAD_NOT_PARSED(HttpStatus.CONFLICT, "파싱이 끝난 업로드만 분석할 수 있습니다."),
    ANALYSIS_NOT_DONE(HttpStatus.CONFLICT, "완료된 분석만 조회할 수 있습니다."),
    RESOURCE_BUSY(HttpStatus.CONFLICT, "파싱 또는 분석이 진행 중입니다."),

    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일은 100MB를 넘을 수 없습니다."),
    UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    SERVER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "처리 중인 작업이 많습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
