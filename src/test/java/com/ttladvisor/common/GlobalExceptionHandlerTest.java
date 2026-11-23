package com.ttladvisor.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttladvisor.common.error.ApiException;
import com.ttladvisor.common.error.ErrorCode;
import com.ttladvisor.common.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 비즈니스_예외는_에러코드의_상태와_details로_응답한다() throws Exception {
        mvc.perform(get("/test/uploads/12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_PARSED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UPLOAD_NOT_PARSED.defaultMessage()))
                .andExpect(jsonPath("$.details.uploadId").value(12))
                .andExpect(jsonPath("$.details.status").value("PARSING"));
    }

    @Test
    void 요청_바디_검증_실패는_400과_필드별_사유를_준다() throws Exception {
        mvc.perform(post("/test/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void 깨진_JSON은_400이다() throws Exception {
        mvc.perform(post("/test/projects").contentType(MediaType.APPLICATION_JSON).content("{name:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 파라미터_타입_불일치와_검증_실패는_400이다() throws Exception {
        mvc.perform(get("/test/uploads/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/test/page").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.size").exists());
    }

    @Test
    void 지원하지_않는_메서드는_405다() throws Exception {
        mvc.perform(delete("/test/projects"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 작업_큐가_가득_차면_503이다() throws Exception {
        mvc.perform(get("/test/busy"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVER_BUSY"));
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_숨기고_500이다() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    record CreateRequest(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/uploads/{id}")
        String upload(@PathVariable long id) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_PARSED, Map.of("uploadId", id, "status", "PARSING"));
        }

        @PostMapping("/test/projects")
        String create(@Valid @RequestBody CreateRequest request) {
            return "ok";
        }

        @GetMapping("/test/page")
        String page(@RequestParam @Min(1) int size) {
            return "ok";
        }

        @GetMapping("/test/busy")
        String busy() {
            throw new TaskRejectedException("queue full");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("db password=secret");
        }
    }
}
