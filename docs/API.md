# TTL Advisor — API 명세 (v1)

## 공통 규칙

| 항목 | 규칙 |
|---|---|
| Base URL | `/api` |
| 형식 | 요청·응답 모두 `application/json` (업로드만 `multipart/form-data`) |
| 필드 이름 | camelCase |
| 시각 | ISO-8601 UTC, 밀리초 포함 (`2026-11-23T10:00:00.123Z`) |
| 비율 | 0~1 사이 소수 (`0.87` = 87%) |
| 시간 단위 | 필드 이름에 단위 표기 (`ttlSec`, `latencyMs`) |
| 인증 | v1 없음 |
| 응답 래핑 | 하지 않음. 성공 시 리소스를 그대로 반환 |

### 비동기 작업

업로드 파싱과 분석은 오래 걸리므로 `202 Accepted`로 즉시 응답하고, 클라이언트는 상태 조회 API를 폴링한다 (권장 간격 1초).

```
업로드:  UPLOADED → PARSING → PARSED
                          └→ FAILED
분석:    PENDING → RUNNING → DONE
                         └→ FAILED
```

### 에러 응답

```json
{
  "code": "UPLOAD_NOT_PARSED",
  "message": "파싱이 끝난 업로드만 분석할 수 있습니다.",
  "details": { "uploadId": 12, "status": "PARSING" }
}
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 값 누락, 형식 오류 (`details`에 필드별 사유) |
| 404 | `PROJECT_NOT_FOUND`, `UPLOAD_NOT_FOUND`, `ANALYSIS_NOT_FOUND`, `ENDPOINT_RESULT_NOT_FOUND` | 리소스 없음 |
| 409 | `UPLOAD_NOT_PARSED` | 파싱이 끝나지 않은 업로드로 분석 요청 |
| 409 | `ANALYSIS_NOT_DONE` | 완료되지 않은 분석의 결과·내보내기 요청 |
| 409 | `RESOURCE_BUSY` | 파싱·분석이 진행 중인 프로젝트 삭제 요청 |
| 413 | `FILE_TOO_LARGE` | 100MB 초과 |
| 415 | `UNSUPPORTED_FORMAT` | 확장자가 `.jsonl`, `.log`, `.txt`가 아님 (내용 검사는 하지 않음) |
| 404 | `NOT_FOUND` | 존재하지 않는 경로 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 500 | `INTERNAL_ERROR` | 서버 오류 (내부 메시지는 노출하지 않음) |
| 503 | `SERVER_BUSY` | 파싱·분석 작업 큐가 가득 참. 잠시 후 재시도 |

`details`는 추가 정보가 없으면 빈 객체 `{}`다.

> 파일 **내용**의 형식 오류와 100만 줄 초과는 업로드 요청 시점에 알 수 없으므로 HTTP 에러가 아니라,
> 업로드 상태를 `FAILED`로 바꾸고 `failureReason`에 사유를 담는다 (2-3 참고).

---

## 1. 프로젝트

### 1-1. 프로젝트 생성

`POST /api/projects`

```json
{ "name": "상품 API" }
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| name | string | O | 1~100자 |

**201 Created** (`Location: /api/projects/1`)

```json
{ "id": 1, "name": "상품 API", "createdAt": "2026-11-23T10:00:00.000Z" }
```

### 1-2. 프로젝트 목록

`GET /api/projects`

**200 OK** — 최근 생성 순

```json
[
  {
    "id": 1,
    "name": "상품 API",
    "createdAt": "2026-11-23T10:00:00.000Z",
    "uploadCount": 3,
    "latestAnalysisId": 7
  }
]
```

### 1-3. 프로젝트 단건 조회

`GET /api/projects/{projectId}`

**200 OK** — 1-2의 항목과 같은 형식

### 1-4. 프로젝트 삭제

`DELETE /api/projects/{projectId}`

