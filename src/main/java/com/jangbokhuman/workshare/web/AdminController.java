package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveCalendarService;
import com.jangbokhuman.workshare.attendance.LeaveRequestService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    public String showAdminPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "section", defaultValue = "leave") String sectionCode,
            @RequestParam(required = false) Long employeeId,
            Model model
    ) {
        AdminSection section = AdminSection.getByCode(sectionCode);
        List<RosterService.EmployeeView> staff = rosterService.getEmployeeViews();
        LocalDate selectedDate = date == null ? workTime.getToday() : date;
        RosterService.EmployeeView selected = findSelectedEmployee(staff, employeeId, selectedDate);
        switch (section) {
            case ACTIVITIES -> model.addAttribute("album", activityManagementService.getCards());
            case HOLIDAYS -> model.addAttribute("view", holidayCalendarService.getHolidayCalendar(selectedDate));
            case LEAVE -> model.addAttribute(
                    "view",
                    leaveCalendarService.getCalendar(selectedDate, selected == null ? null : selected.id())
            );
            case EMPLOYEES -> {
            }
        }
        model.addAttribute("employees", staff);
        model.addAttribute("selectedEmployeeId", selected == null ? null : selected.id());
        model.addAttribute("section", section.getCode());
        model.addAttribute("attendances", Attendance.values());
        model.addAttribute("groups", WorkGroupType.values());
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

    @PostMapping("/admin/leave-requests")
    public String saveEmployeeLeave(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            RedirectAttributes flash
    ) {
        leaveRequestService.save(employeeId, date, attendance);
        flash.addFlashAttribute("success", "직원의 근무 상태를 저장했습니다.");
        flash.addAttribute("section", AdminSection.LEAVE.getCode());
        flash.addAttribute("employeeId", employeeId);
        flash.addAttribute("date", date.toString());
        return "redirect:/admin#day-details";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/admin";
    }
}
