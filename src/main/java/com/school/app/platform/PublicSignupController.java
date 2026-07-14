package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/signup-requests")
@RequiredArgsConstructor
@Tag(name = "Public Signup")
public class PublicSignupController {

    private final PublicSignupService publicSignupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a public school signup request (rate-limited, CAPTCHA-verified, no auth)")
    public void submit(@Valid @RequestBody PublicSignupRequest request, HttpServletRequest servletRequest) {
        publicSignupService.submit(request, clientIp(servletRequest));
    }

    /**
     * Trusts {@code X-Forwarded-For} when present — fine behind Render's own proxy (this app's only
     * deployment target today), but would need a trusted-proxy allowlist if ever exposed directly.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
