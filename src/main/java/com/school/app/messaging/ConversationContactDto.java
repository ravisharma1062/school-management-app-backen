package com.school.app.messaging;

import java.util.UUID;

/** A person the current user is allowed to start a conversation with. */
public record ConversationContactDto(
        UUID id,
        String name,
        String email
) {
}
