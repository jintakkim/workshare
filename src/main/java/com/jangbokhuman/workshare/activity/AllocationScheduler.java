package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;

@RequiredArgsConstructor
@Component
public class AllocationScheduler implements SchedulingConfigurer {
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

    private final DailyAllocationService dailyAllocationService;
    private final WorkSettings workSettings;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(this::allocate, new CronTrigger(createDailyCron(workSettings.allocationTime()), workSettings.zone()));
        // A failed draw is retried every minute; allocating again does nothing once the day is saved.
        taskRegistrar.addFixedDelayTask(new IntervalTask(this::allocate, RETRY_INTERVAL, RETRY_INTERVAL));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void allocate() {
        dailyAllocationService.allocateTodayIfDue();
    }

    private static String createDailyCron(LocalTime time) {
        return time.getSecond() + " " + time.getMinute() + " " + time.getHour() + " * * *";
    }
}
