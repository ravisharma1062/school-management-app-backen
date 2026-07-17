package com.school.app.student;

import com.school.app.user.Role;
import com.school.app.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentMapperTest {

    private final StudentMapper mapper = new StudentMapper();

    @Test
    void mapsAllFieldsIncludingParentId() {
        UUID parentId = UUID.randomUUID();
        User parent = User.builder().id(parentId).schoolId(UUID.randomUUID()).name("Parent")
                .email("parent@school.app").passwordHash("hashed").role(Role.PARENT).build();
        Student student = Student.builder()
                .id(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .name("Bart Simpson")
                .rollNo("10-A-05")
                .studentClass("10")
                .section("A")
                .dob(LocalDate.of(2011, 4, 1))
                .parent(parent)
                .active(true)
                .build();

        StudentDto dto = mapper.toDto(student);

        assertThat(dto.id()).isEqualTo(student.getId());
        assertThat(dto.name()).isEqualTo("Bart Simpson");
        assertThat(dto.rollNo()).isEqualTo("10-A-05");
        assertThat(dto.studentClass()).isEqualTo("10");
        assertThat(dto.section()).isEqualTo("A");
        assertThat(dto.dob()).isEqualTo(LocalDate.of(2011, 4, 1));
        assertThat(dto.parentId()).isEqualTo(parentId);
        assertThat(dto.active()).isTrue();
    }

    @Test
    void mapsANullParentToANullParentId() {
        Student student = Student.builder()
                .id(UUID.randomUUID())
                .schoolId(UUID.randomUUID())
                .name("Orphan Record")
                .rollNo("10-A-06")
                .studentClass("10")
                .section("A")
                .dob(LocalDate.of(2011, 5, 1))
                .parent(null)
                .active(false)
                .build();

        StudentDto dto = mapper.toDto(student);

        assertThat(dto.parentId()).isNull();
        assertThat(dto.active()).isFalse();
    }
}
