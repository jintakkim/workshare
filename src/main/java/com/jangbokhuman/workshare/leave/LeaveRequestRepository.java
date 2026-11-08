package com.jangbokhuman.workshare.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    Optional<LeaveRequest> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<LeaveRequest> findByWorkDate(LocalDate date);

    List<LeaveRequest> findByEmployeeIdAndWorkDateBetween(
            Long employeeId, LocalDate from, LocalDate to);
}
