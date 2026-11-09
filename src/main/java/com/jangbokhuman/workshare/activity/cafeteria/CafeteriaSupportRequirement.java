package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class CafeteriaSupportRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private int requiredWorkers;

    public CafeteriaSupportRequirement(LocalDate effectiveFrom, int requiredWorkers) {
        this.effectiveFrom = effectiveFrom;
        change(requiredWorkers);
    }

    public void change(int requiredWorkers) {
        if (requiredWorkers < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원 인원은 0명 이상으로 입력하세요.");
        }
        this.requiredWorkers = requiredWorkers;
    }
}
