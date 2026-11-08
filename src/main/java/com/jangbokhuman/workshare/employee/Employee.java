package com.jangbokhuman.workshare.employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDate activeFrom;

    private LocalDate inactiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_group_code", nullable = false, length = 40)
    private WorkGroupType workGroup;

    @ElementCollection
    @CollectionTable(name = "employee_responsibility")
    @OrderColumn(name = "duty_order")
    @Column(name = "title", nullable = false, length = 60)
    @Getter(AccessLevel.NONE)
    private List<String> responsibilities = new ArrayList<>();

    public Employee(
            String name, String passwordHash, LocalDate activeFrom, WorkGroupType workGroup) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.activeFrom = activeFrom;
        this.workGroup = workGroup;
    }

    public void changeWorkGroup(WorkGroupType workGroup) {
        this.workGroup = workGroup;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(activeFrom) && (inactiveFrom == null || date.isBefore(inactiveFrom));
    }

    public void retire(LocalDate date) {
        inactiveFrom = date;
    }

    public void changeResponsibilities(List<String> titles) {
        List<String> normalized =
                titles.stream().map(String::strip).filter(t -> !t.isEmpty()).distinct().toList();
        if (normalized.stream().anyMatch(t -> t.length() > 60))
            throw new IllegalArgumentException("담당 업무 이름은 60자 이내로 입력해주세요.");
        responsibilities.clear();
        responsibilities.addAll(normalized);
    }

    public List<String> getResponsibilities() {
        return List.copyOf(responsibilities);
    }
}
