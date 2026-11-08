package com.jangbokhuman.workshare.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class WebExceptionHandler {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public String handleConflictingSchedule(
            org.springframework.dao.DataIntegrityViolationException error,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes flash) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!java.util.Set.of(
                        "/leave-requests", "/sharing-shop/time-preference", "/admin/leave-requests",
                        "/admin/supports/reasons", "/admin/supports/month")
                .contains(path)) throw error;
        return handleInvalidInput(
                new IllegalArgumentException("같은 정보가 동시에 저장되었습니다. 새로고침 후 다시 저장해주세요."),
                request,
                flash);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleInvalidInput(
            IllegalArgumentException error,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes flash) {
        flash.addFlashAttribute("error", error.getMessage());
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/supports")) return "redirect:/supports";
        if (path.startsWith("/admin/supports")) {
            String reason = request.getParameter("reasonId"), month = request.getParameter("month");
            if (reason != null && reason.matches("[A-Za-z0-9_-]{1,36}"))
                flash.addAttribute("reasonId", reason);
            if (month != null && month.matches("[0-9]{4}-[0-9]{2}"))
                flash.addAttribute("month", month);
            return "redirect:/admin/supports";
        }
        if (path.equals("/sharing-shop/time-preference")) return "redirect:/sharing-shop";
        if (path.equals("/leave-requests")) {
            String date = request.getParameter("date");
            if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}"))
                flash.addAttribute("date", date);
            String activity = request.getParameter("activityCode");
            if (activity != null && activity.matches("[A-Z][A-Z0-9_]*"))
                flash.addAttribute("activityCode", activity);
            return "redirect:/my-leave#day-details";
        }
        if (path.startsWith("/employees/") && path.endsWith("/password"))
            return "redirect:" + path.substring(0, path.lastIndexOf('/')) + "#change-password";
        if (path.startsWith("/employees/") && path.endsWith("/photo"))
            return "redirect:" + path.substring(0, path.lastIndexOf('/'));
        if (path.startsWith("/admin/employees/")
                && (path.endsWith("/responsibilities")
                        || path.endsWith("/work-group")
                        || path.endsWith("/photo")))
            return "redirect:" + path.substring(0, path.lastIndexOf('/'));
        if (path.startsWith("/admin/employees")) return "redirect:/admin?section=employees";
        if (path.startsWith("/admin/holidays")) return "redirect:/admin?section=holidays";
        if (path.startsWith("/admin/responsibility-options"))
            return "redirect:/admin?section=employees";
        if (path.startsWith("/admin/activities")) return "redirect:/admin?section=activities";
        return path.startsWith("/admin") ? "redirect:/admin" : "redirect:/";
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String handleOversizedPhoto(
            org.springframework.web.multipart.MaxUploadSizeExceededException error,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes flash) {
        return handleInvalidInput(
                new IllegalArgumentException("사진은 2MB 이하의 JPG 또는 PNG 파일로 선택해주세요."), request, flash);
    }

    @ExceptionHandler(com.jangbokhuman.workshare.activity.ParticipationDeniedException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public String handleForbidden(
            com.jangbokhuman.workshare.activity.ParticipationDeniedException error,
            org.springframework.ui.Model model) {
        model.addAttribute("reason", error.getMessage());
        return "forbidden";
    }
}
