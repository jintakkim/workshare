package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.employee.EmployeeRegistered;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CafeteriaSupportAutoRegistration {
    private final ActivityRegistrationService activityRegistrationService;

    @EventListener
    public void registerNewEmployee(EmployeeRegistered event) {
        if (event.workGroup() != WorkGroupType.RESTAURANT) {
            activityRegistrationService.addRegistration(Activity.CAFETERIA_SUPPORT.getCode(), event.employeeId());
        }
    }
}
