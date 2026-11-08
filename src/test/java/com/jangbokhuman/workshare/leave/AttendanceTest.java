package com.jangbokhuman.workshare.leave;

import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

class AttendanceTest {
    @ParameterizedTest
    @CsvSource({
        "WORK,true,true",
        "MORNING_HALF_DAY,false,true",
        "AFTERNOON_HALF_DAY,true,false",
        "VACATION,false,false",
        "SICK_LEAVE,false,false"
    })
    void attendanceDeterminesTheAvailableShiftAndFullDaySupport(
            Attendance attendance, boolean morning, boolean evening) {
        Worker worker = new Worker(1L, "직원", attendance);
        assertThat(WorkPeriod.MORNING.isEligible(worker)).isEqualTo(morning);
        assertThat(WorkPeriod.EVENING.isEligible(worker)).isEqualTo(evening);
        assertThat(WorkPeriod.UNSPECIFIED.isEligible(worker)).isFalse();
        assertThat(attendance.isAbsent()).isEqualTo(!morning && !evening);
        assertThat(attendance.isFullDayAvailable()).isEqualTo(morning && evening);
        assertThat(attendance.isHalfDay()).isEqualTo(morning != evening);
        assertThat(WorkPeriod.MORNING.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(WorkPeriod.MORNING.getEndTime())
                .isEqualTo(WorkPeriod.EVENING.getStartTime())
                .isEqualTo(LocalTime.of(14, 0));
        assertThat(WorkPeriod.EVENING.getEndTime()).isEqualTo(LocalTime.of(18, 0));
    }
}
