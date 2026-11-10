package com.jangbokhuman.workshare.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

@DisplayName("복무 개월에 따른 계급·호봉과 진행률")
class ServiceStatusTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("소집월을 1개월째로 세어 이병·일병·상병·병장과 계급 안의 호봉을 정한다")
    void rankFollowsTheServiceMonth() {
        LocalDate start = LocalDate.of(2026, 1, 20);
        LocalDate inactiveFrom = LocalDate.of(2027, 10, 20);

        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 2, 28)).getRankLabel()).isEqualTo("이병 2호봉");
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 3, 1)).getRankLabel()).isEqualTo("일병 1호봉");
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 9, 15)).getRankLabel()).isEqualTo("상병 1호봉");
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2027, 3, 2)).getRankLabel()).isEqualTo("병장 1호봉");
    }

    @Test
    @DisplayName("소집해제일까지 남은 날과 복무 진행률을 계산한다")
    void countsDaysLeftAndProgress() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate inactiveFrom = LocalDate.of(2026, 1, 11);

        ServiceStatus halfway = evaluate(start, inactiveFrom, LocalDate.of(2026, 1, 6));
        assertThat(halfway.getDayCountLabel()).isEqualTo("D-4");
        assertThat(halfway.progressPercent()).isCloseTo(50.0, within(0.0000001));
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 1, 10)).getDayCountLabel()).isEqualTo("D-DAY");
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 1, 12)).getDayCountLabel()).isEqualTo("소집해제");
        assertThat(evaluate(start, inactiveFrom, LocalDate.of(2026, 1, 12)).progressPercent()).isEqualTo(100.0);
    }

    private ServiceStatus evaluate(LocalDate start, LocalDate inactiveFrom, LocalDate today) {
        return ServiceStatus.evaluate(start, inactiveFrom, today.atStartOfDay(SEOUL).toInstant(), SEOUL);
    }
}
