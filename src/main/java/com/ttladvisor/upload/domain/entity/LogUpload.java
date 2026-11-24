package com.ttladvisor.upload.domain.entity;

import com.ttladvisor.project.domain.entity.Project;
import com.ttladvisor.upload.domain.enums.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로그 업로드. 개별 요청 로그(request_log)는 대량이라 엔티티 없이 JdbcTemplate으로 다룬다.
 */
@Entity
@Table(name = "log_upload")
public class LogUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadStatus status;

    @Column(name = "processed_lines", nullable = false)
    private int processedLines;

    @Column(name = "total_lines")
    private Integer totalLines;

    @Column(name = "error_lines", nullable = false)
    private int errorLines;

    /** 실패 줄 샘플 JSON 배열 원문. 직렬화는 서비스 계층에서 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_samples")
    private String errorSamples;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "range_start")
    private Instant rangeStart;

    @Column(name = "range_end")
    private Instant rangeEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LogUpload() {
    }

    public LogUpload(Project project, String filename, Instant createdAt) {
        this.project = project;
        this.filename = filename;
        this.status = UploadStatus.UPLOADED;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getFilename() {
        return filename;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public int getProcessedLines() {
        return processedLines;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public int getErrorLines() {
        return errorLines;
    }

    public String getErrorSamples() {
        return errorSamples;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRangeStart() {
        return rangeStart;
    }

    public Instant getRangeEnd() {
        return rangeEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
