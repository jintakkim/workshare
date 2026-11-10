package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Leave before the cutoff feeds the daily draw. Once the day's draw is saved, later leave is only a record,
// so today reopens; past days stay open for the same reason.
@Component
@RequiredArgsConstructor
public class LeaveChangeWindow {
    private final WorkTime workTime;
    private final DailyAllocationStatus dailyAllocationStatus;

    public boolean canChangeLeaveOn(LocalDate workDate) {
        LocalDate today = workTime.getToday();
        if (workDate.isBefore(today) || workTime.isBeforeScheduleChangeDeadline(workDate)) {
            return true;
        }
        return workDate.equals(today) && dailyAllocationStatus.isAllocationComplete(today);
    }
}
