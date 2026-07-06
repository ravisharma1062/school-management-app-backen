package com.school.app.homework;

import com.school.app.common.notification.PushNotificationService;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkMapper homeworkMapper;
    private final PushNotificationService pushNotificationService;

    public HomeworkDto create(HomeworkCreateRequest request, User currentUser) {
        Homework homework = Homework.builder()
                .studentClass(request.studentClass())
                .section(request.section())
                .subject(request.subject())
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .createdBy(currentUser)
                .build();

        Homework saved = homeworkRepository.save(homework);
        HomeworkDto dto = homeworkMapper.toDto(saved);

        pushNotificationService.publishHomeworkCreated(dto);

        return dto;
    }

    public Page<HomeworkDto> getByClassAndSection(String studentClass, String section, Pageable pageable) {
        return homeworkRepository.findByStudentClassAndSection(studentClass, section, pageable)
                .map(homeworkMapper::toDto);
    }
}
