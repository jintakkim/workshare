package com.jangbokhuman.workshare.activity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActivityConfiguration {
    @Bean
    TaskAllocationPolicy createTaskAllocationPolicy() {
        return new RandomTaskAllocationPolicy();
    }
}
