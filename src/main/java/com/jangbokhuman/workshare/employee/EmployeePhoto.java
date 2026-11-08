package com.jangbokhuman.workshare.employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class EmployeePhoto {
    @Id private Long employeeId;

    @Getter(AccessLevel.NONE)
    @Lob
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private byte[] image;

    public EmployeePhoto(Long employeeId, byte[] image) {
        this.employeeId = employeeId;
        this.image = image.clone();
    }

    public byte[] getImage() {
        return image.clone();
    }
}
