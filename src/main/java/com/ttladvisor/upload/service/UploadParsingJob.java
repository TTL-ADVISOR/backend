package com.ttladvisor.upload.service;

import com.ttladvisor.config.AppProperties;
import com.ttladvisor.upload.repository.RequestLogJdbcRepository;
import com.ttladvisor.upload.repository.RequestLogJdbcRepository.Row;
import com.ttladvisor.upload.repository.UploadStatusJdbcRepository;
import com.ttladvisor.upload.service.JsonLinesParser.LineError;
import com.ttladvisor.upload.service.JsonLinesParser.ParseSummary;
import com.ttladvisor.upload.service.JsonLinesParser.ParsedLog;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 업로드 파일 하나를 파싱해 request_log에 적재하는 백그라운드 작업 (상태 UPLOADED → PARSING → PARSED | FAILED).
 * 트랜잭션을 걸지 않는다: 배치마다 커밋해 수십만 행을 한 트랜잭션에 몰지 않는다.
 * 대신 실패하면 그때까지 적재한 행을 직접 지운다.
 */
@Component
public class UploadParsingJob {

    static final String REASON_FORMAT = "JSON Lines 형식이 아닙니다";
    static final String REASON_NO_GET = "분석할 GET 요청이 없습니다";
    static final String REASON_ENCODING = "UTF-8 텍스트 파일이 아닙니다";
    static final String REASON_UNEXPECTED = "파일을 처리하지 못했습니다. 다시 업로드해 주세요";
    private static final int DELETE_BATCH = 10_000;
    private static final Logger log = LoggerFactory.getLogger(UploadParsingJob.class);

    private final JsonLinesParser parser;
    private final RequestLogJdbcRepository requestLogs;
    private final UploadStatusJdbcRepository status;
    private final ObjectMapper objectMapper;
    private final AppProperties.Upload settings;

    public UploadParsingJob(JsonLinesParser parser, RequestLogJdbcRepository requestLogs,
                            UploadStatusJdbcRepository status, ObjectMapper objectMapper, AppProperties properties) {
        this.parser = parser;
        this.requestLogs = requestLogs;
        this.status = status;
        this.objectMapper = objectMapper;
        this.settings = properties.upload();
    }

    static String tooManyLinesReason(int maxLines) {
        // 서버 기본 로캘에 따라 구분 기호가 바뀌지 않게 고정한다 (예: de에서는 1.000.000)
        return String.format(Locale.ROOT, "최대 %,d줄까지 업로드할 수 있습니다", maxLines);
    }

    /** 파일은 성공·실패와 관계없이 끝나면 지운다. */
    public void run(long uploadId, Path file) {
        try {
            if (!status.startParsing(uploadId)) {
                log.info("업로드 {}는 이미 처리 중이거나 삭제되어 건너뜀", uploadId);
                return;
            }
            parseAndStore(uploadId, file);
        } catch (RuntimeException | IOException e) {
            log.error("업로드 {} 파싱 실패", uploadId, e);
            fail(uploadId, e instanceof CharacterCodingException ? REASON_ENCODING : REASON_UNEXPECTED, null);
        } finally {
            deleteQuietly(file);
        }
    }

    private void parseAndStore(long uploadId, Path file) throws IOException {
        Batch batch = new Batch(uploadId);
        ParseSummary summary;
        // newBufferedReader는 잘못된 UTF-8 바이트를 만나면 CharacterCodingException을 던진다 (조용히 깨진 문자로 바꾸지 않음)
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            summary = parser.parse(reader, batch::add, line -> {
                if (line % settings.progressEveryLines() == 0) {
                    status.updateProgress(uploadId, line);
                }
            }, settings.maxLines());
        }
        batch.flush();

        if (summary.invalidFormat()) {
            fail(uploadId, REASON_FORMAT, summary);
        } else if (summary.tooManyLines()) {
            fail(uploadId, tooManyLinesReason(settings.maxLines()), summary);
        } else if (batch.cacheableGets == 0) {
            fail(uploadId, REASON_NO_GET, summary);
        } else {
            status.markParsed(uploadId, summary.processedLines(), summary.errorLines(),
                    samplesJson(summary.errorSamples()), batch.rangeStart, batch.rangeEnd);
            log.info("업로드 {} 파싱 완료: {}줄, 실패 {}줄", uploadId, summary.processedLines(), summary.errorLines());
        }
    }

    /** 적재한 요청 로그를 지우고 FAILED로 바꾼다 (API.md 2-3: FAILED가 되면 저장된 요청 로그는 삭제). */
    void fail(long uploadId, String reason, ParseSummary summary) {
        requestLogs.deleteByUploadId(uploadId, DELETE_BATCH);
        status.markFailed(uploadId, reason,
                summary == null ? 0 : summary.processedLines(),
                summary == null ? 0 : summary.errorLines(),
                samplesJson(summary == null ? List.of() : summary.errorSamples()));
    }

    private String samplesJson(List<LineError> samples) {
        return objectMapper.writeValueAsString(samples);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", file, e);
        }
    }

    /** 유효한 로그를 모아 배치로 저장하고, 시간 범위와 분석 대상(GET 2xx) 수를 센다. */
    private final class Batch {
        private final long uploadId;
        private final List<Row> rows = new ArrayList<>(settings.insertBatchSize());
        private Instant rangeStart;
        private Instant rangeEnd;
        private long cacheableGets;

        private Batch(long uploadId) {
            this.uploadId = uploadId;
        }

        void add(ParsedLog parsed) {
            rows.add(new Row(parsed.timestamp(), parsed.method(), RequestKeys.resourceKey(parsed.path()),
                    RequestKeys.cacheKey(parsed.method(), parsed.path()), parsed.status(), parsed.latencyMs(),
                    parsed.size(), parsed.responseHash()));
            if (rangeStart == null || parsed.timestamp().isBefore(rangeStart)) {
                rangeStart = parsed.timestamp();
            }
            if (rangeEnd == null || parsed.timestamp().isAfter(rangeEnd)) {
                rangeEnd = parsed.timestamp();
            }
            // 캐시 분석 대상은 GET 2xx뿐이다 (DESIGN.md 4장)
            if ("GET".equals(parsed.method()) && parsed.status() >= 200 && parsed.status() < 300) {
                cacheableGets++;
            }
            if (rows.size() >= settings.insertBatchSize()) {
                flush();
            }
        }

        void flush() {
            if (!rows.isEmpty()) {
                requestLogs.insertBatch(uploadId, rows);
                rows.clear();
            }
        }
    }
}
