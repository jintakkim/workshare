package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.DailyActivityParticipationPolicy;
import com.jangbokhuman.workshare.activity.ParticipationDecision;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CafeteriaSupportAvailabilityChecker {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;

    public void validateRosterAvailability(LocalDate date, Collection<Long> employeeIds) {
        CafeteriaSupportAvailability availability = loadAvailability(employeeIds, date, date);
        for (Long employeeId : employeeIds) {
            requireAllowed(evaluateRosterAvailability(availability, date, employeeId));
        }
    }

    public void validateExchangeAvailability(LocalDate date, Long employeeId, Long replacedAssignmentId) {
        CafeteriaSupportAvailability availability = loadAvailability(List.of(employeeId), date, date);
        requireAllowed(evaluateExchangeAvailability(availability, date, employeeId, replacedAssignmentId));
    }

    ParticipationDecision evaluateRosterAvailability(CafeteriaSupportAvailability availability, LocalDate date, Long employeeId) {
        Optional<Employee> found = availability.findEmployee(employeeId);
        if (found.isEmpty()) {
            return ParticipationDecision.deny("직원을 확인해주세요.");
        }
        Employee employee = found.get();
        ParticipationDecision eligibility = dailyActivityParticipationPolicy.evaluateEligibility(
                employee,
                date,
                availability.isRegistered(employeeId)
        );
        if (!eligibility.allowed()) {
            return eligibility;
        }
        if (isOnLeave(availability, employeeId, date)) {
            return ParticipationDecision.deny(employee.getName() + " 님은 " + date + "에 휴가·반차가 있어 종일 지원할 수 없습니다.");
        }
        return ParticipationDecision.allow();
    }

    ParticipationDecision evaluateExchangeAvailability(
            CafeteriaSupportAvailability availability,
            LocalDate date,
            Long employeeId,
            Long replacedAssignmentId
    ) {
        ParticipationDecision rosterAvailability = evaluateRosterAvailability(availability, date, employeeId);
        if (!rosterAvailability.allowed()) {
            return rosterAvailability;
        }
        boolean alreadySupporting = availability.getAssignmentsOn(employeeId, date).stream()
                .anyMatch(assignment -> !assignment.getId().equals(replacedAssignmentId));
        if (alreadySupporting) {
            String name = availability.findEmployee(employeeId).map(Employee::getName).orElse("직원");
            return ParticipationDecision.deny(name + " 님은 해당 날짜에 다른 지원이 등록되어 있습니다.");
        }
        return ParticipationDecision.allow();
    }

    CafeteriaSupportAvailability loadAvailability(Collection<Long> employeeIds, LocalDate start, LocalDate end) {
        if (employeeIds.isEmpty()) {
            return CafeteriaSupportAvailability.empty();
        }
        return loadAvailability(employeeRepository.findAllById(employeeIds), start, end);
    }

    CafeteriaSupportAvailability loadAvailability(List<Employee> employees, LocalDate start, LocalDate end) {
        if (employees.isEmpty()) {
            return CafeteriaSupportAvailability.empty();
        }
        Map<Long, Employee> employeesById = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Set<Long> employeeIds = employeesById.keySet();
        Map<Long, List<LeaveRequest>> leaves = leaveRequestRepository.findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end)
                .stream()
                .collect(Collectors.groupingBy(LeaveRequest::getEmployeeId));
        Map<Long, List<CafeteriaSupportAssignment>> assignments = cafeteriaSupportAssignmentRepository
                .findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end)
                .stream()
                .collect(Collectors.groupingBy(CafeteriaSupportAssignment::getEmployeeId));
        return new CafeteriaSupportAvailability(
                employeesById,
                activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.CAFETERIA_SUPPORT),
                leaves,
                assignments
        );
    }

    private boolean isOnLeave(CafeteriaSupportAvailability availability, Long employeeId, LocalDate date) {
        return availability.findAttendance(employeeId, date)
                .map(attendance -> !attendance.isFullDayAvailable())
                .orElse(false);
    }

    private void requireAllowed(ParticipationDecision decision) {
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, decision.reason());
        }
    }
}
