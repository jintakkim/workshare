package com.jangbokhuman.workshare.activity.cafeteria;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "target_id"}))
public class CafeteriaSupportExchangeRequest {
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

    public CafeteriaSupportExchangeRequest(Long sourceId, Long targetId, Long requesterId, Long recipientId, Instant expiresAt) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.requesterId = requesterId;
        this.recipientId = recipientId;
        this.expiresAt = expiresAt;
    }
}
