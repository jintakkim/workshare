package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.common.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AdminEmployeeController {
    private static final String EMPLOYEE_SECTION = "redirect:/admin/employees";

    private final RosterService rosterService;

    @PostMapping("/admin/employees")
    public String registerEmployee(
            @RequestParam String name,
            @RequestParam String password,
            @RequestParam String workGroupCode,
            RedirectAttributes flash
    ) {
        rosterService.registerEmployee(name, password, workGroupCode);
        flash.addFlashAttribute("success", "직원을 등록했습니다. 적용일: " + rosterService.getRosterChangeEffectiveDate());
        return EMPLOYEE_SECTION;
    }

    @PostMapping("/admin/employees/{id}/retirement")
    public String retire(@PathVariable Long id, RedirectAttributes flash) {
        rosterService.retireEmployee(id);
        flash.addFlashAttribute("success", "직원 제외를 등록했습니다. 적용일: " + rosterService.getRosterChangeEffectiveDate());
        return EMPLOYEE_SECTION;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        return EMPLOYEE_SECTION;
    }
}
