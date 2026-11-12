package com.jangbokhuman.workshare.attendance;

import java.time.LocalDate;
import java.util.List;

// Implemented by activities that schedule people ahead of time, so a saved leave can flag duties it now clashes with.
public interface LeaveConflictReader {
    List<String> findConflictingDuties(Long employeeId, LocalDate date, Attendance attendance);
}
