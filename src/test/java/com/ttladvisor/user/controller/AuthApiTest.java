package com.ttladvisor.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttladvisor.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 회원가입·로그인·로그아웃·내 정보 (API 0-1~0-4).
 * 테스트용 인증 우회 없이, 실제 브라우저처럼 SESSION·XSRF-TOKEN 쿠키를 주고받으며 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM users");
    }

    // ---- 0-4 내 정보 ----

    @Test
    void 로그인하지_않으면_me는_401이고_CSRF_쿠키를_준다() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    // ---- 0-1 회원가입 ----

    @Test
    void 가입하면_201과_함께_로그인되고_비밀번호는_해시로만_저장된다() throws Exception {
        Browser browser = new Browser();
        browser.send(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"  박개발 ","email":"Dev@Example.COM","password":"correct-horse-1","company":"  "}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/auth/me"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("박개발"))
                .andExpect(jsonPath("$.email").value("dev@example.com"))
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(cookie().httpOnly("SESSION", true))
                .andExpect(cookie().sameSite("SESSION", "Lax"))
                .andExpect(cookie().sameSite("XSRF-TOKEN", "Lax"));

        browser.send(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@example.com"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("correct-horse-1");
    }

    @Test
    void 대소문자만_다른_이메일로_다시_가입하면_409다() throws Exception {
        new Browser().signup("dev@example.com", "correct-horse-1").andExpect(status().isCreated());

        new Browser().signup("DEV@example.com", "another-pass-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 입력값이_규칙에_맞지_않으면_400과_필드별_사유를_준다() throws Exception {
        new Browser().send(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":" ","email":"not-an-email","password":"short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.email").value("이메일 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.details.password").value("비밀번호는 8~64자여야 합니다."));
    }

    @Test
    void 비밀번호가_BCrypt_한도인_72바이트를_넘으면_400이다() throws Exception {
        // 한글 24자 = 72바이트는 허용, 25자 = 75바이트는 거절 (글자 수로는 64자 이하라 @Size를 통과한다)
        new Browser().signup("a@example.com", "가".repeat(24)).andExpect(status().isCreated());

        new Browser().signup("b@example.com", "가".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    // ---- 0-2 로그인 ----

    @Test
    void 없는_이메일과_틀린_비밀번호는_구분하지_않고_같은_401을_준다() throws Exception {
        new Browser().signup("dev@example.com", "correct-horse-1").andExpect(status().isCreated());

        String wrongPassword = new Browser().login("dev@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = new Browser().login("nobody@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void 로그인하면_세션이_DB에_저장되고_CSRF_토큰이_새로_발급된다() throws Exception {
        new Browser().signup("dev@example.com", "correct-horse-1").andExpect(status().isCreated());
        jdbc.update("DELETE FROM SPRING_SESSION");
        long userId = jdbc.queryForObject("SELECT id FROM users", Long.class);

        Browser browser = new Browser();
        browser.send(get("/api/auth/me"));
        String csrfBefore = browser.cookie("XSRF-TOKEN");

        browser.login(" DEV@example.com ", "correct-horse-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));

        assertThat(browser.cookie("XSRF-TOKEN")).isNotNull().isNotEqualTo(csrfBefore);
        assertThat(jdbc.queryForList("SELECT PRINCIPAL_NAME FROM SPRING_SESSION", String.class))
                .containsExactly(String.valueOf(userId));
        browser.send(get("/api/projects")).andExpect(status().isOk());
    }

    @Test
    void 로그인하면_기존_세션_ID가_바뀐다() throws Exception {
        Browser browser = new Browser();
        browser.signup("dev@example.com", "correct-horse-1").andExpect(status().isCreated());
        String before = browser.cookie("SESSION");

        browser.login("dev@example.com", "correct-horse-1").andExpect(status().isOk());

        assertThat(browser.cookie("SESSION")).isNotNull().isNotEqualTo(before);
    }

    // ---- 0-3 로그아웃 ----

    @Test
    void 로그아웃하면_204이고_같은_쿠키로는_더_이상_로그인_상태가_아니다() throws Exception {
        Browser browser = new Browser();
        browser.signup("dev@example.com", "correct-horse-1").andExpect(status().isCreated());
        Cookie oldSession = browser.jar.get("SESSION");

        browser.send(post("/api/auth/logout")).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isZero();
    }

    // ---- CSRF ----

    @Test
    void CSRF_헤더_없이_보낸_쓰기_요청은_403이다() throws Exception {
        Browser browser = new Browser();
        browser.send(get("/api/auth/me"));

        mvc.perform(post("/api/auth/login").cookie(browser.jar.values().toArray(Cookie[]::new))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.com\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    /** 쿠키를 저장해 두었다가 다음 요청에 보내고, XSRF-TOKEN 쿠키 값을 헤더로 붙이는 브라우저 흉내. */
    class Browser {

        final Map<String, Cookie> jar = new LinkedHashMap<>();

        ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
            if (!jar.containsKey("XSRF-TOKEN")) {
                store(mvc.perform(get("/api/auth/me").cookie(cookies())));
            }
            request.cookie(cookies()).header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue());
            return store(mvc.perform(request));
        }

        ResultActions signup(String email, String password) throws Exception {
            return send(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"테스터\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
        }

        ResultActions login(String email, String password) throws Exception {
            return send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
        }

        String cookie(String name) {
            Cookie c = jar.get(name);
            return c == null ? null : c.getValue();
        }

        private Cookie[] cookies() {
            return jar.isEmpty() ? new Cookie[] {new Cookie("none", "")} : jar.values().toArray(Cookie[]::new);
        }

        private ResultActions store(ResultActions actions) {
            for (Cookie c : actions.andReturn().getResponse().getCookies()) {
                if (c.getMaxAge() == 0) {
                    jar.remove(c.getName());
                } else {
                    jar.put(c.getName(), c);
                }
            }
            return actions;
        }
    }
}
