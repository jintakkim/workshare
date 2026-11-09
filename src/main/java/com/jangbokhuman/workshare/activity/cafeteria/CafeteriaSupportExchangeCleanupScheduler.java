package com.jangbokhuman.workshare.activity.cafeteria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CafeteriaSupportExchangeCleanupScheduler {
    private final CafeteriaSupportExchangeService cafeteriaSupportExchangeService;

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.zone}")
    public void clean() {
        long count = cafeteriaSupportExchangeService.purgeExpired();
        if (count > 0) {
            log.info("만료된 식당지원 교환 요청 {}건 정리", count);
        }
    }
}
