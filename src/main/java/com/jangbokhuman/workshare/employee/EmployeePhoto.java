package com.jangbokhuman.workshare.employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class EmployeePhoto {
    @Id
    private Long employeeId;

    @Getter(AccessLevel.NONE)
    @Lob
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private byte[] image;

    @Column(nullable = false)
    private Instant savedAt;

    public EmployeePhoto(Long employeeId, byte[] image, Instant savedAt) {
        this.employeeId = employeeId;
        this.image = image.clone();
        this.savedAt = savedAt;
    }

    public byte[] getImage() {
        return image.clone();
    }
}
