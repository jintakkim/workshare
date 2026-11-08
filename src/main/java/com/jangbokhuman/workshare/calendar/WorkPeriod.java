package com.jangbokhuman.workshare.calendar;

import com.jangbokhuman.workshare.activity.Worker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor
public enum WorkPeriod {
    MORNING("오전", LocalTime.of(9, 0), LocalTime.of(14, 0)),
    EVENING("오후", LocalTime.of(14, 0), LocalTime.of(18, 0)),
    UNSPECIFIED("기존 업무 · 시간대 미기록", null, null);

    private final String label;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public String getTimeRange() {
        return startTime == null ? "시간대 미기록" : startTime + "~" + endTime;
    }

    public boolean isEligible(Worker worker) {
        return worker.attendance().canWork(this);
    }
}
