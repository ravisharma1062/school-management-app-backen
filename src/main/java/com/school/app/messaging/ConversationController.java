package com.school.app.messaging;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Messaging")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    @Operation(summary = "Start (or resume) a conversation with a parent/teacher")
    public ConversationDto start(
            @Valid @RequestBody ConversationCreateRequest request, @AuthenticationPrincipal User currentUser) {
        return conversationService.startOrGet(request, currentUser);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    @Operation(summary = "List the current user's conversations")
    public List<ConversationDto> list(@AuthenticationPrincipal User currentUser) {
        return conversationService.getForCurrentUser(currentUser);
    }

    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    @Operation(summary = "List people the current user is allowed to start a conversation with")
    public List<ConversationContactDto> contacts(@AuthenticationPrincipal User currentUser) {
        return conversationService.getContacts(currentUser);
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    @Operation(summary = "Send a message in a conversation")
    public MessageDto sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody MessageCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return conversationService.sendMessage(id, request, currentUser);
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    @Operation(summary = "List messages in a conversation")
    public List<MessageDto> getMessages(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return conversationService.getMessages(id, currentUser);
    }
}
