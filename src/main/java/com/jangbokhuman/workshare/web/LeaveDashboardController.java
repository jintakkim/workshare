package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveCalendarService;
import com.jangbokhuman.workshare.attendance.LeaveHistoryService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class LeaveDashboardController {
    private final LeaveCalendarService leaveCalendarService;
    private final LeaveHistoryService leaveHistoryService;
    private final WorkTime workTime;

    @GetMapping("/")
    public String redirectToLeaveCalendar() {
        return "redirect:/leave";
    }

    @GetMapping("/leave")
    public String showLeaveCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser authentication,
            Model model
    ) {
        model.addAttribute(
                "view",
                leaveCalendarService.getCalendar(date == null ? workTime.getToday() : date, authentication.getEmployeeId())
        );
        model.addAttribute("admin", authentication.isAdmin());
        model.addAttribute("attendances", Attendance.values());
        if (authentication.getEmployeeId() != null) {
            model.addAttribute("leaveHistory", leaveHistoryService.getHistory(authentication.getEmployeeId()));
        }
        model.addAttribute("section", "leave");
        return "index";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/leave";
    }
}
