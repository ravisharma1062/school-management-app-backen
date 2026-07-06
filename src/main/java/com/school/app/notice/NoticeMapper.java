package com.school.app.notice;

import org.springframework.stereotype.Component;

@Component
public class NoticeMapper {

    public NoticeDto toDto(Notice notice) {
        return new NoticeDto(
                notice.getId(),
                notice.getTitle(),
                notice.getDescription(),
                notice.getTargetRole(),
                notice.getCreatedBy().getId(),
                notice.getCreatedAt()
        );
    }
}
