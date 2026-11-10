package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.RosterService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class EmployeePasswordController {
    private final RosterService rosterService;

    @PutMapping("/employees/{id}/password")
    public String changePassword(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser auth,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmation,
            RedirectAttributes flash
    ) {
        if (!id.equals(auth.getEmployeeId())) {
            throw new AccessDeniedException("본인만 비밀번호를 변경할 수 있습니다.");
        }
        rosterService.changePassword(auth.getEmployeeId(), currentPassword, newPassword, confirmation);
        flash.addFlashAttribute("success", "비밀번호를 변경했습니다.");
        return "redirect:/employees/" + id + "#change-password";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(
            BusinessException error,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        flash.addFlashAttribute("error", error.getMessage());
        return "redirect:/employees/" + auth.getEmployeeId() + "#change-password";
    }
}
