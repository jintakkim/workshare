package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class LeaveRequestService {
    private static final int MAX_TRAINING_DAYS = 60;

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestValidator leaveRequestValidator;
    private final WorkTime workTime;
    private final BusinessCalendar businessCalendar;
    private final List<LeaveConflictReader> leaveConflictReaders;

    @Transactional(readOnly = true)
    public List<String> findConflictingDuties(Long employeeId, LocalDate date, Attendance attendance) {
        return leaveConflictReaders.stream()
                .flatMap(reader -> reader.findConflictingDuties(employeeId, date, attendance).stream())
                .toList();
    }

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

    public int saveTrainingDays(Long employeeId, LocalDate startDate, LocalDate endDate) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "등록된 직원이 없습니다."));
        validateTrainingPeriod(employee, startDate, endDate);
        List<LocalDate> trainingDays = businessCalendar.findWorkingDays(startDate, endDate).stream()
                .sorted()
                .toList();
        for (LocalDate date : trainingDays) {
            LeaveRequest leaveRequest = leaveRequestRepository.findByEmployeeIdAndWorkDate(employee.getId(), date)
                    .orElseGet(() -> new LeaveRequest(employee.getId(), date));
            leaveRequest.update(Attendance.TRAINING, workTime.getInstant());
            leaveRequestRepository.save(leaveRequest);
        }
        return trainingDays.size();
    }

    private void validateTrainingPeriod(Employee employee, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "교육·훈련 시작일과 종료일을 선택하세요.");
        }
        if (endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_TRAINING_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "교육·훈련 기간은 시작일부터 " + MAX_TRAINING_DAYS + "일 이내로 입력하세요.");
        }
        if (!employee.isActiveOn(startDate) || !employee.isActiveOn(endDate)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "교육·훈련 기간은 직원의 복무 기간 안이어야 합니다.");
        }
    }
}
