package com.ttladvisor.upload.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
public class JsonLinesParser {

    private static final int FORMAT_SAMPLE_SIZE = 1_000;
    private static final int FORMAT_ERROR_PERCENT_THRESHOLD = 90;
    private static final int MAX_ERROR_SAMPLES = 20;
    private static final int MAX_METHOD_LENGTH = 10;
    /**
     * request_log.cache_key(VARCHAR 512)에는 "메서드 + 공백 + 경로"가 들어간다. 메서드 최대 10자 + 공백 1자를 빼고
     * 남는 501자보다 조금 여유 있게 500자로 제한해, 저장 단계에서 "Data too long"으로 업로드 전체가 실패하지 않게 한다.
     */
    static final int MAX_PATH_LENGTH = 500;
    private static final char BOM = '\uFEFF';
    private static final int MAX_HASH_LENGTH = 64;
    private static final int MIN_HTTP_STATUS = 100;
    private static final int MAX_HTTP_STATUS = 599;
    private static final int NANOS_PER_MILLISECOND = 1_000_000;
    private static final Instant MIN_STORABLE_TS = Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant END_OF_STORABLE_TS = Instant.parse("+10000-01-01T00:00:00Z");
    // HTTP 메서드는 대소문자를 구분하는 토큰이며, 저장 컬럼은 10자까지 허용한다.
    private static final Pattern HTTP_METHOD =
            Pattern.compile("[A-Za-z0-9!#$%&'*+.^_\\x60|~-]{1," + MAX_METHOD_LENGTH + "}");

    private final ObjectReader lineReader;

