package com.jangbokhuman.workshare.support;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ExclusionReasonRepository extends JpaRepository<ExclusionReason, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reason
            FROM ExclusionReason reason
            WHERE reason.id = :id
            """)
    Optional<ExclusionReason> findByIdForUpdate(Long id);

    List<ExclusionReason> findByScopeCodeAndNameNotOrderByName(String scopeCode, String excludedName);

    Optional<ExclusionReason> findByName(String name);

    boolean existsByScopeCodeAndName(String scopeCode, String name);
}