**204 No Content** — 하위 정책·업로드·요청 로그·분석 결과까지 모두 삭제 (요청 로그는 서버에서 배치 삭제)

**409** `RESOURCE_BUSY` — 상태가 `PARSING`인 업로드나 `PENDING`/`RUNNING`인 분석이 있을 때

---

## 2. 로그 업로드

### 2-1. 로그 업로드

`POST /api/projects/{projectId}/uploads` (`multipart/form-data`)

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| file | file | O | JSON Lines (`.jsonl`, `.log`, `.txt`), 최대 100MB / 100만 줄 |

각 줄 형식은 [DESIGN.md 4장](DESIGN.md#4-입력-로그-포맷-json-lines) 참고.

**202 Accepted** (`Location: /api/uploads/12`)

```json
{ "id": 12, "status": "UPLOADED" }
```

### 2-2. 업로드 목록

`GET /api/projects/{projectId}/uploads`

**200 OK** — 최근 순, 2-3에서 `errorSamples`를 뺀 형식의 배열

### 2-3. 업로드 상태 조회

`GET /api/uploads/{uploadId}`

**200 OK**

```json
{
  "id": 12,
  "projectId": 1,
  "filename": "access-1123.jsonl",
  "status": "PARSED",
  "processedLines": 400000,
  "totalLines": 400000,
  "errorLines": 1790,
  "rangeStart": "2026-11-22T00:00:00.012Z",
  "rangeEnd": "2026-11-22T23:59:59.870Z",
  "errorSamples": [
    { "line": 1532, "reason": "ts 필드 누락" },
    { "line": 20811, "reason": "JSON 파싱 실패" }
  ],
  "failureReason": null,
  "createdAt": "2026-11-23T10:01:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| processedLines | 지금까지 처리한 줄 수 (진행률 표시용) |
| totalLines | 파싱 완료 전에는 `null` |
| errorSamples | 실패한 줄 최대 20개 |
| failureReason | `FAILED`일 때 사유. 아래 표 참고 |

| 실패 조건 | failureReason 예시 |
|---|---|
| 처음 1,000줄 중 90% 이상 파싱 실패 | "JSON Lines 형식이 아닙니다" |
| 100만 줄 초과 | "최대 1,000,000줄까지 업로드할 수 있습니다" |
| 유효한 GET 요청이 0건 | "분석할 GET 요청이 없습니다" |

`FAILED`가 되면 그때까지 저장된 요청 로그는 삭제한다.

---

## 3. 엔드포인트 정책

자동으로 묶인 패턴을 덮어쓰는 커스텀 패턴과, 패턴별 허용 staleness를 관리한다.
정책은 분석 실행 시점에 **스냅샷**으로 저장되어 그 분석에만 적용된다. 바꾼 뒤에는 재분석해야 결과에 반영된다.
패턴은 분석할 때 계산하므로, 커스텀 패턴을 바꾸고 재분석하면 다시 업로드할 필요가 없다.

### 3-1. 정책 목록

`GET /api/projects/{projectId}/policies`

**200 OK**

```json
[
  { "id": 3, "method": "GET", "pattern": "/products/:id", "maxStalenessSec": 60, "isCustomPattern": false },
  { "id": 4, "method": "GET", "pattern": "/posts/:slug", "maxStalenessSec": null, "isCustomPattern": true }
]
```

### 3-2. 정책 일괄 저장

`PUT /api/projects/{projectId}/policies`

요청 배열로 프로젝트의 정책 전체를 **교체**한다.

```json
[
  { "method": "GET", "pattern": "/products/:id", "maxStalenessSec": 60, "isCustomPattern": false },
  { "method": "GET", "pattern": "/posts/:slug", "maxStalenessSec": null, "isCustomPattern": true }
]
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| method | string | O | `GET` (v1은 GET만 캐시 대상) |
| pattern | string | O | `/`로 시작, 변수는 `:이름` 형식, `(method, pattern)` 중복 불가 |
| maxStalenessSec | int \| null | - | 1 이상. `null`이면 분석 기본값 사용 |
| isCustomPattern | boolean | O | `true`면 경로 정규화 시 자동 규칙보다 우선 |

**200 OK** — 저장된 정책 목록 (3-1과 같은 형식)

---

## 4. 분석

### 4-1. 분석 실행

`POST /api/uploads/{uploadId}/analyses`

본문은 생략 가능하며, 생략한 필드는 기본값을 쓴다.

```json
{
  "candidateTtlsSec": [1, 5, 10, 30, 60, 120, 300, 600, 1800, 3600, 21600, 86400],
  "defaultMaxStalenessSec": null,
  "maxStaleRate": 0.01,
  "kneeRatio": 0.9,
  "minRequestCount": 100,
  "minHitRate": 0.1,
  "invalidateOnWrite": true
}
```

| 필드 | 기본값 | 설명 |
|---|---|---|
| candidateTtlsSec | 위 12개 | 시뮬레이션할 TTL 후보 (오름차순, 1~604800). 로그 기간의 절반을 넘는 값은 서버가 제외 |
| defaultMaxStalenessSec | `null` (제한 없음) | 정책에 값이 없는 패턴에 적용할 허용 staleness |
| maxStaleRate | 0.01 | 허용 stale 비율 상한 |
| kneeRatio | 0.9 | 최대 히트율의 몇 %에 도달하면 추천할지 |
| minRequestCount | 100 | 이보다 요청이 적으면 `INSUFFICIENT_DATA` |
| minHitRate | 0.1 | 최대 히트율이 이보다 낮으면 `DONT_CACHE` |
| invalidateOnWrite | true | 같은 리소스에 쓰기 요청이 오면 캐시 무효화로 간주 |

**202 Accepted** (`Location: /api/analyses/7`)

```json
{ "id": 7, "status": "PENDING" }
```

**409** `UPLOAD_NOT_PARSED` — 업로드 상태가 `PARSED`가 아닐 때

### 4-2. 업로드의 분석 목록

`GET /api/uploads/{uploadId}/analyses`

**200 OK** — 최근 순, 4-3에서 `endpoints`를 뺀 형식의 배열

### 4-3. 분석 결과 조회 (대시보드)

`GET /api/analyses/{analysisId}`

**200 OK**

```json
{
  "id": 7,
  "uploadId": 12,
  "status": "DONE",
  "params": {
    "candidateTtlsSec": [1, 5, 10, 30, 60, 120, 300, 600, 1800, 3600],
    "excludedTtlsSec": [21600, 86400],
    "maxStaleRate": 0.01,
    "kneeRatio": 0.9,
    "policies": [
      { "method": "GET", "pattern": "/products/:id", "maxStalenessSec": 60, "isCustomPattern": false }
    ]
  },
  "createdAt": "2026-11-23T10:05:00.000Z",
  "finishedAt": "2026-11-23T10:05:04.210Z",
  "failureReason": null,
  "summary": {
    "endpointCount": 4,
    "cacheCount": 2,
    "dontCacheCount": 1,
    "insufficientDataCount": 1,
    "totalRequests": 335120,
    "expectedSavedBackendMs": 8120400
  },
  "endpoints": [
    {
      "id": 31,
      "method": "GET",
      "pattern": "/products/:id",
      "requestCount": 210000,
      "uniqueKeys": 5400,
      "writeRatio": 0.02,
      "avgLatencyMs": 48.2,
      "recommendedTtlSec": 300,
      "expectedHitRate": 0.91,
      "verdict": "CACHE"
    },
    {
      "id": 32,
      "method": "GET",
      "pattern": "/products/:id/stock",
      "requestCount": 80000,
      "uniqueKeys": 5400,
      "writeRatio": 0.35,
      "avgLatencyMs": 12.0,
      "recommendedTtlSec": null,
      "expectedHitRate": 0.08,
      "verdict": "DONT_CACHE"
    }
  ]
}
```

- `params`는 실제 적용된 값. `excludedTtlsSec`는 로그 기간이 짧아 제외된 후보, `policies`는 실행 시점 정책 스냅샷
- `status`가 `DONE`이 아니면 `summary`와 `endpoints`는 `null`
- `endpoints`는 요청 수 내림차순. 정렬·필터는 클라이언트에서 처리
- `verdict`: `CACHE` | `DONT_CACHE` | `INSUFFICIENT_DATA`

### 4-4. 엔드포인트 상세 (곡선 데이터)

`GET /api/analyses/{analysisId}/endpoints/{resultId}`

**200 OK**

```json
{
  "id": 31,
  "method": "GET",
  "pattern": "/products/:id",
  "requestCount": 210000,
  "uniqueKeys": 5400,
  "writeRatio": 0.02,
  "avgLatencyMs": 48.2,
  "medianChangeIntervalSec": 640,
  "maxStalenessSec": null,
  "recommendedTtlSec": 300,
  "expectedHitRate": 0.91,
  "verdict": "CACHE",
  "reason": "TTL 300초에서 최대 히트율(0.93)의 90%에 도달, stale 0.4%",
  "points": [
    { "ttlSec": 1,   "hitRate": 0.12, "staleRate": 0.0,   "savedBackendMs": 1209000, "peakMemoryBytes": 210000 },
    { "ttlSec": 60,  "hitRate": 0.85, "staleRate": 0.001, "savedBackendMs": 8604000, "peakMemoryBytes": 4100000 },
    { "ttlSec": 300, "hitRate": 0.91, "staleRate": 0.004, "savedBackendMs": 9211000, "peakMemoryBytes": 9800000 }
  ]
}
```

- `maxStalenessSec`: 이 분석에 실제로 적용된 값 (정책 스냅샷 또는 기본값, `endpoint_result.max_staleness_sec`)
- `resultId`가 해당 `analysisId`에 속하지 않으면 404 `ENDPOINT_RESULT_NOT_FOUND`
- `points`는 `ttlSec` 오름차순. 프론트의 staleness 슬라이더는 이 배열로 추천값을 다시 계산한다

### 4-5. 추천 설정 내보내기

`GET /api/analyses/{analysisId}/export?format=yaml`

| 쿼리 | 값 | 기본값 |
|---|---|---|
| format | `yaml` \| `json` | `yaml` |
| verdict | `CACHE` \| `ALL` | `CACHE` |

**200 OK** (`Content-Type: application/x-yaml` 또는 `application/json`)

```yaml
# TTL Advisor analysis #7 (upload #12)
cache:
  ttl:
    "GET /products/:id": 300
    "GET /categories": 3600
```

`verdict=ALL`이면 캐시하지 않을 엔드포인트는 값 없이 주석으로 남긴다.

```yaml
cache:
  ttl:
    "GET /products/:id": 300
    # "GET /products/:id/stock": DONT_CACHE (최대 히트율 8%, 쓰기 비율 35%)
    # "GET /admin/report": INSUFFICIENT_DATA (요청 120건)
```

JSON에서는 `{"GET /products/:id/stock": null}`로 내보내고 `_reasons` 객체에 사유를 담는다.

**409** `ANALYSIS_NOT_DONE`

---

## 부록. 화면별 사용 API

| 화면 | 호출 API |
|---|---|
| 프로젝트 목록 / 생성 | 1-2, 1-1 |
| 로그 업로드 | 2-1 → 2-3 폴링 |
| 업로드 이력 | 2-2, 4-2 |
| 분석 대시보드 | 4-1 → 4-3 폴링 |
| 엔드포인트 상세 | 4-4 |
| 패턴·정책 관리 | 3-1, 3-2 → 4-1 (재분석) |
| 설정 내보내기 | 4-5 |
