package com.school.app.timetable;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableMapper timetableMapper;

    public List<TimetableDto> getByClassAndSection(String studentClass, String section) {
        return timetableRepository.findByStudentClassAndSectionOrderByDayOfWeekAscPeriodAsc(studentClass, section).stream()
                .map(timetableMapper::toDto)
                .toList();
    }

    public TimetableDto create(TimetableCreateRequest request) {
        Teacher teacher = teacherRepository.findById(request.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher with id " + request.teacherId() + " not found"));

        Timetable timetable = Timetable.builder()
                .studentClass(request.studentClass())
                .section(request.section())
                .dayOfWeek(request.dayOfWeek())
                .period(request.period())
                .subject(request.subject())
                .teacher(teacher)
                .build();

        return timetableMapper.toDto(timetableRepository.save(timetable));
    }
}
