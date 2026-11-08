package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.leave.Attendance;

public record SharingShopWorker(Long id, String name, Attendance attendance, Preference preference, boolean facilitySupport) {
    public Worker toWorker() {
        return new Worker(id, name, attendance);
    }
}
