package com.ttladvisor;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트 공용 데이터 헬퍼. 같은 MySQL 컨테이너를 여러 테스트 클래스가 함께 쓰므로,
 * FK 순서(요청 로그 → 프로젝트 → 세션 → 회원)에 맞춰 지워야 다른 클래스가 남긴 데이터에 막히지 않는다.
 */
public final class TestData {

    private TestData() {
    }

    /** 모든 테스트 데이터를 FK 순서대로 지운다. 정책·업로드·분석은 프로젝트를 지우면 CASCADE로 지워진다. */
    public static void deleteAll(JdbcTemplate jdbc) {
        for (String sql : List.of(
                "DELETE FROM request_log",
                "DELETE FROM project",
                "DELETE FROM SPRING_SESSION",
                "DELETE FROM users")) {
            jdbc.update(sql);
        }
    }

    /** 비밀번호 없는 회원을 넣고 ID를 돌려준다 (프로젝트 소유자 FK용). */
    public static long insertUser(JdbcTemplate jdbc, String email) {
        jdbc.update("INSERT INTO users (name, email, created_at) VALUES ('테스터', ?, NOW(3))", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
