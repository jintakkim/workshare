package com.jangbokhuman.workshare.employee;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByName(String name);

    boolean existsByName(String name);

    List<Employee> findByWorkGroup(WorkGroupType workGroup);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
            FROM Employee e
            WHERE e.id = :id
            """)
    Optional<Employee> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
            FROM Employee e
            WHERE e.id IN :ids
            ORDER BY e.id
            """)
    List<Employee> findAllByIdForUpdate(Collection<Long> ids);
}
