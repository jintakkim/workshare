package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.RequestPaths;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.employee.ProfileMessageService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProfileMessageController {
    private final ProfileMessageService profileMessageService;

    // Polled by the profile page to refresh only the message list.
    @GetMapping("/employees/{id}/messages")
    public String showMessages(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser auth, Model model) {
        model.addAttribute("recipientId", id);
        model.addAttribute("messages", profileMessageService.getMessages(id, auth.getEmployeeId(), auth.isAdmin()));
        model.addAttribute("admin", auth.isAdmin());
        return "fragments/profile-messages :: list";
    }

    @PostMapping("/employees/{id}/messages")
    public String createMessage(
            @PathVariable Long id,
            @RequestParam(required = false) String content,
            @RequestParam(defaultValue = "false") boolean anonymous,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        profileMessageService.create(id, auth.getEmployeeId(), content, anonymous);
        flash.addFlashAttribute("messageNotice", "한마디를 남겼어요.");
        return "redirect:/employees/" + id;
    }

    @PutMapping("/employees/{id}/messages/{messageId}")
    public String changeMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestParam(required = false) String content,
            @RequestParam(defaultValue = "false") boolean anonymous,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        profileMessageService.change(id, messageId, auth.getEmployeeId(), content, anonymous);
        flash.addFlashAttribute("messageNotice", "한마디를 고쳤어요.");
        return "redirect:/employees/" + id;
    }

    @DeleteMapping("/employees/{id}/messages/{messageId}")
    public String deleteMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @AuthenticationPrincipal AuthenticatedUser auth,
            RedirectAttributes flash
    ) {
        profileMessageService.delete(id, messageId, auth.getEmployeeId());
        flash.addFlashAttribute("messageNotice", "한마디를 지웠어요.");
        return "redirect:/employees/" + id;
    }

    @DeleteMapping("/admin/employees/{id}/messages/{messageId}")
    public String deleteMessageByAdmin(@PathVariable Long id, @PathVariable Long messageId, RedirectAttributes flash) {
        profileMessageService.deleteByAdmin(id, messageId);
        flash.addFlashAttribute("messageNotice", "한마디를 지웠어요.");
        return "redirect:/admin/employees/" + id;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException error, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("messageError", error.getMessage());
        String profile = request.getRequestURI().startsWith("/admin/") ? "/admin/employees/" : "/employees/";
        return RequestPaths.findPathVariable(request, "id")
                .map(id -> "redirect:" + profile + id)
                .orElse("redirect:/employees");
    }
}
