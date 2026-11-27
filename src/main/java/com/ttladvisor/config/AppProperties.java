package com.ttladvisor.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 {@code app.*} 설정.
 */
@ConfigurationProperties("app")
public record AppProperties(@DefaultValue Cors cors, @DefaultValue Async async, @DefaultValue Upload upload) {

    /** @param allowedOrigins 프론트 개발 서버 등 허용할 Origin 목록 */
    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /**
     * 업로드 파싱·분석용 작업 스레드풀.
     *
     * @param corePoolSize  기본 스레드 수
     * @param maxPoolSize   큐가 찼을 때 늘릴 최대 스레드 수
     * @param queueCapacity 대기 작업 수. 넘치면 요청을 503으로 거절한다
     */
    /**
     * 로그 업로드 처리.
     *
     * @param maxLines           업로드 한 번에 허용하는 줄 수. 넘으면 FAILED
     * @param insertBatchSize    request_log 배치 insert 크기
     * @param progressEveryLines 이 줄 수마다 processed_lines를 갱신한다 (진행률)
     */
    public record Upload(@DefaultValue("1000000") int maxLines,
                         @DefaultValue("1000") int insertBatchSize,
                         @DefaultValue("10000") int progressEveryLines) {
    }

    public record Async(@DefaultValue("2") int corePoolSize,
                        @DefaultValue("4") int maxPoolSize,
                        @DefaultValue("50") int queueCapacity) {
    }
}
