package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.DailyAllocationStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkTaskAllocationStatus implements DailyAllocationStatus {
    private final WorkTaskRepository workTaskRepository;
    private final List<DailyActivityAllocator> dailyActivityAllocators;

    @Override
    public boolean hasAnyAllocation(LocalDate workDate) {
        return workTaskRepository.existsByWorkDate(workDate);
    }

    @Override
    public boolean isAllocationComplete(LocalDate workDate) {
        return dailyActivityAllocators.stream()
                .allMatch(allocator -> allocator.isAllocated(workDate));
    }
}
