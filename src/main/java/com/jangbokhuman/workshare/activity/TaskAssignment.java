package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.attendance.Attendance;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "employee_id"}))
public class TaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private WorkTask task;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Attendance attendance;

    TaskAssignment(WorkTask task, Worker worker) {
        this.task = task;
        employeeId = worker.id();
        name = worker.name();
        attendance = worker.attendance();
    }
}
