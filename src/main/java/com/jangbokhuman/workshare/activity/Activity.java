package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum Activity {
    SHARING_SHOP("나눔가게 활동", "나눔가게 오전·오후 업무를 함께 나눕니다."),
    CAFETERIA_SUPPORT("식당지원", "월별 지원표에 따라 식당 업무를 지원합니다.");

    private final String name;
    private final String description;

    public String getCode() {
        return name();
    }

    // 당일 아래 활동을 맡은 직원은 이 활동의 후보에서 빠진다.
    public Set<Activity> getExcludingActivities() {
        return switch (this) {
            case SHARING_SHOP -> Set.of(CAFETERIA_SUPPORT);
            case CAFETERIA_SUPPORT -> Set.of();
        };
    }

    public static Activity getByCode(String code) {
        return Arrays.stream(values())
                .filter(activity -> activity.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "활동을 선택하세요."));
    }
}
