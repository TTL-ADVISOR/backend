package com.ttladvisor.upload.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.domain.enums.UploadStatus;
import java.time.Instant;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 2-3 업로드 상태. 목록(2-2)은 같은 형식에서 errorSamples를 뺀다 ({@code errorSamples == null}이면 필드 생략).
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record UploadResponse(Long id, Long projectId, String filename, UploadStatus status, int processedLines,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Integer totalLines, int errorLines,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Instant rangeStart,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Instant rangeEnd,
                             List<ErrorSample> errorSamples,
                             @JsonInclude(JsonInclude.Include.ALWAYS) String failureReason,
                             Instant createdAt) {

    private static final TypeReference<List<ErrorSample>> SAMPLES = new TypeReference<>() {
    };

    public record ErrorSample(int line, String reason) {
    }

    public static UploadResponse detail(LogUpload upload, ObjectMapper mapper) {
        List<ErrorSample> samples = upload.getErrorSamples() == null
                ? List.of()
                : mapper.readValue(upload.getErrorSamples(), SAMPLES);
        return of(upload, samples);
    }

    public static UploadResponse summary(LogUpload upload) {
        return of(upload, null);
    }

    private static UploadResponse of(LogUpload u, List<ErrorSample> samples) {
        return new UploadResponse(u.getId(), u.getProject().getId(), u.getFilename(), u.getStatus(),
                u.getProcessedLines(), u.getTotalLines(), u.getErrorLines(), u.getRangeStart(), u.getRangeEnd(),
                samples, u.getFailureReason(), u.getCreatedAt());
    }
}
