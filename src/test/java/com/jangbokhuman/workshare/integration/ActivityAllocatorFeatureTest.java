package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("활동별 배정 정책과 공통 업무의 분리")
class ActivityAllocatorFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("전용 배정기가 없으면 오전·오후 업무를 만들지 않고 대기 업무도 추첨하지 않는다")
    void activitiesWithoutAnAllocatorReceiveNoAutomaticWork() {
        register("홍길동");
        Long taskId = addTask(TODAY, "정리 업무", WorkPeriod.EVENING, 1);
        now("08:50");
        WorkTaskService workTaskServiceWithoutAllocators = new WorkTaskService(List.of());
        workTaskServiceWithoutAllocators.allocate(Activity.SHARING_SHOP, TODAY);
        assertThat(workTaskRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getId()).isEqualTo(taskId);
            assertThat(task.getStatus()).isEqualTo(WorkTask.Status.PENDING);
        });
        assertThat(taskAssignmentRepository.findByTaskIdInOrderByNameAscEmployeeIdAsc(List.of(taskId))).isEmpty();
    }
}
