package com.school.app.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableRepository extends JpaRepository<Timetable, UUID> {

    List<Timetable> findByStudentClassAndSectionOrderByDayOfWeekAscPeriodAsc(String studentClass, String section);
}
