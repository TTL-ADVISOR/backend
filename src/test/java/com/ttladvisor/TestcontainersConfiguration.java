package com.ttladvisor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * {@code @ServiceConnection}은 application.yml의 datasource URL을 무시하고 컨테이너 URL을 쓴다.
     * 운영과 같은 접속 파라미터를 직접 붙여야 한다. 특히 serverTimezone이 빠지면
     * JDBC 드라이버가 JVM 기본 시간대(KST)로 시각을 변환해 JdbcTemplate 경로만 9시간 어긋난다.
     */
    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                .withUrlParam("serverTimezone", "UTC")
                .withUrlParam("rewriteBatchedStatements", "true");
    }
}
