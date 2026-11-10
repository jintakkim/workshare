package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.common.RequestPaths;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveCalendarService;
import com.jangbokhuman.workshare.attendance.LeaveRequestService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
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

@RequiredArgsConstructor
@Controller
public class AdminController {
    private final RosterService rosterService;
    private final LeaveCalendarService leaveCalendarService;
    private final WorkTime workTime;
    private final LeaveRequestService leaveRequestService;
    private final ActivityManagementService activityManagementService;
    private final HolidayCalendarService holidayCalendarService;

    @GetMapping("/admin")
    public String redirectToLeaveSection() {
        return "redirect:/admin/leave";
    }

    @GetMapping("/admin/leave")
    public String showLeaveSection(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeeId,
            Model model
    ) {
        List<RosterService.EmployeeView> staff = rosterService.getEmployeeViews();
        LocalDate selectedDate = date == null ? workTime.getToday() : date;
        RosterService.EmployeeView selected = findSelectedEmployee(staff, employeeId, selectedDate);
        model.addAttribute("view", leaveCalendarService.getCalendar(selectedDate, selected == null ? null : selected.id()));
        model.addAttribute("selectedEmployeeId", selected == null ? null : selected.id());
        model.addAttribute("attendances", Attendance.values());
        return renderAdminPage("leave", staff, model);
    }

    @GetMapping("/admin/employees")
    public String showEmployeeSection(Model model) {
        model.addAttribute("groups", WorkGroupType.values());
        return renderAdminPage("employees", rosterService.getEmployeeViews(), model);
    }

    @GetMapping("/admin/holidays")
    public String showHolidaySection(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model
    ) {
        model.addAttribute("view", holidayCalendarService.getHolidayCalendar(date == null ? workTime.getToday() : date));
        return renderAdminPage("holidays", rosterService.getEmployeeViews(), model);
    }

    @GetMapping("/admin/activities")
    public String showActivitySection(Model model) {
        model.addAttribute("album", activityManagementService.getCards());
        return renderAdminPage("activities", rosterService.getEmployeeViews(), model);
    }

    @PutMapping("/admin/employees/{employeeId}/leave/{date}")
    public String saveEmployeeLeave(
            @PathVariable Long employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            RedirectAttributes flash
    ) {
        leaveRequestService.save(employeeId, date, attendance);
        flash.addFlashAttribute("success", "직원의 근무 상태를 저장했습니다.");
        flash.addAttribute("employeeId", employeeId);
        flash.addAttribute("date", date.toString());
        return "redirect:/admin/leave#day-details";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        RequestPaths.findPathVariable(request, "employeeId").ifPresent(employeeId -> flash.addAttribute("employeeId", employeeId));
        RequestPaths.findPathVariable(request, "date").ifPresent(date -> flash.addAttribute("date", date));
        return "redirect:/admin/leave";
    }

    private String renderAdminPage(String section, List<RosterService.EmployeeView> staff, Model model) {
        model.addAttribute("employees", staff);
        model.addAttribute("section", section);
        return "admin";
    }

    private RosterService.EmployeeView findSelectedEmployee(
            List<RosterService.EmployeeView> staff,
            Long employeeId,
            LocalDate selectedDate
    ) {
        if (employeeId != null) {
            return staff.stream()
                    .filter(employee -> employee.id().equals(employeeId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "직원을 선택하세요."));
        }
        return staff.stream()
                .filter(employee -> !selectedDate.isBefore(employee.activeFrom()) && selectedDate.isBefore(employee.inactiveFrom()))
                .findFirst()
                .orElse(staff.isEmpty() ? null : staff.getFirst());
    }
}
