package com.jangbokhuman.workshare.employee;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record ServiceStatus(
        ServiceRank rank,
        int payGrade,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        long daysLeft,
        long startEpochMilli,
        long endEpochMilli,
        double progressPercent
) {
    public static ServiceStatus evaluate(LocalDate activeFrom, LocalDate inactiveFrom, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate serviceEndDate = inactiveFrom.minusDays(1);
        int serviceMonth = getServiceMonth(activeFrom, clampToServicePeriod(today, activeFrom, serviceEndDate));
        ServiceRank rank = ServiceRank.findByServiceMonth(serviceMonth);
        long startEpochMilli = activeFrom.atStartOfDay(zone).toInstant().toEpochMilli();
        long endEpochMilli = inactiveFrom.atStartOfDay(zone).toInstant().toEpochMilli();
        return new ServiceStatus(
                rank,
                rank.getPayGrade(serviceMonth),
                activeFrom,
                serviceEndDate,
                ChronoUnit.DAYS.between(today, serviceEndDate),
                startEpochMilli,
                endEpochMilli,
                calculateProgressPercent(startEpochMilli, endEpochMilli, now.toEpochMilli())
        );
    }

    private static double calculateProgressPercent(long startEpochMilli, long endEpochMilli, long nowEpochMilli) {
        double ratio = (double) (nowEpochMilli - startEpochMilli) / (endEpochMilli - startEpochMilli);
        return Math.min(1.0, Math.max(0.0, ratio)) * 100;
    }

    private static LocalDate clampToServicePeriod(LocalDate date, LocalDate serviceStartDate, LocalDate serviceEndDate) {
        if (date.isBefore(serviceStartDate)) {
            return serviceStartDate;
        }
        return date.isAfter(serviceEndDate) ? serviceEndDate : date;
    }

    public static int getServiceMonth(LocalDate activeFrom, LocalDate date) {
        return (int) ChronoUnit.MONTHS.between(YearMonth.from(activeFrom), YearMonth.from(date)) + 1;
    }

    public String getRankLabel() {
        return rank.getLabel() + " " + payGrade + "호봉";
    }

    public String getDayCountLabel() {
        if (daysLeft > 0) {
            return "D-" + daysLeft;
        }
        return daysLeft == 0 ? "D-DAY" : "소집해제";
    }
}
