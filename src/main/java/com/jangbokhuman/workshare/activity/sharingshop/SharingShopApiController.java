package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.config.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/sharing-shop")
@RequiredArgsConstructor
public class SharingShopApiController {
    private final SharingShopQueryService sharingShopQueryService;

    @GetMapping("/days/{date}")
    public ResponseEntity<SharingShopQueryService.Day> getDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sharingShopQueryService.getDay(date, principal.getEmployeeId()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(BusinessException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
