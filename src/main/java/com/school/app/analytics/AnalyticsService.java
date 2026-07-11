package com.school.app.analytics;

import com.school.app.attendance.Attendance;
import com.school.app.attendance.AttendanceRepository;
import com.school.app.attendance.AttendanceStatus;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AttendanceRepository attendanceRepository;
    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;

    public List<AttendanceTrendPointDto> getAttendanceTrend(String studentClass, int rangeDays) {
        int days = rangeDays > 0 ? rangeDays : AtRiskThresholds.ATTENDANCE_WINDOW_DAYS;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);

        Map<LocalDate, List<Attendance>> byDate = attendanceRepository.findInRange(from, to, studentClass).stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        List<AttendanceTrendPointDto> trend = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<Attendance> dayRecords = byDate.getOrDefault(date, List.of());
            long present = countByStatus(dayRecords, AttendanceStatus.PRESENT);
            long absent = countByStatus(dayRecords, AttendanceStatus.ABSENT);
            long late = countByStatus(dayRecords, AttendanceStatus.LATE);
            long excused = countByStatus(dayRecords, AttendanceStatus.EXCUSED);
            double percentage = AtRiskThresholds.attendancePercentage(present + late, dayRecords.size());
            trend.add(new AttendanceTrendPointDto(date, present, absent, late, excused, percentage));
        }
        return trend;
    }

    public FeeSummaryDto getFeeSummary(String studentClass) {
        List<Fee> fees = studentClass != null
                ? feeRepository.findByStudentStudentClass(studentClass)
                : feeRepository.findAll();

        BigDecimal totalDue = fees.stream().map(Fee::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = fees.stream().map(Fee::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = totalDue.subtract(totalPaid);
        double collectionPercentage = totalDue.compareTo(BigDecimal.ZERO) == 0
                ? 0.0
                : totalPaid.divide(totalDue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();

        return new FeeSummaryDto(
                totalDue,
                totalPaid,
                totalOutstanding,
                collectionPercentage,
                countByStatus(fees, FeeStatus.PENDING),
                countByStatus(fees, FeeStatus.PARTIAL),
                countByStatus(fees, FeeStatus.PAID),
                countByStatus(fees, FeeStatus.OVERDUE));
    }

    public List<AtRiskStudentDto> getAtRiskStudents() {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(AtRiskThresholds.ATTENDANCE_WINDOW_DAYS - 1L);

        List<Student> activeStudents = studentRepository.findAll().stream().filter(Student::isActive).toList();

        Map<UUID, List<Attendance>> attendanceByStudent = attendanceRepository.findInRange(windowStart, today, null)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId()));

        Map<UUID, List<Fee>> feesByStudent = feeRepository.findAll().stream()
                .collect(Collectors.groupingBy(f -> f.getStudent().getId()));

        List<AtRiskStudentDto> atRisk = new ArrayList<>();
        for (Student student : activeStudents) {
            List<Attendance> records = attendanceByStudent.getOrDefault(student.getId(), List.of());
            long attended = records.stream()
                    .filter(a -> a.getStatus() == AttendanceStatus.PRESENT || a.getStatus() == AttendanceStatus.LATE)
                    .count();
            long total = records.size();
            boolean attendanceAtRisk = AtRiskThresholds.isAttendanceAtRisk(attended, total);
            Double attendancePercentage = total == 0 ? null : AtRiskThresholds.attendancePercentage(attended, total);

            Integer maxDaysOverdue = feesByStudent.getOrDefault(student.getId(), List.of()).stream()
                    .filter(f -> f.getStatus() != FeeStatus.PAID)
                    .map(f -> (int) ChronoUnit.DAYS.between(f.getDueDate(), today))
                    .filter(daysOverdue -> daysOverdue > 0)
                    .max(Integer::compareTo)
                    .orElse(null);
            boolean feeAtRisk = AtRiskThresholds.isFeeAtRisk(maxDaysOverdue);

            if (attendanceAtRisk || feeAtRisk) {
                atRisk.add(new AtRiskStudentDto(
                        student.getId(),
                        student.getName(),
                        student.getStudentClass(),
                        student.getSection(),
                        attendancePercentage,
                        attendanceAtRisk,
                        feeAtRisk,
                        maxDaysOverdue));
            }
        }
        return atRisk;
    }

    private long countByStatus(List<Attendance> records, AttendanceStatus status) {
        return records.stream().filter(a -> a.getStatus() == status).count();
    }

    private long countByStatus(List<Fee> fees, FeeStatus status) {
        return fees.stream().filter(f -> f.getStatus() == status).count();
    }
}
