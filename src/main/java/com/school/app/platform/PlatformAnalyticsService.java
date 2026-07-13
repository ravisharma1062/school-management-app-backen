package com.school.app.platform;

import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformAnalyticsService {

    private final SchoolRepository schoolRepository;
    private final SubscriptionRepository subscriptionRepository;

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

        return new PlatformAnalyticsDto(schools.size(), byStatus, byPlan);
    }
}
