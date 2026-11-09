package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.DailyAllocationStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WorkTaskAllocationStatus implements DailyAllocationStatus {
    private final WorkTaskRepository workTaskRepository;

    @Override
    public boolean isAllocated(LocalDate workDate) {
        return workTaskRepository.existsByWorkDateAndStatus(workDate, WorkTask.Status.ASSIGNED);
    }
}
