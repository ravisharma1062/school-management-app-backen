package com.school.app.fee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

    List<Fee> findByStudentId(UUID studentId);

    List<Fee> findByStudentStudentClass(String studentClass);
}
