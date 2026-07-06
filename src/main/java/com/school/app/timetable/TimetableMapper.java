package com.school.app.timetable;

import org.springframework.stereotype.Component;

@Component
public class TimetableMapper {

    public TimetableDto toDto(Timetable timetable) {
        return new TimetableDto(
                timetable.getId(),
                timetable.getStudentClass(),
                timetable.getSection(),
                timetable.getDayOfWeek(),
                timetable.getPeriod(),
                timetable.getSubject(),
                timetable.getTeacher().getId()
        );
    }
}
