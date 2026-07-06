package com.school.app.attendance;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final AttendanceMapper attendanceMapper;

    public AttendanceDto mark(AttendanceMarkRequest request, User currentUser) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + request.studentId() + " not found"));

        Attendance attendance = attendanceRepository.findByStudentIdAndDate(request.studentId(), request.date())
                .orElseGet(() -> Attendance.builder().student(student).date(request.date()).build());

        attendance.setStatus(request.status());
        attendance.setMarkedBy(currentUser);

        return attendanceMapper.toDto(attendanceRepository.save(attendance));
    }

    public List<AttendanceDto> getByStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's attendance");
        }

        return attendanceRepository.findByStudentIdOrderByDateDesc(studentId).stream()
                .map(attendanceMapper::toDto)
                .toList();
    }

    public List<AttendanceDto> getByClassSectionDate(String studentClass, String section, LocalDate date) {
        return attendanceRepository.findByClassAndSectionAndDate(studentClass, section, date).stream()
                .map(attendanceMapper::toDto)
                .toList();
    }

    /**
     * Percentage of records marked PRESENT or LATE out of all attendance records.
     */
    public double calculateAttendancePercentage(List<Attendance> records) {
        if (records.isEmpty()) {
            return 0.0;
        }
        long presentCount = records.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.PRESENT || r.getStatus() == AttendanceStatus.LATE)
                .count();
        return (presentCount * 100.0) / records.size();
    }
}
