package com.jangbokhuman.workshare.support;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"reason_id", "effective_from"}))
public class ExclusionRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reasonId;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private int requiredWorkers;

    public ExclusionRequirement(Long reasonId, LocalDate date, int count) {
        this.reasonId = reasonId;
        effectiveFrom = date;
        change(count);
    }

    public void change(int count) {
        if (count < 0) throw new IllegalArgumentException("지원 인원은 0명 이상으로 입력하세요.");
        requiredWorkers = count;
    }
}
