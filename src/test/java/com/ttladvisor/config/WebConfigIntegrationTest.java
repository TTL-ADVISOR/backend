package com.ttladvisor.config;

import com.ttladvisor.TestcontainersConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실제 컨텍스트에서 CORS, 404 에러 형식, @Async 스레드풀 연결을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WebConfigIntegrationTest.PingController.class,
        WebConfigIntegrationTest.AsyncProbe.class})
class WebConfigIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AsyncProbe asyncProbe;

    @Autowired
    @Qualifier(AsyncConfig.JOB_EXECUTOR)
    ThreadPoolTaskExecutor jobExecutor;

    @Test
    void 허용된_Origin의_preflight는_통과하고_Location_헤더를_노출한다() throws Exception {
        mvc.perform(options("/api/ping")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mvc.perform(get("/api/ping").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.LOCATION));
    }

    @Test
    void 허용되지_않은_Origin은_거절한다() throws Exception {
        mvc.perform(options("/api/ping")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_경로도_공통_에러_형식으로_404를_준다() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void Async_메서드는_job_스레드풀에서_실행된다() throws Exception {
        String threadName = asyncProbe.currentThreadName().get(5, TimeUnit.SECONDS);

        assertThat(threadName).startsWith("job-");
        assertThat(jobExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(jobExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(jobExecutor.getQueueCapacity()).isEqualTo(50);
    }

    @RestController
    static class PingController {
        @GetMapping("/api/ping")
        String ping() {
            return "pong";
        }
    }

    @Component
    static class AsyncProbe {
        @Async
        public CompletableFuture<String> currentThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
