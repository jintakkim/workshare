package com.jangbokhuman.workshare.support;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"activity_code", "work_date", "employee_id"}))
public class DailyExclusion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ExclusionReason reason;

    @Column(name = "activity_code", nullable = false, length = 40)
    private String scopeCode;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private Long employeeId;

    public DailyExclusion(ExclusionReason reason, LocalDate date, Long employeeId) {
        this.reason = reason;
        scopeCode = reason.getScopeCode();
        workDate = date;
        this.employeeId = employeeId;
    }

    public void replaceEmployee(Long employeeId) {
        this.employeeId = employeeId;
    }
}
