package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository.EmployeeActivity;
import com.jangbokhuman.workshare.employee.EmployeeActivityReader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegisteredActivityReader implements EmployeeActivityReader {
    private final ActivityRegistrationRepository activityRegistrationRepository;

    @Override
    public Map<Long, List<String>> getActivityNamesByEmployeeId() {
        return activityRegistrationRepository.findEmployeeActivities().stream()
                .collect(Collectors.groupingBy(
                        EmployeeActivity::getEmployeeId,
                        Collectors.mapping(registration -> registration.getActivityCode().getName(), Collectors.toList())
                ));
    }

    @Override
    public List<String> getActivityNames(Long employeeId) {
        return activityRegistrationRepository.findByEmployeeId(employeeId).stream()
                .map(ActivityRegistration::getActivityCode)
                .sorted(Comparator.comparing(Activity::getCode))
                .map(Activity::getName)
                .toList();
    }
}
