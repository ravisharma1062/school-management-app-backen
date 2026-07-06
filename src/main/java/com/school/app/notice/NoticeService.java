package com.school.app.notice;

import com.school.app.common.notification.PushNotificationService;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeMapper noticeMapper;
    private final PushNotificationService pushNotificationService;

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

        return dto;
    }

    public Page<NoticeDto> list(TargetRole roleFilter, User currentUser, Pageable pageable) {
        if (roleFilter != null) {
            return noticeRepository.findByTargetRole(roleFilter, pageable).map(noticeMapper::toDto);
        }

        TargetRole ownRoleAsTargetRole = TargetRole.valueOf(currentUser.getRole().name());
        return noticeRepository.findByTargetRoleIn(List.of(ownRoleAsTargetRole, TargetRole.ALL), pageable)
                .map(noticeMapper::toDto);
    }
}
