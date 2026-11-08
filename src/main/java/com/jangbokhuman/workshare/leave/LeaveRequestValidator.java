package com.jangbokhuman.workshare.leave;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkScheduleDeadlineValidator;
import com.jangbokhuman.workshare.employee.Employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 직원의 휴가 신청 조건을 검증한다. 직원 조회는 호출한 서비스에서 수행한다. */
@RequiredArgsConstructor
@Component
public class LeaveRequestValidator {
    private final WorkScheduleDeadlineValidator workScheduleDeadlineValidator;
    private final BusinessCalendar businessCalendar;

    public void validateLeaveRequest(Employee employee, LocalDate workDate) {
        workScheduleDeadlineValidator.validateChangeDeadline(workDate);
        validateWorkingDay(workDate);
        validateEmployeeWorkPeriod(employee, workDate);
    }

    private void validateWorkingDay(LocalDate workDate) {
        if (!businessCalendar.isWorkingDay(workDate))
            throw new IllegalArgumentException("주말·휴일에는 신청하지 않습니다.");
    }

    private void validateEmployeeWorkPeriod(Employee employee, LocalDate workDate) {
        if (!employee.isActiveOn(workDate)) {
            throw new IllegalArgumentException("해당 날짜는 직원의 등록된 근무 기간에 포함되지 않습니다.");
        }
    }
}
