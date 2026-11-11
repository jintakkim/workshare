package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.DailyActivityParticipationPolicy;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CafeteriaSupportQueryService {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportRequirementRepository cafeteriaSupportRequirementRepository;
    private final EmployeeRepository employeeRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final BusinessCalendar businessCalendar;

    public Month getMonth(YearMonth month) {
        List<Employee> staff = employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();
        Map<LocalDate, Set<Long>> assignedIdsByDate = findAssignments(month).stream()
                .collect(Collectors.groupingBy(
                        CafeteriaSupportAssignment::getWorkDate,
                        Collectors.mapping(CafeteriaSupportAssignment::getEmployeeId, Collectors.toSet())
                ));
        Set<Long> registeredIds = activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.CAFETERIA_SUPPORT);
        List<CafeteriaSupportRequirement> requirements = cafeteriaSupportRequirementRepository.findAllByOrderByEffectiveFromAsc();
        Set<LocalDate> workingDays = businessCalendar.findWorkingDays(month.atDay(1), month.atEndOfMonth());
        Map<LocalDate, Set<Long>> onLeaveIdsByDate = findOnLeaveIdsByDate(month);
        List<Day> days = Stream.iterate(month.atDay(1), date -> !date.isAfter(month.atEndOfMonth()), date -> date.plusDays(1))
                .map(date -> createDay(
                        date,
                        staff,
                        assignedIdsByDate.getOrDefault(date, Set.of()),
                        onLeaveIdsByDate.getOrDefault(date, Set.of()),
                        registeredIds,
                        requirements,
                        workingDays.contains(date))
                )
                .toList();
        LocalDate effectiveDate = cafeteriaSupportDeadline.getChangeEffectiveDate();
        return new Month(
                month,
                days,
                cafeteriaSupportDeadline.getCutoff(),
                effectiveDate,
                getRequiredWorkersOn(requirements, effectiveDate)
        );
    }

    public List<ScheduleWeek> getScheduleWeeks(YearMonth month) {
        Map<LocalDate, List<SupportDay>> daysByWeekStart = getSchedule(month).stream()
                .collect(Collectors.groupingBy(
                        day -> day.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
                        TreeMap::new,
                        Collectors.toList()
                ));
        return daysByWeekStart.entrySet().stream()
                .map(entry -> new ScheduleWeek(
                        getLaterDate(entry.getKey(), month.atDay(1)),
                        getEarlierDate(entry.getKey().plusDays(6), month.atEndOfMonth()),
                        entry.getValue())
                )
                .toList();
    }

    private List<SupportDay> getSchedule(YearMonth month) {
        List<CafeteriaSupportAssignment> assignments = findAssignments(month);
        Map<Long, String> namesById = employeeRepository.findAllById(
                        assignments.stream().map(CafeteriaSupportAssignment::getEmployeeId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Map<LocalDate, List<String>> namesByDate = assignments.stream()
                .collect(Collectors.groupingBy(
                        CafeteriaSupportAssignment::getWorkDate,
                        TreeMap::new,
                        Collectors.mapping(assignment -> namesById.get(assignment.getEmployeeId()), Collectors.toList())
                ));
        return namesByDate.entrySet().stream()
                .map(entry -> new SupportDay(entry.getKey(), entry.getValue().stream().sorted().toList()))
                .toList();
    }

    private static LocalDate getLaterDate(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate getEarlierDate(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private List<CafeteriaSupportAssignment> findAssignments(YearMonth month) {
        return cafeteriaSupportAssignmentRepository.findByWorkDateBetweenOrderByWorkDateAsc(month.atDay(1), month.atEndOfMonth());
    }

    private Map<LocalDate, Set<Long>> findOnLeaveIdsByDate(YearMonth month) {
        return leaveRequestRepository.findByWorkDateBetweenAndAttendanceNot(month.atDay(1), month.atEndOfMonth(), Attendance.WORK).stream()
                .filter(leave -> !leave.getAttendance().isFullDayAvailable())
                .collect(Collectors.groupingBy(
                        LeaveRequest::getWorkDate,
                        Collectors.mapping(LeaveRequest::getEmployeeId, Collectors.toSet())
                ));
    }

    private Day createDay(
            LocalDate date,
            List<Employee> staff,
            Set<Long> assignedIds,
            Set<Long> onLeaveIds,
            Set<Long> registeredIds,
            List<CafeteriaSupportRequirement> requirements,
            boolean working
    ) {
        List<Employee> assigned = staff.stream()
                .filter(employee -> assignedIds.contains(employee.getId()))
                .toList();
        List<Person> selectable = staff.stream()
                .filter(employee -> assignedIds.contains(employee.getId()) || canSupportOn(employee, date, registeredIds))
                .map(employee -> new Person(
                        employee.getId(),
                        employee.getName(),
                        assignedIds.contains(employee.getId()))
                )
                .toList();
        return new Day(
                date,
                working,
                working && cafeteriaSupportDeadline.canEditRoster(date),
                getRequiredWorkersOn(requirements, date),
                assigned.stream().map(Employee::getId).toList(),
                selectable,
                assigned.stream().map(Employee::getName).toList(),
                assigned.stream()
                        .filter(employee -> onLeaveIds.contains(employee.getId()))
                        .map(Employee::getName)
                        .toList()
        );
    }

    private boolean canSupportOn(Employee employee, LocalDate date, Set<Long> registeredIds) {
        return dailyActivityParticipationPolicy.evaluateEligibility(employee, date, registeredIds.contains(employee.getId())).allowed();
    }

    private int getRequiredWorkersOn(List<CafeteriaSupportRequirement> requirementsByEffectiveFrom, LocalDate date) {
        return requirementsByEffectiveFrom.stream()
                .filter(requirement -> !requirement.getEffectiveFrom().isAfter(date))
                .reduce((earlier, later) -> later)
                .map(CafeteriaSupportRequirement::getRequiredWorkers)
                .orElse(0);
    }

    public record Person(Long id, String name, boolean selected) {}

    public record Day(
            LocalDate date,
            boolean working,
            boolean editable,
            int required,
            List<Long> selected,
            List<Person> people,
            List<String> names,
            List<String> onLeaveNames
    ) {}

    public record Month(
            YearMonth month,
            List<Day> days,
            LocalTime cutoff,
            LocalDate effectiveDate,
            int required
    ) {}

    public record SupportDay(LocalDate date, List<String> employeeNames) {}

    public record ScheduleWeek(LocalDate start, LocalDate end, List<SupportDay> days) {}
}
