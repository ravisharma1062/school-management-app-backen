package com.school.app.user;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

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

        User saved = userRepository.save(user);

        if (saved.getRole() == Role.TEACHER) {
            teacherRepository.save(Teacher.builder().user(saved).build());
        }

        notificationService.notify(
                NotificationEventType.USER_WELCOME,
                saved,
                "Welcome to School App",
                "Hi " + saved.getName() + ",\n\nAn account has been created for you.\n\n"
                        + "Email: " + saved.getEmail() + "\nTemporary password: " + request.password()
                        + "\n\nPlease sign in and change your password as soon as possible.");

        return userMapper.toDto(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserDto> list(Role role, Pageable pageable) {
        Page<User> users = role != null
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);
        return users.map(userMapper::toDto);
    }

    public UserDto updateMyLanguage(User currentUser, LanguageCode preferredLanguage) {
        currentUser.setPreferredLanguage(preferredLanguage);
        return userMapper.toDto(userRepository.save(currentUser));
    }

    /**
     * MT-6e — "billing actions restricted to the billing owner": only the current billing owner
     * may hand the role to another ADMIN. The one exception is a school with no billing owner at
     * all (shouldn't happen after {@code V22}'s backfill, but defensive against edge cases) —
     * any ADMIN may claim it then, so a school can never be permanently locked out.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto reassignBillingOwner(UUID targetUserId, User actingUser) {
        if (userRepository.existsByBillingOwnerTrue() && !actingUser.isBillingOwner()) {
            throw new AccessDeniedException("Only the current billing owner can reassign billing ownership");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + targetUserId + " not found"));
        if (target.getRole() != Role.ADMIN) {
            throw new BadRequestException("Only an ADMIN can be the billing owner");
        }

        userRepository.findByBillingOwnerTrue().ifPresent(current -> {
            current.setBillingOwner(false);
            userRepository.save(current);
        });
        target.setBillingOwner(true);
        return userMapper.toDto(userRepository.save(target));
    }
}
