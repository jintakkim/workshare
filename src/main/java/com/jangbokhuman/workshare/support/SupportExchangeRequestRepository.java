package com.jangbokhuman.workshare.support;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.*;

public interface SupportExchangeRequestRepository
        extends JpaRepository<SupportExchangeRequest, Long> {
    @Query("""
            SELECT entry.reason.id
            FROM SupportExchangeRequest request, DailyExclusion entry
            WHERE request.id = :id
                AND entry.id = request.sourceId
            """)
    Optional<Long> findReasonIdById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM SupportExchangeRequest request
            WHERE request.id = :id
            """)
    Optional<SupportExchangeRequest> findByIdForUpdate(Long id);

    @Modifying
    @Query("""
            DELETE FROM SupportExchangeRequest request
            WHERE request.expiresAt <= :now
            """)
    int deleteExpired(Instant now);

    List<SupportExchangeRequest> findByRequesterIdOrRecipientId(Long requester, Long recipient);

    boolean existsBySourceIdAndTargetId(Long source, Long target);

    void deleteBySourceIdInOrTargetIdIn(Collection<Long> sources, Collection<Long> targets);
}
