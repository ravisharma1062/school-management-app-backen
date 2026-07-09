package com.school.app.student;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentMapper studentMapper;

    public Page<StudentDto> list(Pageable pageable) {
        return studentRepository.findAll(pageable).map(studentMapper::toDto);
    }

    public StudentDto create(StudentCreateRequest request) {
        if (studentRepository.existsByStudentClassAndSectionAndRollNo(
                request.studentClass(), request.section(), request.rollNo())) {
            throw new BadRequestException("A student with this roll number already exists in this class/section");
        }

        User parent = null;
        if (request.parentId() != null) {
            parent = userRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent user with id " + request.parentId() + " not found"));
        }

        Student student = Student.builder()
                .name(request.name())
                .rollNo(request.rollNo())
                .studentClass(request.studentClass())
                .section(request.section())
                .dob(request.dob())
                .parent(parent)
                .build();

        return studentMapper.toDto(studentRepository.save(student));
    }

    public List<StudentDto> getMyChildren(User currentUser) {
        return studentRepository.findByParentId(currentUser.getId()).stream()
                .map(studentMapper::toDto)
                .toList();
    }

    public StudentDto getById(UUID id, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's record");
        }

        return studentMapper.toDto(student);
    }

    public StudentDto update(UUID id, StudentUpdateRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));

        if (request.name() != null) {
            student.setName(request.name());
        }
        if (request.rollNo() != null) {
            student.setRollNo(request.rollNo());
        }
        if (request.studentClass() != null) {
            student.setStudentClass(request.studentClass());
        }
        if (request.section() != null) {
            student.setSection(request.section());
        }
        if (request.dob() != null) {
            student.setDob(request.dob());
        }
        if (request.parentId() != null) {
            User parent = userRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent user with id " + request.parentId() + " not found"));
            student.setParent(parent);
        }

        return studentMapper.toDto(studentRepository.save(student));
    }
}
