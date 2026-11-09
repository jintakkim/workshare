package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.TaskAssignment;
import com.jangbokhuman.workshare.activity.TaskAssignmentRepository;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.calendar.WorkPeriod;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SharingShopTaskReader {
    private final WorkTaskRepository workTaskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;

    public List<TaskView> getTasks(LocalDate date) {
        List<WorkTask> tasks = workTaskRepository.findByWorkDateAndActivityCodeOrderByCreatedAtAscCodeAsc(date, Activity.SHARING_SHOP);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TaskAssignment>> assignmentsByTaskId = taskAssignmentRepository.findByTaskIdInOrderByNameAscEmployeeIdAsc(
                        tasks.stream().map(WorkTask::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getTask().getId()));
        return tasks.stream()
                .map(task -> createTaskView(task, assignmentsByTaskId.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    private TaskView createTaskView(WorkTask task, List<TaskAssignment> assignments) {
        List<AssigneeView> assignees = assignments.stream()
                .map(assignment -> new AssigneeView(
                        assignment.getEmployeeId(),
                        assignment.getName(),
                        assignment.getAttendance())
                )
                .toList();
        return new TaskView(
                task.getCode(),
                task.getName(),
                task.getPeriod(),
                task.getRequiredWorkers(),
                assignees,
                task.getWarnings(),
                task.getAssignedAt()
        );
    }

    public record AssigneeView(Long employeeId, String name, Attendance attendance) {}

    public record TaskView(
            String code,
            String name,
            WorkPeriod period,
            int requiredWorkers,
            List<AssigneeView> assignees,
            List<String> warnings,
            Instant assignedAt
    ) {}
}
