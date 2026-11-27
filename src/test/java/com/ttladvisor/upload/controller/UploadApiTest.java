package com.ttladvisor.upload.controller;

import static com.ttladvisor.upload.controller.UploadTestSupport.awaitFinished;
import static com.ttladvisor.upload.controller.UploadTestSupport.idOf;
import static com.ttladvisor.upload.controller.UploadTestSupport.log;
import static com.ttladvisor.upload.controller.UploadTestSupport.mvcAs;
import static com.ttladvisor.upload.controller.UploadTestSupport.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ttladvisor.TestData;
import com.ttladvisor.TestcontainersConfiguration;
import com.ttladvisor.upload.service.UploadRecovery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UploadApiTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UploadRecovery recovery;

    MockMvc mvc;
    MockMvc otherMvc;
    long projectId;

    @BeforeEach
    void setUp() {
        TestData.deleteAll(jdbc);
        long ownerId = TestData.insertUser(jdbc, "owner@example.com");
        long otherId = TestData.insertUser(jdbc, "other@example.com");
        mvc = mvcAs(context, ownerId);
        otherMvc = mvcAs(context, otherId);
        jdbc.update("INSERT INTO project (owner_id, name, created_at) VALUES (?, '쇼핑몰', NOW(3))", ownerId);
        projectId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void 업로드하면_202로_바로_응답하고_백그라운드에서_파싱해_저장한다() throws Exception {
        String content = log("2026-11-22T00:00:00.012Z", "GET", "/users/1?z=2&a=1", 200)
                + "not json\n"
                + log("2026-11-22T00:00:05Z", "PUT", "/users/1", 204)
                + log("2026-11-22T23:59:59.870Z", "GET", "/users/2", 200);

        var accepted = upload(mvc, projectId, content)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", matchesPattern("/api/uploads/\\d+")))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
        long uploadId = idOf(accepted);

        String body = awaitFinished(mvc, uploadId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("PARSED");
        assertThat((Integer) JsonPath.read(body, "$.totalLines")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(body, "$.processedLines")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(body, "$.errorLines")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.rangeStart")).isEqualTo("2026-11-22T00:00:00.012Z");
        assertThat((String) JsonPath.read(body, "$.rangeEnd")).isEqualTo("2026-11-22T23:59:59.870Z");
        assertThat((List<Map<String, Object>>) JsonPath.read(body, "$.errorSamples"))
                .containsExactly(Map.of("line", 2, "reason", "JSON 파싱 실패"));
        assertThat((Object) JsonPath.read(body, "$.failureReason")).isNull();

        // 유효한 3줄 저장, 캐시 키는 쿼리를 정렬, 리소스 키는 쿼리를 뺀 경로
        assertThat(jdbc.queryForList("SELECT cache_key FROM request_log WHERE upload_id = ? ORDER BY ts", String.class, uploadId))
                .containsExactly("GET /users/1?a=1&z=2", "PUT /users/1", "GET /users/2");
        assertThat(jdbc.queryForList("SELECT resource_key FROM request_log WHERE upload_id = ? ORDER BY ts", String.class, uploadId))
                .containsExactly("/users/1", "/users/1", "/users/2");
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(ts, '%Y-%m-%d %H:%i:%s.%f') FROM request_log WHERE upload_id = ? ORDER BY ts LIMIT 1",
                String.class, uploadId)).isEqualTo("2026-11-22 00:00:00.012000");
    }

    @Test
    void 목록은_최근_순이고_실패_샘플은_빠진다() throws Exception {
        long first = idOf(upload(mvc, projectId, log("2026-11-22T00:00:00Z", "GET", "/a", 200)));
        awaitFinished(mvc, first);
        long second = idOf(upload(mvc, projectId, log("2026-11-22T00:00:00Z", "GET", "/b", 200)));
        awaitFinished(mvc, second);

        mvc.perform(get("/api/projects/{id}/uploads", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[0].filename").value("access.jsonl"))
                .andExpect(jsonPath("$[0].errorSamples").doesNotExist());
    }

    @Test
    void 형식이_JSON_Lines가_아니면_FAILED이고_저장한_줄은_지운다() throws Exception {
        // 유효한 1줄이 먼저 저장된 뒤 형식 오류로 판정된다 (9/10 실패)
        String content = log("2026-11-22T00:00:00Z", "GET", "/a", 200) + "garbage\n".repeat(9);
        long uploadId = idOf(upload(mvc, projectId, content));

        String body = awaitFinished(mvc, uploadId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.failureReason")).isEqualTo("JSON Lines 형식이 아닙니다");
        assertThat((Object) JsonPath.read(body, "$.totalLines")).isNull();
        assertThat((Integer) JsonPath.read(body, "$.errorLines")).isEqualTo(9);
        assertThat(rows(uploadId)).isZero();
    }

    @Test
    void 분석할_GET_2xx가_없으면_FAILED다() throws Exception {
        String content = log("2026-11-22T00:00:00Z", "POST", "/a", 201) + log("2026-11-22T00:00:01Z", "GET", "/a", 404);
        long uploadId = idOf(upload(mvc, projectId, content));

        String body = awaitFinished(mvc, uploadId);
        assertThat((String) JsonPath.read(body, "$.failureReason")).isEqualTo("분석할 GET 요청이 없습니다");
        assertThat(rows(uploadId)).isZero();
    }

    @Test
    void UTF8이_아닌_파일은_FAILED다() throws Exception {
        byte[] latin1 = (log("2026-11-22T00:00:00Z", "GET", "/café", 200))
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        long uploadId = idOf(upload(mvc, projectId, "access.log", latin1));

        String body = awaitFinished(mvc, uploadId);
        assertThat((String) JsonPath.read(body, "$.failureReason")).isEqualTo("UTF-8 텍스트 파일이 아닙니다");
    }

    @Test
    void 확장자가_jsonl_log_txt가_아니면_415이고_업로드를_만들지_않는다() throws Exception {
        upload(mvc, projectId, "access.csv", "a".getBytes())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log_upload", Integer.class)).isZero();
    }

    @Test
    void 파일_파트가_없으면_400이다() throws Exception {
        mvc.perform(multipart("/api/projects/{id}/uploads", projectId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 남의_프로젝트에는_올릴_수_없고_남의_업로드는_볼_수_없다() throws Exception {
        upload(otherMvc, projectId, log("2026-11-22T00:00:00Z", "GET", "/a", 200))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        otherMvc.perform(get("/api/projects/{id}/uploads", projectId)).andExpect(status().isNotFound());

        long uploadId = idOf(upload(mvc, projectId, log("2026-11-22T00:00:00Z", "GET", "/a", 200)));
        awaitFinished(mvc, uploadId);
        otherMvc.perform(get("/api/uploads/{id}", uploadId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
    }

    @Test
    void 서버_재시작으로_멈춘_업로드는_시작할_때_FAILED로_바꾸고_저장한_줄을_지운다() throws Exception {
        jdbc.update("INSERT INTO log_upload (project_id, filename, status, created_at) VALUES (?, 'a.jsonl', 'PARSING', NOW(3))", projectId);
        long uploadId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO request_log (upload_id, ts, method, resource_key, cache_key, status_code) VALUES (?, NOW(3), 'GET', '/a', 'GET /a', 200)", uploadId);

        recovery.failInterruptedUploads();

        mvc.perform(get("/api/uploads/{id}", uploadId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.startsWith("서버가 다시 시작되어")));
        assertThat(rows(uploadId)).isZero();
    }

    private long rows(long uploadId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM request_log WHERE upload_id = ?", Long.class, uploadId);
    }
}
