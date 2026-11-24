package com.ttladvisor.analysis.domain.entity;

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
@Table(name = "ttl_sim_point")
public class TtlSimPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_result_id", nullable = false)
    private EndpointResult endpointResult;

    @Column(name = "ttl_sec", nullable = false)
    private int ttlSec;

    @Column(name = "hit_rate", nullable = false)
    private double hitRate;

    @Column(name = "stale_rate", nullable = false)
    private double staleRate;

    @Column(name = "saved_backend_ms", nullable = false)
    private long savedBackendMs;

    @Column(name = "peak_memory_bytes", nullable = false)
    private long peakMemoryBytes;

    protected TtlSimPoint() {
    }

    public TtlSimPoint(EndpointResult endpointResult, int ttlSec, double hitRate, double staleRate,
                       long savedBackendMs, long peakMemoryBytes) {
        this.endpointResult = endpointResult;
        this.ttlSec = ttlSec;
        this.hitRate = hitRate;
        this.staleRate = staleRate;
        this.savedBackendMs = savedBackendMs;
        this.peakMemoryBytes = peakMemoryBytes;
    }

    public Long getId() {
        return id;
    }

    public EndpointResult getEndpointResult() {
        return endpointResult;
    }

    public int getTtlSec() {
        return ttlSec;
    }

    public double getHitRate() {
        return hitRate;
    }

    public double getStaleRate() {
        return staleRate;
    }

    public long getSavedBackendMs() {
        return savedBackendMs;
    }

    public long getPeakMemoryBytes() {
        return peakMemoryBytes;
    }
}
