package com.jangbokhuman.workshare.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface EmployeePhotoRepository extends JpaRepository<EmployeePhoto, Long> {
    @Query("""
            SELECT new com.jangbokhuman.workshare.employee.PhotoVersion(p.employeeId, p.savedAt)
            FROM EmployeePhoto p
            """)
    List<PhotoVersion> findPhotoVersions();

    @Query("""
            SELECT new com.jangbokhuman.workshare.employee.PhotoVersion(p.employeeId, p.savedAt)
            FROM EmployeePhoto p
            WHERE p.employeeId = :employeeId
            """)
    Optional<PhotoVersion> findPhotoVersion(Long employeeId);

    default Map<Long, Long> findPhotoVersionsByEmployeeId() {
        return findPhotoVersions().stream()
                .collect(Collectors.toMap(PhotoVersion::employeeId, PhotoVersion::getVersion));
    }
}
