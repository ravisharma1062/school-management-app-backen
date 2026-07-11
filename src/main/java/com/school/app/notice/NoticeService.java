package com.school.app.notice;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationService;
import com.school.app.common.notification.PushNotificationService;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeMapper noticeMapper;
    private final PushNotificationService pushNotificationService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NoticeDto create(NoticeCreateRequest request, User currentUser) {
        Notice notice = Notice.builder()
                .title(request.title())
                .description(request.description())
                .targetRole(request.targetRole())
                .createdBy(currentUser)
                .build();

        Notice saved = noticeRepository.save(notice);
        NoticeDto dto = noticeMapper.toDto(saved);

        pushNotificationService.publishNoticeCreated(dto);
        emailFanOut(dto);

        return dto;
    }

    /**
     * Emails every user matching the notice's target role. Fine at this app's scale (a school's
     * user count); a real high-volume deployment would queue this rather than send synchronously
     * on the request thread.
     */
    private void emailFanOut(NoticeDto notice) {
        List<User> recipients = notice.targetRole() == TargetRole.ALL
                ? userRepository.findAll()
                : userRepository.findByRole(Role.valueOf(notice.targetRole().name()));

        String body = notice.title() + (notice.description() != null ? "\n\n" + notice.description() : "");
        for (User recipient : recipients) {
            notificationService.notify(NotificationEventType.NOTICE_CREATED, recipient, notice.title(), body);
        }
    }

    public Page<NoticeDto> list(TargetRole roleFilter, User currentUser, Pageable pageable, boolean includeArchived) {
        if (roleFilter != null) {
            Page<Notice> page = includeArchived
                    ? noticeRepository.findByTargetRole(roleFilter, pageable)
                    : noticeRepository.findByTargetRoleAndActiveTrue(roleFilter, pageable);
            return page.map(noticeMapper::toDto);
        }

        TargetRole ownRoleAsTargetRole = TargetRole.valueOf(currentUser.getRole().name());
        List<TargetRole> visibleRoles = List.of(ownRoleAsTargetRole, TargetRole.ALL);
        Page<Notice> page = includeArchived
                ? noticeRepository.findByTargetRoleIn(visibleRoles, pageable)
                : noticeRepository.findByTargetRoleInAndActiveTrue(visibleRoles, pageable);
        return page.map(noticeMapper::toDto);
    }

    public NoticeDto archive(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice with id " + id + " not found"));
        notice.setActive(false);
        return noticeMapper.toDto(noticeRepository.save(notice));
    }

    public NoticeDto restore(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice with id " + id + " not found"));
        notice.setActive(true);
        return noticeMapper.toDto(noticeRepository.save(notice));
    }
}
