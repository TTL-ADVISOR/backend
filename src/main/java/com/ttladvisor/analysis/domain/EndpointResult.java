package com.ttladvisor.analysis.domain;

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

@Entity
@Table(name = "endpoint_result")
public class EndpointResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "unique_keys", nullable = false)
    private int uniqueKeys;

    @Column(name = "write_ratio", nullable = false)
    private double writeRatio;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @Column(name = "median_change_interval_sec")
    private Integer medianChangeIntervalSec;

    @Column(name = "max_staleness_sec")
    private Integer maxStalenessSec;

    @Column(name = "recommended_ttl_sec")
    private Integer recommendedTtlSec;

    @Column(name = "expected_hit_rate")
    private Double expectedHitRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    @Column(length = 500)
    private String reason;

    protected EndpointResult() {
    }

    public EndpointResult(Analysis analysis, String method, String pattern, int requestCount, int uniqueKeys,
                          double writeRatio, Double avgLatencyMs, Integer medianChangeIntervalSec,
                          Integer maxStalenessSec, Integer recommendedTtlSec, Double expectedHitRate,
                          Verdict verdict, String reason) {
        this.analysis = analysis;
        this.method = method;
        this.pattern = pattern;
        this.requestCount = requestCount;
        this.uniqueKeys = uniqueKeys;
        this.writeRatio = writeRatio;
        this.avgLatencyMs = avgLatencyMs;
        this.medianChangeIntervalSec = medianChangeIntervalSec;
        this.maxStalenessSec = maxStalenessSec;
        this.recommendedTtlSec = recommendedTtlSec;
        this.expectedHitRate = expectedHitRate;
        this.verdict = verdict;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public String getMethod() {
        return method;
    }

    public String getPattern() {
        return pattern;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getUniqueKeys() {
        return uniqueKeys;
    }

    public double getWriteRatio() {
        return writeRatio;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public Integer getMedianChangeIntervalSec() {
        return medianChangeIntervalSec;
    }

    public Integer getMaxStalenessSec() {
        return maxStalenessSec;
    }

    public Integer getRecommendedTtlSec() {
        return recommendedTtlSec;
    }

    public Double getExpectedHitRate() {
        return expectedHitRate;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getReason() {
        return reason;
    }
}
