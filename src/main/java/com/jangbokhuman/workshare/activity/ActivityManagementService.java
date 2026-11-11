package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository.RegistrationCount;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityManagementService {
    private final EmployeeRepository employeeRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityRegistrationPolicy activityRegistrationPolicy;

    @Transactional(readOnly = true)
    public List<Card> getCards() {
        Map<Activity, Long> memberCountsByActivity = activityRegistrationRepository.findRegistrationCountsByActivity()
                .stream()
                .collect(Collectors.toMap(RegistrationCount::getActivityCode, RegistrationCount::getMemberCount));
        return Arrays.stream(Activity.values())
                .map(activity -> new Card(
                        activity.getCode(),
                        activity.getName(),
                        activity.getDescription(),
                        memberCountsByActivity.getOrDefault(activity, 0L))
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail getDetails(String code) {
        Activity activity = Activity.getByCode(code);
        Set<Long> registeredIds = activityRegistrationRepository.findEmployeeIdsByActivityCode(activity);
        List<Candidate> candidates = employeeRepository.findAll().stream()
                .filter(employee -> registeredIds.contains(employee.getId())
                        || activityRegistrationPolicy.evaluateRegistration(employee, activity).allowed())
                .sorted(Comparator.comparing(Employee::getName))
                .map(employee -> new Candidate(
                        employee.getId(),
                        employee.getName(),
                        employee.getWorkGroup().getLabel(),
                        registeredIds.contains(employee.getId()))
                )
                .toList();
        return new Detail(activity.getCode(), activity.getName(), activity.getDescription(), candidates);
    }

    @Transactional(readOnly = true)
    public List<WorkGroupChangeImpact> getWorkGroupChangeImpacts(Long employeeId) {
        List<Activity> activities = activityRegistrationRepository.findByEmployeeId(employeeId).stream()
                .map(ActivityRegistration::getActivityCode)
                .sorted()
                .toList();
        List<WorkGroupChangeImpact> impacts = new ArrayList<>();
        for (WorkGroupType workGroup : WorkGroupType.values()) {
            List<String> activityNames = activities.stream()
                    .filter(activity -> !activityRegistrationPolicy.evaluateWorkGroupEligibility(workGroup, activity).allowed())
                    .map(Activity::getName)
                    .toList();
            if (!activityNames.isEmpty()) {
                impacts.add(new WorkGroupChangeImpact(workGroup.name(), workGroup.getLabel(), activityNames));
            }
        }
        return impacts;
    }

    public record Card(String code, String name, String description, long members) {}

    public record Candidate(Long id, String name, String groupName, boolean registered) {}

    public record Detail(String code, String name, String description, List<Candidate> candidates) {}

    public record WorkGroupChangeImpact(String workGroupCode, String workGroupLabel, List<String> activityNames) {}
}
