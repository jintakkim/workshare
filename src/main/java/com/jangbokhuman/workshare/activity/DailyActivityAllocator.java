package com.jangbokhuman.workshare.activity;

import java.time.LocalDate;

public interface DailyActivityAllocator {
    Activity getActivity();

    void allocate(LocalDate workDate);

    boolean isAllocated(LocalDate workDate);
}
