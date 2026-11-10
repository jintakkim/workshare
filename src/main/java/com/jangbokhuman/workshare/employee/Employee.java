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
    private static final int DEFAULT_DAILY_TRANSPORT_FEE = 3000;
    private static final int MAX_DAILY_TRANSPORT_FEE = 100_000;

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

    @Column(nullable = false)
    private int dailyTransportFee = DEFAULT_DAILY_TRANSPORT_FEE;

    public Employee(
            String name,
            String passwordHash,
            LocalDate activeFrom,
            WorkGroupType workGroup
    ) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.activeFrom = activeFrom;
        this.inactiveFrom = calculateInactiveFrom(activeFrom);
        this.workGroup = workGroup;
    }

    public void changeWorkGroup(WorkGroupType workGroup) {
        this.workGroup = workGroup;
    }

    public void changeDailyTransportFee(Integer dailyTransportFee) {
        if (dailyTransportFee == null || dailyTransportFee < 0 || dailyTransportFee > MAX_DAILY_TRANSPORT_FEE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "하루 교통비는 0원부터 100,000원 사이로 입력해주세요.");
        }
        this.dailyTransportFee = dailyTransportFee;
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

    public void changeServicePeriod(LocalDate serviceStartDate, LocalDate enteredServiceEndDate, LocalDate today) {
        if (excludedByAdmin) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "관리자가 업무 인원에서 제외한 직원은 복무 기간을 변경할 수 없습니다.");
        }
        if (serviceStartDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소집일을 선택해주세요.");
        }
        LocalDate serviceEndDate = enteredServiceEndDate == null
                ? calculateInactiveFrom(serviceStartDate).minusDays(1)
                : enteredServiceEndDate;
        if (!serviceEndDate.isAfter(serviceStartDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소집해제일은 소집일보다 늦어야 합니다.");
        }
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
