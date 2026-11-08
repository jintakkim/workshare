package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 선호도 변경은 활동 가입 여부를 확인하며, 날짜별 업무 참여 자격과 구분한다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharingShopTimePreferenceChangePolicy {
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final WorkTime workTime;

    public ChangePermission evaluateChangePermission(Employee employee) {
        if (employee.getInactiveFrom() != null
                && !workTime.getToday().isBefore(employee.getInactiveFrom())) {
            return ChangePermission.deny("제외된 직원은 나눔가게 시간대 선호도를 변경할 수 없습니다.");
        }
        boolean registered = activityRegistrationRepository.existsByActivityCodeAndEmployeeId(
                Activity.SHARING_SHOP, employee.getId());
        return registered
                ? ChangePermission.allow()
                : ChangePermission.deny("나눔가게 담당 직원으로 등록되어 있지 않습니다.");
    }

    public record ChangePermission(boolean allowed, String reason) {
        public static ChangePermission allow() {
            return new ChangePermission(true, "변경 가능");
        }

        public static ChangePermission deny(String reason) {
            return new ChangePermission(false, reason);
        }

        public void requireAllowed() {
            if (!allowed) {
                throw new ParticipationDeniedException(reason);
            }
        }
    }
}
