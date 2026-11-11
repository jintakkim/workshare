package com.jangbokhuman.workshare.web;

import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.common.BusinessException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class WebExceptionHandler {
    private static final String PHOTO_PATH_SUFFIX = "/photo";

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(
            BusinessException error,
            HttpServletRequest request,
            RedirectAttributes flash
    ) {
        flash.addFlashAttribute("error", error.getMessage());
        return getPath(request).startsWith("/admin") ? "redirect:/admin" : "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleOversizedPhoto(HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "사진은 15MB 이하의 JPG, PNG 또는 HEIC(HEIF) 파일로 선택해주세요.");
        String path = getPath(request);
        if (path.endsWith(PHOTO_PATH_SUFFIX)) {
            return "redirect:" + path.substring(0, path.length() - PHOTO_PATH_SUFFIX.length());
        }
        return "redirect:/";
    }

    @ExceptionHandler(ParticipationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(ParticipationDeniedException error, Model model) {
        model.addAttribute("reason", error.getMessage());
        return "forbidden";
    }

    // An oversized upload is first rejected by the CSRF filter and reaches here on the /error dispatch,
    // so the address the user posted to is the original request URI.
    private String getPath(HttpServletRequest request) {
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String uri = originalUri instanceof String value ? value : request.getRequestURI();
        return uri.substring(request.getContextPath().length());
    }
}
