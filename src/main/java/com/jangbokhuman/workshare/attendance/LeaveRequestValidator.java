package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkScheduleDeadlineValidator;
import com.jangbokhuman.workshare.calendar.LeaveChangeWindow;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class LeaveRequestValidator {
    private final WorkScheduleDeadlineValidator workScheduleDeadlineValidator;
    private final BusinessCalendar businessCalendar;
    private final LeaveChangeWindow leaveChangeWindow;
    private final WorkTime workTime;

    public void validateLeaveRequest(Employee employee, LocalDate workDate) {
        validateLeaveDeadline(workDate);
        validateWorkingDay(workDate);
        validateEmployeeWorkPeriod(employee, workDate);
    }

    private void validateLeaveDeadline(LocalDate workDate) {
        if (workDate != null && leaveChangeWindow.canChangeLeaveOn(workDate)) {
            return;
        }
        if (workDate != null && workDate.equals(workTime.getToday())) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "오늘 근무 상태는 08:49에 마감됐어요. 오늘 배정이 저장되면 다시 바꿀 수 있어요.");
        }
        workScheduleDeadlineValidator.validateChangeDeadline(workDate);
    }

    private void validateWorkingDay(LocalDate workDate) {
        if (!businessCalendar.isWorkingDay(workDate)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "주말·휴일에는 신청하지 않습니다.");
        }
    }

    private void validateEmployeeWorkPeriod(Employee employee, LocalDate workDate) {
        if (!employee.isActiveOn(workDate)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "해당 날짜는 직원의 등록된 근무 기간에 포함되지 않습니다.");
        }
    }
}
