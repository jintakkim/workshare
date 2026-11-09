package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.AssignmentGuard;
import com.jangbokhuman.workshare.activity.Duty;
import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.TaskAllocation;
import com.jangbokhuman.workshare.activity.TaskAssignmentRepository;
import com.jangbokhuman.workshare.activity.TaskRequirement;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Locale;

@RequiredArgsConstructor
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SharingShopExtraTaskService {
    private static final SecureRandom TASK_CODE_RANDOM = new SecureRandom();

    private final WorkTaskRepository workTaskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final BusinessCalendar businessCalendar;
    private final WorkTime workTime;
    private final TaskAllocationPolicy taskAllocationPolicy;
    private final RosterReader rosterReader;
    private final AssignmentGuard assignmentGuard;

    public Long addTask(LocalDate date, String name, WorkPeriod period, int requiredWorkers) {
        validateCurrentOrFuture(date);
        if (name == null || name.isBlank() || name.strip().length() > 60) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업무명은 1~60자입니다.");
        }
        if (period == null || period == WorkPeriod.UNSPECIFIED || requiredWorkers < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "추가 업무의 시간대와 필요 인원(1명 이상)을 확인하세요.");
        }
        if (!businessCalendar.isWorkingDay(date)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "주말·휴일에는 업무를 추가할 수 없습니다.");
        }
        boolean assignImmediately = date.equals(workTime.getToday()) && workTime.isAllocationDue();
        TaskRequirement requirement = new TaskRequirement(
                new Duty("EXTRA_" + new BigInteger(128, TASK_CODE_RANDOM).toString(16).toUpperCase(Locale.ROOT), name.strip()),
                period,
                requiredWorkers
        );
        WorkTask task = workTaskRepository.save(WorkTask.create(Activity.SHARING_SHOP, date, requirement, workTime.getInstant()));
        if (assignImmediately) {
            assign(task);
        }
        return task.getId();
    }

    public LocalDate removePendingTask(Long id) {
        WorkTask task = workTaskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "업무가 없습니다."));
        validateCurrentOrFuture(task.getWorkDate());
        if (task.getStatus() != WorkTask.Status.PENDING) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "배정 대기 중인 업무만 취소할 수 있습니다.");
        }
        workTaskRepository.delete(task);
        return task.getWorkDate();
    }

    public void assignPendingTasks(LocalDate workDate) {
        for (WorkTask task : workTaskRepository.findByWorkDateAndActivityCodeAndStatusForUpdate(workDate, Activity.SHARING_SHOP, WorkTask.Status.PENDING)) {
            assign(task);
        }
    }

    private void assign(WorkTask task) {
        TaskAllocation allocation = taskAllocationPolicy.allocate(
                task.getRequirement(),
                rosterReader.getWorkersOn(task.getActivityCode(), task.getWorkDate())
        );
        assignmentGuard.validate(task.getActivityCode(), task.getWorkDate(), task.getPeriod(), allocation.workers());
        taskAssignmentRepository.saveAll(task.assign(allocation, workTime.getInstant()));
    }

    private void validateCurrentOrFuture(LocalDate date) {
        if (date == null || date.isBefore(workTime.getToday())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오늘 이후의 날짜를 선택하세요.");
        }
    }
}
