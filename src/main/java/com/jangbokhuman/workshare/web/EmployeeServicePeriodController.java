package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class EmployeeServicePeriodController {
    private final EmployeeProfileService employeeProfileService;

    @PutMapping("/employees/{id}/service-period")
    public String changeServicePeriod(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceEndDate,
            RedirectAttributes flash
    ) {
        if (!id.equals(auth.getEmployeeId())) {
            throw new AccessDeniedException("본인만 복무 기간을 변경할 수 있습니다.");
        }
        employeeProfileService.changeServicePeriod(auth.getEmployeeId(), serviceStartDate, serviceEndDate);
        flash.addFlashAttribute("success", "복무 기간을 저장했습니다.");
        flash.addFlashAttribute("servicePeriodFeedback", true);
        return "redirect:/employees/" + id + "#service-period";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(
            BusinessException error,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        flash.addFlashAttribute("error", error.getMessage());
        flash.addFlashAttribute("servicePeriodFeedback", true);
        return "redirect:/employees/" + auth.getEmployeeId() + "#service-period";
    }
}
