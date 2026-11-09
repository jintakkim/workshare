package com.jangbokhuman.workshare.activity.cafeteria;

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

    public boolean canChange(LocalDate workDate) {
        return workDate != null && workTime.getInstant().isBefore(getDeadline(workDate));
    }

    public void validateChange(LocalDate workDate) {
        if (!canChange(workDate)) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "지원 변경은 해당 날짜 " + getCutoff() + " 전까지 가능합니다.");
        }
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
