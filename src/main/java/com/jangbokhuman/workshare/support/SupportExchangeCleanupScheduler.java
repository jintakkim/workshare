package com.jangbokhuman.workshare.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportExchangeCleanupScheduler {
    private final SupportExchangeService supportExchangeService;

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.zone}")
    public void clean() {
        long count = supportExchangeService.purgeExpired();
        if (count > 0) log.info("만료된 지원 교환 요청 {}건 정리", count);
    }
}
