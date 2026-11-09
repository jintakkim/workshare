package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record CafeteriaSupportAvailability(
        Map<Long, Employee> employeesById,
        Set<Long> registeredEmployeeIds,
        Map<Long, List<LeaveRequest>> leavesByEmployeeId,
        Map<Long, List<CafeteriaSupportAssignment>> assignmentsByEmployeeId
) {
    static CafeteriaSupportAvailability empty() {
        return new CafeteriaSupportAvailability(Map.of(), Set.of(), Map.of(), Map.of());
    }

    Optional<Employee> findEmployee(Long employeeId) {
        return Optional.ofNullable(employeesById.get(employeeId));
    }

    boolean isRegistered(Long employeeId) {
        return registeredEmployeeIds.contains(employeeId);
    }

    Optional<Attendance> findAttendance(Long employeeId, LocalDate date) {
        return leavesByEmployeeId.getOrDefault(employeeId, List.of()).stream()
                .filter(leave -> leave.getWorkDate().equals(date))
                .map(LeaveRequest::getAttendance)
                .findFirst();
    }

    List<CafeteriaSupportAssignment> getAssignmentsOn(Long employeeId, LocalDate date) {
        return assignmentsByEmployeeId.getOrDefault(employeeId, List.of()).stream()
                .filter(assignment -> assignment.getWorkDate().equals(date))
                .toList();
    }
}
