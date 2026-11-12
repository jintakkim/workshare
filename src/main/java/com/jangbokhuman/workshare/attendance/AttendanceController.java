package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.RequestPaths;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AttendanceController {
    private final LeaveRequestService leaveRequestService;
    private final TeamLeaveCalendarService teamLeaveCalendarService;
    private final WorkTime workTime;

    @GetMapping("/leave/team")
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

    @PutMapping("/leave/{date}")
    public String saveLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            RedirectAttributes flash
    ) {
        leaveRequestService.save(principal.getEmployeeId(), date, attendance);
        flash.addFlashAttribute("success", "근무 상태를 저장했습니다. 모든 활동에 공통으로 적용합니다.");
        List<String> duties = leaveRequestService.findConflictingDuties(principal.getEmployeeId(), date, attendance);
        if (!duties.isEmpty()) {
            flash.addFlashAttribute("warning", date.getMonthValue() + "월 " + date.getDayOfMonth() + "일은 " + String.join(", ", duties)
                    + " 날이에요. 지원일을 바꾸거나 관리자에게 알려 주세요.");
        }
        flash.addAttribute("date", date.toString());
        return "redirect:/leave#day-details";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        return redirectToLeaveCalendar(error.getMessage(), request, flash);
    }

    private String redirectToLeaveCalendar(String message, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", message);
        RequestPaths.findPathVariable(request, "date").ifPresent(date -> flash.addAttribute("date", date));
        return "redirect:/leave#day-details";
    }
}
