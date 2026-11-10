package com.jangbokhuman.workshare.employee;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServicePaySettings.class)
public class EmployeeConfiguration {
}
