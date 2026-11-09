package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum AdminSection {
    LEAVE("leave"),
    EMPLOYEES("employees"),
    HOLIDAYS("holidays"),
    ACTIVITIES("activities");

    private final String code;

    public static AdminSection getByCode(String code) {
        return Arrays.stream(values())
                .filter(section -> section.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "관리 메뉴를 선택하세요."));
    }
}
