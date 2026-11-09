package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityWorkGroupRestriction;
import com.jangbokhuman.workshare.activity.ParticipationDecision;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import org.springframework.stereotype.Component;

@Component
public class RestaurantCafeteriaSupportRestriction implements ActivityWorkGroupRestriction {
    @Override
    public ParticipationDecision evaluate(WorkGroupType workGroup, Activity activity) {
        if (activity == Activity.CAFETERIA_SUPPORT && workGroup == WorkGroupType.RESTAURANT) {
            return ParticipationDecision.deny("식당 직군은 식당지원을 맡을 수 없습니다.");
        }
        return ParticipationDecision.allow();
    }
}
