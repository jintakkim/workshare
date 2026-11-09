package com.jangbokhuman.workshare.activity;

import java.time.LocalDate;
import java.util.List;

public interface ActivityExclusionReader {
    List<ActivityExclusion> getExclusionsOn(Activity activity, LocalDate date);

    List<String> getWarnings(Activity activity, LocalDate date);

    record ActivityExclusion(Long employeeId, String reasonName) {}
}
