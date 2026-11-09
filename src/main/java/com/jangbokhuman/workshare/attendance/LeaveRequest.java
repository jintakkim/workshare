package com.jangbokhuman.workshare.attendance;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Attendance attendance;

    @Column(nullable = false)
    private Instant updatedAt;

    public LeaveRequest(Long employeeId, LocalDate workDate) {
        this.employeeId = employeeId;
        this.workDate = workDate;
    }

    public void update(Attendance attendance, Instant now) {
        this.attendance = attendance;
        this.updatedAt = now;
    }
}
