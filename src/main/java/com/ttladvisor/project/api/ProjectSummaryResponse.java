package com.ttladvisor.project.api;

import com.ttladvisor.project.repository.ProjectSummary;
import java.time.Instant;

/** 1-2 목록, 1-3 단건 조회 응답. */
public record ProjectSummaryResponse(Long id, String name, Instant createdAt, long uploadCount, Long latestAnalysisId) {

    static ProjectSummaryResponse from(ProjectSummary s) {
        return new ProjectSummaryResponse(s.id(), s.name(), s.createdAt(), s.uploadCount(), s.latestAnalysisId());
    }
}
