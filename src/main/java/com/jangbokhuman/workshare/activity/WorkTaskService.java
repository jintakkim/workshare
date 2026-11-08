package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class WorkTaskService {
    private static final SecureRandom TASK_CODE_RANDOM = new SecureRandom();
    private final WorkTaskRepository workTaskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final BusinessCalendar businessCalendar;
    private final WorkTime workTime;
    private final TaskAllocationPolicy taskAllocationPolicy;
    private final RosterReader rosterReader;
    private final AssignmentGuard assignmentGuard;
    private final List<DailyActivityAllocator> dailyActivityAllocators;

    @Transactional
    public void allocateTodayIfDue() {
        LocalDate workDate = workTime.getToday();
        if (!workTime.isAllocationDue() || !businessCalendar.isWorkingDay(workDate)) {
            return;
        }
        for (DailyActivityAllocator allocator : dailyActivityAllocators) {
            allocator.allocate(workDate);
        }
        for (Activity activity : Activity.values()) {
            for (WorkTask task : workTaskRepository.findByWorkDateAndActivityCodeAndStatusForUpdate(workDate, activity, WorkTask.Status.PENDING)) {
                if (task.getStatus() == WorkTask.Status.PENDING) {
                    assign(task);
                }
            }
        }
    }

    public Long addTask(
            String activityCode,
            LocalDate date,
            String name,
            WorkPeriod period,
            int requiredWorkers
    ) {
        requireCurrentOrFuture(date);
        if (name == null || name.isBlank() || name.strip().length() > 60)
            throw new IllegalArgumentException("업무명은 1~60자입니다.");
        if (period == null || period == WorkPeriod.UNSPECIFIED || requiredWorkers < 1) {
            throw new IllegalArgumentException("추가 업무의 시간대와 필요 인원(1명 이상)을 확인하세요.");
        }
        boolean assignImmediately = date.equals(workTime.getToday()) && workTime.isAllocationDue();
        Activity activity = Activity.getByCode(activityCode);
        if (!businessCalendar.isWorkingDay(date))
            throw new IllegalArgumentException("주말·휴일에는 업무를 추가할 수 없습니다.");
        TaskRequirement requirement =
                new TaskRequirement(
                        new Duty(
                                "EXTRA_"
                                        + new BigInteger(128, TASK_CODE_RANDOM)
                                                .toString(16)
                                                .toUpperCase(java.util.Locale.ROOT),
                                name.strip()),
                        period,
                        requiredWorkers);
        WorkTask task = workTaskRepository.save(WorkTask.create(activity, date, requirement, workTime.getInstant()));
        if (assignImmediately) {
            assign(task);
        }
        return task.getId();
    }

    public LocalDate removePendingTask(Long id) {
        WorkTask task =
                workTaskRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalArgumentException("업무가 없습니다."));
        requireCurrentOrFuture(task.getWorkDate());
        if (task.getStatus() != WorkTask.Status.PENDING) {
            throw new IllegalArgumentException("배정 대기 중인 업무만 취소할 수 있습니다.");
        }
        workTaskRepository.delete(task);
        return task.getWorkDate();
    }

    private void assign(WorkTask task) {
        TaskAllocation allocation = taskAllocationPolicy.allocate(
                task.getRequirement(),
                rosterReader.getWorkersOn(task.getActivityCode(), task.getWorkDate()));
        assignmentGuard.validate(task.getActivityCode(), task.getWorkDate(), task.getPeriod(), allocation.workers());
        taskAssignmentRepository.saveAll(task.assign(allocation, workTime.getInstant()));
    }

    private void requireCurrentOrFuture(LocalDate date) {
        if (date == null || date.isBefore(workTime.getToday())) {
            throw new IllegalArgumentException("오늘 이후의 날짜를 선택하세요.");
        }
    }
}
