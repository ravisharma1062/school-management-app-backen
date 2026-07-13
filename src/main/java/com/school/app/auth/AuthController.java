package com.school.app.auth;

import com.school.app.platform.ActivateAccountRequest;
import com.school.app.platform.ActivationInfoDto;
import com.school.app.platform.ActivationService;
import com.school.app.user.User;
import com.school.app.user.UserDto;
import com.school.app.user.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final ActivationService activationService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email/password and receive access + refresh tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public UserDto me(@AuthenticationPrincipal User currentUser) {
        return userMapper.toDto(currentUser);
    }

    @GetMapping("/activation/{token}")
    @Operation(summary = "Look up the school/admin email an invite link activates (public, single-use)")
    public ActivationInfoDto activationInfo(@PathVariable String token) {
        return activationService.getInfo(token);
    }

    @PostMapping("/activate")
    @Operation(summary = "Set a password and activate a provisioning-created admin account (public, single-use)")
    public void activate(@Valid @RequestBody ActivateAccountRequest request) {
        activationService.activate(request);
    }
}
