package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum WorkGroupType {
    RESTAURANT("식당"),
    GENERAL("운영지원"),
    FACILITY_SUPPORT("운영지원(기계실)"),
    THIRD_FLOOR("3층"),
    DEVELOPMENT_SUPPORT("발달지원");
    private final String label;

    public static WorkGroupType getByCode(String code) {
        return Arrays.stream(values())
                .filter(workGroup -> workGroup.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "직군을 선택하세요."));
    }
}
