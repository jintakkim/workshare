package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Activity activityCode;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private Long employeeId;

    public DailyExclusion(ExclusionReason reason, LocalDate date, Long employeeId) {
        this.reason = reason;
        activityCode = reason.getActivityCode();
        workDate = date;
        this.employeeId = employeeId;
    }

    public void replaceEmployee(Long employeeId) {
        this.employeeId = employeeId;
    }
}