    public JsonLinesParser(ObjectMapper mapper) {
        this.lineReader = mapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    /**
     * 유효한 줄을 파일 순서대로 전달하며 입력은 호출자가 닫는다.
     * 형식 오류가 확정되기 전에 전달된 행이 있을 수 있으므로, 저장하는 호출자가 해당 업로드의 행을 정리한다.
     */
    public ParseSummary parse(Reader source, Consumer<ParsedLog> onValidLog) throws IOException {
        BufferedReader lines = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        List<LineError> samples = new ArrayList<>(MAX_ERROR_SAMPLES);
        int processedLines = 0;
        int errorLines = 0;

        String line;
        while ((line = lines.readLine()) != null) {
            processedLines++;
            // Windows 메모장 등이 붙이는 UTF-8 BOM이 첫 줄 앞에 있으면 JSON 파싱이 실패하므로 지운다
            if (processedLines == 1 && !line.isEmpty() && line.charAt(0) == BOM) {
                line = line.substring(1);
            }
            ParsedLog log = null;
            try {
                log = parseLine(line);
            } catch (InvalidLine ex) {
                addSample(samples, processedLines, ex.getMessage());
                errorLines++;
            }
            if (log != null) {
                onValidLog.accept(log);
            }
            if (processedLines == FORMAT_SAMPLE_SIZE && reachesFormatErrorThreshold(errorLines, processedLines)) {
                return new ParseSummary(processedLines, errorLines, samples, true);
            }
        }

        boolean invalidFormat = processedLines == 0
                || (processedLines < FORMAT_SAMPLE_SIZE && reachesFormatErrorThreshold(errorLines, processedLines));
        return new ParseSummary(processedLines, errorLines, samples, invalidFormat);
    }

    private ParsedLog parseLine(String line) throws InvalidLine {
        JsonNode node;
        try {
            node = lineReader.readTree(line);
        } catch (JacksonException ex) {
            // 파서 예외에는 로그에 들어 있던 경로나 값이 포함될 수 있다.
            throw new InvalidLine("JSON 파싱 실패");
        }
        if (!node.isObject()) {
            throw new InvalidLine("JSON 객체가 필요합니다");
        }

        Instant timestamp = parseTimestamp(requiredText(node, "ts"));
        String method = requiredText(node, "method");
        String path = requiredText(node, "path");
        if (!HTTP_METHOD.matcher(method).matches()) {
            throw new InvalidLine("method는 " + MAX_METHOD_LENGTH + "자 이하의 HTTP 메서드여야 합니다");
        }
        if (!path.startsWith("/") || path.indexOf('#') >= 0 || !isStorableText(path, MAX_PATH_LENGTH)
                || path.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new InvalidLine("path는 /로 시작하고 공백이나 프래그먼트가 없는 " + MAX_PATH_LENGTH + "자 이하의 경로여야 합니다");
        }

        int status = checkedInt(node.get("status"), "status", MIN_HTTP_STATUS, MAX_HTTP_STATUS);
        Integer latencyMs = optionalNonNegativeInt(node, "latencyMs");
        Integer size = optionalNonNegativeInt(node, "size");
        String responseHash = optionalHash(node);
        return new ParsedLog(timestamp, method, path, status, latencyMs, size, responseHash);
    }

    private static Instant parseTimestamp(String text) throws InvalidLine {
        try {
            Instant timestamp = OffsetDateTime.parse(text).toInstant();
            if (timestamp.getNano() % NANOS_PER_MILLISECOND == 0
                    && !timestamp.isBefore(MIN_STORABLE_TS) && timestamp.isBefore(END_OF_STORABLE_TS)) {
                return timestamp;
            }
        } catch (DateTimeParseException ex) {
            throw new InvalidLine("ts는 시간대가 포함된 ISO-8601 시각이어야 합니다");
        }
        throw new InvalidLine("ts는 UTC 1000~9999년 범위의 밀리초 단위여야 합니다");
    }

    private static String requiredText(JsonNode node, String field) throws InvalidLine {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new InvalidLine(field + " 필드 누락");
        }
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new InvalidLine(field + " 필드는 비어 있지 않은 문자열이어야 합니다");
        }
        return value.stringValue();
    }

    private static int checkedInt(JsonNode value, String field, int min, int max) throws InvalidLine {
        if (value == null || value.isNull()) {
            throw new InvalidLine(field + " 필드 누락");
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidLine(field + "는 정수여야 합니다");
        }
        int number = value.intValue();
        if (number < min || number > max) {
            throw new InvalidLine(field + "는 " + min + "~" + max + " 범위여야 합니다");
        }
        return number;
    }

    private static Integer optionalNonNegativeInt(JsonNode node, String field) throws InvalidLine {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return checkedInt(value, field, 0, Integer.MAX_VALUE);
    }

    private static String optionalHash(JsonNode node) throws InvalidLine {
        JsonNode value = node.get("respHash");
        if (value == null || value.isNull()) {
            return null;
        }
        String hash = requiredText(node, "respHash");
        if (!isStorableText(hash, MAX_HASH_LENGTH)) {
            throw new InvalidLine("respHash는 제어 문자가 없는 " + MAX_HASH_LENGTH + "자 이하의 문자열이어야 합니다");
        }
        return hash;
    }

    private static boolean isStorableText(String text, int limit) {
        // utf8mb4 컬럼은 UTF-16 코드 유닛이 아닌 유니코드 문자 개수로 제한한다.
        return text.codePointCount(0, text.length()) <= limit
                && text.codePoints().noneMatch(c -> Character.isISOControl(c)
                        || (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE));
    }

    private static boolean reachesFormatErrorThreshold(int errorLines, int sampleLines) {
        return errorLines * 100 >= sampleLines * FORMAT_ERROR_PERCENT_THRESHOLD;
    }

    private static void addSample(List<LineError> samples, int line, String reason) {
        if (samples.size() < MAX_ERROR_SAMPLES) {
            samples.add(new LineError(line, reason));
        }
    }

    public record ParsedLog(Instant timestamp, String method, String path, int status,
                            Integer latencyMs, Integer size, String responseHash) {
    }

    public record LineError(int line, String reason) {
    }

    public record ParseSummary(int processedLines, int errorLines, List<LineError> errorSamples, boolean invalidFormat) {
        public ParseSummary {
            errorSamples = List.copyOf(errorSamples);
        }
    }

    private static final class InvalidLine extends Exception {
        private InvalidLine(String reason) {
            super(reason, null, false, false);
        }
    }
}
