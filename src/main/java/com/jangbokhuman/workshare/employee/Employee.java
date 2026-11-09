package com.jangbokhuman.workshare.employee;

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

    @Column(nullable = false)
    private LocalDate inactiveFrom;

    @Column(nullable = false)
    private boolean excludedByAdmin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkGroupType workGroup;

    public Employee(
            String name, String passwordHash, LocalDate activeFrom, WorkGroupType workGroup) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.activeFrom = activeFrom;
        this.inactiveFrom = calculateInactiveFrom(activeFrom);
        this.workGroup = workGroup;
    }

    public void changeWorkGroup(WorkGroupType workGroup) {
        this.workGroup = workGroup;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(activeFrom) && date.isBefore(inactiveFrom);
    }

    public boolean isExcludedByAdminOn(LocalDate date) {
        return excludedByAdmin && !date.isBefore(inactiveFrom);
    }

    public boolean isServiceExtended() {
        return !excludedByAdmin && inactiveFrom.isAfter(calculateInactiveFrom(activeFrom));
    }

    public void changeServicePeriod(LocalDate serviceStartDate, LocalDate extendedServiceEndDate, LocalDate today) {
        if (excludedByAdmin) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "관리자가 업무 인원에서 제외한 직원은 복무 기간을 변경할 수 없습니다.");
        }
        if (serviceStartDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소집일을 선택해주세요.");
        }
        LocalDate calculatedInactiveFrom = calculateInactiveFrom(serviceStartDate);
        LocalDate calculatedEndDate = calculatedInactiveFrom.minusDays(1);
        if (extendedServiceEndDate != null && !extendedServiceEndDate.isAfter(calculatedEndDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연장된 소집해제일은 자동 계산일(" + calculatedEndDate + ")보다 늦어야 합니다.");
        }
        LocalDate serviceEndDate = extendedServiceEndDate == null ? calculatedEndDate : extendedServiceEndDate;
        if (serviceEndDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소집해제일은 오늘보다 이전으로 설정할 수 없습니다.");
        }
        activeFrom = serviceStartDate;
        inactiveFrom = serviceEndDate.plusDays(1);
    }

    private static LocalDate calculateInactiveFrom(LocalDate serviceStartDate) {
        LocalDate anniversary = serviceStartDate.plusMonths(SOCIAL_SERVICE_MONTHS);
        return anniversary.getDayOfMonth() == serviceStartDate.getDayOfMonth() ? anniversary : anniversary.plusDays(1);
    }

    public void retire(LocalDate date) {
        if (date.isBefore(inactiveFrom)) {
            inactiveFrom = date;
        }
        excludedByAdmin = true;
    }
}
