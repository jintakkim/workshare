package com.jangbokhuman.workshare.calendar;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@RequiredArgsConstructor
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HolidayService {
    private final HolidayRepository holidayRepository;
    private final WorkScheduleDeadlineValidator workScheduleDeadlineValidator;

    public void addHoliday(LocalDate date, String name) {
        workScheduleDeadlineValidator.validateChangeDeadline(date);
        String reason = name == null ? "" : name.strip();
        if (reason.length() > 60) throw new BusinessException(ErrorCode.INVALID_INPUT, "휴일 사유는 60자 이내로 입력해주세요.");
        holidayRepository.save(new Holiday(date, reason));
    }

    public void removeHoliday(LocalDate date) {
        workScheduleDeadlineValidator.validateChangeDeadline(date);
        holidayRepository.deleteById(date);
    }
}
