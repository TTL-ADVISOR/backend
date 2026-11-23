package com.ttladvisor.project.api;

import com.ttladvisor.project.domain.Project;
import java.time.Instant;

/** 1-1 생성 응답. */
public record ProjectResponse(Long id, String name, Instant createdAt) {

    static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getCreatedAt());
    }
}
