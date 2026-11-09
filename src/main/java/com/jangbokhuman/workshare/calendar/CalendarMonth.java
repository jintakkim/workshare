package com.jangbokhuman.workshare.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public record CalendarMonth(YearMonth month) {
    private static final DayOfWeek FIRST_DAY_OF_WEEK = DayOfWeek.SUNDAY;
    private static final DayOfWeek LAST_DAY_OF_WEEK = DayOfWeek.SATURDAY;

    public static CalendarMonth of(LocalDate date) {
        return new CalendarMonth(YearMonth.from(date));
    }

    public LocalDate getFirstDay() {
        return month.atDay(1);
    }

    public LocalDate getLastDay() {
        return month.atEndOfMonth();
    }

    public LocalDate getGridStart() {
        return getFirstDay().with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK));
    }

    public LocalDate getGridEnd() {
        return getLastDay().with(TemporalAdjusters.nextOrSame(LAST_DAY_OF_WEEK));
    }

    public List<LocalDate> getGridDates() {
        return getGridStart().datesUntil(getGridEnd().plusDays(1)).toList();
    }

    public boolean contains(LocalDate date) {
        return YearMonth.from(date).equals(month);
    }

    public LocalDate getPreviousMonthStart() {
        return month.minusMonths(1).atDay(1);
    }

    public LocalDate getNextMonthStart() {
        return month.plusMonths(1).atDay(1);
    }
}
