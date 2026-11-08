package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityWorkGroupRestriction;
import com.jangbokhuman.workshare.activity.ParticipationDecision;
import com.jangbokhuman.workshare.employee.WorkGroupType;

public final class RestaurantSharingRestriction implements ActivityWorkGroupRestriction {
    @Override
    public ParticipationDecision evaluate(WorkGroupType workGroup, Activity activity) {
        if (activity == Activity.SHARING_SHOP
                && workGroup == WorkGroupType.RESTAURANT) {
            return ParticipationDecision.deny("식당 직군은 나눔가계 활동에 참여할 수 없습니다.");
        }
        return ParticipationDecision.allow();
    }
}
