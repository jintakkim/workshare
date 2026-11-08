package com.jangbokhuman.workshare.employee;

import org.springframework.data.jpa.repository.*;

import java.util.List;

public interface EmployeePhotoRepository extends JpaRepository<EmployeePhoto, Long> {
    @Query("""
            SELECT p.employeeId
            FROM EmployeePhoto p
            """)
    List<Long> findEmployeeIds();
}
