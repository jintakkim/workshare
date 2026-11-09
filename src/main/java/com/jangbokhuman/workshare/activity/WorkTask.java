package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.*;
import java.util.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"work_date", "activity_code", "code"}))
public class WorkTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Activity activityCode;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkPeriod period;

    @Column(nullable = false)
    private int requiredWorkers;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant assignedAt;

    @ElementCollection
    @CollectionTable(name = "task_warning", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "warning_order")
    @Column(name = "message", nullable = false)
    @Getter(AccessLevel.NONE)
    private List<String> warnings = new ArrayList<>();

    private WorkTask(
            Activity activity,
            LocalDate workDate,
            Duty duty,
            WorkPeriod period,
            int requiredWorkers,
            Instant now
    ) {
        this.activityCode = Objects.requireNonNull(activity);
        this.workDate = Objects.requireNonNull(workDate);
        this.code = duty.code();
        this.name = duty.label();
        this.period = period;
        this.requiredWorkers = requiredWorkers;
        this.createdAt = now;
    }

    public static WorkTask create(
            Activity activity,
            LocalDate workDate,
            TaskRequirement requirement,
            Instant now
    ) {
        return new WorkTask(
                activity,
                workDate,
                requirement.duty(),
                requirement.period(),
                requirement.requiredWorkers(),
                now);
    }

    public List<TaskAssignment> assign(TaskAllocation allocation, Instant now) {
        if (assignedAt != null) {
            throw new IllegalStateException("확정된 업무는 다시 추첨할 수 없습니다.");
        }
        if (allocation.workers().size() > requiredWorkers
                || allocation.workers().stream().anyMatch(worker -> !worker.canWork(period))
                || allocation.workers().stream().map(Worker::id).distinct().count() != allocation.workers().size()
        ) {
            throw new IllegalArgumentException("업무 배정이 인원·출근 조건을 위반했습니다.");
        }
        warnings.addAll(allocation.warnings());
        assignedAt = now;
        return allocation.workers().stream()
                .sorted(Comparator.comparing(Worker::name).thenComparing(Worker::id))
                .map(worker -> new TaskAssignment(this, worker))
                .toList();
    }

    public TaskRequirement getRequirement() {
        return new TaskRequirement(new Duty(code, name), period, requiredWorkers);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }
}
