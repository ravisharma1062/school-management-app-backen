package com.school.app.messaging;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationService;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.timetable.Timetable;
import com.school.app.timetable.TimetableRepository;
import com.school.app.user.Role;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableRepository timetableRepository;
    private final StudentRepository studentRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final NotificationService notificationService;

    public ConversationDto startOrGet(ConversationCreateRequest request, User currentUser) {
        User other = userRepository.findById(request.otherUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + request.otherUserId() + " not found"));

        User parent;
        User teacher;
        if (currentUser.getRole() == Role.PARENT && other.getRole() == Role.TEACHER) {
            parent = currentUser;
            teacher = other;
        } else if (currentUser.getRole() == Role.TEACHER && other.getRole() == Role.PARENT) {
            parent = other;
            teacher = currentUser;
        } else {
            throw new BadRequestException("Conversations are only between a parent and a teacher");
        }

        requireTeacherTeachesParentsChild(teacher, parent);

        Conversation conversation = conversationRepository
                .findByParentIdAndTeacherIdFetchParticipants(parent.getId(), teacher.getId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().parent(parent).teacher(teacher).build()));

        return conversationMapper.toDto(conversation);
    }

    public List<ConversationDto> getForCurrentUser(User currentUser) {
        List<Conversation> conversations = currentUser.getRole() == Role.TEACHER
                ? conversationRepository.findByTeacherIdFetchParticipants(currentUser.getId())
                : conversationRepository.findByParentIdFetchParticipants(currentUser.getId());
        return conversations.stream().map(conversationMapper::toDto).toList();
    }

    public MessageDto sendMessage(UUID conversationId, MessageCreateRequest request, User currentUser) {
        Conversation conversation = requireParticipant(conversationId, currentUser);

        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .body(request.body())
                .build());

        UUID recipientId = conversation.getParent().getId().equals(currentUser.getId())
                ? conversation.getTeacher().getId()
                : conversation.getParent().getId();

        userRepository.findById(recipientId).ifPresent(recipient -> notificationService.notify(
                NotificationEventType.MESSAGE_RECEIVED,
                recipient,
                "New message from " + currentUser.getName(),
                request.body()));

        return messageMapper.toDto(message);
    }

    public List<MessageDto> getMessages(UUID conversationId, User currentUser) {
        requireParticipant(conversationId, currentUser);
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId).stream()
                .map(messageMapper::toDto)
                .toList();
    }

    public List<ConversationContactDto> getContacts(User currentUser) {
        if (currentUser.getRole() == Role.PARENT) {
            return teachersOfParentsChildren(currentUser.getId());
        }
        if (currentUser.getRole() == Role.TEACHER) {
            return parentsOfTeachersStudents(currentUser.getId());
        }
        return List.of();
    }

    private List<ConversationContactDto> teachersOfParentsChildren(UUID parentId) {
        List<Student> children = studentRepository.findByParentIdAndActiveTrue(parentId);
        Set<UUID> teacherUserIds = new LinkedHashSet<>();
        for (Student child : children) {
            teacherUserIds.addAll(timetableRepository.findDistinctTeacherUserIdsByClassAndSection(
                    child.getStudentClass(), child.getSection()));
        }
        return toContactDtos(teacherUserIds);
    }

    private List<ConversationContactDto> parentsOfTeachersStudents(UUID teacherUserId) {
        Teacher teacher = teacherRepository.findByUserId(teacherUserId)
                .orElseThrow(() -> new BadRequestException("Teacher profile not found for this account"));

        Set<UUID> parentIds = new LinkedHashSet<>();
        for (Timetable entry : timetableRepository.findByTeacherIdAndActiveTrue(teacher.getId())) {
            studentRepository
                    .findByStudentClassAndSectionAndActiveTrue(entry.getStudentClass(), entry.getSection())
                    .stream()
                    .map(Student::getParent)
                    .filter(Objects::nonNull)
                    .forEach(parent -> parentIds.add(parent.getId()));
        }
        return toContactDtos(parentIds);
    }

    private List<ConversationContactDto> toContactDtos(Set<UUID> userIds) {
        return userIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(u -> new ConversationContactDto(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    private Conversation requireParticipant(UUID conversationId, User currentUser) {
        Conversation conversation = conversationRepository.findByIdFetchParticipants(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation with id " + conversationId + " not found"));

        boolean isParticipant = conversation.getParent().getId().equals(currentUser.getId())
                || conversation.getTeacher().getId().equals(currentUser.getId());
        if (!isParticipant) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
        return conversation;
    }

    private void requireTeacherTeachesParentsChild(User teacherUser, User parentUser) {
        Teacher teacher = teacherRepository.findByUserId(teacherUser.getId())
                .orElseThrow(() -> new BadRequestException("Teacher profile not found for this account"));

        List<Student> children = studentRepository.findByParentIdAndActiveTrue(parentUser.getId());
        List<Timetable> taughtClasses = timetableRepository.findByTeacherIdAndActiveTrue(teacher.getId());

        boolean overlap = children.stream().anyMatch(child -> taughtClasses.stream().anyMatch(t ->
                t.getStudentClass().equals(child.getStudentClass()) && t.getSection().equalsIgnoreCase(child.getSection())));

        if (!overlap) {
            throw new AccessDeniedException("This teacher does not teach any of this parent's children");
        }
    }
}
