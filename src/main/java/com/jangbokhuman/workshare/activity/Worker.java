package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;

public record Worker(Long id, String name, Attendance attendance) {
    public boolean canWork(WorkPeriod period) {
        return attendance.canWork(period);
    }
}
