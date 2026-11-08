package com.jangbokhuman.workshare.activity.sharingshop;

public record ShiftQuota(int morning, int evening) {
    public ShiftQuota {
        if (morning < 0 || evening < 0) throw new IllegalArgumentException("배정 인원은 0명 이상이어야 합니다.");
    }

    public static ShiftQuota createForHeadcount(int count) {
        if (count == 0) return new ShiftQuota(0, 0);
        if (count == 1) return new ShiftQuota(1, 1);
        return new ShiftQuota((count + 1) / 2, count / 2);
    }
}
