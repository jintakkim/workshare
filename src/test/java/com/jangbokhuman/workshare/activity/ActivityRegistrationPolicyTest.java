package com.jangbokhuman.workshare.activity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.jangbokhuman.workshare.activity.sharingshop.RestaurantSharingRestriction;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.calendar.WorkTime;

import org.junit.jupiter.api.Test;

import java.util.List;

class ActivityRegistrationPolicyTest {
    private final WorkTime workTime = mock(WorkTime.class);
    private final ActivityRegistrationPolicy activityRegistrationPolicy =
            new ActivityRegistrationPolicy(List.of(new RestaurantSharingRestriction()), workTime);

    @Test
    void restaurantWorkersCannotRegisterForSharingShop() {
        assertThat(activityRegistrationPolicy.evaluateWorkGroupEligibility(WorkGroupType.RESTAURANT, Activity.SHARING_SHOP).allowed()).isFalse();
    }

    @Test
    void missingGroupsAreDeniedAndEligibleGroupsAreAllowed() {
        assertThat(activityRegistrationPolicy.evaluateWorkGroupEligibility(null, Activity.SHARING_SHOP).allowed()).isFalse();
        for (WorkGroupType workGroup : List.of(WorkGroupType.GENERAL, WorkGroupType.FACILITY_SUPPORT, WorkGroupType.THIRD_FLOOR)) {
            assertThat(activityRegistrationPolicy.evaluateWorkGroupEligibility(workGroup, Activity.SHARING_SHOP).allowed()).isTrue();
        }
    }

    @Test
    void additionalRegistrationRestrictionsCanBeComposed() {
        ActivityWorkGroupRestriction restriction = (workGroup, activity) -> workGroup == WorkGroupType.FACILITY_SUPPORT
                ? ParticipationDecision.deny("추가 등록 제한") : ParticipationDecision.allow();
        ActivityRegistrationPolicy policy = new ActivityRegistrationPolicy(List.of(new RestaurantSharingRestriction(), restriction), workTime);
        assertThat(policy.evaluateWorkGroupEligibility(WorkGroupType.FACILITY_SUPPORT, Activity.SHARING_SHOP).reason())
                .isEqualTo("추가 등록 제한");
    }
}
