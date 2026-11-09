package com.jangbokhuman.workshare.activity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.time.LocalDate;
import java.util.*;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM WorkTask task
            WHERE task.id = :id
            """)
    Optional<WorkTask> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM WorkTask task
            WHERE task.workDate = :workDate
                AND task.activityCode = :activityCode
                AND task.status = :status
            ORDER BY task.id
            """)
    List<WorkTask> findByWorkDateAndActivityCodeAndStatusForUpdate(
            LocalDate workDate,
            Activity activityCode,
            WorkTask.Status status
    );

    List<WorkTask> findByWorkDateAndActivityCodeOrderByCreatedAtAscCodeAsc(LocalDate workDate, Activity activityCode);

    long countByWorkDateAndActivityCodeAndCodeIn(LocalDate workDate, Activity activityCode, Collection<String> codes);

    boolean existsByWorkDateAndStatus(LocalDate workDate, WorkTask.Status status);
}
