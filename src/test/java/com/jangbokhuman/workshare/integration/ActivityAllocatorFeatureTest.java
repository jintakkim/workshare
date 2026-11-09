package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.WorkTaskService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("활동별 배정 정책과 공통 업무의 분리")
class ActivityAllocatorFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("전용 배정기가 없는 활동은 업무를 만들거나 배정하지 않는다")
    void activitiesWithoutAnAllocatorReceiveNoAutomaticWork() {
        register("홍길동");
        now("08:50");
        WorkTaskService workTaskServiceWithoutAllocators = new WorkTaskService(List.of());
        workTaskServiceWithoutAllocators.allocate(Activity.SHARING_SHOP, TODAY);
        assertThat(workTaskRepository.findAll()).isEmpty();
        assertThat(taskAssignmentRepository.findAll()).isEmpty();
    }
}
