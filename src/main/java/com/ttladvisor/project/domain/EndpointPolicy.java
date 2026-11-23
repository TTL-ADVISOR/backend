package com.ttladvisor.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "endpoint_policy")
public class EndpointPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Column(name = "max_staleness_sec")
    private Integer maxStalenessSec;

    @Column(name = "is_custom_pattern", nullable = false)
    private boolean customPattern;

    protected EndpointPolicy() {
    }

    public EndpointPolicy(Project project, String method, String pattern, Integer maxStalenessSec, boolean customPattern) {
        this.project = project;
        this.method = method;
        this.pattern = pattern;
        this.maxStalenessSec = maxStalenessSec;
        this.customPattern = customPattern;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getMethod() {
        return method;
    }

    public String getPattern() {
        return pattern;
    }

    public Integer getMaxStalenessSec() {
        return maxStalenessSec;
    }

    public boolean isCustomPattern() {
        return customPattern;
    }
}
