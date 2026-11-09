package com.jangbokhuman.workshare.activity.cafeteria;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CafeteriaSupportAssignmentRepository extends JpaRepository<CafeteriaSupportAssignment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM CafeteriaSupportAssignment assignment
            WHERE assignment.id IN :ids
            ORDER BY assignment.id
            """)
    List<CafeteriaSupportAssignment> findAllByIdForUpdate(Collection<Long> ids);

    List<CafeteriaSupportAssignment> findByWorkDate(LocalDate workDate);

    List<CafeteriaSupportAssignment> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate start, LocalDate end);

    List<CafeteriaSupportAssignment> findByEmployeeIdInAndWorkDateBetween(Collection<Long> employeeIds, LocalDate start, LocalDate end);
}
