package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityExclusionReader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CafeteriaSupportExclusionReader implements ActivityExclusionReader {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportRequirementRepository cafeteriaSupportRequirementRepository;

    @Override
    public List<ActivityExclusion> getExclusionsOn(Activity activity, LocalDate date) {
        if (!isExcludedBy(activity)) {
            return List.of();
        }
        return cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream()
                .map(assignment -> new ActivityExclusion(assignment.getEmployeeId(), Activity.CAFETERIA_SUPPORT.getName()))
                .toList();
    }

    @Override
    public List<String> getWarnings(Activity activity, LocalDate date) {
        if (!isExcludedBy(activity)) {
            return List.of();
        }
        int needed = cafeteriaSupportRequirementRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
                .map(CafeteriaSupportRequirement::getRequiredWorkers)
                .orElse(0);
        int actual = cafeteriaSupportAssignmentRepository.findByWorkDate(date).size();
        if (actual == needed) {
            return List.of();
        }
        return List.of(Activity.CAFETERIA_SUPPORT.getName() + " 표: 필요 " + needed + "명 / 등록 " + actual + "명입니다. 지원표를 확인해주세요.");
    }

    private boolean isExcludedBy(Activity activity) {
        return activity.getExcludingActivities().contains(Activity.CAFETERIA_SUPPORT);
    }
}
