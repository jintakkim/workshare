package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Duty;
import com.jangbokhuman.workshare.activity.TaskAllocation;
import com.jangbokhuman.workshare.activity.TaskAssignment;
import com.jangbokhuman.workshare.activity.TaskRequirement;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class RandomTaskAllocationPolicyTest {
    private final TaskAllocationPolicy taskAllocationPolicy =
            new RandomTaskAllocationPolicy(new Random(123));
    private final List<Worker> workerList =
            List.of(
                    new Worker(1L, "반차", Attendance.AFTERNOON_HALF_DAY),
                    new Worker(2L, "정상", Attendance.WORK),
                    new Worker(3L, "휴가", Attendance.VACATION),
                    new Worker(4L, "병가", Attendance.SICK_LEAVE),
                    new Worker(5L, "오전 반차", Attendance.MORNING_HALF_DAY));

    @Test
    void morningAndEveningSelectOnlyStaffAvailableDuringThatPeriod() {
        TaskAllocation morning =
                taskAllocationPolicy.allocate(
                        new TaskRequirement(new Duty("PREP", "준비"), WorkPeriod.MORNING, 2),
                        workerList);
        assertThat(morning.workers()).extracting(Worker::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(morning.warnings()).isEmpty();
        TaskAllocation evening =
                taskAllocationPolicy.allocate(
                        new TaskRequirement(new Duty("CLEANUP", "정리"), WorkPeriod.EVENING, 3),
                        workerList);
        assertThat(evening.workers()).extracting(Worker::id).containsExactlyInAnyOrder(2L, 5L);
        assertThat(evening.warnings()).hasSize(1);
    }

    @Test
    void confirmedTasksCreateAssignmentsAndRejectRedraws() {
        TaskRequirement requirement =
                new TaskRequirement(new Duty("PREP", "준비"), WorkPeriod.MORNING, 1);
        WorkTask task =
                WorkTask.create(
                        Activity.SHARING_SHOP,
                        LocalDate.of(2026, 11, 4),
                        requirement,
                        Instant.EPOCH);
        List<TaskAssignment> assignments = task.assign(
                taskAllocationPolicy.allocate(requirement, workerList),
                Instant.EPOCH.plusSeconds(1));
        assertThat(task.getStatus()).isEqualTo(WorkTask.Status.ASSIGNED);
        assertThat(assignments).singleElement().satisfies(assignment -> {
            assertThat(assignment.getTask()).isSameAs(task);
        });
        assertThatThrownBy(
                        () ->
                                task.assign(
                                        taskAllocationPolicy.allocate(requirement, workerList),
                                        Instant.EPOCH.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aggregateRejectsInvalidAssignmentsEvenIfAnAllocationPolicyIsWrong() {
        WorkTask task =
                WorkTask.create(
                        Activity.SHARING_SHOP,
                        LocalDate.of(2026, 11, 4),
                        new TaskRequirement(new Duty("CLEANUP", "정리"), WorkPeriod.EVENING, 1),
                        Instant.EPOCH);
        assertThatThrownBy(
                        () ->
                                task.assign(
                                        new TaskAllocation(
                                                List.of(workerList.getFirst()), List.of()),
                                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(task.getStatus()).isEqualTo(WorkTask.Status.PENDING);
        assertThat(task.getAssignedAt()).isNull();
    }
}
