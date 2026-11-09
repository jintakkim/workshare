package com.jangbokhuman.workshare.activity.sharingshop;

import java.util.List;

public interface AllocationPolicy {
    Allocation allocate(
            List<SharingShopWorker> workers,
            ShiftQuota quota,
            FacilityCoverage coverage
    );

    ShiftQuota getPlannedQuota(
            List<SharingShopWorker> workers,
            ShiftQuota quota,
            FacilityCoverage coverage
    );
}
