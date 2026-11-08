package com.jangbokhuman.workshare.activity.sharingshop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Preference {
    ANY("상관없음"),
    MORNING("오전 선호"),
    EVENING("오후 선호");
    private final String label;
}
