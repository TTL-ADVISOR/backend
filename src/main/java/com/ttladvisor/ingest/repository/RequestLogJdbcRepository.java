package com.ttladvisor.ingest.repository;

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
