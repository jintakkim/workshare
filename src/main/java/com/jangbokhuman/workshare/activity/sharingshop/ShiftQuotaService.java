package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.config.WorkSettings;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShiftQuotaService {
    private final WorkSettings workSettings;

    public ShiftQuota getQuota(int count) {
        ShiftQuota quota = workSettings.sharingShopQuotas().get(count);
        return quota == null ? ShiftQuota.createForHeadcount(count) : quota;
    }
}
