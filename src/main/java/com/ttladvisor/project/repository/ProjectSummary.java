package com.ttladvisor.project.repository;

import java.time.Instant;

public record ProjectSummary(Long id, String name, Instant createdAt, long uploadCount, Long latestAnalysisId) {
}
