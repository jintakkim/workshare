package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.ProfileMessageService;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
@RequiredArgsConstructor
class EmployeeProfileModel {
    private final RosterService rosterService;
    private final ActivityManagementService activityManagementService;
    private final ProfileMessageService profileMessageService;
    private final WorkTime workTime;

    void addProfile(Long id, AuthenticatedUser auth, Model model, boolean management) {
        RosterService.EmployeeView employee = rosterService.getEmployeeView(id);
        boolean ownProfile = id.equals(auth.getEmployeeId());
        model.addAttribute("employee", employee);
        model.addAttribute("admin", auth.isAdmin());
        model.addAttribute("management", management);
        model.addAttribute("canEditPhoto", management || ownProfile);
        model.addAttribute("canChangePassword", ownProfile);
        model.addAttribute("canChangeServicePeriod", ownProfile && !employee.excludedByAdmin());
        model.addAttribute("today", workTime.getToday());
        model.addAttribute("recipientId", id);
        model.addAttribute("recipientName", employee.name());
        model.addAttribute("messages", profileMessageService.getMessages(id, auth.getEmployeeId(), auth.isAdmin()));
        model.addAttribute("canWriteMessage", !auth.isAdmin() && !ownProfile);
        model.addAttribute("section", "employees");
        if (management) {
            model.addAttribute("groups", WorkGroupType.values());
            model.addAttribute("effectiveDate", rosterService.getRosterChangeEffectiveDate());
            if (!employee.excludedByAdmin()) {
                model.addAttribute("workGroupChangeImpacts", activityManagementService.getWorkGroupChangeImpacts(id));
            }
        }
    }
}
