package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.common.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/activities")
@RequiredArgsConstructor
public class ActivityAlbumController {
    private final ActivityManagementService activityManagementService;
    private final ActivityRegistrationService activityRegistrationService;

    @GetMapping("/{code}")
    public String showActivityDetails(@PathVariable String code, Model model) {
        model.addAttribute("activity", activityManagementService.getDetails(code));
        model.addAttribute("section", "activities");
        return "activity-detail";
    }

    @PutMapping("/{code}/members/{employeeId}")
    public String addRegistration(
            @PathVariable String code,
            @PathVariable Long employeeId,
            RedirectAttributes flash
    ) {
        activityRegistrationService.addRegistration(code, employeeId);
        flash.addFlashAttribute("success", "활동에 직원을 추가했습니다.");
        return "redirect:/admin/activities/" + code;
    }

    @DeleteMapping("/{code}/members/{employeeId}")
    public String deleteRegistration(
            @PathVariable String code,
            @PathVariable Long employeeId,
            RedirectAttributes flash
    ) {
        activityRegistrationService.deleteRegistration(code, employeeId);
        flash.addFlashAttribute("success", "활동에서 직원을 제외했습니다.");
        return "redirect:/admin/activities/" + code;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/admin/activities";
    }
}
