package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;
import com.jangbokhuman.workshare.employee.RosterService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class EmployeeManagementController {
    private final RosterService rosterService;
    private final EmployeeProfileService employeeProfileService;
    private final EmployeeProfileModel employeeProfileModel;

    @GetMapping("/admin/employees/{id}")
    public String showEmployeeManagement(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        employeeProfileModel.addProfile(id, auth, model, true);
        return "employee-profile";
    }

    @PostMapping("/admin/employees/{id}/photo")
    public String savePhoto(@PathVariable Long id, @RequestParam MultipartFile photo, RedirectAttributes flash) {
        employeeProfileService.savePhoto(id, photo);
        flash.addFlashAttribute("success", "프로필 사진을 저장했습니다.");
        return "redirect:/admin/employees/" + id;
    }

    @PostMapping("/admin/employees/{id}/work-group")
    public String changeWorkGroup(@PathVariable Long id, @RequestParam String workGroupCode, RedirectAttributes flash) {
        List<String> removedActivityNames = rosterService.changeWorkGroup(id, workGroupCode);
        flash.addFlashAttribute("success", "직군을 변경했습니다. 이미 확정된 배정은 유지합니다.");
        if (!removedActivityNames.isEmpty()) {
            flash.addFlashAttribute("warning", "직군 변경으로 " + String.join(", ", removedActivityNames) + "에서 자동 탈퇴했습니다.");
        }
        return "redirect:/admin/employees/" + id;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        Optional<String> employeeId = RequestPaths.findPathVariable(request, "id");
        if (request.getMethod().equals("POST") && employeeId.isPresent()) {
            return "redirect:/admin/employees/" + employeeId.get();
        }
        return "redirect:/admin?section=employees";
    }
}
