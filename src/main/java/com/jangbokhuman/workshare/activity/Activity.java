package com.jangbokhuman.workshare.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.jangbokhuman.workshare.support.ExclusionReason.CAFETERIA_SUPPORT;

@Getter
@RequiredArgsConstructor
public enum Activity {
    SHARING_SHOP("나눔가게 활동", "나눔가게 오전·오후 업무를 함께 나눕니다.", List.of(CAFETERIA_SUPPORT));

    private final String name;
    private final String description;
    @Getter(lombok.AccessLevel.NONE)
    private final List<String> additionalSupportScopes;

    public String getCode() {
        return name();
    }

    public List<String> getSupportScopes() {
        return Stream.concat(Stream.of(getCode()), additionalSupportScopes.stream()).toList();
    }

    public static Activity getByCode(String code) {
        return Arrays.stream(values())
                .filter(activity -> activity.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("활동을 선택하세요."));
    }
}
