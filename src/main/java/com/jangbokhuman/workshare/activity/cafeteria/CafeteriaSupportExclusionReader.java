package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityExclusionReader;
import com.jangbokhuman.workshare.calendar.WorkPeriod;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CafeteriaSupportExclusionReader implements ActivityExclusionReader {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;

    @Override
    public List<ActivityExclusion> getExclusionsOn(Activity activity, LocalDate date) {
        if (!isExcludedBy(activity)) {
            return List.of();
        }
        return cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream()
                // Cafeteria support runs through lunch; staff are back at their own post in the afternoon.
                .map(assignment -> new ActivityExclusion(
                        assignment.getEmployeeId(),
                        Activity.CAFETERIA_SUPPORT.getName(),
                        Set.of(WorkPeriod.MORNING)
                ))
                .toList();
    }

    private boolean isExcludedBy(Activity activity) {
        return activity.getExcludingActivities().contains(Activity.CAFETERIA_SUPPORT);
    }
}
