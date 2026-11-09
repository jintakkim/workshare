package com.jangbokhuman.workshare.activity;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class WorkTaskService {
    private final List<DailyActivityAllocator> dailyActivityAllocators;

    public void allocate(Activity activity, LocalDate workDate) {
        for (DailyActivityAllocator allocator : dailyActivityAllocators) {
            if (allocator.getActivity() == activity) {
                allocator.allocate(workDate);
            }
        }
    }
}
