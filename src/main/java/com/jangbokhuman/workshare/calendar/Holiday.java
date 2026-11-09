package com.jangbokhuman.workshare.calendar;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class Holiday {
    @Id
    private LocalDate date;

    @Column(nullable = false, length = 60)
    private String name;

    public Holiday(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }
}
