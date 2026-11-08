package com.jangbokhuman.workshare.activity;

/** 업무 코드와 화면에 표시할 이름. */
public record Duty(String code, String label) {
    public Duty {
        if (code == null
                || !code.matches("[A-Z][A-Z0-9_]{1,39}")
                || label == null
                || label.isBlank()
                || label.length() > 60) {
            throw new IllegalArgumentException("업무 코드·이름이 올바르지 않습니다.");
        }
    }
}
