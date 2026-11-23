package com.ttladvisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TtlAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TtlAdvisorApplication.class, args);
    }
}
