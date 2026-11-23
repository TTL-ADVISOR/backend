-- TTL Advisor v1 초기 스키마
-- 시각 컬럼은 모두 UTC, 밀리초 정밀도(DATETIME(3))

CREATE TABLE project (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '프로젝트 ID',
    name       VARCHAR(100) NOT NULL COMMENT '프로젝트명',
    created_at DATETIME(3)  NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id)
) COMMENT = '프로젝트';

CREATE TABLE endpoint_policy (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '정책 ID',
    project_id        BIGINT       NOT NULL COMMENT '프로젝트 ID',
    method            VARCHAR(10)  NOT NULL COMMENT 'HTTP 메서드',
    pattern           VARCHAR(255) NOT NULL COMMENT '경로 패턴',
    max_staleness_sec INT          NULL COMMENT '허용 지연(초)',
    is_custom_pattern BOOLEAN      NOT NULL COMMENT '커스텀 패턴 여부',
    PRIMARY KEY (id),
    UNIQUE KEY uk_endpoint_policy (project_id, method, pattern),
    CONSTRAINT fk_endpoint_policy_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) COMMENT = '엔드포인트 정책';

CREATE TABLE log_upload (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '업로드 ID',
    project_id      BIGINT        NOT NULL COMMENT '프로젝트 ID',
    filename        VARCHAR(255)  NOT NULL COMMENT '파일명',
    status          VARCHAR(20)   NOT NULL COMMENT '상태 (UPLOADED|PARSING|PARSED|FAILED)',
    processed_lines INT           NOT NULL DEFAULT 0 COMMENT '처리한 줄 수',
    total_lines     INT           NULL COMMENT '전체 줄 수 (파싱 완료 전 NULL)',
    error_lines     INT           NOT NULL DEFAULT 0 COMMENT '실패 줄 수',
    error_samples   JSON          NULL COMMENT '실패 줄 샘플 (최대 20개)',
    failure_reason  VARCHAR(500)  NULL COMMENT '실패 사유',
    range_start     DATETIME(3)   NULL COMMENT '로그 시작 시각',
    range_end       DATETIME(3)   NULL COMMENT '로그 종료 시각',
    created_at      DATETIME(3)   NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_log_upload_project (project_id, created_at),
    CONSTRAINT fk_log_upload_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) COMMENT = '로그 업로드';

-- 대량 테이블. JdbcTemplate 배치 insert/delete로만 다룬다.
-- FK에 CASCADE를 걸지 않아, 요청 로그를 먼저 지우지 않으면 업로드 삭제가 실패한다 (거대한 연쇄 삭제 방지).
CREATE TABLE request_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '요청 로그 ID',
    upload_id    BIGINT       NOT NULL COMMENT '업로드 ID',
    ts           DATETIME(3)  NOT NULL COMMENT '요청 시각(ms)',
    method       VARCHAR(10)  NOT NULL COMMENT 'HTTP 메서드',
    resource_key VARCHAR(512) NOT NULL COMMENT '리소스 키(쿼리 제외 경로)',
    cache_key    VARCHAR(512) NOT NULL COMMENT '캐시 키',
    status_code  SMALLINT     NOT NULL COMMENT '응답 코드',
    latency_ms   INT          NULL COMMENT '응답 시간(ms)',
    resp_size    INT          NULL COMMENT '응답 크기(byte)',
    resp_hash    VARCHAR(64)  NULL COMMENT '응답 해시',
    PRIMARY KEY (id),
    KEY idx_request_log_upload_ts (upload_id, ts),
    CONSTRAINT fk_request_log_upload FOREIGN KEY (upload_id) REFERENCES log_upload (id)
) COMMENT = '요청 로그';

CREATE TABLE analysis (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '분석 ID',
    upload_id      BIGINT       NOT NULL COMMENT '업로드 ID',
    status         VARCHAR(20)  NOT NULL COMMENT '상태 (PENDING|RUNNING|DONE|FAILED)',
    params         JSON         NULL COMMENT '적용된 분석 파라미터',
    failure_reason VARCHAR(500) NULL COMMENT '실패 사유',
    created_at     DATETIME(3)  NOT NULL COMMENT '생성일시',
    finished_at    DATETIME(3)  NULL COMMENT '완료일시',
    PRIMARY KEY (id),
    KEY idx_analysis_upload (upload_id, created_at),
    CONSTRAINT fk_analysis_upload FOREIGN KEY (upload_id) REFERENCES log_upload (id) ON DELETE CASCADE
) COMMENT = '분석';

CREATE TABLE endpoint_result (
    id                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결과 ID',
    analysis_id                BIGINT       NOT NULL COMMENT '분석 ID',
    method                     VARCHAR(10)  NOT NULL COMMENT 'HTTP 메서드',
    pattern                    VARCHAR(255) NOT NULL COMMENT '경로 패턴',
    request_count              INT          NOT NULL COMMENT '요청 수',
    unique_keys                INT          NOT NULL COMMENT '고유 키 수',
    write_ratio                DOUBLE       NOT NULL COMMENT '쓰기 비율',
    avg_latency_ms             DOUBLE       NULL COMMENT '평균 응답 시간(ms)',
    median_change_interval_sec INT          NULL COMMENT '변경 주기 중앙값(초)',
    max_staleness_sec          INT          NULL COMMENT '적용된 허용 지연(초)',
    recommended_ttl_sec        INT          NULL COMMENT '추천 TTL(초)',
    expected_hit_rate          DOUBLE       NULL COMMENT '예상 히트율',
    verdict                    VARCHAR(20)  NOT NULL COMMENT '판정 (CACHE|DONT_CACHE|INSUFFICIENT_DATA)',
    reason                     VARCHAR(500) NULL COMMENT '판정 근거',
    PRIMARY KEY (id),
    CONSTRAINT fk_endpoint_result_analysis FOREIGN KEY (analysis_id) REFERENCES analysis (id) ON DELETE CASCADE
) COMMENT = '엔드포인트 분석 결과';

CREATE TABLE ttl_sim_point (
    id                 BIGINT NOT NULL AUTO_INCREMENT COMMENT '시뮬레이션 지점 ID',
    endpoint_result_id BIGINT NOT NULL COMMENT '결과 ID',
    ttl_sec            INT    NOT NULL COMMENT 'TTL(초)',
    hit_rate           DOUBLE NOT NULL COMMENT '히트율',
    stale_rate         DOUBLE NOT NULL COMMENT 'stale 비율',
    saved_backend_ms   BIGINT NOT NULL COMMENT '절감 백엔드 시간(ms)',
    peak_memory_bytes  BIGINT NOT NULL COMMENT '최대 메모리(byte)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ttl_sim_point (endpoint_result_id, ttl_sec),
    CONSTRAINT fk_ttl_sim_point_result FOREIGN KEY (endpoint_result_id) REFERENCES endpoint_result (id) ON DELETE CASCADE
) COMMENT = 'TTL 시뮬레이션 지점';
