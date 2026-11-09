package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyAllocationService {
    private final WorkTaskService workTaskService;
    private final WorkTime workTime;
    private final BusinessCalendar businessCalendar;

    public void allocateTodayIfDue() {
        LocalDate workDate = workTime.getToday();
        if (!workTime.isAllocationDue() || !businessCalendar.isWorkingDay(workDate)) {
            return;
        }
        for (Activity activity : Activity.values()) {
            try {
                workTaskService.allocate(activity, workDate);
            } catch (RuntimeException error) {
                log.error("{} {} 자동 배정에 실패했습니다.", workDate, activity.getName(), error);
            }
        }
    }
}
