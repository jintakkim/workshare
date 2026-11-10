package com.jangbokhuman.workshare.employee;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Map;

@ConfigurationProperties("app.service-pay")
public record ServicePaySettings(Map<ServiceRank, Integer> monthlyPay, int dailyMeal) {
    public ServicePaySettings {
        if (monthlyPay == null || !monthlyPay.keySet().containsAll(EnumSet.allOf(ServiceRank.class))) {
            throw new IllegalArgumentException("app.service-pay.monthly-pay에 모든 계급의 월 보수를 지정해야 합니다.");
        }
        if (dailyMeal < 0) {
            throw new IllegalArgumentException("app.service-pay.daily-meal은 0 이상이어야 합니다.");
        }
    }

    public int getMonthlyPay(ServiceRank rank) {
        return monthlyPay.get(rank);
    }
}
