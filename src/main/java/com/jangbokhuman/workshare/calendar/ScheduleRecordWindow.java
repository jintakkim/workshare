package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Changes before a day's cutoff feed that day's draw. Once the day is settled (past, or every activity saved
// today's draw), later changes only correct the record and never run the draw again.
@Component
@RequiredArgsConstructor
public class ScheduleRecordWindow {
    private final WorkTime workTime;
    private final DailyAllocationStatus dailyAllocationStatus;

    public boolean isSettled(LocalDate workDate) {
        LocalDate today = workTime.getToday();
        if (workDate.isBefore(today)) {
            return true;
        }
        return workDate.equals(today) && dailyAllocationStatus.isAllocationComplete(today);
    }

    public boolean canChangeLeaveOn(LocalDate workDate) {
        return workTime.isBeforeScheduleChangeDeadline(workDate) || isSettled(workDate);
    }
}
