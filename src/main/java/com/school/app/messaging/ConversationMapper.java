package com.school.app.messaging;

import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

    public ConversationDto toDto(Conversation conversation) {
        return new ConversationDto(
                conversation.getId(),
                conversation.getParent().getId(),
                conversation.getParent().getName(),
                conversation.getTeacher().getId(),
                conversation.getTeacher().getName(),
                conversation.getCreatedAt()
        );
    }
}
