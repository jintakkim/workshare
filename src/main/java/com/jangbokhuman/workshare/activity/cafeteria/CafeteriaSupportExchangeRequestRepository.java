package com.jangbokhuman.workshare.activity.cafeteria;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CafeteriaSupportExchangeRequestRepository extends JpaRepository<CafeteriaSupportExchangeRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM CafeteriaSupportExchangeRequest request
            WHERE request.id = :id
            """)
    Optional<CafeteriaSupportExchangeRequest> findByIdForUpdate(Long id);

    @Modifying
    @Query("""
            DELETE FROM CafeteriaSupportExchangeRequest request
            WHERE request.expiresAt <= :now
            """)
    int deleteExpired(Instant now);

    List<CafeteriaSupportExchangeRequest> findByRequesterIdOrRecipientId(Long requesterId, Long recipientId);

    boolean existsBySourceIdAndTargetId(Long sourceId, Long targetId);

    void deleteBySourceIdInOrTargetIdIn(Collection<Long> sourceIds, Collection<Long> targetIds);
}
