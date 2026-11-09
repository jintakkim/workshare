package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationDeleted;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.EmployeeServicePeriodChanged;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class CafeteriaSupportRosterCleaner {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportExchangeRequestRepository cafeteriaSupportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final WorkTime workTime;

    @EventListener
    public void removeAssignmentsOnRegistrationDeleted(ActivityRegistrationDeleted event) {
        if (event.activity() == Activity.CAFETERIA_SUPPORT) {
            removeChangeableAssignments(event.employeeId(), assignment -> true);
        }
    }

    @EventListener
    public void removeAssignmentsOutsideServicePeriod(EmployeeServicePeriodChanged event) {
        employeeRepository.findById(event.employeeId())
                .ifPresent(employee -> removeChangeableAssignments(employee.getId(), assignment -> isOutsideServicePeriod(employee, assignment)));
    }

    private boolean isOutsideServicePeriod(Employee employee, CafeteriaSupportAssignment assignment) {
        return !employee.isActiveOn(assignment.getWorkDate());
    }

    private void removeChangeableAssignments(Long employeeId, Predicate<CafeteriaSupportAssignment> removable) {
        List<CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository
                .findByEmployeeIdAndWorkDateGreaterThanEqual(employeeId, workTime.getToday())
                .stream()
                .filter(assignment -> cafeteriaSupportDeadline.canChange(assignment.getWorkDate()))
                .filter(removable)
                .toList();
        if (assignments.isEmpty()) {
            return;
        }
        List<Long> assignmentIds = assignments.stream()
                .map(CafeteriaSupportAssignment::getId)
                .toList();
        cafeteriaSupportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(assignmentIds, assignmentIds);
        cafeteriaSupportAssignmentRepository.deleteAll(assignments);
    }
}
