package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.DailyActivityParticipationPolicy;
import com.jangbokhuman.workshare.activity.ParticipationDecision;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class CafeteriaSupportScheduleService {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportRequirementRepository cafeteriaSupportRequirementRepository;
    private final CafeteriaSupportExchangeRequestRepository cafeteriaSupportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final BusinessCalendar businessCalendar;

    public void changeRequiredWorkers(int count) {
        LocalDate date = cafeteriaSupportDeadline.getChangeEffectiveDate();
        CafeteriaSupportRequirement requirement = cafeteriaSupportRequirementRepository.findByEffectiveFrom(date)
                .orElseGet(() -> new CafeteriaSupportRequirement(date, count));
        requirement.change(count);
        cafeteriaSupportRequirementRepository.save(requirement);
    }

    public void saveMonth(YearMonth month, Map<LocalDate, List<Long>> changes) {
        if (changes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 가능한 날짜가 없습니다.");
        }
        Map<LocalDate, List<CafeteriaSupportAssignment>> existingByDate = lockExistingAssignments(changes.keySet());
        Set<Long> employeeIds = new HashSet<>();
        changes.values().forEach(employeeIds::addAll);
        existingByDate.values().forEach(assignments -> assignments.forEach(assignment -> employeeIds.add(assignment.getEmployeeId())));
        lockEmployees(employeeIds);
        for (Map.Entry<LocalDate, List<Long>> change : changes.entrySet()) {
            LocalDate date = change.getKey();
            if (!YearMonth.from(date).equals(month)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 달의 날짜만 변경할 수 있습니다.");
            }
            cafeteriaSupportDeadline.validateChange(date);
            if (!businessCalendar.isWorkingDay(date)) {
                throw new BusinessException(ErrorCode.NOT_ALLOWED, "주말·휴일에는 지원을 등록하지 않습니다.");
            }
            saveDay(date, new HashSet<>(change.getValue()), existingByDate.getOrDefault(date, List.of()));
        }
    }

    private Map<LocalDate, List<CafeteriaSupportAssignment>> lockExistingAssignments(Collection<LocalDate> dates) {
        List<Long> ids = dates.stream()
                .flatMap(date -> cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream())
                .map(CafeteriaSupportAssignment::getId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cafeteriaSupportAssignmentRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.groupingBy(CafeteriaSupportAssignment::getWorkDate));
    }

    private void saveDay(LocalDate date, Set<Long> employeeIds, List<CafeteriaSupportAssignment> existing) {
        CafeteriaSupportAvailability availability = loadAvailability(employeeIds, date, date);
        for (Long employeeId : employeeIds) {
            requireAllowed(evaluateAvailability(availability, date, employeeId, null));
        }
        List<CafeteriaSupportAssignment> removed = existing.stream()
                .filter(assignment -> !employeeIds.contains(assignment.getEmployeeId()))
                .toList();
        if (!removed.isEmpty()) {
            List<Long> removedIds = removed.stream().map(CafeteriaSupportAssignment::getId).toList();
            cafeteriaSupportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(removedIds, removedIds);
            cafeteriaSupportAssignmentRepository.deleteAll(removed);
            cafeteriaSupportAssignmentRepository.flush();
        }
        Set<Long> kept = existing.stream()
                .map(CafeteriaSupportAssignment::getEmployeeId)
                .collect(Collectors.toSet());
        for (Long employeeId : employeeIds) {
            if (!kept.contains(employeeId)) {
                cafeteriaSupportAssignmentRepository.save(new CafeteriaSupportAssignment(date, employeeId));
            }
        }
    }

    public void requireAvailable(LocalDate date, Long employeeId, Long replacingId) {
        requireAllowed(evaluateAvailability(loadAvailability(List.of(employeeId), date, date), date, employeeId, replacingId));
    }

    private void requireAllowed(ParticipationDecision decision) {
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, decision.reason());
        }
    }

    ParticipationDecision evaluateAvailability(
            CafeteriaSupportAvailability availability,
            LocalDate date,
            Long employeeId,
            Long replacingId
    ) {
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
        boolean onLeave = availability.findAttendance(employeeId, date)
                .map(attendance -> !attendance.isFullDayAvailable())
                .orElse(false);
        if (onLeave) {
            return ParticipationDecision.deny(employee.getName() + " 님은 " + date + "에 휴가·반차가 있어 종일 지원할 수 없습니다.");
        }
        boolean duplicate = replacingId != null && availability.getAssignmentsOn(employeeId, date).stream()
                .anyMatch(assignment -> !assignment.getId().equals(replacingId));
        if (duplicate) {
            return ParticipationDecision.deny(employee.getName() + " 님은 해당 날짜에 다른 지원이 등록되어 있습니다.");
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
        Map<Long, Employee> employeesById = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        if (employeesById.isEmpty()) {
            return CafeteriaSupportAvailability.empty();
        }
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

    void lockEmployees(Collection<Long> employeeIds) {
        for (Long employeeId : new TreeSet<>(employeeIds)) {
            employeeRepository.findByIdForUpdate(employeeId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 확인해주세요."));
        }
    }

    @Transactional(readOnly = true)
    public int getRequiredWorkers(LocalDate date) {
        return cafeteriaSupportRequirementRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                .map(CafeteriaSupportRequirement::getRequiredWorkers)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public Month getMonth(YearMonth month) {
        List<Employee> staff = employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();
        Map<LocalDate, Set<Long>> selectedByDate = cafeteriaSupportAssignmentRepository
                .findByWorkDateBetweenOrderByWorkDateAsc(month.atDay(1), month.atEndOfMonth())
                .stream()
                .collect(Collectors.groupingBy(
                        CafeteriaSupportAssignment::getWorkDate,
                        Collectors.mapping(CafeteriaSupportAssignment::getEmployeeId, Collectors.toSet())
                ));
        Set<Long> registeredIds = activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.CAFETERIA_SUPPORT);
        List<CafeteriaSupportRequirement> requirements = cafeteriaSupportRequirementRepository.findAllByOrderByEffectiveFromAsc();
        ArrayList<Day> days = new ArrayList<>();
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            LocalDate date = month.atDay(dayOfMonth);
            Set<Long> selected = selectedByDate.getOrDefault(date, Set.of());
            List<Person> people = staff.stream()
                    .filter(employee -> selected.contains(employee.getId())
                            || dailyActivityParticipationPolicy.evaluateEligibility(employee, date, registeredIds.contains(employee.getId())).allowed())
                    .map(employee -> new Person(
                            employee.getId(),
                            employee.getName(),
                            selected.contains(employee.getId()))
                    )
                    .toList();
            boolean working = businessCalendar.isWorkingDay(date);
            days.add(new Day(
                    date,
                    working,
                    working && cafeteriaSupportDeadline.canChange(date),
                    getRequiredWorkers(requirements, date),
                    staff.stream()
                            .map(Employee::getId)
                            .filter(selected::contains)
                            .toList(),
                    people,
                    staff.stream()
                            .filter(employee -> selected.contains(employee.getId()))
                            .map(Employee::getName)
                            .toList()
            ));
        }
        LocalDate effectiveDate = cafeteriaSupportDeadline.getChangeEffectiveDate();
        return new Month(month, days, cafeteriaSupportDeadline.getCutoff(), effectiveDate, getRequiredWorkers(requirements, effectiveDate));
    }

    private int getRequiredWorkers(List<CafeteriaSupportRequirement> requirements, LocalDate date) {
        int requiredWorkers = 0;
        for (CafeteriaSupportRequirement requirement : requirements) {
            if (requirement.getEffectiveFrom().isAfter(date)) {
                break;
            }
            requiredWorkers = requirement.getRequiredWorkers();
        }
        return requiredWorkers;
    }

    @Transactional(readOnly = true)
    public List<SupportDay> getSchedule(YearMonth month) {
        List<CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository
                .findByWorkDateBetweenOrderByWorkDateAsc(month.atDay(1), month.atEndOfMonth());
        Map<Long, String> employeeNames = employeeRepository.findAllById(
                        assignments.stream().map(CafeteriaSupportAssignment::getEmployeeId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Map<LocalDate, List<String>> namesByDate = new LinkedHashMap<>();
        for (CafeteriaSupportAssignment assignment : assignments) {
            namesByDate.computeIfAbsent(assignment.getWorkDate(), date -> new ArrayList<>())
                    .add(employeeNames.get(assignment.getEmployeeId()));
        }
        return namesByDate.entrySet().stream()
                .map(entry -> new SupportDay(entry.getKey(), entry.getValue().stream().sorted().toList()))
                .toList();
    }

    public record Person(Long id, String name, boolean selected) {}

    public record Day(
            LocalDate date,
            boolean working,
            boolean editable,
            int required,
            List<Long> selected,
            List<Person> people,
            List<String> names
    ) {}

    public record Month(
            YearMonth month,
            List<Day> days,
            LocalTime cutoff,
            LocalDate effectiveDate,
            int required
    ) {}

    public record SupportDay(LocalDate date, List<String> employeeNames) {}
}
