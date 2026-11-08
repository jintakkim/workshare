package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.AssignmentGuard;
import com.jangbokhuman.workshare.activity.DailyActivityAllocator;
import com.jangbokhuman.workshare.activity.Duty;
import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.TaskAllocation;
import com.jangbokhuman.workshare.activity.TaskAssignment;
import com.jangbokhuman.workshare.activity.TaskAssignmentRepository;
import com.jangbokhuman.workshare.activity.TaskRequirement;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional
public class SharingShopDailyAllocator implements DailyActivityAllocator {
    private final WorkTaskRepository workTaskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final RosterReader rosterReader;
    private final AllocationPolicy allocationPolicy;
    private final SharingShopStaffing sharingShopStaffing;
    private final AssignmentGuard assignmentGuard;
    private final WorkTime workTime;

    @Override
    public Activity getActivity() {
        return Activity.SHARING_SHOP;
    }

    @Override
    public void allocate(LocalDate workDate) {
        Activity activity = getActivity();
        long existingShiftCount = workTaskRepository.countByWorkDateAndActivityCodeAndCodeIn(
                workDate,
                activity,
                List.of(WorkPeriod.MORNING.name(), WorkPeriod.EVENING.name())
        );
        if (existingShiftCount == 1) {
            throw new IllegalStateException("오전·오후 배정 기록의 무결성을 확인하세요.");
        }
        if (existingShiftCount == 2) {
            return;
        }
        List<Worker> workers = rosterReader.getWorkersOn(activity, workDate);
        SharingShopStaffing.Roster roster = sharingShopStaffing.getRoster(workDate, workers);
        Allocation allocation = allocationPolicy.allocate(roster.workers(), roster.quota(), roster.facilityCoverage());
        for (WorkPeriod period : List.of(WorkPeriod.MORNING, WorkPeriod.EVENING)) {
            List<Worker> selected = allocation.assignments().stream()
                    .filter(assignment -> assignment.periods().contains(period))
                    .map(Allocation.Assignment::worker)
                    .map(SharingShopWorker::toWorker)
                    .toList();
            assignmentGuard.validate(activity, workDate, period, selected);
            TaskRequirement requirement = new TaskRequirement(
                    new Duty(period.name(), period.getLabel() + " 업무"), period, selected.size());
            WorkTask task = WorkTask.create(activity, workDate, requirement, workTime.getInstant());
            List<TaskAssignment> assignments = task.assign(
                    new TaskAllocation(selected, allocation.getWarnings(period)), workTime.getInstant());
            workTaskRepository.save(task);
            taskAssignmentRepository.saveAll(assignments);
        }
    }
}
