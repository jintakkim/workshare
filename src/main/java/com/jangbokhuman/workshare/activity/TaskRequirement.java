package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.calendar.WorkPeriod;

public record TaskRequirement(Duty duty, WorkPeriod period, int requiredWorkers) {
    public TaskRequirement {
        if (duty == null
                || period == null
                || requiredWorkers < 0) {
            throw new IllegalArgumentException("업무의 시간대와 필요 인원을 확인하세요.");
        }
    }
}
