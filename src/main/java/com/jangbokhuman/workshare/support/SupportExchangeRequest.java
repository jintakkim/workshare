package com.jangbokhuman.workshare.support;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "target_id"}))
public class SupportExchangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private Long requesterId;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private Instant expiresAt;

    public SupportExchangeRequest(
            Long source, Long target, Long requester, Long recipient, Instant expiresAt) {
        sourceId = source;
        targetId = target;
        requesterId = requester;
        recipientId = recipient;
        this.expiresAt = expiresAt;
    }
}
