package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.TaskAllocationPolicy;
import com.jangbokhuman.workshare.activity.TaskAssignment;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskService;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@DisplayName("활동별 배정 정책과 공통 업무의 분리")
class ActivityAllocatorFeatureTest extends BusinessFeatureTestSupport {
    @Autowired WorkTime workTime;
    @Autowired BusinessCalendar businessCalendar;
    @Autowired TaskAllocationPolicy taskAllocationPolicy;
    @Autowired RosterReader rosterReader;
    @Autowired PlatformTransactionManager platformTransactionManager;

    @Test
    @DisplayName("전용 배정 정책이 없으면 오전·오후 업무를 만들지 않고 등록된 대기 업무만 배정한다")
    void activitiesWithoutAnAllocatorDoNotReceiveImplicitMorningAndEveningWork() {
        register("홍길동");
        Long taskId = addTask(TODAY, "정리 업무", WorkPeriod.EVENING, 1);
        now("08:50");
        WorkTaskService workTaskServiceWithoutAllocators = new WorkTaskService(
                workTaskRepository, taskAssignmentRepository, businessCalendar, workTime, taskAllocationPolicy, rosterReader,
                assignmentGuard, List.of());
        new TransactionTemplate(platformTransactionManager).executeWithoutResult(status -> {
            workTaskServiceWithoutAllocators.allocateTodayIfDue();
            assertThat(workTaskRepository.findAll()).singleElement().satisfies(task -> {
                assertThat(task.getId()).isEqualTo(taskId);
                assertThat(task.getStatus()).isEqualTo(WorkTask.Status.ASSIGNED);
            });
            assertThat(taskAssignmentRepository.findByTaskIdInOrderByNameAscEmployeeIdAsc(List.of(taskId)))
                    .extracting(TaskAssignment::getEmployeeId)
                    .containsExactly(employeeId("홍길동"));
        });
    }
}
