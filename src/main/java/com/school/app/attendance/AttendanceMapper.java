package com.school.app.attendance;

import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceDto toDto(Attendance attendance) {
        return new AttendanceDto(
                attendance.getId(),
                attendance.getStudent().getId(),
                attendance.getDate(),
                attendance.getStatus(),
                attendance.getMarkedBy().getId()
        );
    }
}
