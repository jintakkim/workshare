package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class AssignmentGuard {
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;
    private final EmployeeRepository employeeRepository;
    private final RosterReader rosterReader;

    public void validate(
            Activity activity, LocalDate date, WorkPeriod period, List<Worker> selected) {
        Map<Long, Worker> candidates =
                rosterReader.getWorkersOn(activity, date).stream()
                        .collect(Collectors.toMap(Worker::id, Function.identity()));
        for (Worker worker : selected) {
            Employee employee =
                    employeeRepository
                            .findById(worker.id())
                            .orElseThrow(
                                    () ->
                                            new ParticipationDeniedException(
                                                    "등록되지 않은 직원을 배정할 수 없습니다."));
            dailyActivityParticipationPolicy.requireEligible(employee, activity, date);
            if (!worker.equals(candidates.get(worker.id())) || !period.isEligible(worker)) {
                throw new ParticipationDeniedException("직원의 실제 출근 상태와 활동 조건에 맞지 않는 배정입니다.");
            }
        }
    }
}
