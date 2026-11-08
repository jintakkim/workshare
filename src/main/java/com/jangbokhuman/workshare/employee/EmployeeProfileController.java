package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {
    private final RosterService rosterService;
    private final EmployeeProfileService employeeProfileService;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final WorkTime workTime;
    private final ResponsibilityCatalogService responsibilityCatalogService;
    private final ActivityManagementService activityManagementService;

    @GetMapping("/employees")
    public String showEmployeeDirectory(
            @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        model.addAttribute(
                "employees",
                rosterService.getEmployeeViews().stream()
                        .filter(
                                e ->
                                        e.inactiveFrom() == null
                                                || workTime.getToday().isBefore(e.inactiveFrom()))
                        .toList());
        model.addAttribute("admin", isAdmin(auth));
        model.addAttribute("section", "employees");
        return "employees";
    }

    @GetMapping("/my-profile")
    public String redirectToOwnProfile(@AuthenticationPrincipal AuthenticatedUser auth) {
        return "redirect:/employees/" + auth.getEmployeeId();
    }

    @GetMapping("/employees/{id}")
    public String showEmployeeProfile(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        return renderEmployeeProfile(id, auth, model, false);
    }

    @GetMapping("/admin/employees/{id}")
    public String showEmployeeManagement(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        return renderEmployeeProfile(id, auth, model, true);
    }

    private String renderEmployeeProfile(
            Long id, AuthenticatedUser auth, Model model, boolean management) {
        RosterService.EmployeeView employee = rosterService.getEmployeeView(id);
        model.addAttribute("employee", employee);
        model.addAttribute("admin", isAdmin(auth));
        model.addAttribute("management", management);
        model.addAttribute(
                "canEditPhoto", management || employee.id().equals(auth.getEmployeeId()));
        model.addAttribute("canChangePassword", employee.id().equals(auth.getEmployeeId()));
        model.addAttribute("section", "employees");
        if (management) {
            model.addAttribute("responsibilityOptions", responsibilityCatalogService.getChoices());
            model.addAttribute("groups", WorkGroupType.values());
            model.addAttribute("effectiveDate", rosterService.getRosterChangeEffectiveDate());
            if (employee.inactiveFrom() == null) {
                model.addAttribute("workGroupChangeImpacts", activityManagementService.getWorkGroupChangeImpacts(id));
            }
        }
        return "employee-profile";
    }

    @GetMapping("/employees/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        return employeePhotoRepository
                .findById(id)
                .map(
                        photo ->
                                ResponseEntity.ok()
                                        .contentType(MediaType.IMAGE_JPEG)
                                        .cacheControl(CacheControl.noStore())
                                        .body(photo.getImage()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/employees/{id}/password")
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

    @PostMapping("/employees/{id}/photo")
    public String savePhoto(
            @PathVariable Long id,
            @RequestParam MultipartFile photo,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash) {
        RosterService.EmployeeView employee = rosterService.getEmployeeView(id);
        if (!isAdmin(auth) && !employee.id().equals(auth.getEmployeeId()))
            throw new AccessDeniedException("본인 또는 관리자만 사진을 변경할 수 있습니다.");
        employeeProfileService.savePhoto(id, photo);
        flash.addFlashAttribute("success", "프로필 사진을 저장했습니다.");
        return "redirect:/employees/" + id;
    }

    @PostMapping("/admin/employees/{id}/photo")
    public String saveManagementPhoto(
            @PathVariable Long id, @RequestParam MultipartFile photo, RedirectAttributes flash) {
        employeeProfileService.savePhoto(id, photo);
        flash.addFlashAttribute("success", "프로필 사진을 저장했습니다.");
        return "redirect:/admin/employees/" + id;
    }

    @PostMapping("/admin/employees/{id}/responsibilities")
    public String changeResponsibilities(
            @PathVariable Long id,
            @RequestParam(required = false) java.util.List<String> responsibilities,
            RedirectAttributes flash) {
        employeeProfileService.changeResponsibilities(id, responsibilities);
        flash.addFlashAttribute("success", "담당 업무를 저장했습니다.");
        return "redirect:/admin/employees/" + id;
    }

    private boolean isAdmin(AuthenticatedUser auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
