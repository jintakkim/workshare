package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
public class SupportController {
    private final SupportExchangeService supportExchangeService;
    private final DailyExclusionReader dailyExclusionReader;
    private final WorkTime workTime;

    @GetMapping("/supports/cafeteria")
    public String showCafeteriaSupport(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            Model model
    ) {
        YearMonth selectedMonth = month == null ? YearMonth.from(workTime.getToday()) : month;
        model.addAttribute("month", selectedMonth);
        model.addAttribute("days", dailyExclusionReader.getSupportDays(Activity.SHARING_SHOP, "식당지원", selectedMonth));
        model.addAttribute("admin", false);
        model.addAttribute("section", "cafeteria-support");
        return "cafeteria-support";
    }

    @GetMapping("/supports")
    public String showSupportPage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long sourceId,
            Model model) {
        model.addAttribute(
                "view", supportExchangeService.getExchangePage(user.getEmployeeId(), sourceId));
        model.addAttribute("admin", false);
        model.addAttribute("section", "supports");
        return "supports";
    }

    @PostMapping("/supports/requests")
    public String request(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long sourceId,
            @RequestParam Long targetId,
            RedirectAttributes flash) {
        supportExchangeService.request(user.getEmployeeId(), sourceId, targetId);
        flash.addFlashAttribute("success", "상대 직원에게 지원일 교환을 요청했습니다. 수락 전까지 기존 지원표를 유지합니다.");
        return "redirect:/supports";
    }

    @PostMapping("/supports/requests/{id}/accept")
    public String accept(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            RedirectAttributes flash) {
        supportExchangeService.accept(user.getEmployeeId(), id);
        flash.addFlashAttribute("success", "교환을 수락했습니다. 두 날짜의 지원 담당자가 변경되었습니다.");
        return "redirect:/supports";
    }

    @PostMapping("/supports/requests/{id}/dismiss")
    public String dismiss(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            RedirectAttributes flash) {
        supportExchangeService.dismiss(user.getEmployeeId(), id);
        flash.addFlashAttribute("success", "교환 요청을 종료했습니다.");
        return "redirect:/supports";
    }
}
