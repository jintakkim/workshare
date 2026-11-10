package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Attendance {
    WORK("정상 출근", false, false),
    MORNING_HALF_DAY("오전 반차", true, false),
    AFTERNOON_HALF_DAY("오후 반차", false, true),
    VACATION("연차", true, true),
    SICK_LEAVE("병가", true, true),
    SPECIAL_LEAVE("특별휴가", true, true),
    TRAINING("교육·훈련", true, true);

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

    public boolean isMealPaid() {
        return !morningAbsent;
    }

    public boolean isTransportPaid() {
        return !isAbsent();
    }

    public boolean isEmployeeSelectable() {
        return this != TRAINING;
    }

    public static List<Attendance> getEmployeeChoices() {
        return Arrays.stream(values())
                .filter(Attendance::isEmployeeSelectable)
                .toList();
    }

    public boolean canWork(WorkPeriod period) {
        return switch (period) {
            case MORNING -> !morningAbsent;
            case EVENING -> !afternoonAbsent;
            case UNSPECIFIED -> false;
        };
    }
}
