package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.RequestPaths;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CafeteriaSupportAdminController {
    private final CafeteriaSupportScheduleService cafeteriaSupportScheduleService;
    private final CafeteriaSupportQueryService cafeteriaSupportQueryService;
    private final WorkTime workTime;

    @GetMapping("/admin/cafeteria-support")
    public String showSupportMonth(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            Model model
    ) {
        YearMonth selectedMonth = month == null ? YearMonth.from(workTime.getToday()) : month;
        model.addAttribute("view", cafeteriaSupportQueryService.getMonth(selectedMonth));
        model.addAttribute("section", "cafeteria-support");
        return "admin-cafeteria-support";
    }

    @PutMapping("/admin/cafeteria-support/{month}")
    public String saveMonth(
            @PathVariable @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) List<LocalDate> editedDates,
            @RequestParam MultiValueMap<String, String> params,
            RedirectAttributes flash
    ) {
        LinkedHashMap<LocalDate, List<Long>> changes = new LinkedHashMap<>();
        for (LocalDate date : editedDates == null ? List.<LocalDate>of() : editedDates) {
            changes.put(date, parseEmployeeIds(params.getOrDefault("day_" + date, List.of())));
        }
        cafeteriaSupportScheduleService.saveMonth(month, changes);
        flash.addFlashAttribute("success", "월별 지원표를 저장했습니다.");
        flash.addAttribute("month", month.toString());
        return "redirect:/admin/cafeteria-support";
    }

    @PutMapping("/admin/cafeteria-support/requirement")
    public String changeRequiredWorkers(@RequestParam int requiredWorkers, RedirectAttributes flash) {
        cafeteriaSupportScheduleService.changeRequiredWorkers(requiredWorkers);
        flash.addFlashAttribute("success", "지원 필요 인원을 저장했습니다. 기존 지원 명단은 자동 변경하지 않습니다.");
        return "redirect:/admin/cafeteria-support";
    }

    private List<Long> parseEmployeeIds(List<String> employeeIds) {
        try {
            return employeeIds.stream()
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 직원 ID 형식이 올바르지 않습니다.", error);
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        return redirectToSupportMonth(error.getMessage(), request, flash);
    }

    private String redirectToSupportMonth(String message, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", message);
        RequestPaths.findPathVariable(request, "month").ifPresent(month -> flash.addAttribute("month", month));
        return "redirect:/admin/cafeteria-support";
    }
}
