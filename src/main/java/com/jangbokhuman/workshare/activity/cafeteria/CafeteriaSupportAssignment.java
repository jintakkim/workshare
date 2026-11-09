package com.jangbokhuman.workshare.activity.cafeteria;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"work_date", "employee_id"}))
public class CafeteriaSupportAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private Long employeeId;

    public CafeteriaSupportAssignment(LocalDate workDate, Long employeeId) {
        this.workDate = workDate;
        this.employeeId = employeeId;
    }

    public void replaceEmployee(Long employeeId) {
        this.employeeId = employeeId;
    }
}
