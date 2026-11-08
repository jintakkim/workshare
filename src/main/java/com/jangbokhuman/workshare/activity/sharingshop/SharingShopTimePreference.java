package com.jangbokhuman.workshare.activity.sharingshop;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "employee_id"))
public class SharingShopTimePreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Preference timePreference;

    public SharingShopTimePreference(Long employeeId) {
        this.employeeId = employeeId;
    }

    public void changeTimePreference(Preference timePreference) {
        this.timePreference = timePreference;
    }
}
