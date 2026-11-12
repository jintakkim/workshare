package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveConflictReader;
import com.jangbokhuman.workshare.calendar.WorkPeriod;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CafeteriaSupportLeaveConflictReader implements LeaveConflictReader {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;

    @Override
    public List<String> findConflictingDuties(Long employeeId, LocalDate date, Attendance attendance) {
        // Cafeteria support is a morning duty, so an afternoon half day off does not clash with it.
        if (attendance.canWork(WorkPeriod.MORNING)) {
            return List.of();
        }
        boolean assigned = cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream()
                .anyMatch(assignment -> assignment.getEmployeeId().equals(employeeId));
        return assigned ? List.of(Activity.CAFETERIA_SUPPORT.getName()) : List.of();
    }
}
