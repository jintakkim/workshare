package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.*;

@RequiredArgsConstructor
@Component
public class BusinessCalendar {
    private final HolidayRepository holidayRepository;

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public boolean isWorkingDay(LocalDate date) {
        return !isWeekend(date) && !holidayRepository.existsById(date);
    }
}
