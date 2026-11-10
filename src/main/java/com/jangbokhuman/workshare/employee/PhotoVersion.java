package com.jangbokhuman.workshare.employee;

import java.time.Instant;

public record PhotoVersion(Long employeeId, Instant savedAt) {
    public long getVersion() {
        return savedAt.toEpochMilli();
    }
}
