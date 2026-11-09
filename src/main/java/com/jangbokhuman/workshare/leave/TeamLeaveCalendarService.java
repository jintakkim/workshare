package com.jangbokhuman.workshare.leave;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.Holiday;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamLeaveCalendarService {
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final WorkTime workTime;

    public Month getCalendar(LocalDate selectedDate) {
        LocalDate today = workTime.getToday();
        YearMonth month = YearMonth.from(selectedDate);
        LocalDate start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        Set<LocalDate> holidays = holidayRepository.findByDateBetweenOrderByDate(start, end).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
        List<LeaveRequest> leaveRequests = leaveRequestRepository.findByWorkDateBetweenAndAttendanceNot(
                start, end, Attendance.WORK);
        List<Long> employeeIds = leaveRequests.stream().map(LeaveRequest::getEmployeeId).distinct().toList();
        Map<Long, Employee> employees = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<LocalDate, List<StaffLeave>> leavesByDate = new HashMap<>();
        for (LeaveRequest leaveRequest : leaveRequests) {
            Employee employee = employees.get(leaveRequest.getEmployeeId());
            LocalDate date = leaveRequest.getWorkDate();
            if (employee != null && employee.isActiveOn(date)) {
                leavesByDate.computeIfAbsent(date, ignored -> new ArrayList<>())
                        .add(new StaffLeave(employee.getName(), leaveRequest.getAttendance()));
            }
        }

        List<Day> days = start.datesUntil(end.plusDays(1))
                .map(date -> new Day(
                        date,
                        YearMonth.from(date).equals(month),
                        date.equals(selectedDate),
                        date.equals(today),
                        BusinessCalendar.isWeekend(date) || holidays.contains(date),
                        leavesByDate.getOrDefault(date, List.of()).size())
                )
                .toList();
        List<StaffLeave> selectedLeaves = leavesByDate.getOrDefault(selectedDate, List.of()).stream()
                .sorted(Comparator.comparing(StaffLeave::employeeName))
                .toList();
        return new Month(
                selectedDate, month, days, selectedLeaves, month.minusMonths(1).atDay(1), month.plusMonths(1).atDay(1)
        );
    }

    public record Day(LocalDate date, boolean inMonth, boolean selected, boolean today, boolean off, int leaveCount) {}

    public record StaffLeave(String employeeName, Attendance attendance) {}

    public record Month(
            LocalDate selectedDate,
            YearMonth month,
            List<Day> days,
            List<StaffLeave> selectedLeaves,
            LocalDate previousMonth,
            LocalDate nextMonth
    ) {}
}
