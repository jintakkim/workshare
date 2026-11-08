package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 지원 일정과 필요 인원을 배정 전에 변경할 수 있는지 검증한다. */
@Component
@RequiredArgsConstructor
public class AllocationInputChangeValidator {
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final WorkTaskRepository workTaskRepository;

    public boolean canChangeAllocationInputs(LocalDate workDate) {
        return workDate != null
                && workTime.getNow().isBefore(workDate.atTime(workSettings.allocationTime()))
                && !workTaskRepository.existsByWorkDateAndStatus(workDate, WorkTask.Status.ASSIGNED);
    }

    public void validateAllocationInputChange(LocalDate workDate) {
        if (!canChangeAllocationInputs(workDate)) {
            throw new IllegalArgumentException("지원 변경은 해당 날짜 08:50 전까지 가능합니다. 확정된 배정은 변경할 수 없습니다.");
        }
    }

    public LocalDate getAllocationInputChangeEffectiveDate() {
        return canChangeAllocationInputs(workTime.getToday())
                ? workTime.getToday()
                : workTime.getToday().plusDays(1);
    }
}
