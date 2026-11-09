package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CafeteriaSupportAdminController {
    private static final String MONTH_PATTERN = "\\d{4}-\\d{2}";

    private final CafeteriaSupportScheduleService cafeteriaSupportScheduleService;
    private final WorkTime workTime;

    @GetMapping("/admin/supports")
    public String showSupportMonth(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            Model model
    ) {
        YearMonth selectedMonth = month == null ? YearMonth.from(workTime.getToday()) : month;
        model.addAttribute("view", cafeteriaSupportScheduleService.getMonth(selectedMonth));
        model.addAttribute("section", "supports");
        return "admin-supports";
    }

    @PostMapping("/admin/supports/month")
    public String saveMonth(
            @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @RequestParam MultiValueMap<String, String> params,
            RedirectAttributes flash
    ) {
        LinkedHashMap<LocalDate, List<Long>> changes = new LinkedHashMap<>();
        for (String date : params.getOrDefault("editedDates", List.of())) {
            changes.put(parseDate(date), parseEmployeeIds(params.getOrDefault("day_" + date, List.of())));
        }
        cafeteriaSupportScheduleService.saveMonth(month, changes);
        flash.addFlashAttribute("success", "월별 지원표를 저장했습니다.");
        flash.addAttribute("month", month.toString());
        return "redirect:/admin/supports";
    }

    @PostMapping("/admin/supports/requirements")
    public String changeRequiredWorkers(@RequestParam int requiredWorkers, RedirectAttributes flash) {
        cafeteriaSupportScheduleService.changeRequiredWorkers(requiredWorkers);
        flash.addFlashAttribute("success", "지원 필요 인원을 저장했습니다. 기존 지원 명단은 자동 변경하지 않습니다.");
        return "redirect:/admin/supports";
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원표 날짜를 확인해주세요.", error);
        }
    }

    private List<Long> parseEmployeeIds(List<String> employeeIds) {
        try {
            return employeeIds.stream()
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원 직원을 다시 선택해주세요.", error);
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        return redirectToSupportMonth(error.getMessage(), request, flash);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleConcurrentSave(HttpServletRequest request, RedirectAttributes flash) {
        return redirectToSupportMonth("같은 정보가 동시에 저장되었습니다. 새로고침 후 다시 저장해주세요.", request, flash);
    }

    private String redirectToSupportMonth(String message, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", message);
        String month = request.getParameter("month");
        if (request.getMethod().equals("POST") && month != null && month.matches(MONTH_PATTERN)) {
            flash.addAttribute("month", month);
        }
        return "redirect:/admin/supports";
    }
}
