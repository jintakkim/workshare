package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveHistoryService {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WorkTime workTime;

    public LeaveHistory getHistory(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        LocalDate today = workTime.getToday();
        List<Entry> entries = leaveRequestRepository.findByEmployeeIdAndWorkDateBetween(
                        employeeId,
                        employee.getActiveFrom(),
                        employee.getInactiveFrom().minusDays(1)
                )
                .stream()
                .filter(leave -> leave.getAttendance() != Attendance.WORK)
                .sorted(Comparator.comparing(LeaveRequest::getWorkDate).reversed())
                .map(leave -> new Entry(leave.getWorkDate(), leave.getAttendance(), leave.getWorkDate().isAfter(today)))
                .toList();
        List<Count> counts = Arrays.stream(Attendance.values())
                .filter(attendance -> attendance != Attendance.WORK)
                .map(attendance -> new Count(attendance, entries.stream().filter(entry -> entry.attendance() == attendance).count()))
                .filter(count -> count.days() > 0)
                .toList();
        return new LeaveHistory(counts, entries, countAnnualLeaveDays(entries));
    }

    private double countAnnualLeaveDays(List<Entry> entries) {
        return entries.stream()
                .mapToDouble(entry -> switch (entry.attendance()) {
                    case VACATION -> 1.0;
                    case MORNING_HALF_DAY, AFTERNOON_HALF_DAY -> 0.5;
                    default -> 0.0;
                })
                .sum();
    }

    public record Count(Attendance attendance, long days) {}

    public record Entry(LocalDate date, Attendance attendance, boolean upcoming) {}

    public record LeaveHistory(List<Count> counts, List<Entry> entries, double annualLeaveDays) {}
}
