package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class ExclusionReason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Activity activityCode;

    @Column(nullable = false, length = 60)
    private String name;

    public ExclusionReason(Activity activityCode, String name) {
        this.activityCode = activityCode;
        this.name = name;
    }
}
