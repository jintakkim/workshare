package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.RequestPaths;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.EmployeePhoto;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;
import com.jangbokhuman.workshare.employee.RosterService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {
    private final RosterService rosterService;
    private final EmployeeProfileService employeeProfileService;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final EmployeeProfileModel employeeProfileModel;
    private final WorkTime workTime;

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

    @GetMapping("/employees/me")
    public String redirectToOwnProfile(@AuthenticationPrincipal AuthenticatedUser auth) {
        return "redirect:/employees/" + auth.getEmployeeId();
    }

    @GetMapping("/employees/{id}")
    public String showEmployeeProfile(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        employeeProfileModel.addProfile(id, auth, model, false);
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

    @PutMapping("/employees/{id}/photo")
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

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        Optional<String> employeeId = RequestPaths.findPathVariable(request, "id");
        if (!request.getMethod().equals("GET") && employeeId.isPresent()) {
            return "redirect:/employees/" + employeeId.get();
        }
        return "redirect:/employees";
    }
}
