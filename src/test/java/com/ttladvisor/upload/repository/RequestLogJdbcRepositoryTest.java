package com.ttladvisor.upload.repository;

import com.ttladvisor.TestData;
import com.ttladvisor.TestcontainersConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RequestLogJdbcRepositoryTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RequestLogJdbcRepository repository;

    @Test
    void 배치_크기로_나눠_지우고_다른_업로드의_로그는_남긴다() {
        jdbc.update("INSERT INTO project (owner_id, name, created_at) VALUES (?, 'p', NOW(3))", owner());
        long projectId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        long target = insertUpload(projectId);
        long other = insertUpload(projectId);
        insertLogs(target, 25);
        insertLogs(other, 3);

        long deleted = repository.deleteByUploadId(target, 10); // 10 + 10 + 5

        assertThat(deleted).isEqualTo(25);
        assertThat(countLogs(target)).isZero();
        assertThat(countLogs(other)).isEqualTo(3);

        jdbc.update("DELETE FROM request_log WHERE upload_id = ?", other);
        jdbc.update("DELETE FROM project WHERE id = ?", projectId);
    }

    @Test
    void 배치_크기의_배수만큼_있어도_모두_지운다() {
        jdbc.update("INSERT INTO project (owner_id, name, created_at) VALUES (?, 'p', NOW(3))", owner());
        long projectId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        long upload = insertUpload(projectId);
        insertLogs(upload, 20);

        assertThat(repository.deleteByUploadId(upload, 10)).isEqualTo(20);
        assertThat(countLogs(upload)).isZero();

        jdbc.update("DELETE FROM project WHERE id = ?", projectId);
    }

    private long owner() {
        Long id = jdbc.query("SELECT id FROM users WHERE email = 'repo-test@example.com'",
                rs -> rs.next() ? rs.getLong(1) : null);
        return id != null ? id : TestData.insertUser(jdbc, "repo-test@example.com");
    }

    private long insertUpload(long projectId) {
        jdbc.update("INSERT INTO log_upload (project_id, filename, status, created_at) VALUES (?, 'a', 'PARSED', NOW(3))", projectId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertLogs(long uploadId, int n) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new Object[]{uploadId, Timestamp.from(Instant.EPOCH.plusSeconds(i))});
        }
        jdbc.batchUpdate("INSERT INTO request_log (upload_id, ts, method, resource_key, cache_key, status_code) VALUES (?, ?, 'GET', '/a', 'GET /a', 200)", rows);
    }

    private int countLogs(long uploadId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM request_log WHERE upload_id = ?", Integer.class, uploadId);
    }
}
