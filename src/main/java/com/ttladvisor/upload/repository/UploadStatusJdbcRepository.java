package com.ttladvisor.upload.repository;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 파싱 작업이 업로드 상태를 바꾸는 쿼리. 백그라운드 스레드에서 짧게 자주 쓰므로
 * 엔티티를 읽어 고치는 대신 조건부 UPDATE 한 문장으로 처리한다.
 */
@Repository
public class UploadStatusJdbcRepository {

    private final JdbcTemplate jdbc;

    public UploadStatusJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** UPLOADED → PARSING. 이미 다른 상태(삭제 포함)면 false. */
    public boolean startParsing(long uploadId) {
        return jdbc.update("UPDATE log_upload SET status = 'PARSING' WHERE id = ? AND status = 'UPLOADED'",
                uploadId) == 1;
    }

    public void updateProgress(long uploadId, int processedLines) {
        jdbc.update("UPDATE log_upload SET processed_lines = ? WHERE id = ? AND status = 'PARSING'",
                processedLines, uploadId);
    }

    public void markParsed(long uploadId, int totalLines, int errorLines, String errorSamplesJson,
                           Instant rangeStart, Instant rangeEnd) {
        jdbc.update("""
                UPDATE log_upload
                SET status = 'PARSED', processed_lines = ?, total_lines = ?, error_lines = ?,
                    error_samples = CAST(? AS JSON), range_start = ?, range_end = ?, failure_reason = NULL
                WHERE id = ?
                """, totalLines, totalLines, errorLines, errorSamplesJson, rangeStart, rangeEnd, uploadId);
    }

    /** 실패 처리. 진행 중이던 집계(처리 줄 수·실패 샘플)는 사용자가 원인을 볼 수 있게 남긴다. */
    public void markFailed(long uploadId, String reason, int processedLines, int errorLines, String errorSamplesJson) {
        jdbc.update("""
                UPDATE log_upload
                SET status = 'FAILED', failure_reason = ?, processed_lines = ?, error_lines = ?,
                    error_samples = CAST(? AS JSON), total_lines = NULL, range_start = NULL, range_end = NULL
                WHERE id = ?
                """, reason, processedLines, errorLines, errorSamplesJson, uploadId);
    }
}
