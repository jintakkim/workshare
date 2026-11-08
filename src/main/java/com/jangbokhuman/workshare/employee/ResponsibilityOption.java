package com.jangbokhuman.workshare.employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class ResponsibilityOption {
    @Id
    @Column(length = 60)
    private String title;

    public ResponsibilityOption(String title) {
        this.title = title;
    }
}
