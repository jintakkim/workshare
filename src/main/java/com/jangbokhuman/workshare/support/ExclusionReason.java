package com.jangbokhuman.workshare.support;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class ExclusionReason {
    public static final String CAFETERIA_SUPPORT = "CAFETERIA_SUPPORT";
    public static final String CAFETERIA_SUPPORT_NAME = "식당지원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_code", nullable = false, length = 40)
    private String scopeCode;

    @Column(nullable = false, length = 60)
    private String name;

    public ExclusionReason(String scopeCode, String name) {
        this.scopeCode = scopeCode;
        this.name = name;
    }

    public boolean isCafeteriaSupport() {
        return CAFETERIA_SUPPORT_NAME.equals(name);
    }
}
