package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.CalendarMonth;
import com.jangbokhuman.workshare.calendar.Holiday;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        CalendarMonth calendarMonth = CalendarMonth.of(selectedDate);
        LocalDate today = workTime.getToday();
        Set<LocalDate> holidays = findHolidays(calendarMonth);
        Map<LocalDate, List<StaffLeave>> leavesByDate = findActiveLeavesByDate(calendarMonth);
        List<Day> days = calendarMonth.getGridDates().stream()
                .map(date -> new Day(
                        date,
                        calendarMonth.contains(date),
                        date.equals(selectedDate),
                        date.equals(today),
                        BusinessCalendar.isWeekend(date) || holidays.contains(date),
                        leavesByDate.getOrDefault(date, List.of()).size())
                )
                .toList();
        return new Month(
                selectedDate,
                calendarMonth.month(),
                days,
                getSortedLeaves(leavesByDate, selectedDate),
                calendarMonth.getPreviousMonthStart(),
                calendarMonth.getNextMonthStart()
        );
    }

    private Set<LocalDate> findHolidays(CalendarMonth calendarMonth) {
        return holidayRepository.findByDateBetweenOrderByDate(calendarMonth.getGridStart(), calendarMonth.getGridEnd()).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
    }

    private Map<LocalDate, List<StaffLeave>> findActiveLeavesByDate(CalendarMonth calendarMonth) {
        List<LeaveRequest> leaveRequests = leaveRequestRepository.findByWorkDateBetweenAndAttendanceNot(
                calendarMonth.getGridStart(),
                calendarMonth.getGridEnd(),
                Attendance.WORK
        );
        Map<Long, Employee> employeesById = findEmployees(leaveRequests);
        return leaveRequests.stream()
                .filter(leaveRequest -> isActiveOnLeaveDate(employeesById.get(leaveRequest.getEmployeeId()), leaveRequest))
                .collect(Collectors.groupingBy(
                        LeaveRequest::getWorkDate,
                        Collectors.mapping(
                                leaveRequest -> new StaffLeave(employeesById.get(leaveRequest.getEmployeeId()).getName(), leaveRequest.getAttendance()),
                                Collectors.toCollection(ArrayList::new)
                        )
                ));
    }

    private Map<Long, Employee> findEmployees(List<LeaveRequest> leaveRequests) {
        List<Long> employeeIds = leaveRequests.stream()
                .map(LeaveRequest::getEmployeeId)
                .distinct()
                .toList();
        return employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private boolean isActiveOnLeaveDate(Employee employee, LeaveRequest leaveRequest) {
        return employee != null && employee.isActiveOn(leaveRequest.getWorkDate());
    }

    private List<StaffLeave> getSortedLeaves(Map<LocalDate, List<StaffLeave>> leavesByDate, LocalDate date) {
        return leavesByDate.getOrDefault(date, List.of()).stream()
                .sorted(Comparator.comparing(StaffLeave::employeeName))
                .toList();
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
