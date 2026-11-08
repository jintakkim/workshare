package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;

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

    List<ExclusionReason> findByActivityCodeOrderByName(Activity activityCode);

    Optional<ExclusionReason> findByActivityCodeAndName(Activity activityCode, String name);

    boolean existsByActivityCodeAndName(Activity activityCode, String name);
}
