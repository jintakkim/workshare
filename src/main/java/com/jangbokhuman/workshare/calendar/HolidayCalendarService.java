package com.jangbokhuman.workshare.calendar;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HolidayCalendarService {
    private final HolidayRepository holidayRepository;
    private final DailyAllocationStatus dailyAllocationStatus;
    private final WorkTime workTime;

    public Month getHolidayCalendar(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        List<Holiday> registered = holidayRepository.findByDateBetweenOrderByDate(month.atDay(1), month.atEndOfMonth());
        Map<LocalDate, String> reasons = registered.stream().collect(Collectors.toMap(Holiday::getDate, Holiday::getName));
        LocalDate start = month.atDay(1).minusDays(month.atDay(1).getDayOfWeek().getValue() % 7);
        int length = ((month.lengthOfMonth() + month.atDay(1).getDayOfWeek().getValue() % 7 + 6) / 7) * 7;
        ArrayList<Cell> cells = new ArrayList<Cell>();
        for (int i = 0; i < length; i++) {
            LocalDate day = start.plusDays(i);
            cells.add(new Cell(
                    day,
                    YearMonth.from(day).equals(month),
                    day.equals(date),
                    day.equals(workTime.getToday()),
                    BusinessCalendar.isWeekend(day),
                    reasons.containsKey(day),
                    reasons.get(day)));
        }
        return new Month(
                date,
                workTime.getToday(),
                month,
                cells,
                registered,
                reasons.get(date),
                workTime.isBeforeScheduleChangeDeadline(date)
                        && !dailyAllocationStatus.isAllocated(date),
                month.minusMonths(1).atDay(1),
                month.plusMonths(1).atDay(1));
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
