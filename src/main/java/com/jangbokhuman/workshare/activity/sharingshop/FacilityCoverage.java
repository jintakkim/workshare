package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

public record FacilityCoverage(int morningOnDuty, int eveningOnDuty) {
    boolean hasShortage(WorkPeriod period, int assignedWorkers) {
        int onDuty = period == WorkPeriod.MORNING ? morningOnDuty : eveningOnDuty;
        return onDuty <= assignedWorkers;
    }
}
