package com.jangbokhuman.workshare.calendar;

import com.jangbokhuman.workshare.common.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class HolidayAdminController {
    private final HolidayService holidayService;

    @PutMapping("/admin/holidays/{date}")
    public String addHoliday(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String name,
            RedirectAttributes flash
    ) {
        holidayService.addHoliday(date, name);
        flash.addFlashAttribute("success", "휴일을 저장했습니다. 기존 신청은 보관되고, 해당 날짜 배정은 진행하지 않습니다.");
        return "redirect:/admin/holidays?date=" + date;
    }

    @DeleteMapping("/admin/holidays/{date}")
    public String removeHoliday(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes flash
    ) {
        holidayService.removeHoliday(date);
        flash.addFlashAttribute("success", "추가 휴일을 취소했습니다. 평일이면 배정이 다시 활성화됩니다.");
        return "redirect:/admin/holidays?date=" + date;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/admin/holidays";
    }
}
