package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.EmployeePhoto;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {
    private static final String PASSWORD_PATH = "/password";
    private static final String SERVICE_PERIOD_PATH = "/service-period";

    private final RosterService rosterService;
    private final EmployeeProfileService employeeProfileService;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final WorkTime workTime;
    private final ActivityManagementService activityManagementService;

    @GetMapping("/employees")
    public String showEmployeeDirectory(@AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        LocalDate today = workTime.getToday();
        model.addAttribute(
                "employees",
                rosterService.getEmployeeViews().stream()
                        .filter(employee -> today.isBefore(employee.inactiveFrom()))
                        .toList()
        );
        model.addAttribute("admin", auth.isAdmin());
        model.addAttribute("section", "employees");
        return "employees";
    }

    @GetMapping("/my-profile")
    public String redirectToOwnProfile(@AuthenticationPrincipal AuthenticatedUser auth) {
        return "redirect:/employees/" + auth.getEmployeeId();
    }

    @GetMapping("/employees/{id}")
    public String showEmployeeProfile(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        return renderEmployeeProfile(id, auth, model, false);
    }

    @GetMapping("/admin/employees/{id}")
    public String showEmployeeManagement(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        return renderEmployeeProfile(id, auth, model, true);
    }

    private String renderEmployeeProfile(Long id, AuthenticatedUser auth, Model model, boolean management) {
        RosterService.EmployeeView employee = rosterService.getEmployeeView(id);
        boolean ownProfile = id.equals(auth.getEmployeeId());
        model.addAttribute("employee", employee);
        model.addAttribute("admin", auth.isAdmin());
        model.addAttribute("management", management);
        model.addAttribute("canEditPhoto", management || ownProfile);
        model.addAttribute("canChangePassword", ownProfile);
        model.addAttribute("canChangeServicePeriod", ownProfile && !employee.excludedByAdmin());
        model.addAttribute("today", workTime.getToday());
        model.addAttribute("section", "employees");
        if (management) {
            model.addAttribute("groups", WorkGroupType.values());
            model.addAttribute("effectiveDate", rosterService.getRosterChangeEffectiveDate());
            if (!employee.excludedByAdmin()) {
                model.addAttribute("workGroupChangeImpacts", activityManagementService.getWorkGroupChangeImpacts(id));
            }
        }
        return "employee-profile";
    }

    @GetMapping("/employees/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id, WebRequest webRequest) {
        Optional<EmployeePhoto> photo = employeePhotoRepository.findById(id);
        if (photo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] image = photo.get().getImage();
        String etag = "\"" + DigestUtils.md5DigestAsHex(image) + "\"";
        CacheControl cacheControl = CacheControl.noCache().cachePrivate();
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .eTag(etag)
                .cacheControl(cacheControl)
                .body(image);
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

    @PostMapping("/employees/{id}/service-period")
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

    @PostMapping("/employees/{id}/photo")
    public String savePhoto(
            @PathVariable Long id,
            @RequestParam MultipartFile photo,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        RosterService.EmployeeView employee = rosterService.getEmployeeView(id);
        if (!auth.isAdmin() && !employee.id().equals(auth.getEmployeeId())) {
            throw new AccessDeniedException("본인 또는 관리자만 사진을 변경할 수 있습니다.");
        }
        employeeProfileService.savePhoto(id, photo);
        flash.addFlashAttribute("success", "프로필 사진을 저장했습니다.");
        return "redirect:/employees/" + id;
    }

    @PostMapping("/admin/employees/{id}/photo")
    public String saveManagementPhoto(@PathVariable Long id, @RequestParam MultipartFile photo, RedirectAttributes flash) {
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
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/employees") || path.equals("/my-profile")) {
            return "redirect:/employees";
        }
        String profilePath = path.substring(0, path.lastIndexOf('/'));
        if (path.endsWith(PASSWORD_PATH)) {
            return "redirect:" + profilePath + "#change-password";
        }
        if (path.endsWith(SERVICE_PERIOD_PATH)) {
            flash.addFlashAttribute("servicePeriodFeedback", true);
            return "redirect:" + profilePath + "#service-period";
        }
        if (request.getMethod().equals("POST")) {
            return "redirect:" + profilePath;
        }
        return path.startsWith("/admin") ? "redirect:/admin?section=employees" : "redirect:/employees";
    }
}
