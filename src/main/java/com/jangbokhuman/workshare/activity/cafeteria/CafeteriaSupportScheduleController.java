package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CafeteriaSupportScheduleController {
    private final CafeteriaSupportQueryService cafeteriaSupportQueryService;
    private final WorkTime workTime;

    @GetMapping("/cafeteria-support/schedule")
    public String showSchedule(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model
    ) {
        YearMonth selectedMonth = month == null ? YearMonth.from(workTime.getToday()) : month;
        model.addAttribute("month", selectedMonth);
        List<CafeteriaSupportQueryService.ScheduleWeek> weeks = cafeteriaSupportQueryService.getScheduleWeeks(selectedMonth);
        String myName = user.isAdmin() ? null : user.getUsername();
        model.addAttribute("weeks", weeks);
        model.addAttribute("today", workTime.getToday());
        model.addAttribute("myName", myName);
        model.addAttribute("myDayCount", countDaysOf(weeks, myName));
        model.addAttribute("admin", user.isAdmin());
        model.addAttribute("section", "cafeteria-support");
        return "cafeteria-support-schedule";
    }

    private long countDaysOf(List<CafeteriaSupportQueryService.ScheduleWeek> weeks, String employeeName) {
        return weeks.stream()
                .flatMap(week -> week.days().stream())
                .filter(day -> day.employeeNames().contains(employeeName))
                .count();
    }
}
