# TTL Advisor — Backend

API 액세스 로그를 분석해 엔드포인트별 Redis TTL을 추천하는 서비스의 API 서버입니다.

- 설계: [docs/DESIGN.md](docs/DESIGN.md)
- API 명세: [docs/API.md](docs/API.md)
- 로드맵: [docs/ROADMAP.md](docs/ROADMAP.md)

## Stack

Java 21 · Spring Boot 4.1 · Gradle 9 · MySQL 8.4 · Flyway · Testcontainers · Docker

## 로컬 실행

필요: JDK 21, Docker

```bash
docker compose up -d --wait mysql   # MySQL 8.4 (localhost:3306, ttl/ttl)
./gradlew bootRun                   # http://localhost:8080
```

DB 접속 정보는 환경 변수 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`로 바꿀 수 있습니다.

## 테스트

```bash
./gradlew test   # Testcontainers가 MySQL 컨테이너를 띄우므로 Docker 실행 필요
```
