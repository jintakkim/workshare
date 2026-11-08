package com.jangbokhuman.workshare.calendar;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** 휴가·휴일 변경의 마감 시각을 검증한다. */
@RequiredArgsConstructor
@Service
public class WorkScheduleDeadlineValidator {
    private final WorkTime workTime;
    private final WorkSettings workSettings;

    public void validateChangeDeadline(LocalDate workDate) {
        if (workDate == null) throw new IllegalArgumentException("날짜를 선택하세요.");
        if (!workTime.isBeforeScheduleChangeDeadline(workDate)) {
            throw new IllegalArgumentException(
                    "해당 날짜의 근무 일정 변경이 마감되었습니다. 마감 시각은 " + workSettings.leaveCutoff() + "입니다.");
        }
    }
}
