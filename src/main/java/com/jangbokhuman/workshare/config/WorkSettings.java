package com.jangbokhuman.workshare.config;

import com.jangbokhuman.workshare.activity.sharingshop.ShiftQuota;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

@ConfigurationProperties("app")
public record WorkSettings(
        ZoneId zone,
        LocalTime leaveCutoff,
        LocalTime allocationTime,
        String adminUsername,
        String adminPassword,
        Map<Integer, ShiftQuota> sharingShopQuotas) {
    public WorkSettings {
        sharingShopQuotas = sharingShopQuotas == null ? Map.of() : Map.copyOf(sharingShopQuotas);
        for (Map.Entry<Integer, ShiftQuota> entry : sharingShopQuotas.entrySet()) {
            int headcount = entry.getKey();
            ShiftQuota quota = entry.getValue();
            if (headcount < 2 || (long) quota.morning() + quota.evening() != headcount) {
                throw new IllegalArgumentException(
                        "app.sharing-shop-quotas는 2명 이상만 설정하며 오전·오후 인원 합계가 가능 인원과 같아야 합니다.");
            }
        }
        if (zone == null
                || leaveCutoff == null
                || allocationTime == null
                || !leaveCutoff.isBefore(allocationTime)) {
            throw new IllegalArgumentException("입력 마감은 배정 시각보다 빨라야 합니다.");
        }
        if (adminUsername == null
                || adminUsername.isBlank()
                || adminPassword == null
                || adminPassword.length() < 12
                || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "APP_ADMIN_PASSWORD에 12자 이상, UTF-8 72바이트 이하 비밀번호를 지정하세요.");
        }
    }
}
