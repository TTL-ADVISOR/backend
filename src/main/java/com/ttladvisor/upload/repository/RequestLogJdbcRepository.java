package com.ttladvisor.upload.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * request_log는 대량 테이블이라 JPA 대신 JdbcTemplate으로 다룬다.
 */
@Repository
public class RequestLogJdbcRepository {

    private final JdbcTemplate jdbc;

    public RequestLogJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 저장할 요청 로그 한 줄. 키는 {@code RequestKeys}로 만든다. */
    public record Row(Instant ts, String method, String resourceKey, String cacheKey, int statusCode,
                      Integer latencyMs, Integer respSize, String respHash) {
    }

    /** 한 번의 배치 insert로 저장한다 (rewriteBatchedStatements로 다중 VALUES 한 문장이 된다). */
    public void insertBatch(long uploadId, List<Row> rows) {
        jdbc.batchUpdate("""
                INSERT INTO request_log
                    (upload_id, ts, method, resource_key, cache_key, status_code, latency_ms, resp_size, resp_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, uploadId);
            ps.setObject(2, row.ts());
            ps.setString(3, row.method());
            ps.setString(4, row.resourceKey());
            ps.setString(5, row.cacheKey());
            ps.setInt(6, row.statusCode());
            ps.setObject(7, row.latencyMs());
            ps.setObject(8, row.respSize());
            ps.setString(9, row.respHash());
        });
    }

    public long countByUploadId(long uploadId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM request_log WHERE upload_id = ?", Long.class, uploadId);
        return count == null ? 0 : count;
    }

    /**
     * 업로드의 요청 로그를 batchSize 단위로 나눠 지운다.
     * 트랜잭션 밖에서 호출하면 배치마다 커밋되어, 수백만 행을 한 트랜잭션에서 지울 때의
     * 긴 락과 거대한 undo 로그를 피한다.
     *
     * @return 삭제한 전체 행 수
     */
    public long deleteByUploadId(long uploadId, int batchSize) {
        long total = 0;
        int deleted;
        do {
            deleted = jdbc.update("DELETE FROM request_log WHERE upload_id = ? LIMIT ?", uploadId, batchSize);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
