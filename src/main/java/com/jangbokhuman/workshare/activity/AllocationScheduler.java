package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@RequiredArgsConstructor
@Component
public class AllocationScheduler implements SchedulingConfigurer {
    private final DailyAllocationService dailyAllocationService;
    private final WorkSettings workSettings;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(this::allocate, new CronTrigger(createDailyCron(workSettings.allocationTime()), workSettings.zone()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void allocate() {
        dailyAllocationService.allocateTodayIfDue();
    }

    private static String createDailyCron(LocalTime time) {
        return time.getSecond() + " " + time.getMinute() + " " + time.getHour() + " * * *";
    }
}
