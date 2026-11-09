package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.employee.EmployeeRegistered;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SharingShopAutoRegistration {
    private static final Set<WorkGroupType> AUTO_REGISTERED_WORK_GROUPS = Set.of(WorkGroupType.GENERAL, WorkGroupType.FACILITY_SUPPORT);

    private final ActivityRegistrationService activityRegistrationService;

    @EventListener
    public void registerNewEmployee(EmployeeRegistered event) {
        if (AUTO_REGISTERED_WORK_GROUPS.contains(event.workGroup())) {
            activityRegistrationService.addRegistration(Activity.SHARING_SHOP.getCode(), event.employeeId());
        }
    }
}
