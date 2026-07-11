package com.school.app.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TimetableRepository extends JpaRepository<Timetable, UUID> {

    List<Timetable> findByStudentClassAndSectionOrderByDayOfWeekAscPeriodAsc(String studentClass, String section);

    List<Timetable> findByStudentClassAndSectionAndActiveTrueOrderByDayOfWeekAscPeriodAsc(
            String studentClass, String section);

    List<Timetable> findByTeacherIdAndActiveTrue(UUID teacherId);

    /** Projection (not entity loading) so it's safe to read outside a Hibernate session, unlike t.getTeacher().getUser(). */
    @Query("SELECT DISTINCT t.teacher.user.id FROM Timetable t "
            + "WHERE t.studentClass = :studentClass AND t.section = :section AND t.active = true")
    List<UUID> findDistinctTeacherUserIdsByClassAndSection(
            @Param("studentClass") String studentClass, @Param("section") String section);
}
