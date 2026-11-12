package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface ActivityExclusionReader {
    List<ActivityExclusion> getExclusionsOn(Activity activity, LocalDate date);

    // The employee is kept out of the activity all day; awayPeriods says when they are actually elsewhere.
    record ActivityExclusion(Long employeeId, String reasonName, Set<WorkPeriod> awayPeriods) {
        public boolean isAwayDuring(WorkPeriod period) {
            return awayPeriods.contains(period);
        }
    }
}
