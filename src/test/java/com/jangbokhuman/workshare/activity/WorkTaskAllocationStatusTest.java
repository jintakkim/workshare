package com.jangbokhuman.workshare.activity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@DisplayName("활동별 당일 배정 완료 여부")
class WorkTaskAllocationStatusTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Test
    @DisplayName("자동 배정이 있는 모든 활동이 그날 배정을 저장해야 완료로 본다")
    void completesOnlyWhenEveryActivityIsAllocated() {
        assertThat(createStatus(true, true).isAllocationComplete(DAY)).isTrue();
        assertThat(createStatus(true, false).isAllocationComplete(DAY)).isFalse();
        assertThat(createStatus().isAllocationComplete(DAY)).isTrue();
    }

    private WorkTaskAllocationStatus createStatus(boolean... allocated) {
        List<DailyActivityAllocator> allocators = new ArrayList<>();
        for (boolean isAllocated : allocated) {
            allocators.add(new FixedAllocator(isAllocated));
        }
        return new WorkTaskAllocationStatus(null, allocators);
    }

    private record FixedAllocator(boolean allocated) implements DailyActivityAllocator {
        @Override
        public Activity getActivity() {
            return Activity.SHARING_SHOP;
        }

        @Override
        public void allocate(LocalDate workDate) {
        }

        @Override
        public boolean isAllocated(LocalDate workDate) {
            return allocated;
        }
    }
}
