package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class AttendanceController {
    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";
    private static final String ACTIVITY_CODE_PATTERN = "[A-Z][A-Z0-9_]*";

    private final LeaveRequestService leaveRequestService;
    private final TeamLeaveCalendarService teamLeaveCalendarService;
    private final WorkTime workTime;

    @GetMapping("/leave-calendar")
    public String showTeamLeaveCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser authentication,
            Model model
    ) {
        model.addAttribute("view", teamLeaveCalendarService.getCalendar(date == null ? workTime.getToday() : date));
        model.addAttribute("admin", authentication.isAdmin());
        model.addAttribute("section", "team-leave");
        return "team-leave";
    }

    @PostMapping("/leave-requests")
    public String saveLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            RedirectAttributes flash
    ) {
        leaveRequestService.save(principal.getEmployeeId(), date, attendance);
        flash.addFlashAttribute("success", "근무 상태를 저장했습니다. 모든 활동에 공통으로 적용합니다.");
        flash.addAttribute("date", date.toString());
        flash.addAttribute("activityCode", activityCode);
        return "redirect:/my-leave#day-details";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        return redirectToLeaveCalendar(error.getMessage(), request, flash);
    }

    private String redirectToLeaveCalendar(String message, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", message);
        String date = request.getParameter("date");
        if (date != null && date.matches(DATE_PATTERN)) {
            flash.addAttribute("date", date);
        }
        String activityCode = request.getParameter("activityCode");
        if (activityCode != null && activityCode.matches(ACTIVITY_CODE_PATTERN)) {
            flash.addAttribute("activityCode", activityCode);
        }
        return "redirect:/my-leave#day-details";
    }
}
