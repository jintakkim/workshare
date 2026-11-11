package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.ScheduleRecordWindow;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class CafeteriaSupportDeadline {
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final ScheduleRecordWindow scheduleRecordWindow;

    public boolean canChange(LocalDate workDate) {
        return workDate != null && workTime.getInstant().isBefore(getDeadline(workDate));
    }

    public void validateChange(LocalDate workDate) {
        if (!canChange(workDate)) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "지원 변경은 해당 날짜 " + getCutoff() + " 전까지 가능합니다.");
        }
    }

    // Admins keep the roster true to who actually went: closed only between the cutoff and the saved draw.
    public boolean canEditRoster(LocalDate workDate) {
        return canChange(workDate) || (workDate != null && scheduleRecordWindow.isSettled(workDate));
    }

    public void validateRosterEdit(LocalDate workDate) {
        if (canEditRoster(workDate)) {
            return;
        }
        if (workDate != null && workDate.equals(workTime.getToday())) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "오늘 식당지원표는 " + getCutoff() + "에 마감됐어요. 오늘 배정이 저장되면 실제로 간 사람으로 다시 고칠 수 있어요.");
        }
        validateChange(workDate);
    }

    public LocalDate getChangeEffectiveDate() {
        LocalDate today = workTime.getToday();
        return canChange(today) ? today : today.plusDays(1);
    }

    public Instant getDeadline(LocalDate workDate) {
        return workDate.atTime(getCutoff()).atZone(workSettings.zone()).toInstant();
    }

    public LocalTime getCutoff() {
        return workSettings.cafeteriaSupportCutoff();
    }
}
