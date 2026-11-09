package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
@Transactional
public class LeaveRequestService {
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestValidator leaveRequestValidator;
    private final WorkTime workTime;

    public void save(Long employeeId, LocalDate date, Attendance attendance) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "등록된 직원이 없습니다."));
        leaveRequestValidator.validateLeaveRequest(employee, date);
        if (attendance == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "근무 상태를 선택하세요.");
        }
        LeaveRequest leaveRequest = leaveRequestRepository.findByEmployeeIdAndWorkDate(employee.getId(), date)
                .orElseGet(() -> new LeaveRequest(employee.getId(), date));
        leaveRequest.update(attendance, workTime.getInstant());
        leaveRequestRepository.save(leaveRequest);
    }
}
