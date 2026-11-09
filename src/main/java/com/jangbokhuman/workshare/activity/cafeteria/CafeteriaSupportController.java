package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
public class CafeteriaSupportController {
    private final CafeteriaSupportExchangeService cafeteriaSupportExchangeService;
    private final CafeteriaSupportScheduleService cafeteriaSupportScheduleService;
    private final WorkTime workTime;

    @GetMapping("/supports")
    public String showSupportPage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long sourceId,
            Model model
    ) {
        model.addAttribute("view", cafeteriaSupportExchangeService.getExchangePage(user.getEmployeeId(), sourceId));
        model.addAttribute("admin", false);
        model.addAttribute("section", "supports");
        return "supports";
    }

    @GetMapping("/supports/cafeteria")
    public String showCafeteriaSupport(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model
    ) {
        YearMonth selectedMonth = month == null ? YearMonth.from(workTime.getToday()) : month;
        model.addAttribute("month", selectedMonth);
        model.addAttribute("days", cafeteriaSupportScheduleService.getSchedule(selectedMonth));
        model.addAttribute("admin", user.isAdmin());
        model.addAttribute("section", "cafeteria-support");
        return "cafeteria-support";
    }

    @PostMapping("/supports/requests")
    public String request(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long sourceId,
            @RequestParam Long targetId,
            RedirectAttributes flash
    ) {
        cafeteriaSupportExchangeService.request(user.getEmployeeId(), sourceId, targetId);
        flash.addFlashAttribute("success", "상대 직원에게 지원일 교환을 요청했습니다. 수락 전까지 기존 지원표를 유지합니다.");
        return "redirect:/supports";
    }

    @PostMapping("/supports/requests/{id}/accept")
    public String accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes flash) {
        cafeteriaSupportExchangeService.accept(user.getEmployeeId(), id);
        flash.addFlashAttribute("success", "교환을 수락했습니다. 두 날짜의 지원 담당자가 변경되었습니다.");
        return "redirect:/supports";
    }

    @PostMapping("/supports/requests/{id}/dismiss")
    public String dismiss(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes flash) {
        cafeteriaSupportExchangeService.dismiss(user.getEmployeeId(), id);
        flash.addFlashAttribute("success", "교환 요청을 종료했습니다.");
        return "redirect:/supports";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/supports";
    }
}
