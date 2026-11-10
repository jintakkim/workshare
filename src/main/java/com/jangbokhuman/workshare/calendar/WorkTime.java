package com.jangbokhuman.workshare.calendar;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.*;

@RequiredArgsConstructor
@Component
public class WorkTime {
    private final Clock clock;
    private final WorkSettings workSettings;

    public LocalDateTime getNow() {
        return LocalDateTime.ofInstant(clock.instant(), workSettings.zone());
    }

    public Instant getInstant() {
        return clock.instant();
    }

    public ZoneId getZone() {
        return workSettings.zone();
    }

    public LocalDate getToday() {
        return getNow().toLocalDate();
    }

    public boolean isBeforeScheduleChangeDeadline(LocalDate workDate) {
        return getNow().isBefore(workDate.atTime(workSettings.leaveCutoff()));
    }

    public LocalDate getRosterChangeEffectiveDate() {
        return isBeforeScheduleChangeDeadline(getToday()) ? getToday() : getToday().plusDays(1);
    }

    public boolean isAllocationDue() {
        return !getNow().toLocalTime().isBefore(workSettings.allocationTime());
    }
}
