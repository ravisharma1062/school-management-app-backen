package com.school.app.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    Page<Student> findAll(Pageable pageable);

    Optional<Student> findByIdAndParentId(UUID id, UUID parentId);

    List<Student> findByParentId(UUID parentId);

    List<Student> findByParentIdAndActiveTrue(UUID parentId);

    List<Student> findByStudentClassAndSectionAndActiveTrue(String studentClass, String section);

    boolean existsByStudentClassAndSectionAndRollNo(String studentClass, String section, String rollNo);

    long countByActiveTrue();

    /**
     * Bypasses the {@code @TenantId} filter — for platform (MT-6c usage metering) callers, whose
     * token carries no tenant at all, mirroring {@code BusRouteRepository.findByIdBypassingTenantFilter}.
     */
    @Query(value = "SELECT COUNT(*) FROM students WHERE school_id = :schoolId AND is_active = true", nativeQuery = true)
    long countActiveBySchoolIdBypassingTenantFilter(UUID schoolId);
}
