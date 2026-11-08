package com.jangbokhuman.workshare.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Activity {
    SHARING_SHOP("나눔가게 활동", "나눔가게 오전·오후 업무를 함께 나눕니다.");

    private final String name;
    private final String description;

    public String getCode() {
        return name();
    }

    public static Activity getByCode(String code) {
        return Arrays.stream(values())
                .filter(activity -> activity.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("활동을 선택하세요."));
    }
}
