package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class SupportScheduleDeadline {
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final WorkTaskRepository workTaskRepository;

    public boolean canChangeSupportSchedule(ExclusionReason reason, LocalDate workDate) {
        if (workDate == null || !workTime.getInstant().isBefore(getDeadline(reason, workDate))) {
            return false;
        }
        return reason == null || reason.isCafeteriaSupport()
                || !workTaskRepository.existsByWorkDateAndStatus(workDate, WorkTask.Status.ASSIGNED);
    }

    public void validateSupportScheduleChange(ExclusionReason reason, LocalDate workDate) {
        if (!canChangeSupportSchedule(reason, workDate)) {
            throw new IllegalArgumentException("지원 변경은 해당 날짜 " + getCutoff(reason) + " 전까지 가능합니다.");
        }
    }

    public LocalDate getSupportChangeEffectiveDate(ExclusionReason reason) {
        LocalDate today = workTime.getToday();
        return canChangeSupportSchedule(reason, today) ? today : today.plusDays(1);
    }

    public Instant getDeadline(ExclusionReason reason, LocalDate workDate) {
        return workDate.atTime(getCutoff(reason)).atZone(workSettings.zone()).toInstant();
    }

    public LocalTime getCutoff(ExclusionReason reason) {
        return reason != null && reason.isCafeteriaSupport()
                ? workSettings.cafeteriaSupportCutoff()
                : workSettings.allocationTime();
    }
}
