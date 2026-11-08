package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.calendar.WorkTime;

import com.jangbokhuman.workshare.activity.Activity;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.*;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class AdminSupportController {
    private final ExclusionManagementService exclusionManagementService;
    private final WorkTime workTime;

    @GetMapping("/admin/supports")
    public String showSupportMonth(
            @RequestParam(required = false) Long reasonId,
            @RequestParam(required = false) String month,
            Model model) {
        model.addAttribute(
                "view",
                exclusionManagementService.getSupportMonth(
                        reasonId,
                        month == null
                                ? YearMonth.from(workTime.getToday())
                                : YearMonth.parse(month)));
        model.addAttribute("section", "supports");
        model.addAttribute("activities", Activity.values());
        return "admin-supports";
    }

    @PostMapping("/admin/supports/month")
    public String save(
            @RequestParam Long reasonId,
            @RequestParam String month,
            @RequestParam MultiValueMap<String, String> params,
            RedirectAttributes flash) {
        LinkedHashMap<LocalDate, List<Long>> changes = new LinkedHashMap<LocalDate, List<Long>>();
        for (String date : params.getOrDefault("editedDates", List.of()))
            changes.put(
                    LocalDate.parse(date),
                    params.getOrDefault("day_" + date, List.of()).stream()
                            .map(Long::valueOf)
                            .toList());
        exclusionManagementService.saveMonth(reasonId, YearMonth.parse(month), changes);
        flash.addFlashAttribute("success", "월별 지원표를 저장했습니다.");
        flash.addAttribute("reasonId", reasonId);
        flash.addAttribute("month", month);
        return "redirect:/admin/supports";
    }

    @PostMapping("/admin/supports/reasons")
    public String addExclusionReason(
            @RequestParam String activityCode,
            @RequestParam String name,
            @RequestParam int requiredWorkers,
            RedirectAttributes flash) {
        flash.addAttribute(
                "reasonId",
                exclusionManagementService.addReason(activityCode, name, requiredWorkers));
        flash.addFlashAttribute("success", "제외 사유를 추가했습니다.");
        return "redirect:/admin/supports";
    }

    @PostMapping("/admin/supports/requirements")
    public String changeRequiredWorkers(
            @RequestParam Long reasonId,
            @RequestParam int requiredWorkers,
            RedirectAttributes flash) {
        exclusionManagementService.changeRequired(reasonId, requiredWorkers);
        flash.addAttribute("reasonId", reasonId);
        flash.addFlashAttribute("success", "지원 필요 인원을 저장했습니다. 기존 지원 명단은 자동 변경하지 않습니다.");
        return "redirect:/admin/supports";
    }

}
