package com.ttladvisor.analysis.domain.entity;

import com.ttladvisor.analysis.domain.enums.AnalysisStatus;
import com.ttladvisor.upload.domain.entity.LogUpload;
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

@Entity
@Table(name = "analysis")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private LogUpload upload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    /** 실제 적용된 분석 파라미터 JSON 원문. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    private String params;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Analysis() {
    }

    public Analysis(LogUpload upload, String params, Instant createdAt) {
        this.upload = upload;
        this.status = AnalysisStatus.PENDING;
        this.params = params;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LogUpload getUpload() {
        return upload;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getParams() {
        return params;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
