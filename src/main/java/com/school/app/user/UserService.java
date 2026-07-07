package com.school.app.user;

import com.school.app.common.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .phone(request.phone())
                .build();

        return userMapper.toDto(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserDto> list(Role role, Pageable pageable) {
        Page<User> users = role != null
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);
        return users.map(userMapper::toDto);
    }
}
