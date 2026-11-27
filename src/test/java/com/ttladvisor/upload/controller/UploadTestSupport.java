package com.ttladvisor.upload.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.jayway.jsonpath.JsonPath;
import com.ttladvisor.global.security.AuthUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 업로드 통합 테스트 공용: 로그인한 MockMvc, 파일 업로드, 파싱 끝날 때까지 기다리기. */
final class UploadTestSupport {

    private static final String CSRF = "test-csrf-token";

    private UploadTestSupport() {
    }

    /** csrf() 대신 같은 값을 쿠키와 헤더로 보낸다 (공유 컨텍스트를 바꾸지 않게, ProjectApiTest 참고). */
    static MockMvc mvcAs(WebApplicationContext context, long userId) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new AuthUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(authentication(auth))
                        .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF))
                .build();
    }

    static ResultActions upload(MockMvc mvc, long projectId, String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/projects/{projectId}/uploads", projectId)
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content)));
    }

    static ResultActions upload(MockMvc mvc, long projectId, String content) throws Exception {
        return upload(mvc, projectId, "access.jsonl", content.getBytes(StandardCharsets.UTF_8));
    }

    /** 백그라운드 파싱이 끝날 때까지(PARSED 또는 FAILED) 상태 API를 폴링하고 마지막 응답 본문을 돌려준다. */
    static String awaitFinished(MockMvc mvc, long uploadId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            String body = mvc.perform(get("/api/uploads/{id}", uploadId)).andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (status.equals("PARSED") || status.equals("FAILED")) {
                return body;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("파싱이 끝나지 않음: " + body);
            }
            Thread.sleep(50);
        }
    }

    static long idOf(ResultActions accepted) throws Exception {
        return ((Number) JsonPath.read(accepted.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    static String log(String ts, String method, String path, int status) {
        return "{\"ts\":\"%s\",\"method\":\"%s\",\"path\":\"%s\",\"status\":%d}\n".formatted(ts, method, path, status);
    }
}
