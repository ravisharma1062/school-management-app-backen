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
@RequestMapping("/api/v1/public/trial-signups")
@RequiredArgsConstructor
@Tag(name = "Public Trial Signup")
public class PublicTrialSignupController {

    private final PublicTrialSignupService publicTrialSignupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a self-service free trial (rate-limited, CAPTCHA-verified, no auth) — provisions instantly, no operator review")
    public ProvisionResultDto submit(@Valid @RequestBody PublicTrialSignupRequest request, HttpServletRequest servletRequest) {
        return publicTrialSignupService.submit(request, clientIp(servletRequest));
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
