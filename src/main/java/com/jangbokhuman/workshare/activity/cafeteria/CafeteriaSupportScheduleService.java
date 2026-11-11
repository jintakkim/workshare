package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CafeteriaSupportScheduleService {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportRequirementRepository cafeteriaSupportRequirementRepository;
    private final CafeteriaSupportExchangeRequestRepository cafeteriaSupportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CafeteriaSupportAvailabilityChecker cafeteriaSupportAvailabilityChecker;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final BusinessCalendar businessCalendar;

    public void changeRequiredWorkers(int requiredWorkers) {
        LocalDate effectiveFrom = cafeteriaSupportDeadline.getChangeEffectiveDate();
        CafeteriaSupportRequirement requirement = cafeteriaSupportRequirementRepository.findByEffectiveFrom(effectiveFrom)
                .orElseGet(() -> new CafeteriaSupportRequirement(effectiveFrom, requiredWorkers));
        requirement.change(requiredWorkers);
        cafeteriaSupportRequirementRepository.save(requirement);
    }

    public void saveMonth(YearMonth month, Map<LocalDate, List<Long>> rosterByDate) {
        if (rosterByDate.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 가능한 날짜가 없습니다.");
        }
        Set<LocalDate> workingDays = businessCalendar.findWorkingDays(month.atDay(1), month.atEndOfMonth());
        rosterByDate.keySet().forEach(date -> validateEditableDate(month, date, workingDays));
        Map<LocalDate, List<CafeteriaSupportAssignment>> currentAssignmentsByDate = lockCurrentAssignments(rosterByDate.keySet());
        lockRosterEmployees(rosterByDate.values(), currentAssignmentsByDate.values());
        rosterByDate.forEach((date, employeeIds) -> replaceDailyRoster(
                date,
                Set.copyOf(employeeIds),
                currentAssignmentsByDate.getOrDefault(date, List.of())
        ));
    }

    private void validateEditableDate(YearMonth month, LocalDate date, Set<LocalDate> workingDays) {
        if (!YearMonth.from(date).equals(month)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 달의 날짜만 변경할 수 있습니다.");
        }
        cafeteriaSupportDeadline.validateRosterEdit(date);
        if (!workingDays.contains(date)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "주말·휴일에는 지원을 등록하지 않습니다.");
        }
    }

    private Map<LocalDate, List<CafeteriaSupportAssignment>> lockCurrentAssignments(Collection<LocalDate> dates) {
        return cafeteriaSupportAssignmentRepository.findByWorkDateInForUpdate(dates).stream()
                .collect(Collectors.groupingBy(CafeteriaSupportAssignment::getWorkDate));
    }

    private void lockRosterEmployees(
            Collection<List<Long>> selectedEmployeeIds,
            Collection<List<CafeteriaSupportAssignment>> currentAssignments
    ) {
        Set<Long> employeeIds = new HashSet<>();
        selectedEmployeeIds.forEach(employeeIds::addAll);
        currentAssignments.forEach(assignments -> assignments.forEach(assignment -> employeeIds.add(assignment.getEmployeeId())));
        if (!employeeIds.isEmpty()) {
            employeeRepository.findAllByIdForUpdate(employeeIds);
        }
    }

    private void replaceDailyRoster(
            LocalDate date,
            Set<Long> selectedEmployeeIds,
            List<CafeteriaSupportAssignment> currentAssignments
    ) {
        cafeteriaSupportAvailabilityChecker.validateRosterAvailability(date, selectedEmployeeIds);
        removeUnselectedAssignments(currentAssignments, selectedEmployeeIds);
        addNewAssignments(date, selectedEmployeeIds, currentAssignments);
    }

    private void removeUnselectedAssignments(List<CafeteriaSupportAssignment> currentAssignments, Set<Long> selectedEmployeeIds) {
        List<CafeteriaSupportAssignment> unselectedAssignments = currentAssignments.stream()
                .filter(assignment -> !selectedEmployeeIds.contains(assignment.getEmployeeId()))
                .toList();
        if (unselectedAssignments.isEmpty()) {
            return;
        }
        List<Long> unselectedIds = unselectedAssignments.stream()
                .map(CafeteriaSupportAssignment::getId)
                .toList();
        cafeteriaSupportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(unselectedIds, unselectedIds);
        cafeteriaSupportAssignmentRepository.deleteAll(unselectedAssignments);
    }

    private void addNewAssignments(
            LocalDate date,
            Set<Long> selectedEmployeeIds,
            List<CafeteriaSupportAssignment> currentAssignments
    ) {
        Set<Long> currentEmployeeIds = currentAssignments.stream()
                .map(CafeteriaSupportAssignment::getEmployeeId)
                .collect(Collectors.toSet());
        selectedEmployeeIds.stream()
                .filter(employeeId -> !currentEmployeeIds.contains(employeeId))
                .forEach(employeeId -> cafeteriaSupportAssignmentRepository.save(new CafeteriaSupportAssignment(date, employeeId)));
    }
}
