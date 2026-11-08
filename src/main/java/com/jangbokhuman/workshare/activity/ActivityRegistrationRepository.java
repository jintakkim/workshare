package com.jangbokhuman.workshare.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {
    boolean existsByActivityCodeAndEmployeeId(Activity activityCode, Long employeeId);

    Optional<ActivityRegistration> findByActivityCodeAndEmployeeId(Activity activityCode, Long employeeId);

    @Query("""
            SELECT registration.employee.id
            FROM ActivityRegistration registration
            WHERE registration.activityCode = :activityCode
            """)
    Set<Long> findEmployeeIdsByActivityCode(Activity activityCode);

    @EntityGraph(attributePaths = "employee")
    List<ActivityRegistration> findByActivityCodeOrderByEmployeeIdAsc(Activity activityCode);

    List<ActivityRegistration> findByEmployeeId(Long employeeId);

    @Query("""
            SELECT registration.activityCode AS activityCode, COUNT(registration) AS memberCount
            FROM ActivityRegistration registration
            GROUP BY registration.activityCode
            """)
    List<RegistrationCount> findRegistrationCountsByActivity();

    interface RegistrationCount {
        Activity getActivityCode();

        long getMemberCount();
    }
}
