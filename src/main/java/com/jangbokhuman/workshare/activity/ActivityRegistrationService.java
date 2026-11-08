package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityRegistrationService {
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final EmployeeRepository employeeRepository;
    private final ActivityRegistrationPolicy activityRegistrationPolicy;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void addRegistration(String activityCode, Long employeeId) {
        Activity activity = Activity.getByCode(activityCode);
        Employee employee = getEmployeeForUpdate(employeeId);
        activityRegistrationPolicy.evaluateRegistration(employee, activity).requireAllowed();
        if (!isRegistered(activity, employeeId)) {
            activityRegistrationRepository.save(new ActivityRegistration(activity, employee));
        }
    }

    private boolean isRegistered(Activity activity, Long employeeId) {
        return activityRegistrationRepository.existsByActivityCodeAndEmployeeId(activity, employeeId);
    }

    public void deleteRegistration(String activityCode, Long employeeId) {
        Activity activity = Activity.getByCode(activityCode);
        getEmployeeForUpdate(employeeId);
        activityRegistrationRepository.findByActivityCodeAndEmployeeId(activity, employeeId)
                .ifPresent(registration -> {
                    activityRegistrationRepository.delete(registration);
                    applicationEventPublisher.publishEvent(new ActivityRegistrationDeleted(activity, employeeId));
                });
    }

    public List<Activity> removeIneligibleRegistrations(Employee employee) {
        List<Activity> removedActivities = new ArrayList<>();
        for (ActivityRegistration registration : activityRegistrationRepository.findByEmployeeId(employee.getId())) {
            Activity activity = registration.getActivityCode();
            if (!activityRegistrationPolicy.evaluateRegistration(employee, activity).allowed()) {
                activityRegistrationRepository.delete(registration);
                applicationEventPublisher.publishEvent(new ActivityRegistrationDeleted(activity, employee.getId()));
                removedActivities.add(activity);
            }
        }
        return removedActivities;
    }

    private Employee getEmployeeForUpdate(Long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("직원을 확인해주세요."));
    }
}
