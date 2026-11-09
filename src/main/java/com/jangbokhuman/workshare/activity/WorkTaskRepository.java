package com.jangbokhuman.workshare.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WorkTaskRepository extends JpaRepository<WorkTask, Long> {
    List<WorkTask> findByWorkDateAndActivityCodeOrderByCreatedAtAscCodeAsc(LocalDate workDate, Activity activityCode);

    long countByWorkDateAndActivityCodeAndCodeIn(LocalDate workDate, Activity activityCode, Collection<String> codes);

    boolean existsByWorkDate(LocalDate workDate);
}
