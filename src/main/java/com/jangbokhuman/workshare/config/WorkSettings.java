package com.jangbokhuman.workshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;

@ConfigurationProperties("app")
public record WorkSettings(
        ZoneId zone,
        LocalTime leaveCutoff,
        LocalTime cafeteriaSupportCutoff,
        LocalTime allocationTime,
        LocalTime allocationResultTime,
        String adminUsername,
        String adminPassword,
        String rememberMeKey
) {
    private static final int MIN_REMEMBER_ME_KEY_LENGTH = 32;

    public WorkSettings {
        if (zone == null
                || leaveCutoff == null
                || cafeteriaSupportCutoff == null
                || allocationTime == null
                || !leaveCutoff.isBefore(allocationTime)
                || !cafeteriaSupportCutoff.isBefore(allocationTime)
        ) {
            throw new IllegalArgumentException("입력 마감은 배정 시각보다 빨라야 합니다.");
        }
        if (allocationResultTime == null || allocationResultTime.isBefore(allocationTime)) {
            throw new IllegalArgumentException("결과 공개는 배정 시각부터 가능합니다.");
        }
        if (adminUsername == null
                || adminUsername.isBlank()
                || adminPassword == null
                || adminPassword.length() < 12
                || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72
        ) {
            throw new IllegalArgumentException("APP_ADMIN_PASSWORD에 12자 이상, UTF-8 72바이트 이하 비밀번호를 지정하세요.");
        }
        if (rememberMeKey == null || rememberMeKey.length() < MIN_REMEMBER_ME_KEY_LENGTH) {
            throw new IllegalArgumentException("APP_REMEMBER_ME_KEY에 32자 이상의 임의 문자열을 지정하세요.");
        }
        if (rememberMeKey.equals(adminPassword)) {
            throw new IllegalArgumentException("APP_REMEMBER_ME_KEY는 관리자 비밀번호와 다른 값이어야 합니다.");
        }
    }
}
