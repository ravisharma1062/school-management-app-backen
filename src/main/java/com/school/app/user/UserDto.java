package com.school.app.user;

import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String email,
        Role role,
        String phone
) {
}
