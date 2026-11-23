package com.ttladvisor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttladvisor.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectApiTest {

    private static final String ISO_MILLIS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM request_log");
        jdbc.update("DELETE FROM project"); // 나머지는 CASCADE
    }

    // ---- 1-1 생성 ----

    @Test
    void 생성하면_201과_Location_밀리초_포함_시각을_준다() throws Exception {
        String location = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  상품 API  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/projects/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("상품 API"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(ISO_MILLIS)))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("상품 API"));
    }

    @Test
    void 이름이_비었거나_100자를_넘으면_400이다() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.name").exists());

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("이름은 100자 이하여야 합니다."));

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이름이_정확히_100자면_생성된다() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(100) + "\"}"))
                .andExpect(status().isCreated());
    }

    // ---- 1-2, 1-3 조회 ----

    @Test
    void 목록은_최근_생성_순이고_업로드_수와_최신_분석_ID를_포함한다() throws Exception {
        long older = insertProject("old", "2026-11-01T00:00:00Z");
        long newer = insertProject("new", "2026-11-02T00:00:00Z");
        long upload1 = insertUpload(older, "PARSED");
        insertUpload(older, "FAILED");
        insertAnalysis(upload1, "DONE");
        long latest = insertAnalysis(upload1, "DONE");

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[0].uploadCount").value(0))
                .andExpect(jsonPath("$[0].latestAnalysisId").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(older))
                .andExpect(jsonPath("$[1].uploadCount").value(2))
                .andExpect(jsonPath("$[1].latestAnalysisId").value(latest))
                .andExpect(jsonPath("$[1].createdAt").value("2026-11-01T00:00:00.000Z"));
    }

    @Test
    void 없는_프로젝트_조회는_404다() throws Exception {
        mvc.perform(get("/api/projects/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.projectId").value(999999));
    }

    // ---- 1-4 삭제 ----

    @Test
    void 삭제하면_요청_로그를_포함한_하위_데이터가_모두_지워진다() throws Exception {
        long projectId = insertProject("p", "2026-11-01T00:00:00Z");
        jdbc.update("INSERT INTO endpoint_policy (project_id, method, pattern, is_custom_pattern) VALUES (?, 'GET', '/a', false)", projectId);
        long uploadId = insertUpload(projectId, "PARSED");
        for (int i = 0; i < 30; i++) {
            jdbc.update("INSERT INTO request_log (upload_id, ts, method, resource_key, cache_key, status_code) VALUES (?, ?, 'GET', '/a', 'GET /a', 200)",
                    uploadId, Instant.parse("2026-11-01T00:00:00Z").plusSeconds(i));
        }
        insertAnalysis(uploadId, "DONE");

        mvc.perform(delete("/api/projects/" + projectId)).andExpect(status().isNoContent());

        assertThat(count("project")).isZero();
        assertThat(count("endpoint_policy")).isZero();
        assertThat(count("log_upload")).isZero();
        assertThat(count("request_log")).isZero();
        assertThat(count("analysis")).isZero();
    }

    @Test
    void 파싱_대기_중이거나_진행_중인_업로드가_있으면_409다() throws Exception {
        for (String status : new String[]{"UPLOADED", "PARSING"}) {
            long projectId = insertProject("p", "2026-11-01T00:00:00Z");
            insertUpload(projectId, status);

            mvc.perform(delete("/api/projects/" + projectId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_BUSY"));
        }
    }

    @Test
    void 분석이_대기_중이거나_진행_중이면_409다() throws Exception {
        for (String status : new String[]{"PENDING", "RUNNING"}) {
            long projectId = insertProject("p", "2026-11-01T00:00:00Z");
            insertAnalysis(insertUpload(projectId, "PARSED"), status);

            mvc.perform(delete("/api/projects/" + projectId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_BUSY"));
        }
    }

    @Test
    void 없는_프로젝트_삭제는_404다() throws Exception {
        mvc.perform(delete("/api/projects/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ---- helpers ----

    private long insertProject(String name, String createdAt) {
        jdbc.update("INSERT INTO project (name, created_at) VALUES (?, ?)", name, Instant.parse(createdAt));
        return lastId();
    }

    private long insertUpload(long projectId, String status) {
        jdbc.update("INSERT INTO log_upload (project_id, filename, status, created_at) VALUES (?, 'a.jsonl', ?, NOW(3))",
                projectId, status);
        return lastId();
    }

    private long insertAnalysis(long uploadId, String status) {
        jdbc.update("INSERT INTO analysis (upload_id, status, created_at) VALUES (?, ?, NOW(3))", uploadId, status);
        return lastId();
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
