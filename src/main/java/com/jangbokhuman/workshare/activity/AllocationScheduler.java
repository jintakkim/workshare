package com.jangbokhuman.workshare.activity;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AllocationScheduler {
    private final DailyAllocationService dailyAllocationService;

    @Scheduled(cron = "${app.allocation-cron}", zone = "${app.zone}")
    @EventListener(ApplicationReadyEvent.class)
    public void allocate() {
        dailyAllocationService.allocateTodayIfDue();
    }
}
