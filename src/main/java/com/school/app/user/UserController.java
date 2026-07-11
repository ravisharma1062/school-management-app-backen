package com.school.app.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a Teacher or Parent user account")
    public UserDto create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users (paginated), optionally filtered by role")
    public Page<UserDto> list(@RequestParam(required = false) Role role, Pageable pageable) {
        return userService.list(role, pageable);
    }

    @PatchMapping("/me/language")
    @Operation(summary = "Update the current user's preferred UI language")
    public UserDto updateMyLanguage(
            @AuthenticationPrincipal User currentUser, @Valid @RequestBody UserLanguageUpdateRequest request) {
        return userService.updateMyLanguage(currentUser, request.preferredLanguage());
    }
}
