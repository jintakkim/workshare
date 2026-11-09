package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.Employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class DailyActivityParticipationPolicy {
    private final ActivityExclusionReader activityExclusionReader;
    private final ActivityRegistrationRepository activityRegistrationRepository;

    public ParticipationDecision evaluateEligibility(Employee employee, Activity activity, LocalDate participationDate) {
        boolean registered = activityRegistrationRepository.existsByActivityCodeAndEmployeeId(activity, employee.getId());
        return evaluateEligibility(employee, participationDate, registered);
    }

    public ParticipationDecision evaluateEligibility(Employee employee, LocalDate participationDate, boolean registered) {
        if (!employee.isActiveOn(participationDate)) {
            return ParticipationDecision.deny("해당 날짜에 근무하거나 운영하는 대상이 아닙니다.");
        }
        if (!registered) {
            return ParticipationDecision.deny("이 활동의 담당 직원으로 등록되어 있지 않습니다.");
        }
        return ParticipationDecision.allow();
    }

    public ParticipationDecision evaluateAvailability(Employee employee, Activity activity, LocalDate participationDate) {
        ParticipationDecision eligibility = evaluateEligibility(employee, activity, participationDate);
        if (!eligibility.allowed()) {
            return eligibility;
        }
        return activityExclusionReader.getExclusionsOn(activity, participationDate).stream()
                .filter(exclusion -> exclusion.employeeId().equals(employee.getId()))
                .findFirst()
                .map(exclusion -> ParticipationDecision.deny(exclusion.reasonName() + "으로 해당 날짜 활동에서 제외됩니다."))
                .orElse(eligibility);
    }

    public void requireAvailable(Employee employee, Activity activity, LocalDate participationDate) {
        evaluateAvailability(employee, activity, participationDate).requireAllowed();
    }

    public void requireEligible(Employee employee, Activity activity, LocalDate participationDate) {
        evaluateEligibility(employee, activity, participationDate).requireAllowed();
    }
}
