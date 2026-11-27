package com.ttladvisor.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class JsonLinesParserTest {

    private static final String VALID_LOG = """
            {"ts":"2026-11-23T19:00:00.123+09:00","method":"GET","path":"/users/123?z=2&a=1","status":200}
            """;

    private final JsonLinesParser parser = new JsonLinesParser(JsonMapper.builder().build());

    @Test
    void 필수_필드만_있는_로그를_읽고_쿼리_순서를_보존한다() throws IOException {
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();
        var result = parser.parse(new StringReader(VALID_LOG.stripTrailing()), logs::add);

        assertThat(result).isEqualTo(new JsonLinesParser.ParseSummary(1, 0, List.of(), false));
        assertThat(logs).containsExactly(new JsonLinesParser.ParsedLog(
                Instant.parse("2026-11-23T10:00:00.123Z"), "GET", "/users/123?z=2&a=1", 200, null, null, null));
    }

    @Test
    void 쓰기_요청과_선택_필드를_보존하고_CRLF를_읽는다() throws IOException {
        String write = """
                {"ts":"2026-11-23T10:00:00Z","method":"PATCH","path":"/한글/기록","status":404,"latencyMs":0,"size":2147483647,"respHash":"a1b2","ignored":true}
                """;
        String withNulls = VALID_LOG.replace("200}", "200,\"latencyMs\":null,\"size\":null,\"respHash\":null}");
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader((write + withNulls).replace("\n", "\r\n")), logs::add);

        assertThat(result.errorLines()).isZero();
        assertThat(logs).containsExactly(
                new JsonLinesParser.ParsedLog(Instant.parse("2026-11-23T10:00:00Z"), "PATCH", "/한글/기록",
                        404, 0, Integer.MAX_VALUE, "a1b2"),
                new JsonLinesParser.ParsedLog(Instant.parse("2026-11-23T10:00:00.123Z"), "GET",
                        "/users/123?z=2&a=1", 200, null, null, null));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"ts\":\"2026-11-23T19:00:00.123+09:00\", | '' | ts 필드 누락",
        "\"method\":\"GET\", | '' | method 필드 누락",
        "\"path\":\"/users/123?z=2&a=1\", | '' | path 필드 누락",
        "\"status\":200 | \"unknown\":200 | status 필드 누락",
        "\"2026-11-23T19:00:00.123+09:00\" | \"2026-11-23T10:00:00\" | ts는 시간대",
        "\"2026-11-23T19:00:00.123+09:00\" | \"2026-11-23T10:00:00.1234Z\" | 밀리초 단위",
        "\"2026-11-23T19:00:00.123+09:00\" | \"1000-01-01T00:00:00+01:00\" | UTC 1000~9999년",
        "\"GET\" | false | method 필드는 비어 있지 않은 문자열",
        "\"GET\" | \"GE T\" | method는 10자 이하",
        "\"GET\" | \"VERYLONGMETHOD\" | method는 10자 이하",
        "\"/users/123?z=2&a=1\" | \"users/123\" | path는 /로 시작",
        "\"/users/123?z=2&a=1\" | \"/users#123\" | path는 /로 시작",
        "\"/users/123?z=2&a=1\" | \"/bad path\" | path는 /로 시작",
        "200} | 99} | status는 100~599",
        "200} | 600} | status는 100~599",
        "200} | 200.0} | status는 정수",
        "200} | \"200\"} | status는 정수",
        "200} | 200,\"latencyMs\":-1} | latencyMs는 0~",
        "200} | 200,\"size\":2147483648} | size는 정수",
        "200} | 200,\"respHash\":true} | respHash 필드는 비어 있지 않은 문자열"
    })
    void 입력_필드의_누락과_잘못된_값을_검증한다(String original, String replacement, String reason) throws IOException {
        String invalid = VALID_LOG.replace(original, replacement);
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader(invalid + VALID_LOG), logs::add);

        assertThat(result.invalidFormat()).isFalse();
        assertThat(result.errorLines()).isOne();
        assertThat(result.errorSamples()).singleElement().satisfies(error -> {
            assertThat(error.line()).isOne();
            assertThat(error.reason()).contains(reason);
        });
        assertThat(logs).hasSize(1);
    }

    @Test
    void 문자열_길이_한도는_유니코드_문자_단위로_검사한다() throws IOException {
        String maxPath = "/" + "😀".repeat(JsonLinesParser.MAX_PATH_LENGTH - 1);
        String pathAtLimit = VALID_LOG.replace("/users/123?z=2&a=1", maxPath);
        String pathOverLimit = VALID_LOG.replace("/users/123?z=2&a=1", maxPath + "a");
        String hashOverLimit = VALID_LOG.replace("200}", "200,\"respHash\":\"" + "a".repeat(65) + "\"}");
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader(pathAtLimit + pathOverLimit + hashOverLimit), logs::add);

        assertThat(result.errorLines()).isEqualTo(2);
        assertThat(result.errorSamples()).extracting(JsonLinesParser.LineError::line).containsExactly(2, 3);
        assertThat(logs).singleElement().extracting(JsonLinesParser.ParsedLog::path).isEqualTo(maxPath);
    }

    @Test
    void 경로_한도는_메서드를_붙인_캐시_키가_512자_컬럼에_들어가는_길이다() {
        // cache_key = "메서드(최대 10자) 공백 경로" → VARCHAR(512)
        assertThat(10 + 1 + JsonLinesParser.MAX_PATH_LENGTH).isLessThanOrEqualTo(512);
    }

    @Test
    void 첫_줄_앞의_UTF8_BOM은_무시한다() throws IOException {
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader("\uFEFF" + VALID_LOG + VALID_LOG), logs::add);

        assertThat(result.errorLines()).isZero();
        assertThat(logs).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        " ",
        "[]",
        "null",
        "{\"private-url\": ",
        "{\"path\":\"private-url\",\"path\":\"/second\"}",
        "{\"path\":\"private-url\"} {\"path\":\"/second\"}"
    })
    void 한_줄에_JSON_객체_하나만_허용하고_입력값은_오류_사유에_노출하지_않는다(String invalid) throws IOException {
        var result = parser.parse(new StringReader(invalid + "\n" + VALID_LOG), log -> {});

        assertThat(result.errorLines()).isOne();
        assertThat(result.errorSamples()).singleElement().satisfies(error -> {
            assertThat(error.line()).isOne();
            assertThat(error.reason()).isIn("JSON 객체가 필요합니다", "JSON 파싱 실패");
        });
    }

    @Test
    void 첫_천_줄에서_실패율이_정확히_90퍼센트면_파싱을_중단한다() throws IOException {
        String input = VALID_LOG.repeat(100) + "invalid\n".repeat(900) + VALID_LOG;
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader(input), logs::add);

        assertThat(result.invalidFormat()).isTrue();
        assertThat(result.processedLines()).isEqualTo(1_000);
        assertThat(result.errorLines()).isEqualTo(900);
        assertThat(logs).hasSize(100);
        assertThat(result.errorSamples()).hasSize(20);
        assertThat(result.errorSamples().getFirst().line()).isEqualTo(101);
        assertThat(result.errorSamples().getLast().line()).isEqualTo(120);
    }

    @Test
    void 처음_천_줄을_통과하면_이후_오류율에_상관없이_끝까지_읽는다() throws IOException {
        String input = VALID_LOG.repeat(101) + "invalid\n".repeat(1_000) + VALID_LOG;
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader(input), logs::add);

        assertThat(result.invalidFormat()).isFalse();
        assertThat(result.processedLines()).isEqualTo(1_102);
        assertThat(result.errorLines()).isEqualTo(1_000);
        assertThat(result.errorSamples()).hasSize(20);
        assertThat(logs).hasSize(102);
    }

    @Test
    void 짧은_파일은_실제_줄_수를_기준으로_90퍼센트를_계산한다() throws IOException {
        var rejected = parser.parse(new StringReader("invalid\n".repeat(9) + VALID_LOG), log -> {});
        var accepted = parser.parse(new StringReader("invalid\n".repeat(8) + VALID_LOG), log -> {});

        assertThat(rejected.invalidFormat()).isTrue();
        assertThat(accepted.invalidFormat()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", " ", "invalid"})
    void 비어있거나_유효한_줄이_없는_입력은_형식_오류다(String input) throws IOException {
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var result = parser.parse(new StringReader(input), logs::add);

        assertThat(result.invalidFormat()).isTrue();
        assertThat(logs).isEmpty();
    }

    @Test
    void 줄_수_한도를_넘으면_더_읽지_않고_알린다() throws IOException {
        List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();

        var atLimit = parser.parse(new StringReader(VALID_LOG.repeat(3)), log -> { }, line -> { }, 3);
        var overLimit = parser.parse(new StringReader(VALID_LOG.repeat(4)), logs::add, line -> { }, 3);

        assertThat(atLimit.tooManyLines()).isFalse();
        assertThat(overLimit.tooManyLines()).isTrue();
        assertThat(overLimit.processedLines()).isEqualTo(3);
        assertThat(logs).hasSize(3);
    }

    @Test
    void 줄을_읽을_때마다_진행_줄_수를_알린다() throws IOException {
        List<Integer> progress = new ArrayList<>();

        parser.parse(new StringReader(VALID_LOG + "invalid\n" + VALID_LOG), log -> { }, progress::add, 10);

        assertThat(progress).containsExactly(1, 2, 3);
    }

    @Test
    void 유효_로그_처리_중_발생한_오류는_숨기지_않는다() {
        var sinkFailure = new IllegalStateException("저장 실패");

        assertThatThrownBy(() -> parser.parse(new StringReader(VALID_LOG), log -> {
            throw sinkFailure;
        })).isSameAs(sinkFailure);
    }

    @Test
    void 같은_파서로_동시에_읽어도_로그와_오류_집계가_섞이지_않는다() throws Exception {
        int taskCount = 8;
        var allReading = new CountDownLatch(taskCount);
        List<Future<JsonLinesParser.ParseSummary>> jobs = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(taskCount)) {
            for (int index = 0; index < taskCount; index++) {
                int errorCount = index;
                String path = "/concurrent/" + index;
                String input = "invalid\n".repeat(errorCount) + VALID_LOG.replace("/users/123?z=2&a=1", path);

                jobs.add(executor.submit(() -> {
                    List<JsonLinesParser.ParsedLog> logs = new ArrayList<>();
                    var summary = parser.parse(new StringReader(input), log -> {
                        logs.add(log);
                        allReading.countDown();
                        try {
                            assertThat(allReading.await(5, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("동시 파싱 테스트 중 인터럽트", ex);
                        }
                    });
                    assertThat(logs).extracting(JsonLinesParser.ParsedLog::path).containsExactly(path);
                    return summary;
                }));
            }

            for (int index = 0; index < taskCount; index++) {
                var summary = jobs.get(index).get(10, TimeUnit.SECONDS);
                assertThat(summary.processedLines()).isEqualTo(index + 1);
                assertThat(summary.errorLines()).isEqualTo(index);
                assertThat(summary.errorSamples()).hasSize(index);
                assertThat(summary.invalidFormat()).isFalse();
            }
        }
    }
}
