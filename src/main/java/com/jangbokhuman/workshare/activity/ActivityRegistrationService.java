package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.EmployeeWorkGroupChanged;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

    @EventListener
    public void removeIneligibleRegistrations(EmployeeWorkGroupChanged event) {
        Employee employee = getEmployeeForUpdate(event.employeeId());
        List<ActivityRegistration> registrations = activityRegistrationRepository.findByEmployeeId(employee.getId()).stream()
                .sorted(Comparator.comparing(ActivityRegistration::getActivityCode))
                .toList();
        for (ActivityRegistration registration : registrations) {
            Activity activity = registration.getActivityCode();
            if (!activityRegistrationPolicy.evaluateRegistration(employee, activity).allowed()) {
                activityRegistrationRepository.delete(registration);
                applicationEventPublisher.publishEvent(new ActivityRegistrationDeleted(activity, employee.getId()));
            }
        }
    }

    private Employee getEmployeeForUpdate(Long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 확인해주세요."));
    }
}
