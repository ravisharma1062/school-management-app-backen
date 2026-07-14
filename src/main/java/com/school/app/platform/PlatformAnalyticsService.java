package com.school.app.platform;

import com.school.app.common.notification.NotificationLogRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformAnalyticsService {

    private final SchoolRepository schoolRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StudentRepository studentRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public PlatformAnalyticsDto get() {
        List<School> schools = schoolRepository.findAll();

        Map<SchoolStatus, Long> byStatus = schools.stream()
                .collect(Collectors.groupingBy(School::getStatus, Collectors.counting()));

        Map<PlanCode, Long> byPlan = schools.stream()
                .map(school -> subscriptionRepository.findBySchoolId(school.getId())
                        .map(sub -> sub.getPlan().getCode())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(planCode -> planCode, Collectors.counting()));

        // MT-6c usage metering — N+1-acceptable-for-MVP loop over schools, mirroring the
        // byPlan aggregation above; fine at today's provisioned-school volume.
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long totalActiveStudents = 0;
        long totalEmailsSentThisMonth = 0;
        long totalSmsSentThisMonth = 0;
        for (School school : schools) {
            totalActiveStudents += studentRepository.countActiveBySchoolIdBypassingTenantFilter(school.getId());
            totalEmailsSentThisMonth += notificationLogRepository
                    .countSentByChannelSinceBypassingTenantFilter(school.getId(), "EMAIL", monthStart);
            totalSmsSentThisMonth += notificationLogRepository
                    .countSentByChannelSinceBypassingTenantFilter(school.getId(), "SMS", monthStart);
        }

        return new PlatformAnalyticsDto(schools.size(), byStatus, byPlan,
                totalActiveStudents, totalEmailsSentThisMonth, totalSmsSentThisMonth);
    }
}
