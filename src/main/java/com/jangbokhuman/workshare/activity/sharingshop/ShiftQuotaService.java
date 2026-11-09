package com.jangbokhuman.workshare.activity.sharingshop;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShiftQuotaService {
    private final SharingShopSettings sharingShopSettings;

    public ShiftQuota getQuota(int count) {
        ShiftQuota quota = sharingShopSettings.sharingShopQuotas().get(count);
        return quota == null ? ShiftQuota.createForHeadcount(count) : quota;
    }
}
