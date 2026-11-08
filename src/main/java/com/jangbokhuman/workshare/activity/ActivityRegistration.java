package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.Employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"activity_code", "employee_id"}))
public class ActivityRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Activity activityCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Employee employee;

    public ActivityRegistration(Activity activity, Employee employee) {
        this.activityCode = activity;
        this.employee = employee;
    }
}
