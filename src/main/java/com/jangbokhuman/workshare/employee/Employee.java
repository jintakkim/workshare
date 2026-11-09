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
    private static final int SOCIAL_SERVICE_MONTHS = 21;

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

    private LocalDate extendedServiceEndDate;

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
        return !date.isBefore(activeFrom)
                && !date.isAfter(getServiceEndDate())
                && (inactiveFrom == null || date.isBefore(inactiveFrom));
    }

    public LocalDate getServiceEndDate() {
        return extendedServiceEndDate == null ? calculateServiceEndDate(activeFrom) : extendedServiceEndDate;
    }

    public void changeServicePeriod(LocalDate serviceStartDate, LocalDate extendedServiceEndDate, LocalDate today) {
        if (inactiveFrom != null) {
            throw new IllegalArgumentException("관리자가 업무 인원에서 제외한 직원은 복무 기간을 변경할 수 없습니다.");
        }
        if (serviceStartDate == null) {
            throw new IllegalArgumentException("소집일을 선택해주세요.");
        }
        LocalDate calculatedEndDate = calculateServiceEndDate(serviceStartDate);
        if (extendedServiceEndDate != null && !extendedServiceEndDate.isAfter(calculatedEndDate)) {
            throw new IllegalArgumentException("연장된 소집해제일은 자동 계산일(" + calculatedEndDate + ")보다 늦어야 합니다.");
        }
        LocalDate serviceEndDate = extendedServiceEndDate == null ? calculatedEndDate : extendedServiceEndDate;
        if (serviceEndDate.isBefore(today)) {
            throw new IllegalArgumentException("소집해제일은 오늘보다 이전으로 설정할 수 없습니다.");
        }
        activeFrom = serviceStartDate;
        this.extendedServiceEndDate = extendedServiceEndDate;
    }

    private LocalDate calculateServiceEndDate(LocalDate serviceStartDate) {
        LocalDate anniversary = serviceStartDate.plusMonths(SOCIAL_SERVICE_MONTHS);
        return anniversary.getDayOfMonth() == serviceStartDate.getDayOfMonth() ? anniversary.minusDays(1) : anniversary;
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
