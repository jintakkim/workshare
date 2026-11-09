package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Attendance {
    WORK("정상 출근", false, false),
    MORNING_HALF_DAY("오전 반차", true, false),
    AFTERNOON_HALF_DAY("오후 반차", false, true),
    VACATION("연차", true, true),
    SICK_LEAVE("병가", true, true);

    private final String label;
    private final boolean morningAbsent;
    private final boolean afternoonAbsent;

    public boolean isAbsent() {
        return morningAbsent && afternoonAbsent;
    }

    public boolean isHalfDay() {
        return morningAbsent != afternoonAbsent;
    }

    public boolean isFullDayAvailable() {
        return !morningAbsent && !afternoonAbsent;
    }

    public boolean canWork(WorkPeriod period) {
        return switch (period) {
            case MORNING -> !morningAbsent;
            case EVENING -> !afternoonAbsent;
            case UNSPECIFIED -> false;
        };
    }
}
