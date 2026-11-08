package com.jangbokhuman.workshare;

import com.jangbokhuman.workshare.config.WorkSettings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WorkSettings.class)
public class WorkShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkShareApplication.class, args);
    }
}
