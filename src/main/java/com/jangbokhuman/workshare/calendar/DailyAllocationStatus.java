package com.jangbokhuman.workshare.calendar;

import java.time.LocalDate;

public interface DailyAllocationStatus {
    boolean isAllocated(LocalDate workDate);
}
