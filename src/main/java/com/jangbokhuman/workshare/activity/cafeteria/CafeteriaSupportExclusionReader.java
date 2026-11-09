package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityExclusionReader;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CafeteriaSupportExclusionReader implements ActivityExclusionReader {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportRequirementRepository cafeteriaSupportRequirementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public List<ActivityExclusion> getExclusionsOn(Activity activity, LocalDate date) {
        if (!isExcludedBy(activity)) {
            return List.of();
        }
        return cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream()
                .map(assignment -> new ActivityExclusion(assignment.getEmployeeId(), Activity.CAFETERIA_SUPPORT.getName()))
                .toList();
    }

    @Override
    public List<String> getWarnings(Activity activity, LocalDate date) {
        if (!isExcludedBy(activity)) {
            return List.of();
        }
        List<CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository.findByWorkDate(date);
        List<String> warnings = new ArrayList<>();
        getHeadcountWarning(date, assignments.size()).ifPresent(warnings::add);
        warnings.addAll(getLeaveConflictWarnings(date, assignments));
        return warnings;
    }

    private Optional<String> getHeadcountWarning(LocalDate date, int assignedWorkers) {
        int requiredWorkers = cafeteriaSupportRequirementRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                .map(CafeteriaSupportRequirement::getRequiredWorkers)
                .orElse(0);
        if (assignedWorkers == requiredWorkers) {
            return Optional.empty();
        }
        return Optional.of(Activity.CAFETERIA_SUPPORT.getName() + " 표: 필요 " + requiredWorkers + "명 / 등록 " + assignedWorkers + "명입니다. 지원표를 확인해주세요.");
    }

    private List<String> getLeaveConflictWarnings(LocalDate date, List<CafeteriaSupportAssignment> assignments) {
        Map<Long, LeaveRequest> leavesByEmployeeId = leaveRequestRepository.findByWorkDate(date).stream()
                .filter(leave -> !leave.getAttendance().isFullDayAvailable())
                .collect(Collectors.toMap(LeaveRequest::getEmployeeId, Function.identity()));
        List<Long> conflictedIds = assignments.stream()
                .map(CafeteriaSupportAssignment::getEmployeeId)
                .filter(leavesByEmployeeId::containsKey)
                .toList();
        if (conflictedIds.isEmpty()) {
            return List.of();
        }
        return employeeRepository.findAllById(conflictedIds).stream()
                .sorted(Comparator.comparing(Employee::getName))
                .map(employee -> Activity.CAFETERIA_SUPPORT.getName() + " 명단의 " + employee.getName() + " 님은 "
                        + leavesByEmployeeId.get(employee.getId()).getAttendance().getLabel() + "입니다. 지원표를 조정해주세요.")
                .toList();
    }

    private boolean isExcludedBy(Activity activity) {
        return activity.getExcludingActivities().contains(Activity.CAFETERIA_SUPPORT);
    }
}
