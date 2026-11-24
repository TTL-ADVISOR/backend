package com.ttladvisor.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 시각은 항상 밀리초 3자리를 포함한 ISO-8601 UTC로 내보낸다 (예: 2026-11-23T10:00:00.000Z).
 * 기본 Instant 직렬화는 밀리초가 0이면 생략하고, 마이크로초가 있으면 6자리까지 쓰므로 형식이 들쭉날쭉하다.
 */
@Configuration
public class JacksonConfig {

    static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public SimpleModule instantFormatModule() {
        SimpleModule module = new SimpleModule("instant-millis");
        module.addSerializer(Instant.class, new ValueSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(INSTANT_FORMAT.format(value));
            }
        });
        return module;
    }
}
