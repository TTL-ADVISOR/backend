package com.ttladvisor.project.controller;

import com.ttladvisor.global.security.AuthUser;
import com.ttladvisor.project.domain.entity.Project;
import com.ttladvisor.project.dto.request.CreateProjectRequest;
import com.ttladvisor.project.dto.response.ProjectResponse;
import com.ttladvisor.project.dto.response.ProjectSummaryResponse;
import com.ttladvisor.project.service.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.create(user.id(), request.name());
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public List<ProjectSummaryResponse> list(@AuthenticationPrincipal AuthUser user) {
        return projectService.list(user.id()).stream().map(ProjectSummaryResponse::from).toList();
    }

    @GetMapping("/{projectId}")
    public ProjectSummaryResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable long projectId) {
        return ProjectSummaryResponse.from(projectService.get(user.id(), projectId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long projectId) {
        projectService.delete(user.id(), projectId);
        return ResponseEntity.noContent().build();
    }
}
