package com.jangbokhuman.workshare.employee;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;

// Social service pay follows the soldier rank for the month of service, counting the call-up month as month 1.
@Getter
@RequiredArgsConstructor
public enum ServiceRank {
    PRIVATE("이병", 1, 1),
    PRIVATE_FIRST_CLASS("일병", 3, 2),
    CORPORAL("상병", 9, 3),
    SERGEANT("병장", 15, 4);

    private final String label;
    private final int firstServiceMonth;
    private final int stripes;

    public static ServiceRank findByServiceMonth(int serviceMonth) {
        return Arrays.stream(values())
                .filter(rank -> rank.firstServiceMonth <= serviceMonth)
                .max(Comparator.comparingInt(ServiceRank::getFirstServiceMonth))
                .orElse(PRIVATE);
    }

    public int getPayGrade(int serviceMonth) {
        return serviceMonth - firstServiceMonth + 1;
    }
}
