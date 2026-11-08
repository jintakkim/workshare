package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.WorkTaskService;
import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.calendar.HolidayService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.ResponsibilityCatalogService;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequestService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
public class AdminController {
    private final RosterService rosterService;
    private final HolidayService holidayService;
    private final WorkQueryService workQueryService;
    private final WorkTime workTime;
    private final WorkTaskService workTaskService;
    private final LeaveRequestService leaveRequestService;
    private final ResponsibilityCatalogService responsibilityCatalogService;
    private final ActivityManagementService activityManagementService;
    private final HolidayCalendarService holidayCalendarService;

    @GetMapping("/admin")
    public String showAdminPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            @RequestParam(defaultValue = "leave") String section,
            @RequestParam(required = false) Long employeeId,
            Model model) {
        if (!java.util.Set.of("leave", "employees", "tasks", "holidays", "activities")
                .contains(section)) {
            throw new IllegalArgumentException("관리 메뉴를 선택하세요.");
        }
        List<RosterService.EmployeeView> staff = rosterService.getEmployeeViews();
        LocalDate selectedDate = date == null ? workTime.getToday() : date;
        RosterService.EmployeeView selected =
                employeeId == null
                        ? staff.stream()
                                .filter(
                                        e ->
                                                !selectedDate.isBefore(e.activeFrom())
                                                        && (e.inactiveFrom() == null
                                                                || selectedDate.isBefore(
                                                                        e.inactiveFrom())))
                                .findFirst()
                                .orElse(staff.isEmpty() ? null : staff.getFirst())
                        : staff.stream()
                                .filter(e -> e.id().equals(employeeId))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("직원을 선택하세요."));
        if (section.equals("activities"))
            model.addAttribute("album", activityManagementService.getCards());
        else if (section.equals("holidays"))
            model.addAttribute("view", holidayCalendarService.getHolidayCalendar(selectedDate));
        else if (!section.equals("employees"))
            model.addAttribute(
                    "view",
                    workQueryService.getDashboard(
                            selectedDate, selected == null ? null : selected.id(), activityCode));
        model.addAttribute("employees", staff);
        model.addAttribute("selectedEmployeeId", selected == null ? null : selected.id());
        model.addAttribute("section", section);
        model.addAttribute("attendances", Attendance.values());
        model.addAttribute("effectiveDate", rosterService.getRosterChangeEffectiveDate());
        model.addAttribute("periods", java.util.List.of(WorkPeriod.MORNING, WorkPeriod.EVENING));
        model.addAttribute("groups", WorkGroupType.values());
        model.addAttribute("responsibilityOptions", responsibilityCatalogService.getChoices());
        model.addAttribute("activities", Activity.values());
        return "admin";
    }

    @PostMapping("/admin/leave-requests")
    public String saveEmployeeLeave(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Attendance attendance,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            RedirectAttributes flash) {
        leaveRequestService.save(employeeId, date, attendance);
        flash.addFlashAttribute("success", "직원의 근무 상태를 저장했습니다.");
        flash.addAttribute("section", "leave");
        flash.addAttribute("employeeId", employeeId);
        flash.addAttribute("date", date.toString());
        flash.addAttribute("activityCode", activityCode);
        return "redirect:/admin#day-details";
    }

    @PostMapping("/admin/employees")
    public String registerEmployee(
            @RequestParam String name,
            @RequestParam String password,
            @RequestParam String workGroupCode,
            @RequestParam(required = false) java.util.List<String> responsibilities,
            RedirectAttributes flash) {
        rosterService.registerEmployee(name, password, workGroupCode, responsibilities);
        flash.addFlashAttribute(
                "success", "직원을 등록했습니다. 적용일: " + rosterService.getRosterChangeEffectiveDate());
        return "redirect:/admin?section=employees";
    }

    @PostMapping("/admin/employees/{id}/retire")
    public String retire(@PathVariable Long id, RedirectAttributes flash) {
        rosterService.retireEmployee(id);
        flash.addFlashAttribute(
                "success", "직원 제외를 등록했습니다. 적용일: " + rosterService.getRosterChangeEffectiveDate());
        return "redirect:/admin?section=employees";
    }

    @PostMapping("/admin/holidays")
    public String addHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String name,
            RedirectAttributes flash) {
        holidayService.addHoliday(date, name);
        flash.addFlashAttribute("success", "휴일을 저장했습니다. 기존 신청은 보관되고, 해당 날짜 배정은 진행하지 않습니다.");
        return "redirect:/admin?section=holidays&date=" + date;
    }

    @PostMapping("/admin/holidays/remove")
    public String removeHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes flash) {
        holidayService.removeHoliday(date);
        flash.addFlashAttribute("success", "추가 휴일을 취소했습니다. 평일이면 배정이 다시 활성화됩니다.");
        return "redirect:/admin?section=holidays&date=" + date;
    }

    @PostMapping("/admin/tasks")
    public String addTask(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String name,
            @RequestParam WorkPeriod period,
            @RequestParam int requiredWorkers,
            @RequestParam String activityCode,
            RedirectAttributes flash) {
        workTaskService.addTask(activityCode, date, name, period, requiredWorkers);
        flash.addFlashAttribute(
                "success", "업무를 추가했습니다. 당일 배정 시각 이후라면 바로 배정하고, 미래 업무는 당일 자동 배정합니다.");
        flash.addAttribute("section", "tasks");
        flash.addAttribute("date", date.toString());
        flash.addAttribute("activityCode", activityCode);
        return "redirect:/admin";
    }

    @PostMapping("/admin/tasks/{id}/remove")
    public String removeTask(
            @PathVariable Long id,
            @RequestParam(defaultValue = "SHARING_SHOP") String activityCode,
            RedirectAttributes flash) {
        LocalDate date = workTaskService.removePendingTask(id);
        flash.addFlashAttribute("success", "배정 대기 중인 업무를 취소했습니다.");
        flash.addAttribute("section", "tasks");
        flash.addAttribute("date", date.toString());
        flash.addAttribute("activityCode", activityCode);
        return "redirect:/admin";
    }

    @PostMapping("/admin/employees/{id}/work-group")
    public String changeWorkGroup(
            @PathVariable Long id, @RequestParam String workGroupCode, RedirectAttributes flash) {
        List<Activity> removedActivities = rosterService.changeWorkGroup(id, workGroupCode);
        flash.addFlashAttribute("success", "직군을 변경했습니다. 이미 확정된 배정은 유지합니다.");
        if (!removedActivities.isEmpty()) {
            String activityNames = removedActivities.stream()
                    .map(Activity::getName)
                    .collect(Collectors.joining(", "));
            flash.addFlashAttribute("warning", "직군 변경으로 " + activityNames + "에서 자동 탈퇴했습니다.");
        }
        return "redirect:/admin/employees/" + id;
    }

    @PostMapping("/admin/responsibility-options")
    public String addResponsibilityOption(@RequestParam String title, RedirectAttributes flash) {
        responsibilityCatalogService.add(title);
        flash.addFlashAttribute("success", "선택할 업무를 추가했습니다.");
        return "redirect:/admin?section=employees";
    }
}
