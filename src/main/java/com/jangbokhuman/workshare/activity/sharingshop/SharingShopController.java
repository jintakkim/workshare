package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
public class SharingShopController {
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final SharingShopTimePreferenceService sharingShopTimePreferenceService;

    @GetMapping("/sharing-shop")
    public String showSharingShop(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, Model model) {
        return renderSharingShopPage(authenticatedUser, model, false);
    }

    @GetMapping("/admin/sharing-shop")
    public String showSharingShopManagement(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, Model model) {
        return renderSharingShopPage(authenticatedUser, model, true);
    }

    @PostMapping("/sharing-shop/time-preference")
    public String saveTimePreference(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Preference timePreference,
            RedirectAttributes flash
    ) {
        sharingShopTimePreferenceService.save(authenticatedUser.getEmployeeId(), timePreference);
        flash.addFlashAttribute("success", "나눔가게 시간대 선호도를 저장했습니다. 다음 추첨부터 반영됩니다.");
        return "redirect:/sharing-shop";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/sharing-shop";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleConcurrentSave(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "같은 정보가 동시에 저장되었습니다. 새로고침 후 다시 저장해주세요.");
        return "redirect:/sharing-shop";
    }

    private String renderSharingShopPage(AuthenticatedUser authenticatedUser, Model model, boolean management) {
        boolean admin = authenticatedUser.isAdmin();
        model.addAttribute("admin", admin);
        if (!admin) {
            model.addAttribute("timePreferenceSettings", sharingShopTimePreferenceService.getTimePreferenceSettings(authenticatedUser.getEmployeeId()));
            model.addAttribute("timePreferenceChoices", Preference.values());
        }
        model.addAttribute("management", management);
        model.addAttribute("section", "sharing");
        model.addAttribute("today", workTime.getToday());
        model.addAttribute("leaveCutoff", workSettings.leaveCutoff());
        model.addAttribute("allocationResultTime", workSettings.allocationResultTime());
        model.addAttribute("dates", IntStream.rangeClosed(-15, 15)
                .mapToObj(offset -> workTime.getToday().plusDays(offset))
                .toList()
        );
        return "sharing-shop";
    }
}
