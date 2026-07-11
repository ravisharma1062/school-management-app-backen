package com.school.app.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findByStudentIdOrderByDateDesc(UUID studentId);

    Optional<Attendance> findByStudentIdAndDate(UUID studentId, LocalDate date);

    @Query("select a from Attendance a where a.student.studentClass = :studentClass "
            + "and a.student.section = :section and a.date = :date")
    List<Attendance> findByClassAndSectionAndDate(
            @Param("studentClass") String studentClass,
            @Param("section") String section,
            @Param("date") LocalDate date);

    @Query("select a from Attendance a where a.date between :from and :to "
            + "and (:studentClass is null or a.student.studentClass = :studentClass)")
    List<Attendance> findInRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("studentClass") String studentClass);

    List<Attendance> findByStudentIdAndDateBetween(UUID studentId, LocalDate from, LocalDate to);
}
