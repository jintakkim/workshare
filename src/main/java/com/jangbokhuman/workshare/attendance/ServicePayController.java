package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
public class ServicePayController {
    private final ServicePayService servicePayService;
    private final EmployeeProfileService employeeProfileService;

    @GetMapping("/pay")
    public String showPayslip(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model
    ) {
        YearMonth payMonth = month == null ? servicePayService.getNextPayMonth() : month;
        model.addAttribute("payslip", servicePayService.getPayslip(user.getEmployeeId(), payMonth));
        model.addAttribute("nextPayMonth", servicePayService.getNextPayMonth());
        model.addAttribute("admin", false);
        model.addAttribute("section", "pay");
        return "pay";
    }

    @PutMapping("/pay/transport-fee")
    public String changeDailyTransportFee(
            @RequestParam(required = false) Integer dailyTransportFee,
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes flash
    ) {
        employeeProfileService.changeDailyTransportFee(user.getEmployeeId(), dailyTransportFee);
        flash.addFlashAttribute("success", "하루 교통비를 저장했습니다.");
        if (month != null) {
            flash.addAttribute("month", month.toString());
        }
        return "redirect:/pay";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/pay";
    }
}
