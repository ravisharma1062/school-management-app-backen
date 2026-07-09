package com.school.app.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Page<Student> findAll(Pageable pageable);

    Optional<Student> findByIdAndParentId(UUID id, UUID parentId);

    List<Student> findByParentId(UUID parentId);

    boolean existsByStudentClassAndSectionAndRollNo(String studentClass, String section, String rollNo);
}
