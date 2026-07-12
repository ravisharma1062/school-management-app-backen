package com.school.app.timetable;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableMapper timetableMapper;

    public List<TimetableDto> getByClassAndSection(String studentClass, String section, boolean includeArchived) {
        List<Timetable> entries = includeArchived
                ? timetableRepository.findByStudentClassAndSectionOrderByDayOfWeekAscPeriodAsc(studentClass, section)
                : timetableRepository.findByStudentClassAndSectionAndActiveTrueOrderByDayOfWeekAscPeriodAsc(studentClass, section);
        return entries.stream()
                .map(timetableMapper::toDto)
                .toList();
    }

    public TimetableDto create(TimetableCreateRequest request) {
        Teacher teacher = teacherRepository.findByUserId(request.teacherId())
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

    public TimetableDto archive(UUID id) {
        Timetable timetable = timetableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry with id " + id + " not found"));
        timetable.setActive(false);
        return timetableMapper.toDto(timetableRepository.save(timetable));
    }

    public TimetableDto restore(UUID id) {
        Timetable timetable = timetableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry with id " + id + " not found"));
        timetable.setActive(true);
        return timetableMapper.toDto(timetableRepository.save(timetable));
    }
}
