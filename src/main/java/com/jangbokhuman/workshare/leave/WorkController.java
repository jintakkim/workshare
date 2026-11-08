package com.jangbokhuman.workshare.leave;

import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class WorkController {
    private final LeaveRequestService leaveRequestService;
    private final WorkQueryService workQueryService;
    private final WorkTime workTime;

    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    @GetMapping({"/", "/my-leave"})
    public String showLeaveCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            @AuthenticationPrincipal AuthenticatedUser authentication,
            Model model) {
        model.addAttribute(
                "view",
                workQueryService.getDashboard(
                        date == null ? workTime.getToday() : date,
                        authentication.getEmployeeId(),
                        activityCode));
        model.addAttribute(
                "admin",
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        model.addAttribute("attendances", Attendance.values());
        model.addAttribute("section", "leave");
        return "index";
    }

    @PostMapping("/leave-requests")
    public String saveLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            RedirectAttributes flash) {
        leaveRequestService.save(principal.getEmployeeId(), date, attendance);
        flash.addFlashAttribute("success", "근무 상태를 저장했습니다. 모든 활동에 공통으로 적용합니다.");
        flash.addAttribute("date", date.toString());
        flash.addAttribute("activityCode", activityCode);
        return "redirect:/my-leave#day-details";
    }
}
