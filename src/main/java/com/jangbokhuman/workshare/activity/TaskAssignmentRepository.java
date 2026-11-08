package com.jangbokhuman.workshare.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByTaskIdInOrderByNameAscEmployeeIdAsc(Collection<Long> taskIds);
}
