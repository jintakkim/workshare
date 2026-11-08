package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;

import org.springframework.data.jpa.repository.*;

import java.time.LocalDate;
import java.util.*;

public interface DailyExclusionRepository extends JpaRepository<DailyExclusion, Long> {
    @Query("""
            SELECT entry.reason.id
            FROM DailyExclusion entry
            WHERE entry.id = :id
            """)
    Optional<Long> findReasonIdById(Long id);

    @EntityGraph(attributePaths = "reason")
    List<DailyExclusion> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "reason")
    List<DailyExclusion> findByActivityCodeAndWorkDate(Activity code, LocalDate date);

    @EntityGraph(attributePaths = "reason")
    List<DailyExclusion> findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long id, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "reason")
    List<DailyExclusion> findByEmployeeIdAndWorkDateGreaterThanEqualOrderByWorkDateAsc(
            Long id, LocalDate date);

    boolean existsByActivityCodeAndWorkDateAndEmployeeIdAndIdNot(
            Activity code, LocalDate date, Long employeeId, Long id);
}
