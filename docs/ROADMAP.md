# TTL Advisor — v1 로드맵 (이슈 계획)

GitHub Organization `TTL-ADVISOR`의 Project 보드 **"TTL Advisor v1"** 에서 관리한다.
저장소: `backend`(B-), `frontend`(F-)

## 라벨

| 라벨 | 용도 |
|---|---|
| `type: setup` | 프로젝트 초기 설정 |
| `type: feature` | 기능 구현 |
| `type: test` | 테스트 보강 |
| `type: infra` | Docker, 배포 |
| `type: docs` | 문서 |
| `area: api` / `area: ingest` / `area: analysis` / `area: ui` | 영역 |

## 마일스톤

| 마일스톤 | 목표 |
|---|---|
| M1 기반 구축 | 백엔드·프론트 뼈대, DB 스키마, 프로젝트 CRUD |
| M2 로그 수집 | 업로드 → 파싱 → 저장, 정책 관리 |
| M3 분석 엔진 | TTL 시뮬레이션·추천, 분석 API, 샘플 로그 생성기 |
| M4 리포트 | 대시보드, 곡선 차트, 설정 내보내기 |
| M5 배포·데모 | Docker Compose 전체 실행, README |

## 이슈

### M1 기반 구축
| # | 저장소 | 제목 | 완료 조건 |
|---|---|---|---|
| B1 | backend | [Setup] Spring Boot 프로젝트 초기 설정 | Java 21·Gradle·패키지 구조, `docker-compose.yml`(MySQL), 앱이 MySQL에 붙어 기동 |
| B2 | backend | [DB] Flyway V1 스키마와 JPA 엔티티 | `V1__init.sql`, 엔티티, `ddl-auto=validate` 통과, Testcontainers로 마이그레이션 테스트 |
| B3 | backend | [Common] 공통 예외 처리와 설정 | 에러 응답 `{code,message,details}`, CORS, Async 스레드풀 |
| B4 | backend | [API] 프로젝트 CRUD (API 1-1~1-4) | 4개 API + MockMvc 테스트 |
| F1 | frontend | [Setup] Vite + React + TS 초기 설정 | Router, TanStack Query, API 클라이언트, 에러 타입, Vitest·MSW |
| F2 | frontend | [UI] 프로젝트 목록·생성 화면 | API 1-1, 1-2 연동 |

### M2 로그 수집
| # | 저장소 | 제목 | 완료 조건 |
|---|---|---|---|
| B5 | backend | [Ingest] 경로 정규화 PathNormalizer | `:id`/`:uuid`/`:hash` 규칙, 커스텀 패턴 우선, 쿼리 정렬 캐시 키, 단위 테스트 |
| B6 | backend | [Ingest] JSON Lines 파서 | 필드 검증, 실패 줄 수집(최대 20개), 형식 판별 |
| B7 | backend | [API] 로그 업로드와 비동기 파싱 (API 2-1~2-3) | 100MB 제한, JdbcTemplate 배치 insert, 진행률, 상태 전이 |
| B8 | backend | [API] 엔드포인트 정책 (API 3-1, 3-2) | 일괄 교체, 중복·형식 검증 |
| F3 | frontend | [UI] 로그 업로드 화면 | 드래그앤드롭, 진행률 폴링, 오류 샘플 표시 |
| F4 | frontend | [UI] 패턴·정책 관리 화면 | 정책 편집 후 재분석 유도 |

### M3 분석 엔진
| # | 저장소 | 제목 | 완료 조건 |
|---|---|---|---|
| B9 | backend | [Analysis] TTL 시뮬레이터 | 키별 재생, 쓰기 무효화, stale 판정, 히트율·절감 시간·메모리 계산, 단위 테스트 |
| B10 | backend | [Analysis] TTL 추천기 | staleness·stale 비율 필터, knee 선택, verdict와 근거 문장, 단위 테스트 |
| B11 | backend | [API] 분석 실행·조회 (API 4-1~4-4) | 파라미터 기본값, 비동기 실행, 결과 저장·조회 |
| B12 | backend | [Tool] 샘플 로그 생성기 tools/loggen | Zipf 분포, 엔드포인트별 쓰기 비율·변경 주기 설정, 정답 대비 추천 검증 테스트 |

### M4 리포트
| # | 저장소 | 제목 | 완료 조건 |
|---|---|---|---|
| B13 | backend | [API] 추천 설정 내보내기 (API 4-5) | YAML·JSON |
| F5 | frontend | [UI] 분석 대시보드 | 요약 카드, 엔드포인트 표 정렬·필터, 분석 폴링 |
| F6 | frontend | [UI] 엔드포인트 상세 | 히트율·stale 곡선(Recharts), 추천 지점, staleness 슬라이더 재계산 |
| F7 | frontend | [UI] 설정 내보내기 | 복사·다운로드 |

### M5 배포·데모
| # | 저장소 | 제목 | 완료 조건 |
|---|---|---|---|
| B14 | backend | [Infra] 백엔드 Dockerfile과 전체 docker-compose | `docker compose up`으로 mysql·backend·frontend 실행 |
| F8 | frontend | [Infra] 프론트 Dockerfile과 nginx 설정 | 정적 서빙, `/api` 프록시 |
| B15 | backend | [Docs] README와 데모 시나리오 | 실행 방법, 스크린샷, 시나리오 |

## 작업 방식

1. 보드에서 이슈를 `In Progress`로 옮기고 브랜치 생성: `feat/B1-project-setup`
2. 커밋 메시지에 이슈 번호 포함
3. PR 본문에 `Closes #번호` → 머지되면 이슈 종료, 보드에서 `Done`
