package com.ttladvisor;

import com.ttladvisor.analysis.domain.entity.Analysis;
import com.ttladvisor.analysis.domain.entity.EndpointResult;
import com.ttladvisor.analysis.domain.entity.TtlSimPoint;
import com.ttladvisor.analysis.domain.enums.Verdict;
import com.ttladvisor.project.domain.entity.EndpointPolicy;
import com.ttladvisor.project.domain.entity.Project;
import com.ttladvisor.upload.domain.entity.LogUpload;
import com.ttladvisor.upload.domain.enums.UploadStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V1 스키마 검증. 컨텍스트가 뜬다는 것 자체가 ddl-auto=validate 통과를 의미하고,
 * 여기서는 엔티티 왕복과 DB 제약(유니크, FK 연쇄 규칙)을 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00.123Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Test
    void v1_마이그레이션이_적용되고_7개_테이블이_생성된다() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versions).contains("1");

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).contains("project", "endpoint_policy", "log_upload", "request_log",
                "analysis", "endpoint_result", "ttl_sim_point");
    }

    @Test
    void 엔티티를_저장하고_다시_읽을_수_있다() {
        Project project = persist(new Project("demo", NOW));
        persist(new EndpointPolicy(project, "GET", "/users/:id", 60, false));
        LogUpload upload = persist(new LogUpload(project, "access.jsonl", NOW));
        Analysis analysis = persist(new Analysis(upload, "{\"kneeRatio\":0.9}", NOW));
        EndpointResult result = persist(new EndpointResult(analysis, "GET", "/users/:id", 1200, 300, 0.02,
                41.5, 600, 60, 30, 0.71, Verdict.CACHE, "히트율 71%"));
        persist(new TtlSimPoint(result, 30, 0.71, 0.004, 35_000L, 2_048_000L));
        em.flush();
        em.clear();

        LogUpload foundUpload = em.find(LogUpload.class, upload.getId());
        assertThat(foundUpload.getStatus()).isEqualTo(UploadStatus.UPLOADED);
        assertThat(foundUpload.getCreatedAt()).isEqualTo(NOW); // 밀리초 보존

        Analysis foundAnalysis = em.find(Analysis.class, analysis.getId());
        assertThat(foundAnalysis.getParams()).contains("kneeRatio");

        EndpointResult foundResult = em.find(EndpointResult.class, result.getId());
        assertThat(foundResult.getVerdict()).isEqualTo(Verdict.CACHE);
        assertThat(foundResult.getRecommendedTtlSec()).isEqualTo(30);
    }

    @Test
    void 같은_프로젝트에_같은_메서드_패턴_정책은_중복_저장할_수_없다() {
        long projectId = insertProject();
        String sql = "INSERT INTO endpoint_policy (project_id, method, pattern, is_custom_pattern) VALUES (?, 'GET', '/users/:id', false)";
        jdbc.update(sql, projectId);

        assertThatThrownBy(() -> jdbc.update(sql, projectId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 요청_로그가_남아있으면_프로젝트를_삭제할_수_없고_지우면_하위_데이터가_연쇄_삭제된다() {
        long projectId = insertProject();
        jdbc.update("INSERT INTO log_upload (project_id, filename, status, created_at) VALUES (?, 'a.jsonl', 'PARSED', ?)",
                projectId, NOW);
        long uploadId = lastId();
        jdbc.update("""
                INSERT INTO request_log (upload_id, ts, method, resource_key, cache_key, status_code)
                VALUES (?, ?, 'GET', '/users/1', 'GET /users/1', 200)""", uploadId, NOW);
        jdbc.update("INSERT INTO analysis (upload_id, status, created_at) VALUES (?, 'DONE', ?)", uploadId, NOW);

        // request_log FK에는 CASCADE가 없어 대량 연쇄 삭제를 막는다
        assertThatThrownBy(() -> jdbc.update("DELETE FROM project WHERE id = ?", projectId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM request_log WHERE upload_id = ?", uploadId);
        jdbc.update("DELETE FROM project WHERE id = ?", projectId);

        assertThat(count("log_upload", "project_id", projectId)).isZero();
        assertThat(count("analysis", "upload_id", uploadId)).isZero();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private long insertProject() {
        jdbc.update("INSERT INTO project (name, created_at) VALUES ('p', ?)", NOW);
        return lastId();
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }
}
