package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityRegistrationPolicy {
    private final List<ActivityWorkGroupRestriction> activityWorkGroupRestrictions;
    private final WorkTime workTime;

    public ParticipationDecision evaluateRegistration(Employee employee, Activity activity) {
        if (employee.isExcludedByAdminOn(workTime.getToday())) {
            return ParticipationDecision.deny("제외된 직원은 활동에 가입할 수 없습니다.");
        }
        return evaluateWorkGroupEligibility(employee.getWorkGroup(), activity);
    }

    public ParticipationDecision evaluateWorkGroupEligibility(WorkGroupType workGroup, Activity activity) {
        if (workGroup == null) {
            return ParticipationDecision.deny("관리자가 직원의 직군을 먼저 지정해야 합니다.");
        }
        for (ActivityWorkGroupRestriction restriction : activityWorkGroupRestrictions) {
            ParticipationDecision decision = restriction.evaluate(workGroup, activity);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return ParticipationDecision.allow();
    }
}
