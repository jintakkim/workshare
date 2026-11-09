package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Set<LocalDate> findWorkingDays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = holidayRepository.findByDateBetweenOrderByDate(start, end).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
        return start.datesUntil(end.plusDays(1))
                .filter(date -> !isWeekend(date) && !holidays.contains(date))
                .collect(Collectors.toSet());
    }
}
