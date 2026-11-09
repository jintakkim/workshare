package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.CalendarMonth;
import com.jangbokhuman.workshare.calendar.Holiday;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.WorkSettings;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveCalendarService {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final BusinessCalendar businessCalendar;
    private final WorkTime workTime;
    private final WorkSettings workSettings;

    public LeaveCalendar getCalendar(LocalDate selectedDate, Long employeeId) {
        CalendarMonth calendarMonth = CalendarMonth.of(selectedDate);
        Optional<Employee> employee = employeeId == null ? Optional.empty() : employeeRepository.findById(employeeId);
        Map<LocalDate, String> holidayNames = findHolidayNames(calendarMonth);
        Map<LocalDate, Attendance> attendanceByDate = employee
                .map(found -> findAttendanceByDate(found.getId(), calendarMonth))
                .orElse(Map.of());
        LocalDate today = workTime.getToday();
        List<Cell> cells = calendarMonth.getGridDates().stream()
                .map(date -> new Cell(
                        date,
                        calendarMonth.contains(date),
                        date.equals(today),
                        date.equals(selectedDate),
                        BusinessCalendar.isWeekend(date) || holidayNames.containsKey(date),
                        holidayNames.get(date),
                        attendanceByDate.get(date))
                )
                .toList();
        boolean businessDay = businessCalendar.isWorkingDay(selectedDate);
        return new LeaveCalendar(
                selectedDate,
                calendarMonth.month(),
                cells,
                employee.map(Employee::getName).orElse(null),
                employee.map(Employee::getId).orElse(null),
                attendanceByDate.getOrDefault(selectedDate, Attendance.WORK),
                businessDay,
                businessDay && canChangeLeave(employee, selectedDate),
                workSettings.leaveCutoff(),
                holidayNames.get(selectedDate),
                calendarMonth.getPreviousMonthStart(),
                calendarMonth.getNextMonthStart()
        );
    }

    private Map<LocalDate, String> findHolidayNames(CalendarMonth calendarMonth) {
        return holidayRepository.findByDateBetweenOrderByDate(calendarMonth.getGridStart(), calendarMonth.getGridEnd()).stream()
                .collect(Collectors.toMap(Holiday::getDate, Holiday::getName));
    }

    private Map<LocalDate, Attendance> findAttendanceByDate(Long employeeId, CalendarMonth calendarMonth) {
        return leaveRequestRepository.findByEmployeeIdAndWorkDateBetween(employeeId, calendarMonth.getGridStart(), calendarMonth.getGridEnd()).stream()
                .collect(Collectors.toMap(LeaveRequest::getWorkDate, LeaveRequest::getAttendance));
    }

    private boolean canChangeLeave(Optional<Employee> employee, LocalDate date) {
        return workTime.isBeforeScheduleChangeDeadline(date)
                && employee.map(found -> found.isActiveOn(date)).orElse(false);
    }

    public record Cell(
            LocalDate date,
            boolean inMonth,
            boolean today,
            boolean selected,
            boolean off,
            String holiday,
            Attendance attendance
    ) {}

    public record LeaveCalendar(
            LocalDate date,
            YearMonth month,
            List<Cell> cells,
            String myName,
            Long myId,
            Attendance attendance,
            boolean businessDay,
            boolean editable,
            LocalTime cutoff,
            String holidayName,
            LocalDate previousMonth,
            LocalDate nextMonth
    ) {}
}
