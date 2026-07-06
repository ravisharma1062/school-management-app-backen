package com.school.app.homework;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, UUID> {

    Page<Homework> findByStudentClassAndSection(String studentClass, String section, Pageable pageable);
}
