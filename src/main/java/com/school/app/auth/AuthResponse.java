package com.school.app.auth;

import com.school.app.user.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Role role
) {
}
