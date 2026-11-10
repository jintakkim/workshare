package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HolidayCalendarService {
    private final HolidayRepository holidayRepository;
    private final DailyAllocationStatus dailyAllocationStatus;
    private final WorkTime workTime;

    public Month getHolidayCalendar(LocalDate selectedDate) {
        CalendarMonth calendarMonth = CalendarMonth.of(selectedDate);
        LocalDate today = workTime.getToday();
        List<Holiday> gridHolidays = holidayRepository.findByDateBetweenOrderByDate(calendarMonth.getGridStart(), calendarMonth.getGridEnd());
        Map<LocalDate, String> holidayNames = gridHolidays.stream()
                .collect(Collectors.toMap(Holiday::getDate, Holiday::getName));
        List<Holiday> monthHolidays = gridHolidays.stream()
                .filter(holiday -> calendarMonth.contains(holiday.getDate()))
                .toList();
        List<Cell> cells = calendarMonth.getGridDates().stream()
                .map(date -> new Cell(
                        date,
                        calendarMonth.contains(date),
                        date.equals(selectedDate),
                        date.equals(today),
                        BusinessCalendar.isWeekend(date),
                        holidayNames.containsKey(date),
                        holidayNames.get(date))
                )
                .toList();
        return new Month(
                selectedDate,
                today,
                calendarMonth.month(),
                cells,
                monthHolidays,
                holidayNames.get(selectedDate),
                canChangeHoliday(selectedDate),
                calendarMonth.getPreviousMonthStart(),
                calendarMonth.getNextMonthStart()
        );
    }

    private boolean canChangeHoliday(LocalDate date) {
        return workTime.isBeforeScheduleChangeDeadline(date) && !dailyAllocationStatus.hasAnyAllocation(date);
    }

    public record Cell(
            LocalDate date,
            boolean inMonth,
            boolean selected,
            boolean today,
            boolean weekend,
            boolean registered,
            String reason
    ) {}

    public record Month(
            LocalDate date,
            LocalDate today,
            YearMonth month,
            List<Cell> cells,
            List<Holiday> holidays,
            String holidayName,
            boolean holidayEditable,
            LocalDate previousMonth,
            LocalDate nextMonth
    ) {}
}
