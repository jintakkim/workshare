package com.jangbokhuman.workshare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfiguration {
    @Bean
    Clock createClock(WorkSettings workSettings) {
        return Clock.system(workSettings.zone());
    }
}
