package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public record ShiftAllocation(
        List<Assignment> assignments,
        List<String> morningWarnings,
        List<String> eveningWarnings
) {

    public List<String> warnings() {
        return Stream.concat(morningWarnings.stream(), eveningWarnings.stream()).toList();
    }

    public List<String> getWarnings(WorkPeriod period) {
        return period == WorkPeriod.MORNING ? morningWarnings : eveningWarnings;
    }

    public record Assignment(SharingShopWorker worker, Set<WorkPeriod> periods) {
        public boolean isPreferenceMatched() {
            return switch (worker.preference()) {
                case ANY -> false;
                case MORNING -> periods.contains(WorkPeriod.MORNING);
                case EVENING -> periods.contains(WorkPeriod.EVENING);
            };
        }
    }
}
