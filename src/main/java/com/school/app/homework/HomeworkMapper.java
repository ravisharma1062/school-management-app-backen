package com.school.app.homework;

import org.springframework.stereotype.Component;

@Component
public class HomeworkMapper {

    public HomeworkDto toDto(Homework homework) {
        return new HomeworkDto(
                homework.getId(),
                homework.getStudentClass(),
                homework.getSection(),
                homework.getSubject(),
                homework.getTitle(),
                homework.getDescription(),
                homework.getDueDate(),
                homework.getCreatedBy().getId(),
                homework.getCreatedAt()
        );
    }
}
