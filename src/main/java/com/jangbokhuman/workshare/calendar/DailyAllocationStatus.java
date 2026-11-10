package com.jangbokhuman.workshare.calendar;

import java.time.LocalDate;

public interface DailyAllocationStatus {
    boolean hasAnyAllocation(LocalDate workDate);

    boolean isAllocationComplete(LocalDate workDate);
}
