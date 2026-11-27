package com.ttladvisor.upload.controller;

import static com.ttladvisor.upload.controller.UploadTestSupport.awaitFinished;
import static com.ttladvisor.upload.controller.UploadTestSupport.idOf;
import static com.ttladvisor.upload.controller.UploadTestSupport.log;
import static com.ttladvisor.upload.controller.UploadTestSupport.mvcAs;
import static com.ttladvisor.upload.controller.UploadTestSupport.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.ttladvisor.TestData;
import com.ttladvisor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** 100만 줄 한도를 3줄로 낮춰 확인한다 (실제 100만 줄 파일을 만들지 않기 위해 별도 컨텍스트). */
@SpringBootTest(properties = {"app.upload.max-lines=3", "app.upload.insert-batch-size=2"})
@Import(TestcontainersConfiguration.class)
class UploadLineLimitTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 줄_수_한도를_넘으면_FAILED이고_이미_저장한_줄도_지운다() throws Exception {
        TestData.deleteAll(jdbc);
        long ownerId = TestData.insertUser(jdbc, "limit@example.com");
        jdbc.update("INSERT INTO project (owner_id, name, created_at) VALUES (?, 'p', NOW(3))", ownerId);
        long projectId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        MockMvc mvc = mvcAs(context, ownerId);

        String line = log("2026-11-22T00:00:00Z", "GET", "/a", 200);
        long atLimit = idOf(upload(mvc, projectId, line.repeat(3)));
        long overLimit = idOf(upload(mvc, projectId, line.repeat(4)));

        assertThat((String) JsonPath.read(awaitFinished(mvc, atLimit), "$.status")).isEqualTo("PARSED");
        String body = awaitFinished(mvc, overLimit);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.failureReason")).isEqualTo("최대 3줄까지 업로드할 수 있습니다");
        // 배치 크기 2라 한도 전에 2줄이 이미 저장됐다가 지워져야 한다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_log WHERE upload_id = ?", Integer.class, overLimit)).isZero();
        TestData.deleteAll(jdbc);
    }
}
