package com.school.app.student;

import com.school.app.user.User;
import org.springframework.stereotype.Component;

@Component
public class StudentMapper {

    public StudentDto toDto(Student student) {
        User parent = student.getParent();
        return new StudentDto(
                student.getId(),
                student.getName(),
                student.getRollNo(),
                student.getStudentClass(),
                student.getSection(),
                student.getDob(),
                parent != null ? parent.getId() : null,
                student.isActive()
        );
    }
}
