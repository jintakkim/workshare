package com.jangbokhuman.workshare.activity.sharingshop;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("app")
public record SharingShopSettings(Map<Integer, ShiftQuota> sharingShopQuotas) {
    public SharingShopSettings {
        sharingShopQuotas = sharingShopQuotas == null ? Map.of() : Map.copyOf(sharingShopQuotas);
        for (Map.Entry<Integer, ShiftQuota> entry : sharingShopQuotas.entrySet()) {
            int headcount = entry.getKey();
            ShiftQuota quota = entry.getValue();
            if (headcount < 2 || quota.morning() + quota.evening() != headcount) {
                throw new IllegalArgumentException("app.sharing-shop-quotas는 2명 이상만 설정하며 오전·오후 인원 합계가 가능 인원과 같아야 합니다.");
            }
        }
    }
}
