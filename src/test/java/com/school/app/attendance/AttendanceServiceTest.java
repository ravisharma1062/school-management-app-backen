package com.school.app.attendance;

import com.school.app.common.notification.NotificationService;
import com.school.app.student.StudentRepository;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    private AttendanceService newService() {
        return new AttendanceService(attendanceRepository, studentRepository, attendanceMapper, notificationService, userRepository);
    }

    @Test
    void returnsZeroForEmptyRecords() {
        assertThat(newService().calculateAttendancePercentage(List.of())).isEqualTo(0.0);
    }

    @Test
    void countsPresentAndLateAsAttended() {
        Attendance present = Attendance.builder().status(AttendanceStatus.PRESENT).build();
        Attendance late = Attendance.builder().status(AttendanceStatus.LATE).build();
        Attendance absent = Attendance.builder().status(AttendanceStatus.ABSENT).build();
        Attendance excused = Attendance.builder().status(AttendanceStatus.EXCUSED).build();

        double percentage = newService().calculateAttendancePercentage(List.of(present, late, absent, excused));

        assertThat(percentage).isCloseTo(50.0, within(0.001));
    }

    @Test
    void allPresentYieldsFullPercentage() {
        Attendance a = Attendance.builder().status(AttendanceStatus.PRESENT).build();
        Attendance b = Attendance.builder().status(AttendanceStatus.PRESENT).build();

        double percentage = newService().calculateAttendancePercentage(List.of(a, b));

        assertThat(percentage).isCloseTo(100.0, within(0.001));
    }
}
