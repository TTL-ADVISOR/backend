package com.ttladvisor.project.dto.response;

import com.ttladvisor.project.domain.entity.Project;
import java.time.Instant;

/** 1-1 생성 응답. */
public record ProjectResponse(Long id, String name, Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getCreatedAt());
    }
}
