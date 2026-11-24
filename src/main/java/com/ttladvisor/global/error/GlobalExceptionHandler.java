package com.ttladvisor.global.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 {@link ErrorResponse} 형식으로 바꾼다.
 * Spring MVC 표준 예외(400/404/405/413/415 등)는 {@link ResponseEntityExceptionHandler}가 상태 코드를 정하고,
 * 여기서 본문만 우리 형식으로 교체한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, e.getMessage(), e.getDetails()));
    }

    /** 비동기 작업 큐가 가득 찬 경우. 요청 스레드에서 대신 실행(CallerRuns)하지 않고 바로 거절한다. */
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ErrorResponse> handleRejected(TaskRejectedException e) {
        log.warn("비동기 작업 거절: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.SERVER_BUSY.status()).body(ErrorResponse.of(ErrorCode.SERVER_BUSY));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        // 내부 메시지는 응답에 노출하지 않는다
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(ge -> details.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return ResponseEntity.status(status).headers(headers)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, null, details));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(r -> {
            String name = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().forEach(err -> details.putIfAbsent(name, err.getDefaultMessage()));
        });
        return ResponseEntity.status(status).headers(headers)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, null, details));
    }

    /** 나머지 표준 예외: 상태 코드는 Spring이 정한 값을 쓰고, 본문만 교체한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("서버 오류", e);
        }
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(codeFor(status)));
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_FORMAT;
            case 503 -> ErrorCode.SERVER_BUSY;
            default -> status.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }
}
